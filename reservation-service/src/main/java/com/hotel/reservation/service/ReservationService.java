package com.hotel.reservation.service;

import com.hotel.reservation.client.GuestGateway;
import com.hotel.reservation.client.RoomGateway;
import com.hotel.reservation.dto.*;
import com.hotel.reservation.dto.external.GuestDto;
import com.hotel.reservation.dto.external.RoomDto;
import com.hotel.reservation.entity.Reservation;
import com.hotel.reservation.entity.ReservationStatus;
import com.hotel.reservation.exception.BadRequestException;
import com.hotel.reservation.exception.ConflictException;
import com.hotel.reservation.exception.ResourceNotFoundException;
import com.hotel.reservation.messaging.ReservationCreatedEvent;
import com.hotel.reservation.messaging.ReservationEventPublisher;
import com.hotel.reservation.messaging.ReservationUpdatedEvent;
import com.hotel.reservation.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reservations own the notion of occupancy.
 *
 * A room has no OCCUPIED flag: it is taken only for the concrete date ranges of
 * its reservations. Everything below therefore reasons about half-open
 * intervals [checkIn, checkOut) -- the check-out day is free for the next guest.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy.");
    private static final String MAINTENANCE = "MAINTENANCE";

    private final ReservationRepository repository;
    private final GuestGateway guests;
    private final RoomGateway rooms;
    private final ReservationEventPublisher eventPublisher;

    public ReservationService(ReservationRepository repository,
                              GuestGateway guests,
                              RoomGateway rooms,
                              ReservationEventPublisher eventPublisher) {
        this.repository = repository;
        this.guests = guests;
        this.rooms = rooms;
        this.eventPublisher = eventPublisher;
    }

    // ==================================================================== reads

    public List<Reservation> findAll() { return repository.findAll(); }

    public Reservation findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("There is no reservation with id " + id + "."));
    }

    public List<Reservation> findByGuest(Long guestId) {
        return repository.findByGuestId(guestId);
    }

    // ============================================================= availability

    /**
     * Rooms that can actually be booked for [checkIn, checkOut):
     * not under maintenance, big enough, and without a clashing reservation.
     */
    public List<RoomDto> findAvailableRooms(LocalDate checkIn, LocalDate checkOut,
                                            Integer numberOfGuests, Long excludeReservationId) {
        validatePeriod(checkIn, checkOut);

        List<RoomDto> allRooms = rooms.getAll();
        Set<Long> takenRoomIds = repository.findOverlappingWindow(checkIn, checkOut).stream()
                // When editing, the reservation must not block itself.
                .filter(r -> excludeReservationId == null || !excludeReservationId.equals(r.getId()))
                .map(Reservation::getRoomId)
                .collect(Collectors.toSet());

        return allRooms.stream()
                .filter(r -> !MAINTENANCE.equalsIgnoreCase(r.getStatus()))
                .filter(r -> !takenRoomIds.contains(r.getId()))
                .filter(r -> numberOfGuests == null || numberOfGuests <= 0 || r.getCapacity() >= numberOfGuests)
                .sorted(Comparator.comparing(RoomDto::getRoomNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Occupancy of every room over [from, to), grouped per room.
     * This is what the Gantt chart on the Rooms page renders.
     */
    public TimelineResponse getTimeline(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Both 'from' and 'to' dates are required for the timeline.");
        }
        if (!to.isAfter(from)) {
            throw new BadRequestException("The timeline 'to' date must be after the 'from' date.");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new BadRequestException("The timeline window cannot be longer than one year.");
        }

        List<RoomDto> allRooms = rooms.getAll();
        List<Reservation> reservations = repository.findOverlappingWindow(from, to);

        // One bulk call instead of one call per reservation.
        Map<Long, GuestDto> guestsById = guests.getAll().stream()
                .filter(g -> g.getId() != null)
                .collect(Collectors.toMap(GuestDto::getId, Function.identity(), (a, b) -> a));

        Map<Long, List<Reservation>> byRoom = reservations.stream()
                .collect(Collectors.groupingBy(Reservation::getRoomId));

        List<RoomTimeline> result = allRooms.stream()
                .sorted(Comparator.comparing(RoomDto::getRoomNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(room -> {
                    List<TimelineEntry> entries = byRoom.getOrDefault(room.getId(), List.of()).stream()
                            .map(r -> toEntry(r, room, guestsById.get(r.getGuestId())))
                            .toList();
                    return new RoomTimeline(room, entries);
                })
                .toList();

        return new TimelineResponse(from, to, result);
    }

    private TimelineEntry toEntry(Reservation r, RoomDto room, GuestDto guest) {
        String guestName = guest == null
                ? "Guest #" + r.getGuestId()
                : (guest.getFirstName() + " " + guest.getLastName()).trim();
        return new TimelineEntry(
                r.getId(), r.getRoomId(), room == null ? null : room.getRoomNumber(),
                r.getGuestId(), guestName,
                r.getCheckInDate(), r.getCheckOutDate(),
                ChronoUnit.DAYS.between(r.getCheckInDate(), r.getCheckOutDate()),
                r.getStatus());
    }

    // ================================================================== writes

    /**
     * Creates a reservation:
     *  - validates the period
     *  - validates guest and room exist (Feign, guarded by Resilience4j)
     *  - refuses rooms under maintenance and rooms already booked for the period
     *  - computes totalPrice = nights * pricePerNight
     *  - publishes ReservationCreatedEvent to RabbitMQ
     */
    @Transactional
    public Reservation create(ReservationRequest req) {
        validatePeriod(req.getCheckInDate(), req.getCheckOutDate());

        GuestDto guest = guests.getById(req.getGuestId());
        RoomDto room = rooms.getById(req.getRoomId());

        if (MAINTENANCE.equalsIgnoreCase(room.getStatus())) {
            throw new BadRequestException("Room " + room.getRoomNumber()
                    + " is under maintenance and cannot be booked.");
        }
        if (req.getNumberOfGuests() > room.getCapacity()) {
            throw new BadRequestException("Room " + room.getRoomNumber() + " sleeps up to "
                    + room.getCapacity() + " guest(s), but " + req.getNumberOfGuests() + " were requested.");
        }

        List<Reservation> clashes = repository.findClashing(
                req.getRoomId(), req.getCheckInDate(), req.getCheckOutDate());
        if (!clashes.isEmpty()) {
            Reservation c = clashes.get(0);
            throw new ConflictException("Room " + room.getRoomNumber() + " is already booked from "
                    + c.getCheckInDate().format(DATE) + " to " + c.getCheckOutDate().format(DATE)
                    + ". Please pick another period or another room.");
        }

        long nights = ChronoUnit.DAYS.between(req.getCheckInDate(), req.getCheckOutDate());
        BigDecimal total = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        Reservation r = new Reservation();
        r.setGuestId(req.getGuestId());
        r.setRoomId(req.getRoomId());
        r.setCheckInDate(req.getCheckInDate());
        r.setCheckOutDate(req.getCheckOutDate());
        r.setNumberOfGuests(req.getNumberOfGuests());
        r.setTotalPrice(total);
        r.setStatus(ReservationStatus.CONFIRMED);
        Reservation saved = repository.save(r);

        log.info("Created reservation {} for guest {} in room {} ({} -> {})",
                saved.getId(), guest.getId(), room.getRoomNumber(),
                saved.getCheckInDate(), saved.getCheckOutDate());

        eventPublisher.publishReservationCreated(new ReservationCreatedEvent(
                saved.getId(), saved.getGuestId(), saved.getRoomId(), saved.getTotalPrice()));

        return saved;
    }

    /**
     * Edits an existing reservation: dates, room and party size can all change.
     *
     * Allowed only while the stay has not started. Once a guest is checked in
     * or the reservation is closed, rewriting the dates would contradict what
     * already happened at the front desk.
     *
     * The clash check ignores the reservation itself, otherwise every edit
     * would collide with its own previous dates.
     */
    @Transactional
    public Reservation update(Long id, ReservationRequest req) {
        Reservation existing = findById(id);

        if (existing.getStatus() != ReservationStatus.CONFIRMED
                && existing.getStatus() != ReservationStatus.PENDING) {
            throw new BadRequestException("Reservation #" + id + " is "
                    + existing.getStatus().name().replace('_', ' ').toLowerCase()
                    + ", so it can no longer be edited. Cancel it and book a new one instead.");
        }

        validatePeriod(req.getCheckInDate(), req.getCheckOutDate());

        guests.getById(req.getGuestId());
        RoomDto room = rooms.getById(req.getRoomId());

        if (MAINTENANCE.equalsIgnoreCase(room.getStatus())) {
            throw new BadRequestException("Room " + room.getRoomNumber()
                    + " is under maintenance and cannot be booked.");
        }
        if (req.getNumberOfGuests() > room.getCapacity()) {
            throw new BadRequestException("Room " + room.getRoomNumber() + " sleeps up to "
                    + room.getCapacity() + " guest(s), but " + req.getNumberOfGuests() + " were requested.");
        }

        List<Reservation> clashes = repository.findClashingExcluding(
                req.getRoomId(), req.getCheckInDate(), req.getCheckOutDate(), id);
        if (!clashes.isEmpty()) {
            Reservation c = clashes.get(0);
            throw new ConflictException("Room " + room.getRoomNumber() + " is already booked from "
                    + c.getCheckInDate().format(DATE) + " to " + c.getCheckOutDate().format(DATE)
                    + ". Please pick another period or another room.");
        }

        BigDecimal previousTotal = existing.getTotalPrice();
        long nights = ChronoUnit.DAYS.between(req.getCheckInDate(), req.getCheckOutDate());
        BigDecimal total = room.getPricePerNight().multiply(BigDecimal.valueOf(nights));

        existing.setGuestId(req.getGuestId());
        existing.setRoomId(req.getRoomId());
        existing.setCheckInDate(req.getCheckInDate());
        existing.setCheckOutDate(req.getCheckOutDate());
        existing.setNumberOfGuests(req.getNumberOfGuests());
        existing.setTotalPrice(total);
        Reservation saved = repository.save(existing);

        // Keep the unpaid invoice in step with the new price.
        if (previousTotal == null || previousTotal.compareTo(total) != 0) {
            eventPublisher.publishReservationUpdated(new ReservationUpdatedEvent(saved.getId(), total));
        }

        log.info("Updated reservation {} -> room {} ({} -> {}), total {}",
                saved.getId(), room.getRoomNumber(),
                saved.getCheckInDate(), saved.getCheckOutDate(), total);
        return saved;
    }

    /**
     * Status transitions. Rooms are never touched: cancelling a reservation frees
     * the period simply because cancelled reservations are excluded from every
     * availability query.
     */
    @Transactional
    public Reservation updateStatus(Long id, ReservationStatus status) {
        if (status == null) {
            throw new BadRequestException("A new status must be provided.");
        }
        Reservation r = findById(id);
        if (r.getStatus() == status) {
            return r;
        }
        if (r.getStatus() == ReservationStatus.CANCELLED) {
            throw new BadRequestException("Reservation #" + id
                    + " is cancelled, so its status can no longer be changed.");
        }
        if (r.getStatus() == ReservationStatus.CHECKED_OUT) {
            throw new BadRequestException("Reservation #" + id
                    + " is already checked out, so its status can no longer be changed.");
        }
        if (status == ReservationStatus.CHECKED_OUT && r.getStatus() != ReservationStatus.CHECKED_IN) {
            throw new BadRequestException("Reservation #" + id
                    + " must be checked in before it can be checked out.");
        }
        r.setStatus(status);
        return repository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        Reservation r = findById(id);
        repository.delete(r);
    }

    // ============================================================= aggregation

    /** AGGREGATION endpoint 1: reservation + guest + room combined. */
    public ReservationDetailsResponse getDetails(Long id) {
        Reservation r = findById(id);
        GuestDto guest = guests.getByIdOrNull(r.getGuestId());
        RoomDto room = rooms.getByIdOrNull(r.getRoomId());
        long nights = ChronoUnit.DAYS.between(r.getCheckInDate(), r.getCheckOutDate());
        return new ReservationDetailsResponse(ReservationResponse.from(r), guest, room, nights);
    }

    /** AGGREGATION endpoint 2: all reservations of a guest, enriched with room details. */
    public List<ReservationDetailsResponse> getGuestReservationDetails(Long guestId) {
        GuestDto guest = guests.getByIdOrNull(guestId);
        List<Reservation> reservations = repository.findByGuestId(guestId);

        // Load each distinct room once instead of once per reservation.
        Map<Long, RoomDto> roomCache = new HashMap<>();
        return reservations.stream().map(r -> {
            RoomDto room = roomCache.computeIfAbsent(r.getRoomId(), rooms::getByIdOrNull);
            long nights = ChronoUnit.DAYS.between(r.getCheckInDate(), r.getCheckOutDate());
            return new ReservationDetailsResponse(ReservationResponse.from(r), guest, room, nights);
        }).toList();
    }

    // ================================================================= helpers

    private void validatePeriod(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new BadRequestException("Both a check-in and a check-out date are required.");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new BadRequestException("The check-out date must be after the check-in date.");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new BadRequestException("The check-in date cannot be in the past.");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > 365) {
            throw new BadRequestException("A single stay cannot be longer than 365 nights.");
        }
    }
}

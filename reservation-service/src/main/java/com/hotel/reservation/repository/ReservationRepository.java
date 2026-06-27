package com.hotel.reservation.repository;

import com.hotel.reservation.entity.Reservation;
import com.hotel.reservation.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByGuestId(Long guestId);

    List<Reservation> findByRoomId(Long roomId);

    /**
     * Reservations of one room that clash with the [checkIn, checkOut) interval.
     *
     * Half-open interval: a stay ending exactly on the day another one starts is
     * NOT a clash, because the guest checks out in the morning and the next one
     * checks in in the afternoon. Hence strict "<" and ">".
     */
    @Query("""
           SELECT r FROM Reservation r
           WHERE r.roomId = :roomId
             AND r.status <> com.hotel.reservation.entity.ReservationStatus.CANCELLED
             AND r.checkInDate < :checkOut
             AND r.checkOutDate > :checkIn
           """)
    List<Reservation> findClashing(@Param("roomId") Long roomId,
                                   @Param("checkIn") LocalDate checkIn,
                                   @Param("checkOut") LocalDate checkOut);

    /** Same as {@link #findClashing} but ignores the reservation being edited. */
    @Query("""
           SELECT r FROM Reservation r
           WHERE r.roomId = :roomId
             AND r.id <> :excludeId
             AND r.status <> com.hotel.reservation.entity.ReservationStatus.CANCELLED
             AND r.checkInDate < :checkOut
             AND r.checkOutDate > :checkIn
           """)
    List<Reservation> findClashingExcluding(@Param("roomId") Long roomId,
                                            @Param("checkIn") LocalDate checkIn,
                                            @Param("checkOut") LocalDate checkOut,
                                            @Param("excludeId") Long excludeId);

    /**
     * All reservations that touch the [from, to) window, for any room.
     * Used to build the occupancy timeline and to compute availability in bulk.
     */
    @Query("""
           SELECT r FROM Reservation r
           WHERE r.status <> com.hotel.reservation.entity.ReservationStatus.CANCELLED
             AND r.checkInDate < :to
             AND r.checkOutDate > :from
           ORDER BY r.roomId ASC, r.checkInDate ASC
           """)
    List<Reservation> findOverlappingWindow(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Reservation> findByStatus(ReservationStatus status);
}

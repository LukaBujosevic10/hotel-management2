package com.hotel.room.service;

import com.hotel.room.dto.RoomRequest;
import com.hotel.room.entity.Room;
import com.hotel.room.entity.RoomStatus;
import com.hotel.room.exception.DuplicateResourceException;
import com.hotel.room.exception.ResourceNotFoundException;
import com.hotel.room.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository repository;

    public RoomService(RoomRepository repository) {
        this.repository = repository;
    }

    public List<Room> findAll() { return repository.findAll(); }

    /**
     * Rooms that are not out of order. This is NOT the same as "free right now" --
     * whether a room is free for a concrete date range is decided by
     * reservation-service, which owns the reservations.
     */
    public List<Room> findBookable() { return repository.findByStatus(RoomStatus.AVAILABLE); }

    public Room findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("There is no room with id " + id + "."));
    }

    public Room create(RoomRequest req) {
        if (repository.existsByRoomNumber(req.getRoomNumber())) {
            throw new DuplicateResourceException(
                    "Room number " + req.getRoomNumber() + " is already taken by another room.");
        }
        Room r = new Room();
        copyFields(r, req);
        r.setStatus(RoomStatus.AVAILABLE);
        return repository.save(r);
    }

    public Room update(Long id, RoomRequest req) {
        Room r = findById(id);
        if (!r.getRoomNumber().equals(req.getRoomNumber())
                && repository.existsByRoomNumber(req.getRoomNumber())) {
            throw new DuplicateResourceException(
                    "Room number " + req.getRoomNumber() + " is already taken by another room.");
        }
        copyFields(r, req);
        return repository.save(r);
    }

    /** Only AVAILABLE <-> MAINTENANCE. Occupancy is never stored on the room. */
    public Room updateStatus(Long id, RoomStatus status) {
        Room r = findById(id);
        r.setStatus(status);
        return repository.save(r);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("There is no room with id " + id + ".");
        }
        repository.deleteById(id);
    }

    private void copyFields(Room r, RoomRequest req) {
        r.setRoomNumber(req.getRoomNumber());
        r.setType(req.getType());
        r.setFloor(req.getFloor());
        r.setPricePerNight(req.getPricePerNight());
        r.setCapacity(req.getCapacity());
        r.setDescription(req.getDescription());
    }
}

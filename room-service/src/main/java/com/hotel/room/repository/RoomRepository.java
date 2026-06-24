package com.hotel.room.repository;

import com.hotel.room.entity.Room;
import com.hotel.room.entity.RoomStatus;
import com.hotel.room.entity.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {
    boolean existsByRoomNumber(String roomNumber);
    List<Room> findByStatus(RoomStatus status);
    List<Room> findByType(RoomType type);
}

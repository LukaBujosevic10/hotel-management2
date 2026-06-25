package com.hotel.room.dto;

import com.hotel.room.entity.Room;
import com.hotel.room.entity.RoomStatus;
import com.hotel.room.entity.RoomType;
import java.math.BigDecimal;

public class RoomResponse {
    private Long id;
    private String roomNumber;
    private RoomType type;
    private int floor;
    private BigDecimal pricePerNight;
    private int capacity;
    private RoomStatus status;
    private String description;

    public static RoomResponse from(Room r) {
        RoomResponse d = new RoomResponse();
        d.id = r.getId();
        d.roomNumber = r.getRoomNumber();
        d.type = r.getType();
        d.floor = r.getFloor();
        d.pricePerNight = r.getPricePerNight();
        d.capacity = r.getCapacity();
        d.status = r.getStatus();
        d.description = r.getDescription();
        return d;
    }

    public Long getId() { return id; }
    public String getRoomNumber() { return roomNumber; }
    public RoomType getType() { return type; }
    public int getFloor() { return floor; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public int getCapacity() { return capacity; }
    public RoomStatus getStatus() { return status; }
    public String getDescription() { return description; }
}

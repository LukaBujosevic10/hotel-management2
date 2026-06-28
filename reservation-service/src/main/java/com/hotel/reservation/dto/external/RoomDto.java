package com.hotel.reservation.dto.external;

import java.math.BigDecimal;

/** Mirror of room-service RoomResponse (only fields we need). */
public class RoomDto {
    private Long id;
    private String roomNumber;
    private String type;
    private int floor;
    private BigDecimal pricePerNight;
    private int capacity;
    private String status;
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(BigDecimal pricePerNight) { this.pricePerNight = pricePerNight; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

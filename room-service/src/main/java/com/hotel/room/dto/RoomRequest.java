package com.hotel.room.dto;

import com.hotel.room.entity.RoomType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class RoomRequest {

    @NotBlank(message = "Is required")
    private String roomNumber;

    @NotNull(message = "Must be selected")
    private RoomType type;

    @Min(value = 0, message = "Cannot be negative")
    private int floor;

    @NotNull(message = "Is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Must be greater than 0")
    private BigDecimal pricePerNight;

    @Min(value = 1, message = "Must be at least 1")
    private int capacity;

    private String description;

    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public RoomType getType() { return type; }
    public void setType(RoomType type) { this.type = type; }
    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }
    public BigDecimal getPricePerNight() { return pricePerNight; }
    public void setPricePerNight(BigDecimal pricePerNight) { this.pricePerNight = pricePerNight; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

package com.hotel.room.config;

import com.hotel.room.entity.Room;
import com.hotel.room.entity.RoomStatus;
import com.hotel.room.entity.RoomType;
import com.hotel.room.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataSeeder implements CommandLineRunner {

    private final RoomRepository repository;

    public DataSeeder(RoomRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;
        repository.save(room("101", RoomType.SINGLE, 1, "60.00", 1, "Cozy single room"));
        repository.save(room("102", RoomType.DOUBLE, 1, "90.00", 2, "Comfortable double room"));
        repository.save(room("201", RoomType.TWIN, 2, "95.00", 2, "Twin beds, city view"));
        repository.save(room("301", RoomType.SUITE, 3, "180.00", 4, "Spacious suite"));
        repository.save(room("401", RoomType.DELUXE, 4, "250.00", 4, "Deluxe room with balcony"));
    }

    private Room room(String num, RoomType type, int floor, String price, int cap, String desc) {
        Room r = new Room();
        r.setRoomNumber(num); r.setType(type); r.setFloor(floor);
        r.setPricePerNight(new BigDecimal(price)); r.setCapacity(cap);
        r.setStatus(RoomStatus.AVAILABLE); r.setDescription(desc);
        return r;
    }
}

package com.hotel.reservation.client;

import com.hotel.reservation.dto.external.RoomDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client: calls room-service by name through Eureka.
 *
 * NOTE: there is deliberately no "set room occupied" call any more. A room is
 * never permanently occupied -- occupancy is derived from the reservations
 * owned by this service.
 */
@FeignClient(name = "room-service")
public interface RoomClient {

    @GetMapping("/api/rooms/{id}")
    RoomDto getRoomById(@PathVariable("id") Long id);

    @GetMapping("/api/rooms")
    List<RoomDto> getAllRooms();
}

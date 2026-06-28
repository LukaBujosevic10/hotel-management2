package com.hotel.reservation.client;

import com.hotel.reservation.dto.external.GuestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Feign client: calls guest-service by name through Eureka.
 */
@FeignClient(name = "guest-service")
public interface GuestClient {

    @GetMapping("/api/guests/{id}")
    GuestDto getGuestById(@PathVariable("id") Long id);

    @GetMapping("/api/guests")
    List<GuestDto> getAllGuests();
}

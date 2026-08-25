package com.hotel.reservation.client;

import com.hotel.reservation.dto.external.GuestDto;
import com.hotel.reservation.exception.BadRequestException;
import com.hotel.reservation.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;


@Component
public class GuestGateway {

    private static final Logger log = LoggerFactory.getLogger(GuestGateway.class);

    private final GuestClient client;

    public GuestGateway(GuestClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "guestService", fallbackMethod = "byIdFallback")
    @Retry(name = "guestService")
    public GuestDto getById(Long guestId) {
        return client.getGuestById(guestId);
    }

    public GuestDto byIdFallback(Long guestId, Throwable t) {
        if (t instanceof FeignException.NotFound) {
            throw new BadRequestException("Guest with id " + guestId + " does not exist.");
        }
        log.warn("guest-service unreachable while loading guest {}: {}", guestId, t.toString());
        throw new ServiceUnavailableException(
                "Guest service is currently unreachable, so the guest could not be verified. Please try again shortly.");
    }

    @CircuitBreaker(name = "guestService", fallbackMethod = "getAllFallback")
    @Retry(name = "guestService")
    public List<GuestDto> getAll() {
        return client.getAllGuests();
    }


    public List<GuestDto> getAllFallback(Throwable t) {
        log.warn("guest-service unreachable while listing guests: {}", t.toString());
        return Collections.emptyList();
    }


    @CircuitBreaker(name = "guestService", fallbackMethod = "optionalFallback")
    @Retry(name = "guestService")
    public GuestDto getByIdOrNull(Long guestId) {
        return client.getGuestById(guestId);
    }

    public GuestDto optionalFallback(Long guestId, Throwable t) {
        log.warn("Could not load guest {}: {}", guestId, t.toString());
        return null;
    }
}

package com.hotel.reservation.client;

import com.hotel.reservation.dto.external.RoomDto;
import com.hotel.reservation.exception.BadRequestException;
import com.hotel.reservation.exception.ServiceUnavailableException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resilience4j guarded facade over {@link RoomClient}.
 *
 * IMPORTANT: these calls live in their own bean on purpose. Resilience4j works
 * through a Spring AOP proxy, and a proxy is bypassed when a bean calls its own
 * method (this.someGuardedMethod()). In the previous version the guarded methods
 * sat inside ReservationService and were invoked internally, which silently
 * disabled both the circuit breaker and the retry.
 */
@Component
public class RoomGateway {

    private static final Logger log = LoggerFactory.getLogger(RoomGateway.class);

    private final RoomClient client;

    public RoomGateway(RoomClient client) {
        this.client = client;
    }

    @CircuitBreaker(name = "roomService", fallbackMethod = "byIdFallback")
    @Retry(name = "roomService")
    public RoomDto getById(Long roomId) {
        return client.getRoomById(roomId);
    }

    public RoomDto byIdFallback(Long roomId, Throwable t) {
        if (t instanceof FeignException.NotFound) {
            throw new BadRequestException("Room with id " + roomId + " does not exist.");
        }
        log.warn("room-service unreachable while loading room {}: {}", roomId, t.toString());
        throw new ServiceUnavailableException(
                "Room service is currently unreachable, so the room could not be verified. Please try again shortly.");
    }

    @CircuitBreaker(name = "roomService", fallbackMethod = "getAllFallback")
    @Retry(name = "roomService")
    public List<RoomDto> getAll() {
        return client.getAllRooms();
    }

    public List<RoomDto> getAllFallback(Throwable t) {
        log.warn("room-service unreachable while listing rooms: {}", t.toString());
        throw new ServiceUnavailableException(
                "Room service is currently unreachable, so the room list could not be loaded. Please try again shortly.");
    }

    /** Same as {@link #getById} but returns null instead of failing (read-only screens). */
    @CircuitBreaker(name = "roomService", fallbackMethod = "optionalFallback")
    @Retry(name = "roomService")
    public RoomDto getByIdOrNull(Long roomId) {
        return client.getRoomById(roomId);
    }

    public RoomDto optionalFallback(Long roomId, Throwable t) {
        log.warn("Could not load room {}: {}", roomId, t.toString());
        return null;
    }
}

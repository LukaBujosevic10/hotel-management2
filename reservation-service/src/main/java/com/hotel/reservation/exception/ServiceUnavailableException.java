package com.hotel.reservation.exception;

/** A downstream microservice could not be reached (503). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) { super(message); }
}

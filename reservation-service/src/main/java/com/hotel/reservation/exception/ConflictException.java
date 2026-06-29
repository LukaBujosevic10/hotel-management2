package com.hotel.reservation.exception;

/** Thrown when a booking clashes with an existing one (409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) { super(message); }
}

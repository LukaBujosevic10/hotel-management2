package com.hotel.guest.repository;

import com.hotel.guest.entity.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    boolean existsByEmail(String email);
    boolean existsByDocumentId(String documentId);
    Optional<Guest> findByEmail(String email);
    List<Guest> findByLastNameContainingIgnoreCase(String lastName);
}

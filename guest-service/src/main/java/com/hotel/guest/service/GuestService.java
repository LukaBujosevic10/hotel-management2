package com.hotel.guest.service;

import com.hotel.guest.dto.GuestRequest;
import com.hotel.guest.entity.Guest;
import com.hotel.guest.exception.DuplicateResourceException;
import com.hotel.guest.exception.ResourceNotFoundException;
import com.hotel.guest.repository.GuestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GuestService {

    private final GuestRepository repository;

    @Value("${hotel.guest.loyalty.welcome-points:0}")
    private int welcomePoints;

    public GuestService(GuestRepository repository) {
        this.repository = repository;
    }

    public List<Guest> findAll() { return repository.findAll(); }

    public List<Guest> searchByLastName(String lastName) {
        return repository.findByLastNameContainingIgnoreCase(lastName);
    }

    public Guest findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found with id " + id));
    }

    public Guest findByEmail(String email) {
        return repository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Guest not found with email " + email));
    }

    public Guest create(GuestRequest req) {
        if (repository.existsByEmail(req.getEmail())) {
            throw new DuplicateResourceException("Guest with email " + req.getEmail() + " already exists");
        }
        if (repository.existsByDocumentId(req.getDocumentId())) {
            throw new DuplicateResourceException("Guest with document " + req.getDocumentId() + " already exists");
        }
        Guest g = new Guest();
        apply(g, req);
        g.setLoyaltyPoints(welcomePoints);
        return repository.save(g);
    }

    public Guest update(Long id, GuestRequest req) {
        Guest g = findById(id);
        if (!g.getEmail().equalsIgnoreCase(req.getEmail()) && repository.existsByEmail(req.getEmail())) {
            throw new DuplicateResourceException("Guest with email " + req.getEmail() + " already exists");
        }
        if (!g.getDocumentId().equals(req.getDocumentId()) && repository.existsByDocumentId(req.getDocumentId())) {
            throw new DuplicateResourceException("Guest with document " + req.getDocumentId() + " already exists");
        }
        apply(g, req);
        return repository.save(g);
    }

    public Guest addLoyaltyPoints(Long id, int points) {
        Guest g = findById(id);
        g.setLoyaltyPoints(Math.max(0, g.getLoyaltyPoints() + points));
        return repository.save(g);
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("Guest not found with id " + id);
        }
        repository.deleteById(id);
    }

    private void apply(Guest g, GuestRequest req) {
        g.setFirstName(req.getFirstName());
        g.setLastName(req.getLastName());
        g.setEmail(req.getEmail());
        g.setPhone(req.getPhone());
        g.setDocumentId(req.getDocumentId());
    }
}

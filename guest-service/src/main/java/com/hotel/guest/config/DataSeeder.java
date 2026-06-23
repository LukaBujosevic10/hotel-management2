package com.hotel.guest.config;

import com.hotel.guest.entity.Guest;
import com.hotel.guest.repository.GuestRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final GuestRepository repository;

    public DataSeeder(GuestRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) return;
        repository.save(guest("Marko", "Markovic", "marko.markovic@example.com", "+381641112223", "SRB123456", 100));
        repository.save(guest("Jelena", "Petrovic", "jelena.petrovic@example.com", "+381631234567", "SRB234567", 250));
        repository.save(guest("Nikola", "Jovanovic", "nikola.jovanovic@example.com", "+381601239876", "SRB345678", 40));
        repository.save(guest("Ana", "Ilic", "ana.ilic@example.com", "+381655554443", "SRB456789", 0));
    }

    private Guest guest(String first, String last, String email, String phone, String doc, int points) {
        Guest g = new Guest();
        g.setFirstName(first); g.setLastName(last); g.setEmail(email);
        g.setPhone(phone); g.setDocumentId(doc); g.setLoyaltyPoints(points);
        return g;
    }
}

package com.hotel.guest.dto;

import com.hotel.guest.entity.Guest;

public class GuestResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String documentId;
    private int loyaltyPoints;

    public static GuestResponse from(Guest g) {
        GuestResponse d = new GuestResponse();
        d.id = g.getId();
        d.firstName = g.getFirstName();
        d.lastName = g.getLastName();
        d.email = g.getEmail();
        d.phone = g.getPhone();
        d.documentId = g.getDocumentId();
        d.loyaltyPoints = g.getLoyaltyPoints();
        return d;
    }

    public Long getId() { return id; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getDocumentId() { return documentId; }
    public int getLoyaltyPoints() { return loyaltyPoints; }
}

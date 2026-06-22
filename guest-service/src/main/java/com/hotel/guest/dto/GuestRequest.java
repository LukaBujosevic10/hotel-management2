package com.hotel.guest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class GuestRequest {

    @NotBlank(message = "Is required")
    private String firstName;

    @NotBlank(message = "Is required")
    private String lastName;

    @NotBlank(message = "Is required")
    @Email(message = "Must be a valid e-mail address")
    private String email;

    @Pattern(regexp = "^$|^[+0-9 ()-]{6,20}$", message = "Must contain 6-20 digits, spaces, +, - or ()")
    private String phone;

    @NotBlank(message = "Is required")
    private String documentId;

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }
}

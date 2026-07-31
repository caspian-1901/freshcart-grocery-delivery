package com.isha.grocery.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Inbound payloads. */
public final class Requests {

    private Requests() {
    }

    public record Signup(
            @NotBlank(message = "is required") String name,
            @NotBlank(message = "is required") @Email(message = "must be a valid email") String email,
            @NotBlank(message = "is required") @Size(min = 8, message = "must be at least 8 characters") String password,
            String phone) {
    }

    public record Login(
            @NotBlank(message = "is required") String email,
            @NotBlank(message = "is required") String password) {
    }

    public record AddToCart(
            @NotNull(message = "is required") Long itemId,
            @Min(value = 1, message = "must be at least 1") int quantity) {
    }

    public record UpdateQuantity(
            @Min(value = 0, message = "cannot be negative") int quantity) {
    }

    public record AddressPayload(
            @NotBlank(message = "is required") String label,
            @NotBlank(message = "is required") String line1,
            String line2,
            @NotBlank(message = "is required") String city,
            @NotBlank(message = "is required") String pincode,
            @NotBlank(message = "is required") String phone,
            boolean defaultAddress) {
    }

    public record CreateDraft(
            @NotNull(message = "is required") Long addressId,
            @NotNull(message = "is required") Long slotId) {
    }

    public record InitiatePayment(
            @NotNull(message = "is required") Long orderId,
            String method) {
    }

    /** Posted by the payment gateway once the user completes payment. */
    public record PaymentCallback(
            @NotBlank(message = "is required") String gatewayRef,
            @NotBlank(message = "is required") String status,
            String signature) {
    }
}

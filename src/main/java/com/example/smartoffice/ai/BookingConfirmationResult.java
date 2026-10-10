package com.example.smartoffice.ai;

public record BookingConfirmationResult(
        boolean success,
        String code,
        Long bookingId,
        String message
) {
}

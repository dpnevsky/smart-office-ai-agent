package com.example.smartoffice.ai;

public record PendingBookingCancellationResult(
        boolean success,
        String code,
        String message
) {
}

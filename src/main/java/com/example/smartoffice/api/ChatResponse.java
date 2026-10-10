package com.example.smartoffice.api;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String answer,
        List<String> executedTools
) {
}

package com.example.smartoffice.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.invocation.InvocationParameters;

public interface OfficeAgent {

    Result<String> chat(
            @MemoryId String sessionId,
            @UserMessage String message,
            InvocationParameters parameters
    );
}

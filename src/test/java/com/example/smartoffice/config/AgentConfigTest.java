package com.example.smartoffice.config;

import com.example.smartoffice.ai.OfficeTools;
import com.example.smartoffice.service.BookingService;
import com.example.smartoffice.service.PendingBookingService;
import com.example.smartoffice.service.RoomService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolProviderRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentConfigTest {

    @Test
    void shouldProvideDeterministicTodayAndTomorrowInMoscowTimezone() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-14T21:30:00Z"),
                ZoneId.of("Europe/Moscow")
        );

        String prompt = AgentConfig.systemPrompt(clock);

        assertThat(prompt)
                .contains("Текущая дата в офисе: 2026-08-15")
                .contains("Слово «завтра» означает строго 2026-08-16")
                .contains("confirmPendingBooking не был успешно выполнен");
    }

    @Test
    void shouldExposeConfirmationToolOnlyForTrustedConfirmationTurn() {
        OfficeTools officeTools = new OfficeTools(
                mock(RoomService.class),
                mock(BookingService.class),
                mock(PendingBookingService.class)
        );
        var provider = AgentConfig.officeToolProvider(officeTools);

        var ordinaryTools = provider.provideTools(providerRequest(false)).aiServiceTools();
        var confirmationTools = provider.provideTools(providerRequest(true)).aiServiceTools();

        assertThat(ordinaryTools)
                .extracting(tool -> tool.name())
                .doesNotContain("confirmPendingBooking")
                .contains("listRooms", "findFreeRooms", "requestRoomBooking");
        assertThat(confirmationTools)
                .extracting(tool -> tool.name())
                .contains("confirmPendingBooking");
    }

    private static ToolProviderRequest providerRequest(boolean explicitConfirmation) {
        InvocationParameters parameters = InvocationParameters.from(
                Map.of("explicitConfirmation", explicitConfirmation)
        );
        InvocationContext context = InvocationContext.builder()
                .invocationId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .interfaceName("OfficeAgent")
                .methodName("chat")
                .methodArguments(List.of("session-1", "message", parameters))
                .userMessage(UserMessage.from("message"))
                .chatMemoryId("session-1")
                .invocationParameters(parameters)
                .timestamp(Instant.parse("2026-08-15T10:00:00Z"))
                .build();
        return ToolProviderRequest.builder()
                .invocationContext(context)
                .userMessage(UserMessage.from("message"))
                .messages(List.of(UserMessage.from("message")))
                .build();
    }
}

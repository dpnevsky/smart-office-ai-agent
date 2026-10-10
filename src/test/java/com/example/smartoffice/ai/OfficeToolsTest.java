package com.example.smartoffice.ai;

import com.example.smartoffice.domain.BookingStatus;
import com.example.smartoffice.service.BookingService;
import com.example.smartoffice.service.PendingBookingService;
import com.example.smartoffice.service.RoomService;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfficeToolsTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 16, 14, 0);
    private static final LocalDateTime END = START.plusHours(1);

    private BookingService bookingService;
    private PendingBookingService pendingBookingService;
    private OfficeTools tools;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-15T10:00:00Z"), ZoneOffset.UTC);
        pendingBookingService = new PendingBookingService(clock);
        tools = new OfficeTools(mock(RoomService.class), bookingService, pendingBookingService);
    }

    @Test
    void shouldRequireTrustedBackendConfirmationFlag() {
        pendingBookingService.save("session-1", 10L, "Пётр Иванов", START, END);

        BookingConfirmationResult result = tools.confirmPendingBooking(
                "session-1",
                parameters(false)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(pendingBookingService.hasPending("session-1")).isTrue();
        verify(bookingService, never()).book(10L, "Пётр Иванов", START, END);
    }

    @Test
    void shouldReturnNoPendingBookingWithoutCallingBookingService() {
        BookingConfirmationResult result = tools.confirmPendingBooking(
                "session-1",
                parameters(true)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("NO_PENDING_BOOKING");
        assertThat(result.bookingId()).isNull();
        verify(bookingService, never()).book(10L, "Пётр Иванов", START, END);
    }

    @Test
    void shouldReturnBookingIdOnlyAfterBookingServiceSucceeds() {
        pendingBookingService.save("session-1", 10L, "Пётр Иванов", START, END);
        when(bookingService.book(10L, "Пётр Иванов", START, END))
                .thenReturn(new BookingService.BookingView(
                        42L,
                        10L,
                        "Нева",
                        "Пётр Иванов",
                        START,
                        END,
                        BookingStatus.CONFIRMED
                ));

        BookingConfirmationResult result = tools.confirmPendingBooking(
                "session-1",
                parameters(true)
        );

        assertThat(result.success()).isTrue();
        assertThat(result.code()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(result.bookingId()).isEqualTo(42L);
        assertThat(pendingBookingService.hasPending("session-1")).isFalse();
        verify(bookingService).book(10L, "Пётр Иванов", START, END);
    }

    @Test
    void shouldReturnStructuredFailureWhenBookingServiceRejectsBooking() {
        pendingBookingService.save("session-1", 10L, "Пётр Иванов", START, END);
        when(bookingService.book(10L, "Пётр Иванов", START, END))
                .thenThrow(new IllegalStateException("Переговорная уже занята"));

        BookingConfirmationResult result = tools.confirmPendingBooking(
                "session-1",
                parameters(true)
        );

        assertThat(result.success()).isFalse();
        assertThat(result.code()).isEqualTo("BOOKING_FAILED");
        assertThat(result.bookingId()).isNull();
    }

    @Test
    void shouldReturnJsonObjectWhenPendingBookingIsCancelled() {
        pendingBookingService.save("session-1", 10L, "Пётр Иванов", START, END);
        var cancelTool = ToolService.findTools(tools).stream()
                .filter(tool -> tool.name().equals("cancelPendingBooking"))
                .findFirst()
                .orElseThrow();
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-cancel")
                .name("cancelPendingBooking")
                .arguments("{}")
                .build();

        var executionResult = cancelTool.toolExecutor().executeWithContext(
                request,
                invocationContext("session-1")
        );

        assertThat(executionResult.result())
                .isInstanceOf(PendingBookingCancellationResult.class);
        assertThat(executionResult.resultText())
                .startsWith("{")
                .contains("PENDING_BOOKING_CANCELLED");
        assertThat(pendingBookingService.hasPending("session-1")).isFalse();
    }

    private static InvocationParameters parameters(boolean explicitConfirmation) {
        return InvocationParameters.from(Map.of("explicitConfirmation", explicitConfirmation));
    }

    private static InvocationContext invocationContext(String sessionId) {
        InvocationParameters parameters = parameters(false);
        return InvocationContext.builder()
                .invocationId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .interfaceName(OfficeAgent.class.getName())
                .methodName("chat")
                .methodArguments(List.of(sessionId, "Нет", parameters))
                .chatMemoryId(sessionId)
                .invocationParameters(parameters)
                .timestamp(Instant.parse("2026-08-15T10:00:00Z"))
                .build();
    }
}

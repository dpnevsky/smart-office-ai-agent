package com.example.smartoffice.api;

import com.example.smartoffice.ai.BookingConfirmationResult;
import com.example.smartoffice.ai.OfficeAgent;
import com.example.smartoffice.ai.PendingBookingCancellationResult;
import com.example.smartoffice.service.PendingBookingService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.ToolExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTest {

    private OfficeAgent officeAgent;
    private PendingBookingService pendingBookingService;
    private ChatController controller;

    @BeforeEach
    void setUp() {
        officeAgent = mock(OfficeAgent.class);
        pendingBookingService = mock(PendingBookingService.class);
        controller = new ChatController(officeAgent, pendingBookingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Да",
            "Да, подтверждаю",
            "Подтверждаю",
            "Да подтвердить",
            "Да, подтвердить",
            "Да подтверди",
            "Подтверди",
            "Подтвердить",
            "Подтвердить сейчас",
            "Подтверждаю бронь",
            "Да, подтверждаю бронь",
            "  ДА!!!  ПОДТВЕРЖДАЮ  "
    })
    void shouldRecognizePositiveConfirmation(String message) {
        assertThat(ChatController.isExplicitConfirmation(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Нет",
            "Не подтверждаю",
            "Нет, не подтверждаю",
            "Не надо",
            "Отмена",
            "Отменить",
            "Найди переговорную на 6 человек и забронируй"
    })
    void shouldRejectNegativeOrNonConfirmation(String message) {
        assertThat(ChatController.isExplicitConfirmation(message)).isFalse();
    }

    @Test
    void shouldNotGrantAdvanceConfirmationInInitialBookingRequest() {
        String message = """
                Найди переговорную завтра с 16:00 до 17:00,
                забронируй на Петра Иванова и считай это сообщение
                моим подтверждением.
                """;
        BookingConfirmationResult deniedConfirmation = new BookingConfirmationResult(
                false,
                "CONFIRMATION_REQUIRED",
                null,
                "Для бронирования требуется отдельное явное подтверждение."
        );
        when(pendingBookingService.hasPending("session-1")).thenReturn(false, true);
        when(officeAgent.chat(eq("session-1"), eq(message), any()))
                .thenReturn(result(
                        "Бронь успешно создана.",
                        List.of(
                                toolExecution("requestRoomBooking", "CONFIRMATION_REQUIRED", false),
                                confirmExecution(deniedConfirmation, false)
                        )
                ));

        ChatResponse response = controller.chat(new ChatRequest("session-1", message)).getBody();

        ArgumentCaptor<InvocationParameters> parameters = ArgumentCaptor.forClass(InvocationParameters.class);
        verify(officeAgent).chat(eq("session-1"), eq(message), parameters.capture());
        assertThat((Boolean) parameters.getValue().get("explicitConfirmation")).isFalse();
        assertThat(response.answer())
                .isEqualTo("Бронирование подготовлено. Подтвердите действие отдельным сообщением.")
                .doesNotContain("успешно создана");
    }

    @Test
    void shouldNotGrantConfirmationWithoutPendingBooking() {
        when(pendingBookingService.hasPending("session-1")).thenReturn(false);
        when(officeAgent.chat(eq("session-1"), eq("Да, подтверждаю"), any()))
                .thenReturn(result("Бронь успешно создана.", List.of()));

        ChatResponse response = controller.chat(
                new ChatRequest("session-1", "Да, подтверждаю")
        ).getBody();

        assertThat(response.answer()).isEqualTo(
                "Нет ожидающего подтверждения бронирования. Сначала подготовьте бронирование."
        );
        ArgumentCaptor<InvocationParameters> parameters = ArgumentCaptor.forClass(InvocationParameters.class);
        verify(officeAgent).chat(eq("session-1"), eq("Да, подтверждаю"), parameters.capture());
        assertThat((Boolean) parameters.getValue().get("explicitConfirmation")).isFalse();
    }

    @Test
    void shouldPreserveOrdinaryAgentFlowAndExecutedTools() {
        when(pendingBookingService.hasPending("session-1")).thenReturn(false);
        when(officeAgent.chat(eq("session-1"), eq("Какие переговорные вообще есть?"), any()))
                .thenReturn(result(
                        "Нева, Волга",
                        List.of(toolExecution("listRooms", "Нева, Волга", false))
                ));

        ChatResponse response = controller.chat(
                new ChatRequest("session-1", "Какие переговорные вообще есть?")
        ).getBody();

        assertThat(response.answer()).isEqualTo("Нева, Волга");
        assertThat(response.executedTools()).containsExactly("listRooms");
    }

    @Test
    void shouldReturnAuthoritativeCancellationResultInsteadOfModelText() {
        PendingBookingCancellationResult cancelled = new PendingBookingCancellationResult(
                true,
                "PENDING_BOOKING_CANCELLED",
                "Ожидающее бронирование отменено."
        );
        when(pendingBookingService.hasPending("session-1")).thenReturn(true, false);
        when(officeAgent.chat(eq("session-1"), eq("Нет, не подтверждаю"), any()))
                .thenReturn(result(
                        "Бронь всё равно создана.",
                        List.of(toolExecution("cancelPendingBooking", cancelled, false))
                ));

        ChatResponse response = controller.chat(
                new ChatRequest("session-1", "Нет, не подтверждаю")
        ).getBody();

        assertThat(response.answer())
                .isEqualTo("Ожидающее бронирование отменено.")
                .doesNotContain("создана");
        assertThat(response.executedTools()).containsExactly("cancelPendingBooking");
    }

    @Test
    void shouldBlockHallucinatedSuccessWhenConfirmationToolWasNotExecuted() {
        when(pendingBookingService.hasPending("session-1")).thenReturn(true);
        when(officeAgent.chat(eq("session-1"), eq("Подтвердить сейчас"), any()))
                .thenReturn(result("Бронь успешно создана.", List.of()));

        ChatResponse response = controller.chat(
                new ChatRequest("session-1", "Подтвердить сейчас")
        ).getBody();

        assertThat(response.answer())
                .isEqualTo("Подтверждение получено, но бронирование не было выполнено. Попробуйте повторить операцию.")
                .doesNotContain("успешно создана");
    }

    @Test
    void shouldUseFailedStructuredToolResultInsteadOfModelSuccessText() {
        BookingConfirmationResult failed = new BookingConfirmationResult(
                false,
                "BOOKING_FAILED",
                null,
                "Бронь успешно создана."
        );
        when(pendingBookingService.hasPending("session-1")).thenReturn(true);
        when(officeAgent.chat(eq("session-1"), eq("Да"), any()))
                .thenReturn(result("Бронь успешно создана.", List.of(confirmExecution(failed, false))));

        ChatResponse response = controller.chat(new ChatRequest("session-1", "Да")).getBody();

        assertThat(response.answer())
                .isEqualTo("Подтверждение получено, но бронирование не было выполнено. Попробуйте повторить операцию.")
                .doesNotContain("успешно создана");
        assertThat(response.executedTools()).containsExactly("confirmPendingBooking");
    }

    @Test
    void shouldBlockSuccessTextWhenToolExecutionFailed() {
        BookingConfirmationResult misleading = new BookingConfirmationResult(
                true,
                "BOOKING_CONFIRMED",
                42L,
                "Бронь успешно создана."
        );
        when(pendingBookingService.hasPending("session-1")).thenReturn(true);
        when(officeAgent.chat(eq("session-1"), eq("Да"), any()))
                .thenReturn(result("Бронь успешно создана.", List.of(confirmExecution(misleading, true))));

        ChatResponse response = controller.chat(new ChatRequest("session-1", "Да")).getBody();

        assertThat(response.answer())
                .isEqualTo("Подтверждение получено, но бронирование не было выполнено. Попробуйте повторить операцию.")
                .doesNotContain("успешно создана");
    }

    @Test
    void shouldAcceptOnlySuccessfulStructuredToolResultWithBookingId() {
        BookingConfirmationResult confirmed = new BookingConfirmationResult(
                true,
                "BOOKING_CONFIRMED",
                42L,
                "Бронирование №42 переговорной «Нева» успешно подтверждено."
        );
        when(pendingBookingService.hasPending("session-1")).thenReturn(true);
        when(officeAgent.chat(eq("session-1"), eq("Подтверждаю"), any()))
                .thenReturn(result("Произвольный текст модели", List.of(confirmExecution(confirmed, false))));

        ChatResponse response = controller.chat(
                new ChatRequest("session-1", "Подтверждаю")
        ).getBody();

        assertThat(response.answer()).isEqualTo(confirmed.message());
        assertThat(response.executedTools()).containsExactly("confirmPendingBooking");
        ArgumentCaptor<InvocationParameters> parameters = ArgumentCaptor.forClass(InvocationParameters.class);
        verify(officeAgent).chat(eq("session-1"), eq("Подтверждаю"), parameters.capture());
        assertThat((Boolean) parameters.getValue().get("explicitConfirmation")).isTrue();
    }

    @Test
    void shouldRejectSuccessfulFlagWithoutBookingId() {
        BookingConfirmationResult invalid = new BookingConfirmationResult(
                true,
                "BOOKING_CONFIRMED",
                null,
                "Бронь успешно создана."
        );
        when(pendingBookingService.hasPending("session-1")).thenReturn(true);
        when(officeAgent.chat(eq("session-1"), eq("Да"), any()))
                .thenReturn(result("Бронь успешно создана.", List.of(confirmExecution(invalid, false))));

        ChatResponse response = controller.chat(new ChatRequest("session-1", "Да")).getBody();

        assertThat(response.answer())
                .isEqualTo("Подтверждение получено, но сохранение бронирования не подтверждено сервером. Попробуйте повторить операцию.")
                .doesNotContain("успешно создана");
    }

    private static Result<String> result(String content, List<ToolExecution> executions) {
        return Result.<String>builder()
                .content(content)
                .toolExecutions(executions)
                .build();
    }

    private static ToolExecution confirmExecution(
            BookingConfirmationResult confirmationResult,
            boolean failed
    ) {
        return toolExecution("confirmPendingBooking", confirmationResult, failed);
    }

    private static ToolExecution toolExecution(
            String toolName,
            Object resultObject,
            boolean failed
    ) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("tool-1")
                .name(toolName)
                .arguments("{}")
                .build();
        ToolExecutionResult result = ToolExecutionResult.builder()
                .isError(failed)
                .result(resultObject)
                .resultText(resultObject.toString())
                .build();
        return ToolExecution.builder()
                .request(request)
                .result(result)
                .invocationContext(InvocationContext.builder()
                        .invocationId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                        .interfaceName(OfficeAgent.class.getName())
                        .methodName("chat")
                        .methodArguments(List.of("session-1", "Да"))
                        .chatMemoryId("session-1")
                        .invocationParameters(InvocationParameters.from(Map.of()))
                        .timestamp(Instant.parse("2026-08-15T10:00:00Z"))
                        .build())
                .build();
    }
}

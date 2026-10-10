package com.example.smartoffice.api;

import com.example.smartoffice.ai.OfficeAgent;
import com.example.smartoffice.ai.BookingConfirmationResult;
import com.example.smartoffice.ai.PendingBookingCancellationResult;
import com.example.smartoffice.service.PendingBookingService;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Set<String> NEGATIVE_WORDS = Set.of(
            "не", "нет", "отмена", "отменить", "отмени", "cancel"
    );
    private static final Pattern IGNORED_PUNCTUATION = Pattern.compile("[.,!?;:]+");
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s+");
    private static final Pattern RUSSIAN_CONFIRMATION = Pattern.compile(
            "^(?:да )?(?:подтверждаю|подтвердить|подтверди)(?: (?:бронь|бронирование|сейчас))?$"
    );

    private final OfficeAgent officeAgent;
    private final PendingBookingService pendingBookingService;

    public ChatController(OfficeAgent officeAgent, PendingBookingService pendingBookingService) {
        this.officeAgent = officeAgent;
        this.pendingBookingService = pendingBookingService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        boolean confirmationIntent = isExplicitConfirmation(request.message());
        boolean hadPendingBooking = pendingBookingService.hasPending(request.sessionId());
        boolean explicitConfirmation = hadPendingBooking && confirmationIntent;

        InvocationParameters parameters = InvocationParameters.from(
                java.util.Map.of("explicitConfirmation", explicitConfirmation)
        );

        var result = officeAgent.chat(request.sessionId(), request.message(), parameters);
        var tools = result.toolExecutions().stream()
                .map(execution -> execution.request().name())
                .toList();
        boolean hasPendingBookingAfterAgentCall = pendingBookingService.hasPending(request.sessionId());

        String answer = authoritativeAnswer(
                confirmationIntent,
                hadPendingBooking,
                hasPendingBookingAfterAgentCall,
                result.content(),
                result.toolExecutions()
        );

        return ResponseEntity.ok(new ChatResponse(
                request.sessionId(),
                answer,
                tools
        ));
    }

    static boolean isExplicitConfirmation(String message) {
        if (message == null) {
            return false;
        }

        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return false;
        }

        boolean containsNegativeWord = Arrays.stream(normalized.split(" "))
                .anyMatch(NEGATIVE_WORDS::contains);
        if (containsNegativeWord) {
            return false;
        }

        return RUSSIAN_CONFIRMATION.matcher(normalized).matches()
                || normalized.equals("да")
                || normalized.equals("бронируй")
                || normalized.equals("да бронируй")
                || normalized.equals("yes")
                || normalized.equals("confirm");
    }

    private static String normalize(String message) {
        String normalized = message
                .toLowerCase(Locale.ROOT)
                .replace('ё', 'е');
        normalized = IGNORED_PUNCTUATION.matcher(normalized).replaceAll(" ");
        return EXTRA_SPACES.matcher(normalized).replaceAll(" ").trim();
    }

    private static String authoritativeAnswer(
            boolean confirmationIntent,
            boolean hadPendingBooking,
            boolean hasPendingBookingAfterAgentCall,
            String modelAnswer,
            java.util.List<ToolExecution> toolExecutions
    ) {
        ToolExecution cancellationExecution = lastExecution(toolExecutions, "cancelPendingBooking");
        if (cancellationExecution != null) {
            if (cancellationExecution.hasFailed()) {
                return "Не удалось отменить ожидающее бронирование. Попробуйте повторить операцию.";
            }

            Object cancellationResultObject = cancellationExecution.resultObject();
            if (!(cancellationResultObject instanceof PendingBookingCancellationResult cancellationResult)) {
                return "Результат отмены бронирования не удалось проверить.";
            }

            return cancellationResult.success()
                    ? cancellationResult.message()
                    : "Нет ожидающего бронирования для отмены.";
        }

        if (!confirmationIntent) {
            ToolExecution bookingRequest = lastExecution(toolExecutions, "requestRoomBooking");
            if (bookingRequest != null) {
                if (bookingRequest.hasFailed() || !hasPendingBookingAfterAgentCall) {
                    return "Не удалось подготовить бронирование. Попробуйте повторить операцию.";
                }
                return "Бронирование подготовлено. Подтвердите действие отдельным сообщением.";
            }

            ToolExecution unexpectedConfirmation = lastExecution(toolExecutions, "confirmPendingBooking");
            if (unexpectedConfirmation != null) {
                return "Бронирование не было подтверждено: требуется отдельное явное подтверждение пользователя.";
            }

            return modelAnswer;
        }

        if (!hadPendingBooking) {
            return "Нет ожидающего подтверждения бронирования. Сначала подготовьте бронирование.";
        }

        ToolExecution confirmationExecution = lastExecution(toolExecutions, "confirmPendingBooking");

        if (confirmationExecution == null || confirmationExecution.hasFailed()) {
            return "Подтверждение получено, но бронирование не было выполнено. Попробуйте повторить операцию.";
        }

        Object resultObject = confirmationExecution.resultObject();
        if (!(resultObject instanceof BookingConfirmationResult confirmationResult)) {
            return "Подтверждение получено, но результат бронирования не удалось проверить. Попробуйте повторить операцию.";
        }

        if (!confirmationResult.success()) {
            return switch (confirmationResult.code()) {
                case "CONFIRMATION_REQUIRED" ->
                        "Для бронирования требуется отдельное явное подтверждение.";
                case "NO_PENDING_BOOKING" ->
                        "Нет ожидающего подтверждения бронирования или оно истекло.";
                default ->
                        "Подтверждение получено, но бронирование не было выполнено. Попробуйте повторить операцию.";
            };
        }

        if (confirmationResult.bookingId() == null) {
            return "Подтверждение получено, но сохранение бронирования не подтверждено сервером. Попробуйте повторить операцию.";
        }

        return confirmationResult.message();
    }

    private static ToolExecution lastExecution(
            java.util.List<ToolExecution> toolExecutions,
            String toolName
    ) {
        return toolExecutions.stream()
                .filter(execution -> execution.request().name().equals(toolName))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}

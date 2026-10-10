package com.example.smartoffice.ai;

import com.example.smartoffice.service.BookingService;
import com.example.smartoffice.service.PendingBookingService;
import com.example.smartoffice.service.RoomService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Component
public class OfficeTools {

    private final RoomService roomService;
    private final BookingService bookingService;
    private final PendingBookingService pendingBookingService;

    public OfficeTools(
            RoomService roomService,
            BookingService bookingService,
            PendingBookingService pendingBookingService
    ) {
        this.roomService = roomService;
        this.bookingService = bookingService;
        this.pendingBookingService = pendingBookingService;
    }

    @Tool("""
            Найти свободные переговорные на заданный интервал.
            Используй этот инструмент, когда пользователь просит подобрать переговорную.
            Время передавай в ISO-8601 без часового пояса, например 2026-08-14T14:00:00.
            """)
    public Object findFreeRooms(
            @P("Количество участников встречи") int people,
            @P("Дата и время начала в ISO-8601, например 2026-08-14T14:00:00") String start,
            @P("Дата и время окончания в ISO-8601, например 2026-08-14T15:00:00") String end
    ) {
        return roomService.findFreeRooms(people, parse(start), parse(end));
    }

    @Tool("Вернуть список всех переговорных и их параметры")
    public Object listRooms() {
        return roomService.listRooms();
    }

    @Tool("""
            Подготовить бронирование переговорной, но НЕ подтверждать его окончательно.
            Этот инструмент только создаёт pending booking. После него обязательно попроси пользователя подтвердить действие.
            Не вызывай confirmPendingBooking в тот же пользовательский ход.
            """)
    public Object requestRoomBooking(
            @ToolMemoryId String sessionId,
            @P("ID переговорной из результата поиска") Long roomId,
            @P("Имя сотрудника; если пользователь не представился, используй demo-user") String employee,
            @P("Дата и время начала в ISO-8601") String start,
            @P("Дата и время окончания в ISO-8601") String end
    ) {
        var pending = pendingBookingService.save(
                sessionId,
                roomId,
                employee,
                parse(start),
                parse(end)
        );

        return "CONFIRMATION_REQUIRED: подготовлено бронирование " + pending
                + ". Попроси пользователя явно подтвердить действие отдельным сообщением.";
    }

    @Tool("""
            Окончательно подтвердить ранее подготовленное бронирование.
            Вызывай ТОЛЬКО если текущее сообщение пользователя является явным подтверждением.
            Сервер дополнительно проверяет это условие и не позволит подтвердить действие автоматически.
            """)
    public BookingConfirmationResult confirmPendingBooking(
            @ToolMemoryId String sessionId,
            InvocationParameters parameters
    ) {
        Boolean explicitConfirmation = parameters.get("explicitConfirmation");
        if (!Boolean.TRUE.equals(explicitConfirmation)) {
            return new BookingConfirmationResult(
                    false,
                    "CONFIRMATION_REQUIRED",
                    null,
                    "Для бронирования требуется отдельное явное подтверждение."
            );
        }

        var pending = pendingBookingService.consume(sessionId).orElse(null);
        if (pending == null) {
            return new BookingConfirmationResult(
                    false,
                    "NO_PENDING_BOOKING",
                    null,
                    "Нет ожидающего подтверждения бронирования или оно истекло."
            );
        }

        try {
            var booking = bookingService.book(
                    pending.roomId(),
                    pending.employee(),
                    pending.startAt(),
                    pending.endAt()
            );

            if (booking.id() == null) {
                return new BookingConfirmationResult(
                        false,
                        "BOOKING_NOT_PERSISTED",
                        null,
                        "Подтверждение получено, но бронирование не было сохранено."
                );
            }

            return new BookingConfirmationResult(
                    true,
                    "BOOKING_CONFIRMED",
                    booking.id(),
                    "Бронирование №%d переговорной «%s» успешно подтверждено."
                            .formatted(booking.id(), booking.roomName())
            );
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return new BookingConfirmationResult(
                    false,
                    "BOOKING_FAILED",
                    null,
                    "Подтверждение получено, но бронирование не выполнено: " + ex.getMessage()
            );
        }
    }

    @Tool(
            value = "Отменить ожидающее подтверждения бронирование, не создавая запись в БД",
            returnBehavior = ReturnBehavior.IMMEDIATE
    )
    public PendingBookingCancellationResult cancelPendingBooking(@ToolMemoryId String sessionId) {
        boolean cancelled = pendingBookingService.cancel(sessionId);
        if (!cancelled) {
            return new PendingBookingCancellationResult(
                    false,
                    "NO_PENDING_BOOKING",
                    "Нет ожидающего бронирования для отмены."
            );
        }

        return new PendingBookingCancellationResult(
                true,
                "PENDING_BOOKING_CANCELLED",
                "Ожидающее бронирование отменено."
        );
    }

    @Tool("Найти подтверждённые бронирования конкретного сотрудника")
    public Object findEmployeeBookings(
            @P("Имя сотрудника") String employee
    ) {
        return bookingService.findEmployeeBookings(employee);
    }

    private static LocalDateTime parse(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Некорректная дата/время: '" + value + "'. Нужен формат ISO-8601, например 2026-08-14T14:00:00"
            );
        }
    }
}

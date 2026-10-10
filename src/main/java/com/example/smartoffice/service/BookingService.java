package com.example.smartoffice.service;

import com.example.smartoffice.domain.Booking;
import com.example.smartoffice.domain.BookingStatus;
import com.example.smartoffice.domain.Room;
import com.example.smartoffice.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomService roomService;

    public BookingService(BookingRepository bookingRepository, RoomService roomService) {
        this.bookingRepository = bookingRepository;
        this.roomService = roomService;
    }

    @Transactional
    public BookingView book(Long roomId, String employee, LocalDateTime startAt, LocalDateTime endAt) {
        if (employee == null || employee.isBlank()) {
            throw new IllegalArgumentException("Не указан сотрудник");
        }
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Некорректный интервал бронирования");
        }

        Room room = roomService.getRequired(roomId);
        boolean conflict = bookingRepository.existsConflict(
                roomId, BookingStatus.CONFIRMED, startAt, endAt);

        if (conflict) {
            throw new IllegalStateException("Переговорная уже занята на выбранный интервал");
        }

        Booking booking = bookingRepository.save(new Booking(room, employee.trim(), startAt, endAt));
        return BookingView.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingView> findEmployeeBookings(String employee) {
        if (employee == null || employee.isBlank()) {
            return List.of();
        }
        return bookingRepository
                .findAllByEmployeeIgnoreCaseAndStatusOrderByStartAtAsc(employee.trim(), BookingStatus.CONFIRMED)
                .stream()
                .map(BookingView::from)
                .toList();
    }

    public record BookingView(
            Long id,
            Long roomId,
            String roomName,
            String employee,
            LocalDateTime startAt,
            LocalDateTime endAt,
            BookingStatus status
    ) {
        public static BookingView from(Booking booking) {
            return new BookingView(
                    booking.getId(),
                    booking.getRoom().getId(),
                    booking.getRoom().getName(),
                    booking.getEmployee(),
                    booking.getStartAt(),
                    booking.getEndAt(),
                    booking.getStatus()
            );
        }
    }
}

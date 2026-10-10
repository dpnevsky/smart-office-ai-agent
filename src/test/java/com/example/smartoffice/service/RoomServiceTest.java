package com.example.smartoffice.service;

import com.example.smartoffice.domain.BookingStatus;
import com.example.smartoffice.domain.Room;
import com.example.smartoffice.repository.BookingRepository;
import com.example.smartoffice.repository.RoomRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomServiceTest {

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final RoomService roomService = new RoomService(roomRepository, bookingRepository);

    @Test
    void shouldExcludeBusyRoom() {
        Room small = new Room("Нева", 4, 6, "A");
        Room large = new Room("Волга", 5, 8, "B");
        setId(small, 1L);
        setId(large, 2L);

        LocalDateTime start = LocalDateTime.of(2026, 8, 14, 14, 0);
        LocalDateTime end = start.plusHours(1);

        when(roomRepository.findAllByCapacityGreaterThanEqualOrderByCapacityAscNameAsc(6))
                .thenReturn(List.of(small, large));
        when(bookingRepository.existsConflict(1L, BookingStatus.CONFIRMED, start, end))
                .thenReturn(true);
        when(bookingRepository.existsConflict(2L, BookingStatus.CONFIRMED, start, end))
                .thenReturn(false);

        var result = roomService.findFreeRooms(6, start, end);

        assertThat(result).extracting(RoomService.RoomView::name)
                .containsExactly("Волга");
    }

    private static void setId(Room room, Long id) {
        try {
            var field = Room.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(room, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}

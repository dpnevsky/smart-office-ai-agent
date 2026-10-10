package com.example.smartoffice.service;

import com.example.smartoffice.domain.BookingStatus;
import com.example.smartoffice.domain.Room;
import com.example.smartoffice.repository.BookingRepository;
import com.example.smartoffice.repository.RoomRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    public RoomService(RoomRepository roomRepository, BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
    }

    public List<RoomView> findFreeRooms(int people, LocalDateTime startAt, LocalDateTime endAt) {
        validateInterval(startAt, endAt);
        if (people < 1) {
            throw new IllegalArgumentException("Количество участников должно быть больше нуля");
        }

        return roomRepository.findAllByCapacityGreaterThanEqualOrderByCapacityAscNameAsc(people)
                .stream()
                .filter(room -> !bookingRepository.existsConflict(
                        room.getId(), BookingStatus.CONFIRMED, startAt, endAt))
                .map(RoomView::from)
                .toList();
    }

    public Room getRequired(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Переговорная с id=" + id + " не найдена"));
    }

    public List<RoomView> listRooms() {
        return roomRepository.findAll().stream().map(RoomView::from).toList();
    }

    private static void validateInterval(LocalDateTime startAt, LocalDateTime endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Время окончания должно быть позже времени начала");
        }
    }

    public record RoomView(Long id, String name, int floor, int capacity, String location) {
        public static RoomView from(Room room) {
            return new RoomView(
                    room.getId(),
                    room.getName(),
                    room.getFloor(),
                    room.getCapacity(),
                    room.getLocation()
            );
        }
    }
}

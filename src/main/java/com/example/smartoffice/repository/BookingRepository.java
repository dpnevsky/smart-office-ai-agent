package com.example.smartoffice.repository;

import com.example.smartoffice.domain.Booking;
import com.example.smartoffice.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select count(b) > 0
            from Booking b
            where b.room.id = :roomId
              and b.status = :status
              and b.startAt < :requestedEnd
              and b.endAt > :requestedStart
            """)
    boolean existsConflict(
            @Param("roomId") Long roomId,
            @Param("status") BookingStatus status,
            @Param("requestedStart") LocalDateTime requestedStart,
            @Param("requestedEnd") LocalDateTime requestedEnd
    );

    List<Booking> findAllByEmployeeIgnoreCaseAndStatusOrderByStartAtAsc(
            String employee,
            BookingStatus status
    );
}

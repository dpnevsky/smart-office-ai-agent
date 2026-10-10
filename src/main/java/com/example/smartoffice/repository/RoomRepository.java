package com.example.smartoffice.repository;

import com.example.smartoffice.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByCapacityGreaterThanEqualOrderByCapacityAscNameAsc(int capacity);
}

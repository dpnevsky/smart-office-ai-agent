--liquibase formatted sql

--changeset smartoffice:001
CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    floor INTEGER NOT NULL,
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    location VARCHAR(200) NOT NULL
);

CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    employee VARCHAR(160) NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT ck_booking_interval CHECK (end_at > start_at)
);

CREATE INDEX idx_bookings_room_interval
    ON bookings(room_id, start_at, end_at, status);

INSERT INTO rooms(name, floor, capacity, location) VALUES
('Ладога', 4, 4, 'Башня A, сектор 4.1'),
('Нева', 4, 6, 'Башня A, сектор 4.2'),
('Волга', 5, 8, 'Башня A, сектор 5.1'),
('Байкал', 5, 12, 'Башня A, сектор 5.4'),
('Эльбрус', 6, 20, 'Башня A, сектор 6.2');

INSERT INTO bookings(room_id, employee, start_at, end_at, status)
SELECT id, 'Иван Петров', CURRENT_DATE + TIME '14:00', CURRENT_DATE + TIME '15:00', 'CONFIRMED'
FROM rooms WHERE name = 'Нева';

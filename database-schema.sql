-- RideMate Database Schema
-- This file contains the complete database structure for the ride-sharing application

-- Trips table (for drivers offering rides)
CREATE TABLE IF NOT EXISTS trips (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    driver_id INTEGER NOT NULL,
    start_location TEXT NOT NULL,
    end_location TEXT NOT NULL,
    start_lat REAL NOT NULL,
    start_lng REAL NOT NULL,
    end_lat REAL NOT NULL,
    end_lng REAL NOT NULL,
    route_polyline TEXT, -- Encoded route for privacy
    distance_km REAL,
    duration_minutes INTEGER,
    departure_time DATETIME,
    status TEXT DEFAULT 'active', -- 'active', 'completed', 'cancelled'
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES users(id)
);

-- Ride requests table (for passengers)
CREATE TABLE IF NOT EXISTS ride_requests (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    passenger_id INTEGER NOT NULL,
    pickup_location TEXT NOT NULL,
    dropoff_location TEXT NOT NULL,
    pickup_lat REAL NOT NULL,
    pickup_lng REAL NOT NULL,
    dropoff_lat REAL NOT NULL,
    dropoff_lng REAL NOT NULL,
    requested_time DATETIME,
    travel_mode TEXT DEFAULT 'bus', -- 'bus', 'car', 'train' - passenger's alternative transport mode
    status TEXT DEFAULT 'searching', -- 'searching', 'matched', 'completed', 'cancelled'
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (passenger_id) REFERENCES users(id)
);

-- Matches table (connecting drivers and passengers)
CREATE TABLE IF NOT EXISTS matches (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id INTEGER NOT NULL,
    ride_request_id INTEGER NOT NULL,
    pickup_point_lat REAL NOT NULL,
    pickup_point_lng REAL NOT NULL,
    dropoff_point_lat REAL NOT NULL,
    dropoff_point_lng REAL NOT NULL,
    estimated_pickup_time DATETIME,
    fare_amount DECIMAL(10,2),
    status TEXT DEFAULT 'pending', -- 'pending', 'accepted', 'rejected', 'completed'
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id),
    FOREIGN KEY (ride_request_id) REFERENCES ride_requests(id),
    UNIQUE(trip_id, ride_request_id)
);

-- Indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_trips_driver_status ON trips(driver_id, status);
CREATE INDEX IF NOT EXISTS idx_trips_status_departure ON trips(status, departure_time);
CREATE INDEX IF NOT EXISTS idx_ride_requests_passenger_status ON ride_requests(passenger_id, status);
CREATE INDEX IF NOT EXISTS idx_ride_requests_status ON ride_requests(status);
CREATE INDEX IF NOT EXISTS idx_matches_trip ON matches(trip_id);
CREATE INDEX IF NOT EXISTS idx_matches_request ON matches(ride_request_id);
CREATE INDEX IF NOT EXISTS idx_matches_status ON matches(status);

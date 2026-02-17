require('dotenv').config();

const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const bcrypt = require('bcrypt');
const path = require('path');
const session = require('express-session');
const SQLiteStore = require('connect-sqlite3')(session);
const cookieSignature = require('cookie-signature');
const { Resend } = require('resend');
const { createServer } = require('http');
const { Server } = require('socket.io');
const { calculateCostSharing, DEFAULT_FUEL_PRICES } = require('./cost-sharing');

const app = express();
const server = createServer(app);
const defaultAllowedOrigins = [
    'http://localhost:3000',
    'http://localhost:8081',
    'http://localhost:19006'
];
const allowedOrigins = new Set(
    (process.env.ALLOWED_ORIGINS || defaultAllowedOrigins.join(','))
        .split(',')
        .map(origin => origin.trim())
        .filter(Boolean)
);

function isAllowedOrigin(origin) {
    if (!origin) return true;
    return allowedOrigins.has(origin);
}

const io = new Server(server, {
    cors: {
        origin: (origin, callback) => {
            if (isAllowedOrigin(origin)) {
                callback(null, true);
            } else {
                callback(new Error('Socket origin not allowed'));
            }
        },
        methods: ["GET", "POST"],
        credentials: true
    }
});
const port = process.env.PORT || 3000;

const fetchFromWeb = global.fetch
    ? global.fetch.bind(global)
    : async (...args) => {
        const nodeFetch = await import('node-fetch');
        return nodeFetch.default(...args);
    };

async function fetchJsonWithTimeout(url, timeoutMs = 8000) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), timeoutMs);

    try {
        const response = await fetchFromWeb(url, { signal: controller.signal });
        const text = await response.text();
        let data = null;

        try {
            data = text ? JSON.parse(text) : null;
        } catch {
            data = { raw: text };
        }

        return { ok: response.ok, status: response.status, data };
    } finally {
        clearTimeout(timeout);
    }
}

// ============= INPUT SANITIZATION HELPERS =============
function sanitizeString(str) {
    if (typeof str !== 'string') return '';
    return str.trim().replace(/[<>]/g, '');
}

function isValidEmail(email) {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
}

function isValidCoordinate(lat, lng) {
    const latNum = parseFloat(lat);
    const lngNum = parseFloat(lng);
    return !isNaN(latNum) && !isNaN(lngNum) &&
        latNum >= -90 && latNum <= 90 &&
        lngNum >= -180 && lngNum <= 180;
}

function hasValue(value) {
    return value !== undefined && value !== null && value !== '';
}
const saltRounds = 10;

// Temporary store for OTPs and user data
const otpStore = {};
const passwordResetStore = {};  // email -> { otp, timestamp, attempts }
const rateLimitBuckets = new Map();

// Periodic cleanup of expired OTP stores (every 10 minutes)
setInterval(() => {
    const now = Date.now();
    const OTP_EXPIRY_MS = 5 * 60 * 1000;
    for (const key of Object.keys(otpStore)) {
        if (now - (otpStore[key].timestamp || 0) > OTP_EXPIRY_MS) delete otpStore[key];
    }
    for (const key of Object.keys(passwordResetStore)) {
        if (now - (passwordResetStore[key].timestamp || 0) > OTP_EXPIRY_MS) delete passwordResetStore[key];
    }
}, 10 * 60 * 1000);

app.use(express.json({ limit: '5mb' }));

function createRateLimiter({ windowMs, max, message, keyFn }) {
    const getKey = keyFn || ((req) => req.ip || 'unknown');
    return (req, res, next) => {
        const now = Date.now();
        const key = getKey(req);
        const bucketKey = `${req.path}|${key}`;
        const current = rateLimitBuckets.get(bucketKey);

        if (!current || now > current.resetAt) {
            rateLimitBuckets.set(bucketKey, { count: 1, resetAt: now + windowMs });
            return next();
        }

        if (current.count >= max) {
            const retryAfterSec = Math.max(1, Math.ceil((current.resetAt - now) / 1000));
            res.setHeader('Retry-After', retryAfterSec.toString());
            return res.status(429).json({ message });
        }

        current.count += 1;
        rateLimitBuckets.set(bucketKey, current);
        return next();
    };
}

const authRateLimiter = createRateLimiter({
    windowMs: 15 * 60 * 1000,
    max: 20,
    message: 'Too many authentication attempts. Please try again in 15 minutes.'
});

const otpRateLimiter = createRateLimiter({
    windowMs: 10 * 60 * 1000,
    max: 8,
    message: 'Too many OTP attempts. Please try again in 10 minutes.',
    keyFn: (req) => {
        const email = sanitizeString(req.body?.email || '');
        const phone = sanitizeString(req.body?.phone || '');
        return `${req.ip || 'unknown'}:${email || phone || 'anon'}`;
    }
});

setInterval(() => {
    const now = Date.now();
    for (const [key, bucket] of rateLimitBuckets.entries()) {
        if (!bucket || now > bucket.resetAt) {
            rateLimitBuckets.delete(key);
        }
    }
}, 10 * 60 * 1000).unref();

// CORS middleware for Expo web support
app.use((req, res, next) => {
    const origin = req.headers.origin;
    if (origin && !isAllowedOrigin(origin)) {
        return res.status(403).json({ message: 'Origin not allowed' });
    }

    if (origin && isAllowedOrigin(origin)) {
        res.setHeader('Access-Control-Allow-Origin', origin);
        res.setHeader('Vary', 'Origin');
    }
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    if (req.method === 'OPTIONS') {
        return res.sendStatus(200);
    }
    next();
});

app.use(express.static(path.join(__dirname, '..')));
app.set('trust proxy', 1);

// Session configuration
const SESSION_SECRET = process.env.SESSION_SECRET || 'ridemate-dev-secret-change-in-production';
const sessionStore = new SQLiteStore({
    db: 'sessions.db',
    dir: __dirname,
    table: 'sessions',
    concurrentDB: true
});

app.use(session({
    secret: SESSION_SECRET,
    store: sessionStore,
    resave: false,
    saveUninitialized: false,
    cookie: {
        secure: process.env.NODE_ENV === 'production',
        httpOnly: true,
        sameSite: 'lax',
        maxAge: 24 * 60 * 60 * 1000 // 24 hours
    }
}));

// Socket.IO auth: reuse express-session (connect.sid) to identify the user.
// This prevents trusting client-supplied userIds for private rooms/events.
function getCookieValue(cookieHeader, name) {
    if (!cookieHeader || typeof cookieHeader !== 'string') return null;
    const parts = cookieHeader.split(';');
    for (const part of parts) {
        const idx = part.indexOf('=');
        if (idx === -1) continue;
        const key = part.slice(0, idx).trim();
        if (key !== name) continue;
        return part.slice(idx + 1).trim();
    }
    return null;
}

function getSessionIdFromHandshake(socket) {
    const rawCookieHeader = socket?.handshake?.headers?.cookie;
    const rawSid = getCookieValue(rawCookieHeader, 'connect.sid');
    if (!rawSid) return null;

    let decoded = rawSid;
    try { decoded = decodeURIComponent(rawSid); } catch { /* ignore */ }
    if (decoded.startsWith('s:')) decoded = decoded.slice(2);

    const unsigned = cookieSignature.unsign(decoded, SESSION_SECRET);
    return unsigned || null;
}

io.use((socket, next) => {
    const sid = getSessionIdFromHandshake(socket);
    if (!sid) {
        return next(new Error('unauthorized'));
    }

    sessionStore.get(sid, (err, sess) => {
        if (err || !sess || !sess.userId) {
            return next(new Error('unauthorized'));
        }
        socket.data.sessionId = sid;
        socket.data.userId = sess.userId;
        next();
    });
});

const db = new sqlite3.Database('./database.db', (err) => {
    if (err) {
        console.error(err.message);
    }
    console.log('Connected to the SQLite database.');
});

db.serialize(() => {
    // Concurrency + integrity tuning for SQLite.
    db.run(`PRAGMA journal_mode = WAL`);
    db.run(`PRAGMA synchronous = NORMAL`);
    db.run(`PRAGMA foreign_keys = ON`);
    db.run(`PRAGMA busy_timeout = 5000`);

    // Create users table
    db.run(`CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        email TEXT NOT NULL UNIQUE,
        password TEXT NOT NULL
    )`);

    // Create trips table (for drivers)
    db.run(`CREATE TABLE IF NOT EXISTS trips (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        driver_id INTEGER NOT NULL,
        start_location TEXT NOT NULL,
        end_location TEXT NOT NULL,
        start_lat REAL NOT NULL,
        start_lng REAL NOT NULL,
        end_lat REAL NOT NULL,
        end_lng REAL NOT NULL,
        route_polyline TEXT,
        distance_km REAL,
        duration_minutes INTEGER,
        departure_time DATETIME,
        available_seats INTEGER DEFAULT 1,
        status TEXT DEFAULT 'active',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (driver_id) REFERENCES users(id)
    )`);

    // Create ride_requests table (for passengers)
    db.run(`CREATE TABLE IF NOT EXISTS ride_requests (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        passenger_id INTEGER NOT NULL,
        pickup_location TEXT NOT NULL,
        dropoff_location TEXT NOT NULL,
        pickup_lat REAL NOT NULL,
        pickup_lng REAL NOT NULL,
        dropoff_lat REAL NOT NULL,
        dropoff_lng REAL NOT NULL,
        requested_time DATETIME,
        status TEXT DEFAULT 'searching',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (passenger_id) REFERENCES users(id)
    )`);

    // Create matches table
    db.run(`CREATE TABLE IF NOT EXISTS matches (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        trip_id INTEGER NOT NULL,
        ride_request_id INTEGER NOT NULL,
        pickup_point_lat REAL NOT NULL,
        pickup_point_lng REAL NOT NULL,
        dropoff_point_lat REAL NOT NULL,
        dropoff_point_lng REAL NOT NULL,
        estimated_pickup_time DATETIME,
        fare_amount DECIMAL(10,2),
        status TEXT DEFAULT 'pending',
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (trip_id) REFERENCES trips(id),
        FOREIGN KEY (ride_request_id) REFERENCES ride_requests(id),
        UNIQUE(trip_id, ride_request_id)
    )`);

    // Create indexes
    db.run(`CREATE INDEX IF NOT EXISTS idx_trips_driver_status ON trips(driver_id, status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_trips_status_departure ON trips(status, departure_time)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_trips_status_departure_expr ON trips(status, datetime(departure_time))`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_trips_driver_created ON trips(driver_id, created_at DESC)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_ride_requests_passenger_status ON ride_requests(passenger_id, status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_ride_requests_status ON ride_requests(status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_ride_requests_passenger_created ON ride_requests(passenger_id, created_at DESC)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_trip ON matches(trip_id)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_request ON matches(ride_request_id)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_status ON matches(status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_trip_status ON matches(trip_id, status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_request_status ON matches(ride_request_id, status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_trip_status_created ON matches(trip_id, status, created_at DESC)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_request_status_created ON matches(ride_request_id, status, created_at DESC)`);

    // Create fuel_config table (admin-editable fuel prices)
    db.run(`CREATE TABLE IF NOT EXISTS fuel_config (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vehicle_type TEXT NOT NULL,
        fuel_type TEXT NOT NULL,
        cost_per_km REAL NOT NULL,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(vehicle_type, fuel_type)
    )`);

    // Create ratings table
    db.run(`CREATE TABLE IF NOT EXISTS ratings (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        trip_id INTEGER NOT NULL,
        match_id INTEGER NOT NULL,
        rater_id INTEGER NOT NULL,
        rated_id INTEGER NOT NULL,
        rating INTEGER NOT NULL CHECK(rating >= 1 AND rating <= 5),
        review TEXT,
        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (trip_id) REFERENCES trips(id),
        FOREIGN KEY (match_id) REFERENCES matches(id),
        FOREIGN KEY (rater_id) REFERENCES users(id),
        FOREIGN KEY (rated_id) REFERENCES users(id),
        UNIQUE(match_id, rater_id)
    )`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_ratings_rated ON ratings(rated_id)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_ratings_rater ON ratings(rater_id)`);

    // Backward-compatible migration for older DBs created before available_seats existed.
    db.run(`ALTER TABLE trips ADD COLUMN available_seats INTEGER DEFAULT 1`, (err) => {
        if (err && !String(err.message || '').includes('duplicate column name')) {
            console.error('Failed to add available_seats column:', err.message);
        }
    });
    db.run(`ALTER TABLE users ADD COLUMN updated_at DATETIME`, (err) => {
        if (err && !String(err.message || '').includes('duplicate column name')) {
            console.error('Failed to add users.updated_at column:', err.message);
        }
        db.run(`UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE updated_at IS NULL`, (fillErr) => {
            if (fillErr && !String(fillErr.message || '').includes('no such column')) {
                console.error('Failed to backfill users.updated_at:', fillErr.message);
            }
        });
    });

    // ── User profile columns migration ──────────────────────
    const profileColumns = [
        { name: 'phone', type: 'TEXT' },
        { name: 'is_phone_verified', type: 'INTEGER DEFAULT 0' },
        { name: 'gender', type: 'TEXT' },
        { name: 'date_of_birth', type: 'TEXT' },
        { name: 'vehicle_number', type: 'TEXT' },
        { name: 'vehicle_model', type: 'TEXT' },
        { name: 'profile_photo_url', type: 'TEXT' },
        { name: 'bio', type: 'TEXT' },
        { name: 'home_address', type: 'TEXT' },
        { name: 'work_address', type: 'TEXT' },
        { name: 'emergency_contact', type: 'TEXT' },
        { name: 'preferred_role', type: "TEXT DEFAULT 'both'" },
    ];
    for (const col of profileColumns) {
        db.run(`ALTER TABLE users ADD COLUMN ${col.name} ${col.type}`, (err) => {
            if (err && !String(err.message || '').includes('duplicate column name')) {
                console.error(`Failed to add ${col.name} column:`, err.message);
            }
        });
    }
});

function cleanupExpiredTrips() {
    db.run(
        `UPDATE trips
         SET status = 'expired', updated_at = CURRENT_TIMESTAMP
         WHERE status = 'active'
           AND departure_time IS NOT NULL
           AND datetime(departure_time) <= datetime('now', '-24 hours')`,
        (err) => {
            if (err) {
                console.error('Failed to cleanup expired trips:', err.message);
            }
        }
    );
}

cleanupExpiredTrips();
setInterval(cleanupExpiredTrips, 15 * 60 * 1000).unref();

// Initialize Resend with API key from environment variables
const resend = new Resend(process.env.RESEND_API_KEY);

// ============= WEBSOCKET REAL-TIME NOTIFICATIONS =============

// Store active driver connections
const activeDrivers = new Map(); // Map of tripId -> socketId
const driverSockets = new Map(); // Map of socketId -> {userId, tripId, socket}

// Socket.io connection handling
io.on('connection', (socket) => {
    const userId = socket.data.userId;
    console.log('Client connected:', socket.id, 'userId:', userId);

    // Each authenticated user joins their personal notification room.
    socket.join(`user-${userId}`);

    // Driver joins their trip room for notifications
    socket.on('driver-join-trip', (data) => {
        const payload = (data && typeof data === 'object') ? data : { tripId: data };
        const tripId = parseInt(payload.tripId, 10);

        if (!Number.isFinite(tripId) || tripId <= 0) {
            return;
        }

        // Enforce ownership: only the authenticated driver for the trip can join its room.
        db.get(
            `SELECT id FROM trips WHERE id = ? AND driver_id = ? AND status = 'active' LIMIT 1`,
            [tripId, userId],
            (err, row) => {
                if (err) {
                    console.error('Error verifying trip ownership:', err);
                    return;
                }
                if (!row) {
                    console.warn(`User ${userId} denied joining trip room ${tripId}`);
                    return;
                }

                console.log(`Driver ${userId} joined trip ${tripId} room`);

                // Store driver connection
                activeDrivers.set(tripId, socket.id);
                driverSockets.set(socket.id, { userId, tripId, socket });

                // Join the trip room
                socket.join(`trip-${tripId}`);

                // Confirm connection
                socket.emit('driver-connected', { tripId, status: 'connected' });
            }
        );
    });

    // Driver leaves trip room
    socket.on('driver-leave-trip', (data) => {
        const payload = (data && typeof data === 'object') ? data : { tripId: data };
        const tripId = parseInt(payload.tripId, 10);
        const driverData = driverSockets.get(socket.id);
        if (!driverData) return;

        if (Number.isFinite(tripId) && tripId > 0 && driverData.tripId !== tripId) {
            // Prevent a socket from leaving (and deleting) other driver's trip rooms.
            return;
        }
        console.log(`Driver left trip ${driverData.tripId} room`);

        // Remove from active drivers
        activeDrivers.delete(driverData.tripId);
        driverSockets.delete(socket.id);

        // Leave the trip room
        socket.leave(`trip-${driverData.tripId}`);
    });

    // Handle disconnection
    socket.on('disconnect', () => {
        console.log('Client disconnected:', socket.id);

        // Clean up driver connections
        const driverData = driverSockets.get(socket.id);
        if (driverData) {
            activeDrivers.delete(driverData.tripId);
            driverSockets.delete(socket.id);
        }
    });

    // Passenger notification when they select a driver
    socket.on('passenger-selected-driver', (data) => {
        const payload = (data && typeof data === 'object') ? data : { tripId: data };
        const tripId = parseInt(payload.tripId, 10);
        const passengerData = payload.passengerData || {};
        const fareAmount = Number(payload.fareAmount) || 0;

        if (!Number.isFinite(tripId) || tripId <= 0) {
            return;
        }
        console.log(`Passenger selected driver for trip ${tripId}`);

        // Calculate driver earnings (86% of total fare)
        const driverEarnings = Math.round(fareAmount * 0.86);

        // Send instant notification to driver
        io.to(`trip-${tripId}`).emit('passenger-request', {
            passenger: passengerData,
            earnings: driverEarnings,
            fareAmount: fareAmount,
            timestamp: new Date().toISOString()
        });

        console.log(`Sent instant notification to driver for trip ${tripId}: ₹${driverEarnings} earnings`);
    });
});

// Helper function to notify driver instantly
function notifyDriverInstantly(tripId, requestData) {
    const driverEarnings = Math.round(requestData.fare_amount * 0.86);

    io.to(`trip-${tripId}`).emit('passenger-request', {
        id: requestData.id,
        passenger_name: requestData.passenger_name,
        pickup_location: requestData.pickup_location,
        dropoff_location: requestData.dropoff_location,
        pickup_lat: requestData.pickup_lat,
        pickup_lng: requestData.pickup_lng,
        dropoff_lat: requestData.dropoff_lat,
        dropoff_lng: requestData.dropoff_lng,
        fare_amount: requestData.fare_amount,
        earnings: driverEarnings,
        timestamp: new Date().toISOString()
    });

    console.log(`🚨 INSTANT NOTIFICATION: Driver for trip ${tripId} notified of ₹${driverEarnings} earnings`);
}

// Utility function to decode encoded polyline (provider-agnostic format)
function decodePolyline(encoded) {
    const points = [];
    let index = 0;
    let lat = 0;
    let lng = 0;

    while (index < encoded.length) {
        let shift = 0;
        let result = 0;
        let byte;

        do {
            byte = encoded.charCodeAt(index++) - 63;
            result |= (byte & 0x1f) << shift;
            shift += 5;
        } while (byte >= 0x20);

        const dlat = ((result & 1) ? ~(result >> 1) : (result >> 1));
        lat += dlat;

        shift = 0;
        result = 0;

        do {
            byte = encoded.charCodeAt(index++) - 63;
            result |= (byte & 0x1f) << shift;
            shift += 5;
        } while (byte >= 0x20);

        const dlng = ((result & 1) ? ~(result >> 1) : (result >> 1));
        lng += dlng;

        points.push({
            lat: lat / 1e5,
            lng: lng / 1e5
        });
    }

    return points;
}

const decodedRouteCache = new Map();
const MAX_DECODED_ROUTE_CACHE_SIZE = 500;

function getDecodedRoutePoints(routePolyline) {
    if (typeof routePolyline !== 'string' || !routePolyline) {
        return [];
    }

    const cached = decodedRouteCache.get(routePolyline);
    if (cached) {
        // Refresh insertion order to behave as a simple LRU cache.
        decodedRouteCache.delete(routePolyline);
        decodedRouteCache.set(routePolyline, cached);
        return cached;
    }

    const decoded = decodePolyline(routePolyline);
    if (decodedRouteCache.size >= MAX_DECODED_ROUTE_CACHE_SIZE) {
        const oldestKey = decodedRouteCache.keys().next().value;
        if (oldestKey !== undefined) {
            decodedRouteCache.delete(oldestKey);
        }
    }
    decodedRouteCache.set(routePolyline, decoded);
    return decoded;
}

// Calculate distance between two points using Haversine formula
function calculateDistance(lat1, lng1, lat2, lng2) {
    const R = 6371; // Earth's radius in kilometers
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c * 1000; // Return distance in meters
}

// Find the closest point on the route to a given location
function findClosestPointOnRoute(routePoints, targetLat, targetLng) {
    let minDistance = Infinity;
    let closestPointIndex = -1;

    for (let i = 0; i < routePoints.length; i++) {
        const distance = calculateDistance(
            routePoints[i].lat,
            routePoints[i].lng,
            targetLat,
            targetLng
        );

        if (distance < minDistance) {
            minDistance = distance;
            closestPointIndex = i;
        }
    }

    return {
        distance: minDistance,
        index: closestPointIndex
    };
}

// Check if a driver's route matches passenger requirements
function checkRouteMatch(routePolyline, pickupLat, pickupLng, dropoffLat, dropoffLng, threshold) {
    try {
        // Decode the route polyline
        const routePoints = getDecodedRoutePoints(routePolyline);

        // Find closest point on route to pickup location
        const pickupMatch = findClosestPointOnRoute(routePoints, pickupLat, pickupLng);

        // If pickup is too far from route, no match
        if (pickupMatch.distance > threshold) {
            return null;
        }

        // Calculate original passenger journey distance (in meters)
        const originalDistance = calculateDistance(pickupLat, pickupLng, dropoffLat, dropoffLng);

        // Find closest point on route to dropoff location
        // Only check points after the pickup point to ensure correct order
        const dropoffRoutePoints = routePoints.slice(pickupMatch.index);
        const dropoffMatch = findClosestPointOnRoute(dropoffRoutePoints, dropoffLat, dropoffLng);

        // Find the best drop point on the route that minimizes remaining distance
        let bestDropPoint = null;
        let bestDropPointIndex = -1;
        let minRemainingDistance = Infinity;

        // Check all points after pickup for the best drop point
        for (let i = pickupMatch.index; i < routePoints.length; i++) {
            const remainingDist = calculateDistance(
                routePoints[i].lat,
                routePoints[i].lng,
                dropoffLat,
                dropoffLng
            );

            if (remainingDist < minRemainingDistance) {
                minRemainingDistance = remainingDist;
                bestDropPoint = routePoints[i];
                bestDropPointIndex = i;
            }
        }

        // Calculate percentage reduction in distance
        const distanceReduction = ((originalDistance - minRemainingDistance) / originalDistance) * 100;

        // Determine required reduction percentage based on original trip distance
        let requiredReduction;
        const originalDistanceKm = originalDistance / 1000; // Convert to km

        if (originalDistanceKm < 30) {
            requiredReduction = 60; // 60% reduction for trips under 30km
        } else if (originalDistanceKm <= 100) {
            requiredReduction = 55; // 55% reduction for trips 30-100km
        } else {
            requiredReduction = 30; // 30% reduction for trips over 100km
        }

        // Check if either direct dropoff OR partial ride criteria is met
        const isDirectDropoffMatch = dropoffMatch.distance <= threshold;
        const isPartialRideMatch = distanceReduction >= requiredReduction;

        if (isDirectDropoffMatch || isPartialRideMatch) {
            return {
                pickupDistance: pickupMatch.distance,
                dropoffDistance: dropoffMatch.distance,
                pickupIndex: pickupMatch.index,
                dropoffIndex: pickupMatch.index + dropoffMatch.index,
                totalRoutePoints: routePoints.length,
                // New fields for partial ride support
                isPartialRide: !isDirectDropoffMatch && isPartialRideMatch,
                bestDropPoint: bestDropPoint,
                bestDropPointIndex: bestDropPointIndex,
                remainingDistance: minRemainingDistance,
                distanceReduction: distanceReduction,
                originalDistance: originalDistance
            };
        }

        // No match found
        return null;
    } catch (error) {
        console.error('Error checking route match:', error);
        return null;
    }
}

app.post('/api/signup', authRateLimiter, (req, res) => {
    const name = sanitizeString(req.body.name);
    const email = sanitizeString(req.body.email);
    const password = req.body.password;

    if (!name || !email || !password) {
        return res.status(400).json({ message: 'All fields are required' });
    }

    if (!isValidEmail(email)) {
        return res.status(400).json({ message: 'Please enter a valid email address' });
    }

    if (password.length < 6) {
        return res.status(400).json({ message: 'Password must be at least 6 characters' });
    }

    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    console.log(`Generated OTP for ${email}: ${otp}`);

    bcrypt.hash(password, saltRounds, (err, hash) => {
        if (err) {
            console.error('Error hashing password:', err);
            return res.status(500).json({ message: 'Error hashing password' });
        }

        // Store user data and OTP temporarily
        otpStore[email] = { name, email, password: hash, otp, timestamp: Date.now() };
        console.log(`Stored OTP for ${email}`)

        // Send OTP email using Resend
        resend.emails.send({
            from: 'noreply@ridemate.com',
            to: email,
            subject: 'Your RideMate OTP',
            html: `<p>Your OTP for RideMate sign up is: <strong>${otp}</strong></p>
                   <p>This OTP will expire in 5 minutes.</p>`
        })
            .then((result) => {
                console.log('Email sent successfully:', result);
                res.status(200).json({ message: 'OTP sent to your email address. Please check your inbox.' });
            })
            .catch((error) => {
                console.error('Error sending OTP email:', error);
                res.status(500).json({ message: 'Error sending OTP email. Please try again.' });
            });
    });
});

app.post('/api/verify-otp', otpRateLimiter, (req, res) => {
    const { email, otp } = req.body;

    if (!email || !otp) {
        return res.status(400).json({ message: 'Email and OTP are required' });
    }

    const storedData = otpStore[email];

    if (!storedData) {
        return res.status(400).json({ message: 'Invalid request. Please sign up first.' });
    }

    // OTP valid for 5 minutes
    if (Date.now() - storedData.timestamp > 5 * 60 * 1000) {
        delete otpStore[email];
        return res.status(400).json({ message: 'OTP has expired. Please try again.' });
    }

    if (otp === storedData.otp) {
        // Reset attempts on success
        const { name, email, password } = storedData;
        const sql = `INSERT INTO users (name, email, password) VALUES (?, ?, ?)`;
        db.run(sql, [name, email, password], function (err) {
            if (err) {
                if (err.code === 'SQLITE_CONSTRAINT') {
                    return res.status(409).json({ message: 'Email already exists' });
                }
                return res.status(500).json({ message: 'Error creating user' });
            }
            delete otpStore[email]; // Clean up the stored OTP
            res.status(201).json({ message: 'User created successfully', userId: this.lastID });
        });
    } else {
        // Track failed OTP attempts — delete after 5 failures
        storedData.attempts = (storedData.attempts || 0) + 1;
        if (storedData.attempts >= 5) {
            delete otpStore[email];
            return res.status(400).json({ message: 'Too many failed attempts. Please sign up again.' });
        }
        res.status(400).json({ message: `Invalid OTP. ${5 - storedData.attempts} attempts remaining.` });
    }
});

app.post('/api/login', authRateLimiter, (req, res) => {
    const email = sanitizeString(req.body.email);
    const password = req.body.password;

    if (!email || !password) {
        return res.status(400).json({ message: 'Email and password are required' });
    }

    if (!isValidEmail(email)) {
        return res.status(400).json({ message: 'Invalid credentials' });
    }

    const sql = `SELECT * FROM users WHERE email = ?`;
    db.get(sql, [email], (err, user) => {
        if (err) {
            return res.status(500).json({ message: 'An error occurred. Please try again.' });
        }
        if (!user) {
            // Don't reveal whether the email exists - use generic message
            return res.status(401).json({ message: 'Invalid credentials' });
        }

        bcrypt.compare(password, user.password, (bcryptErr, result) => {
            if (bcryptErr) {
                console.error('bcrypt comparison error:', bcryptErr);
                return res.status(500).json({ message: 'An error occurred. Please try again.' });
            }
            if (!result) {
                return res.status(401).json({ message: 'Invalid credentials' });
            }

            // Store user info in session
            req.session.userId = user.id;
            req.session.userName = user.name;
            req.session.userEmail = user.email;

            res.status(200).json({
                message: 'Login successful',
                user: {
                    id: user.id,
                    name: user.name,
                    email: user.email
                }
            });
        });
    });
});

// Check authentication status
app.get('/api/auth/status', (req, res) => {
    if (req.session.userId) {
        res.json({
            isAuthenticated: true,
            user: {
                id: req.session.userId,
                name: req.session.userName,
                email: req.session.userEmail
            }
        });
    } else {
        res.json({
            isAuthenticated: false
        });
    }
});

// Logout endpoint
app.post('/api/logout', (req, res) => {
    req.session.destroy((err) => {
        if (err) {
            return res.status(500).json({ message: 'Error logging out' });
        }
        res.clearCookie('connect.sid'); // Clear session cookie
        res.json({ message: 'Logout successful' });
    });
});

// ============= PASSWORD RESET =============

// Step 1: Request password reset — sends OTP to email
app.post('/api/forgot-password', authRateLimiter, (req, res) => {
    const email = sanitizeString(req.body.email);
    if (!email || !isValidEmail(email)) {
        return res.status(400).json({ message: 'Please enter a valid email address' });
    }

    // Always respond with success to avoid email enumeration
    const successMsg = 'If an account exists with this email, a reset code has been sent.';

    db.get(`SELECT id FROM users WHERE email = ?`, [email], (err, user) => {
        if (err || !user) {
            return res.json({ message: successMsg });
        }

        const otp = Math.floor(100000 + Math.random() * 900000).toString();
        passwordResetStore[email] = { otp, timestamp: Date.now(), attempts: 0 };
        console.log(`[Password Reset] OTP for ${email}: ${otp}`);

        resend.emails.send({
            from: 'noreply@ridemate.com',
            to: email,
            subject: 'Rydius Password Reset',
            html: `<p>Your password reset code is: <strong>${otp}</strong></p>
                   <p>This code expires in 5 minutes. If you didn't request this, ignore this email.</p>`
        })
        .then(() => res.json({ message: successMsg }))
        .catch(() => res.json({ message: successMsg }));
    });
});

// Step 2: Verify reset OTP and set new password
app.post('/api/reset-password', authRateLimiter, (req, res) => {
    const email = sanitizeString(req.body.email);
    const otp = String(req.body.otp || '');
    const newPassword = req.body.newPassword;

    if (!email || !otp || !newPassword) {
        return res.status(400).json({ message: 'Email, OTP, and new password are required' });
    }
    if (newPassword.length < 6) {
        return res.status(400).json({ message: 'Password must be at least 6 characters' });
    }

    const stored = passwordResetStore[email];
    if (!stored) {
        return res.status(400).json({ message: 'No reset request found. Please request a new code.' });
    }

    // Check expiry (5 minutes)
    if (Date.now() - stored.timestamp > 5 * 60 * 1000) {
        delete passwordResetStore[email];
        return res.status(400).json({ message: 'Reset code has expired. Please request a new one.' });
    }

    // Check attempts
    if (stored.attempts >= 5) {
        delete passwordResetStore[email];
        return res.status(400).json({ message: 'Too many failed attempts. Please request a new code.' });
    }

    if (otp !== stored.otp) {
        stored.attempts++;
        return res.status(400).json({ message: `Invalid code. ${5 - stored.attempts} attempts remaining.` });
    }

    // OTP correct — hash and update password
    bcrypt.hash(newPassword, saltRounds, (err, hash) => {
        if (err) {
            return res.status(500).json({ message: 'Error processing request' });
        }
        db.run(`UPDATE users SET password = ? WHERE email = ?`, [hash, email], function (dbErr) {
            delete passwordResetStore[email];
            if (dbErr) {
                return res.status(500).json({ message: 'Error updating password' });
            }
            res.json({ message: 'Password reset successfully. Please log in with your new password.' });
        });
    });
});

// ============= ACCOUNT DELETION =============

app.delete('/api/account', requireAuth, (req, res) => {
    const userId = req.session.userId;

    // Delete user data in a transaction for atomicity
    db.run('BEGIN IMMEDIATE TRANSACTION', (beginErr) => {
        if (beginErr) return res.status(500).json({ message: 'Error deleting account' });

        const rollback = (msg) => {
            db.run('ROLLBACK', () => {
                res.status(500).json({ message: msg || 'Error deleting account' });
            });
        };

        db.run(`DELETE FROM matches WHERE ride_request_id IN (SELECT id FROM ride_requests WHERE passenger_id = ?) OR trip_id IN (SELECT id FROM trips WHERE driver_id = ?)`,
            [userId, userId], function (err) {
            if (err) return rollback('Error removing matches');
            db.run(`DELETE FROM ride_requests WHERE passenger_id = ?`, [userId], function (err2) {
                if (err2) return rollback('Error removing ride requests');
                db.run(`UPDATE trips SET status = 'cancelled' WHERE driver_id = ? AND status = 'active'`, [userId], function (err3) {
                    if (err3) return rollback('Error cancelling trips');
                    db.run(`DELETE FROM trips WHERE driver_id = ?`, [userId], function (err4) {
                        if (err4) return rollback('Error removing trips');
                        db.run(`DELETE FROM ratings WHERE rater_id = ? OR rated_id = ?`, [userId, userId], function (err5) {
                            if (err5) return rollback('Error removing ratings');
                            db.run(`DELETE FROM users WHERE id = ?`, [userId], function (err6) {
                                if (err6) return rollback('Error deleting user');
                                db.run('COMMIT', (commitErr) => {
                                    if (commitErr) return rollback('Error finalizing deletion');
                                    req.session.destroy(() => {
                                        res.clearCookie('connect.sid');
                                        res.json({ message: 'Account deleted successfully' });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    });
});

// Middleware to check authentication
function requireAuth(req, res, next) {
    if (!req.session.userId) {
        return res.status(401).json({ message: 'Authentication required' });
    }
    next();
}

// ============= CONFIG ENDPOINT =============

// ============= PROFILE ENDPOINTS =============

// Phone OTP store (in-memory, same pattern as email OTP)
const phoneOtpStore = {};

// Get user profile
app.get('/api/profile', requireAuth, (req, res) => {
    db.get(
        `SELECT id, name, email, phone, is_phone_verified, gender, date_of_birth,
                vehicle_number, vehicle_model, profile_photo_url, bio,
                home_address, work_address, emergency_contact, preferred_role
         FROM users WHERE id = ?`,
        [req.session.userId],
        (err, user) => {
            if (err) return res.status(500).json({ message: 'Database error' });
            if (!user) return res.status(404).json({ message: 'User not found' });
            res.json({ success: true, profile: user });
        }
    );
});

// Update user profile
app.put('/api/profile', requireAuth, (req, res) => {
    const allowedFields = [
        'name', 'phone', 'gender', 'date_of_birth',
        'vehicle_number', 'vehicle_model', 'bio',
        'home_address', 'work_address', 'emergency_contact', 'preferred_role'
    ];

    const updates = [];
    const values = [];

    for (const field of allowedFields) {
        if (req.body[field] !== undefined) {
            const value = typeof req.body[field] === 'string'
                ? sanitizeString(req.body[field])
                : req.body[field];
            updates.push(`${field} = ?`);
            values.push(value);
        }
    }

    // If phone is manually changed, it must be re-verified.
    if (req.body.phone !== undefined) {
        const normalizedPhone = sanitizeString(String(req.body.phone)).replace(/[\s-]/g, '');
        if (normalizedPhone && !/^\+?\d{10,15}$/.test(normalizedPhone)) {
            return res.status(400).json({ message: 'Valid phone number is required' });
        }
        updates.push(`is_phone_verified = 0`);
    }

    if (updates.length === 0) {
        return res.status(400).json({ message: 'No fields to update' });
    }

    // Validate gender if provided
    if (req.body.gender && !['male', 'female', 'other', 'prefer_not_to_say'].includes(req.body.gender)) {
        return res.status(400).json({ message: 'Invalid gender value' });
    }

    // Validate vehicle number format if provided (Indian format)
    if (req.body.vehicle_number) {
        const vn = sanitizeString(req.body.vehicle_number).toUpperCase();
        if (vn.length > 0 && !/^[A-Z]{2}\d{1,2}[A-Z]{0,3}\d{1,4}$/.test(vn.replace(/[\s-]/g, ''))) {
            return res.status(400).json({ message: 'Invalid vehicle number format' });
        }
    }

    values.push(req.session.userId);

    db.run(
        `UPDATE users SET ${updates.join(', ')}, updated_at = CURRENT_TIMESTAMP WHERE id = ?`,
        values,
        function (err) {
            if (err) {
                // If updated_at column doesn't exist, retry without it
                if (String(err.message || '').includes('updated_at')) {
                    db.run(
                        `UPDATE users SET ${updates.join(', ')} WHERE id = ?`,
                        values,
                        function (retryErr) {
                            if (retryErr) return res.status(500).json({ message: 'Database error' });
                            // Update session name if changed
                            if (req.body.name) req.session.userName = sanitizeString(req.body.name);
                            res.json({ success: true, message: 'Profile updated' });
                        }
                    );
                    return;
                }
                return res.status(500).json({ message: 'Database error' });
            }
            // Update session name if changed
            if (req.body.name) req.session.userName = sanitizeString(req.body.name);
            res.json({ success: true, message: 'Profile updated' });
        }
    );
});

// Upload profile photo (base64)
app.post('/api/profile/photo', requireAuth, (req, res) => {
    const { photo } = req.body; // base64 encoded image
    if (typeof photo !== 'string' || !photo.trim()) {
        return res.status(400).json({ message: 'Photo data is required' });
    }

    // Accept only image data URIs and validate decoded payload size.
    const dataUriMatch = photo.match(/^data:image\/(jpeg|jpg|png|webp);base64,([A-Za-z0-9+/=]+)$/i);
    if (!dataUriMatch) {
        return res.status(400).json({ message: 'Invalid image format' });
    }

    const base64Payload = dataUriMatch[2];
    const decodedSizeBytes = Buffer.byteLength(base64Payload, 'base64');
    if (!Number.isFinite(decodedSizeBytes) || decodedSizeBytes <= 0 || decodedSizeBytes > 3 * 1024 * 1024) {
        return res.status(400).json({ message: 'Photo too large (max 3MB)' });
    }

    db.run(
        `UPDATE users SET profile_photo_url = ? WHERE id = ?`,
        [photo, req.session.userId],
        function (err) {
            if (err) return res.status(500).json({ message: 'Database error' });
            res.json({ success: true, message: 'Photo uploaded', profile_photo_url: photo });
        }
    );
});

// Remove profile photo
app.delete('/api/profile/photo', requireAuth, (req, res) => {
    db.run(
        `UPDATE users SET profile_photo_url = NULL WHERE id = ?`,
        [req.session.userId],
        function (err) {
            if (err) return res.status(500).json({ message: 'Database error' });
            res.json({ success: true, message: 'Photo removed' });
        }
    );
});

// Send phone verification OTP
app.post('/api/profile/verify-phone/send', requireAuth, otpRateLimiter, (req, res) => {
    const phone = sanitizeString(req.body.phone);
    if (!phone || !/^\+?\d{10,15}$/.test(phone.replace(/[\s-]/g, ''))) {
        return res.status(400).json({ message: 'Valid phone number is required' });
    }

    // Generate 6-digit OTP
    const otp = Math.floor(100000 + Math.random() * 900000).toString();
    phoneOtpStore[req.session.userId] = {
        otp,
        phone,
        expiresAt: Date.now() + 5 * 60 * 1000 // 5 minutes
    };

    // In production, send via SMS gateway (Twilio, etc.)
    // For now, log to console and return success
    console.log(`[Phone OTP] User ${req.session.userId} → ${phone}: ${otp}`);

    res.json({
        success: true,
        message: 'OTP sent to your phone',
        // DEV ONLY — remove in production
        ...(process.env.NODE_ENV !== 'production' && { dev_otp: otp })
    });
});

// Verify phone OTP
app.post('/api/profile/verify-phone/confirm', requireAuth, otpRateLimiter, (req, res) => {
    const { otp } = req.body;
    const stored = phoneOtpStore[req.session.userId];

    if (!stored) {
        return res.status(400).json({ message: 'No OTP request found. Please request a new OTP.' });
    }

    if (Date.now() > stored.expiresAt) {
        delete phoneOtpStore[req.session.userId];
        return res.status(400).json({ message: 'OTP expired. Please request a new one.' });
    }

    if (stored.otp !== String(otp)) {
        return res.status(400).json({ message: 'Invalid OTP' });
    }

    // OTP verified — update user phone + verified flag
    db.run(
        `UPDATE users SET phone = ?, is_phone_verified = 1 WHERE id = ?`,
        [stored.phone, req.session.userId],
        function (err) {
            delete phoneOtpStore[req.session.userId];
            if (err) return res.status(500).json({ message: 'Database error' });
            res.json({ success: true, message: 'Phone verified successfully' });
        }
    );
});

// Get profile completion percentage
app.get('/api/profile/completion', requireAuth, (req, res) => {
    db.get(
        `SELECT name, email, phone, is_phone_verified, gender, date_of_birth,
                vehicle_number, profile_photo_url, bio, emergency_contact
         FROM users WHERE id = ?`,
        [req.session.userId],
        (err, user) => {
            if (err) return res.status(500).json({ message: 'Database error' });
            if (!user) return res.status(404).json({ message: 'User not found' });

            const fields = [
                { name: 'Name', filled: !!user.name },
                { name: 'Email', filled: !!user.email },
                { name: 'Phone', filled: !!user.phone && !!user.is_phone_verified },
                { name: 'Gender', filled: !!user.gender },
                { name: 'Date of Birth', filled: !!user.date_of_birth },
                { name: 'Profile Photo', filled: !!user.profile_photo_url },
                { name: 'Vehicle', filled: !!user.vehicle_number },
                { name: 'Emergency Contact', filled: !!user.emergency_contact },
                { name: 'Bio', filled: !!user.bio },
            ];

            const filled = fields.filter(f => f.filled).length;
            res.json({
                success: true,
                total: fields.length,
                filled,
                percentage: Math.round((filled / fields.length) * 100),
                fields
            });
        }
    );
});

// ============= CONFIG ENDPOINT (original) =============

// Serve Ola Maps API key to frontend
app.get('/api/config', requireAuth, (req, res) => {
    const allowClientMapsKey = process.env.ALLOW_CLIENT_MAPS_KEY !== 'false';
    const origin = req.headers.origin;

    if (origin && !isAllowedOrigin(origin)) {
        return res.status(403).json({ message: 'Origin not allowed' });
    }

    res.setHeader('Cache-Control', 'no-store');
    res.json({
        olaMapsApiKey: allowClientMapsKey ? (process.env.OLA_MAPS_API_KEY || '') : ''
    });
});

function getOlaApiKey() {
    return process.env.OLA_MAPS_API_KEY || '';
}

function normalizeAutocompletePrediction(prediction = {}) {
    const location = prediction.geometry?.location || {};
    return {
        description: prediction.description || prediction.structured_formatting?.main_text || '',
        place_id: prediction.place_id || prediction.reference || '',
        lat: typeof location.lat === 'number' ? location.lat : null,
        lng: typeof location.lng === 'number' ? location.lng : null,
        types: Array.isArray(prediction.types) ? prediction.types : []
    };
}

app.get('/api/maps/autocomplete', async (req, res) => {
    const apiKey = getOlaApiKey();
    const query = sanitizeString(req.query.q || req.query.input || '');
    const limit = Math.min(Math.max(parseInt(req.query.limit, 10) || 8, 1), 10);

    if (!apiKey) {
        return res.status(503).json({ message: 'Ola Maps API key not configured' });
    }

    if (!query || query.length < 2) {
        return res.json({ predictions: [] });
    }

    const url = `https://api.olamaps.io/places/v1/autocomplete?input=${encodeURIComponent(query)}&api_key=${apiKey}`;

    try {
        const result = await fetchJsonWithTimeout(url);

        if (!result.ok) {
            return res.status(result.status).json({
                message: 'Failed to fetch autocomplete results',
                predictions: []
            });
        }

        const predictions = Array.isArray(result.data?.predictions)
            ? result.data.predictions.slice(0, limit).map(normalizeAutocompletePrediction)
            : [];

        res.json({ predictions });
    } catch (error) {
        console.error('Autocomplete proxy error:', error.message || error);
        res.status(500).json({ message: 'Autocomplete service unavailable', predictions: [] });
    }
});

app.get('/api/maps/geocode', async (req, res) => {
    const apiKey = getOlaApiKey();
    const query = sanitizeString(req.query.query || req.query.q || req.query.input || '');

    if (!apiKey) {
        return res.status(503).json({ message: 'Ola Maps API key not configured' });
    }

    if (!query || query.length < 2) {
        return res.status(400).json({ message: 'Query must be at least 2 characters' });
    }

    const url = `https://api.olamaps.io/places/v1/autocomplete?input=${encodeURIComponent(query)}&api_key=${apiKey}`;

    try {
        const result = await fetchJsonWithTimeout(url);

        if (!result.ok) {
            return res.status(result.status).json({ message: 'Geocode lookup failed' });
        }

        const first = Array.isArray(result.data?.predictions) ? result.data.predictions[0] : null;
        const normalized = first ? normalizeAutocompletePrediction(first) : null;

        if (!normalized || normalized.lat === null || normalized.lng === null) {
            return res.status(404).json({ message: 'No matching location found' });
        }

        res.json({ location: normalized });
    } catch (error) {
        console.error('Geocode proxy error:', error.message || error);
        res.status(500).json({ message: 'Geocode service unavailable' });
    }
});

app.get('/api/maps/reverse-geocode', async (req, res) => {
    const apiKey = getOlaApiKey();
    const lat = parseFloat(req.query.lat);
    const lng = parseFloat(req.query.lng);

    if (!apiKey) {
        return res.status(503).json({ message: 'Ola Maps API key not configured' });
    }

    if (!isValidCoordinate(lat, lng)) {
        return res.status(400).json({ message: 'Invalid coordinates' });
    }

    const url = `https://api.olamaps.io/places/v1/reverse-geocode?latlng=${lat},${lng}&api_key=${apiKey}`;

    try {
        const result = await fetchJsonWithTimeout(url);

        if (!result.ok) {
            return res.status(result.status).json({ message: 'Reverse geocode lookup failed' });
        }

        const first = Array.isArray(result.data?.results) ? result.data.results[0] : null;
        const location = first?.geometry?.location || {};

        res.json({
            address: first?.formatted_address || '',
            lat: typeof location.lat === 'number' ? location.lat : lat,
            lng: typeof location.lng === 'number' ? location.lng : lng,
            place_id: first?.place_id || ''
        });
    } catch (error) {
        console.error('Reverse geocode proxy error:', error.message || error);
        res.status(500).json({ message: 'Reverse geocode service unavailable' });
    }
});

app.get('/api/maps/directions', async (req, res) => {
    const apiKey = getOlaApiKey();
    const origin = sanitizeString(req.query.origin || '');
    const destination = sanitizeString(req.query.destination || '');

    if (!apiKey) {
        return res.status(503).json({ message: 'Ola Maps API key not configured' });
    }

    if (!origin || !destination) {
        return res.status(400).json({ message: 'Origin and destination are required' });
    }

    const directionsUrl = `https://api.olamaps.io/routing/v1/directions?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&api_key=${apiKey}`;
    const distanceMatrixUrl = `https://api.olamaps.io/routing/v1/distanceMatrix?origins=${encodeURIComponent(origin)}&destinations=${encodeURIComponent(destination)}&api_key=${apiKey}`;

    try {
        const directionsResult = await fetchJsonWithTimeout(directionsUrl);

        if (directionsResult.ok && Array.isArray(directionsResult.data?.routes) && directionsResult.data.routes.length > 0) {
            return res.json(directionsResult.data);
        }

        const matrixResult = await fetchJsonWithTimeout(distanceMatrixUrl);

        if (!matrixResult.ok) {
            return res.status(matrixResult.status).json({ message: 'Directions lookup failed' });
        }

        const element = matrixResult.data?.rows?.[0]?.elements?.[0];
        if (!element) {
            return res.status(404).json({ message: 'No route found' });
        }

        res.json({
            routes: [
                {
                    legs: [
                        {
                            distance: element.distance || 0,
                            duration: element.duration || 0
                        }
                    ],
                    overview_polyline: element.polyline || ''
                }
            ]
        });
    } catch (error) {
        console.error('Directions proxy error:', error.message || error);
        res.status(500).json({ message: 'Directions service unavailable' });
    }
});

// ============= DRIVER ENDPOINTS =============

// Create a new trip (driver offering a ride)
app.post('/api/trips', requireAuth, (req, res) => {
    const start_location = sanitizeString(req.body.start_location);
    const end_location = sanitizeString(req.body.end_location);
    const { start_lat, start_lng, end_lat, end_lng,
        route_polyline, distance_km, duration_minutes, departure_time, available_seats } = req.body;

    if (!start_location || !end_location ||
        !hasValue(start_lat) || !hasValue(start_lng) ||
        !hasValue(end_lat) || !hasValue(end_lng)) {
        return res.status(400).json({ message: 'Missing required location data' });
    }

    if (!isValidCoordinate(start_lat, start_lng) || !isValidCoordinate(end_lat, end_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

    const seats = Math.max(1, Math.min(8, parseInt(available_seats, 10) || 1));
    const parsedDeparture = new Date(departure_time);
    const normalizedDepartureTime =
        !departure_time ||
        String(departure_time).toLowerCase() === 'now' ||
        Number.isNaN(parsedDeparture.getTime())
            ? new Date().toISOString()
            : parsedDeparture.toISOString();

    // First check if the driver already has an active trip
    const checkActiveTripSql = `SELECT id FROM trips WHERE driver_id = ? AND status = 'active' LIMIT 1`;

    db.get(checkActiveTripSql, [req.session.userId], (err, existingTrip) => {
        if (err) {
            console.error('Error checking for existing trip:', err);
            return res.status(500).json({ message: 'Error checking existing trips' });
        }

        if (existingTrip) {
            return res.status(409).json({
                message: 'You already have an active trip. Please complete or cancel your current trip before creating a new one.',
                existingTripId: existingTrip.id
            });
        }

        // No active trip found, proceed with creating new trip
        const sql = `INSERT INTO trips (driver_id, start_location, end_location, start_lat, start_lng, 
                     end_lat, end_lng, route_polyline, distance_km, duration_minutes, departure_time, available_seats) 
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`;

        db.run(sql, [req.session.userId, start_location, end_location, start_lat, start_lng,
            end_lat, end_lng, route_polyline, distance_km, duration_minutes,
            normalizedDepartureTime, seats], function (err) {
            if (err) {
                console.error('Error creating trip:', err);
                return res.status(500).json({ message: 'Error creating trip' });
            }
            res.status(201).json({
                message: 'Trip created successfully',
                tripId: this.lastID
            });
        });
    });
});

// Get all rides (as driver and passenger) for current user
app.get('/api/trips/my-rides', requireAuth, (req, res) => {
    const userId = req.session.userId;

    // Rides as driver
    const driverSql = `SELECT t.*, 'driver' as user_role, 
                        (SELECT COUNT(*) FROM matches WHERE trip_id = t.id AND status = 'accepted') as passenger_count
                        FROM trips t WHERE t.driver_id = ? ORDER BY t.created_at DESC`;

    // Rides as passenger (via matches)
    const passengerSql = `SELECT t.*, 'passenger' as user_role, m.status as match_status,
                           ud.name as driver_name, m.fare_amount as fare_share,
                           m.id as match_id, ud.id as driver_user_id
                           FROM matches m 
                           JOIN trips t ON m.trip_id = t.id 
                           JOIN ride_requests rr ON m.ride_request_id = rr.id
                           JOIN users ud ON t.driver_id = ud.id
                           WHERE rr.passenger_id = ? 
                           ORDER BY t.created_at DESC`;

    db.all(driverSql, [userId], (err1, driverRides) => {
        if (err1) return res.status(500).json({ message: 'Error fetching rides' });
        db.all(passengerSql, [userId], (err2, passengerRides) => {
            if (err2) return res.status(500).json({ message: 'Error fetching rides' });
            
            const allRides = [
                ...(driverRides || []).map(r => ({ ...r, userRole: 'driver' })),
                ...(passengerRides || []).map(r => ({ ...r, userRole: 'passenger' }))
            ].sort((a, b) => new Date(b.created_at) - new Date(a.created_at));

            res.json({ rides: allRides });
        });
    });
});

// Get active trip for current driver - MUST BE BEFORE :id ROUTE
app.get('/api/trips/active', requireAuth, (req, res) => {
    const sql = `SELECT * FROM trips WHERE driver_id = ? AND status = 'active' LIMIT 1`;

    db.get(sql, [req.session.userId], (err, trip) => {
        if (err) {
            return res.status(500).json({ message: 'Error fetching active trip' });
        }

        if (!trip) {
            return res.json({ hasActiveTrip: false });
        }

        res.json({
            hasActiveTrip: true,
            trip: trip
        });
    });
});

// Get trip details
app.get('/api/trips/:id', requireAuth, (req, res) => {
    const sql = `SELECT t.*, u.name as driver_name 
                 FROM trips t 
                 JOIN users u ON t.driver_id = u.id 
                 WHERE t.id = ?
                   AND (t.driver_id = ? OR EXISTS (
                       SELECT 1 FROM matches m
                       JOIN ride_requests rr ON m.ride_request_id = rr.id
                       WHERE m.trip_id = t.id AND rr.passenger_id = ? AND m.status IN ('pending','accepted')
                   ))`;

    db.get(sql, [req.params.id, req.session.userId, req.session.userId], (err, trip) => {
        if (err) {
            return res.status(500).json({ message: 'Error fetching trip' });
        }
        if (!trip) {
            return res.status(404).json({ message: 'Trip not found' });
        }
        res.json(trip);
    });
});

// Get route segment for passenger (privacy-preserving)
app.get('/api/trips/:id/route-segment', requireAuth, (req, res) => {
    const { pickup_lat, pickup_lng, dropoff_lat, dropoff_lng } = req.query;

    if (!hasValue(pickup_lat) || !hasValue(pickup_lng) ||
        !hasValue(dropoff_lat) || !hasValue(dropoff_lng)) {
        return res.status(400).json({ message: 'Missing required coordinates' });
    }

    if (!isValidCoordinate(pickup_lat, pickup_lng) || !isValidCoordinate(dropoff_lat, dropoff_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

    // First get the trip with polyline
    const sql = `SELECT id, route_polyline FROM trips WHERE id = ? AND status = 'active'`;

    db.get(sql, [req.params.id], (err, trip) => {
        if (err) {
            return res.status(500).json({ message: 'Error fetching trip' });
        }
        if (!trip || !trip.route_polyline) {
            return res.status(404).json({ message: 'Trip or route not found' });
        }

        try {
            // Decode the full route
            const fullRoute = getDecodedRoutePoints(trip.route_polyline);

            // Parse coordinates
            const pickupPoint = {
                lat: parseFloat(pickup_lat),
                lng: parseFloat(pickup_lng)
            };
            const passengerDestination = {
                lat: parseFloat(dropoff_lat),
                lng: parseFloat(dropoff_lng)
            };

            // Calculate dynamic threshold (same logic as in available-drivers endpoint)
            const originalDistance = calculateDistance(
                pickupPoint.lat, pickupPoint.lng,
                passengerDestination.lat, passengerDestination.lng
            );
            const distKm = originalDistance / 1000;

            let threshold = 800; // Base threshold in meters
            if (distKm <= 5) {
                threshold = 800;
            } else if (distKm <= 20) {
                threshold = 1000 + (distKm - 5) * 50;
            } else if (distKm <= 50) {
                threshold = 1750 + (distKm - 20) * 30;
            } else if (distKm <= 100) {
                threshold = 2650 + (distKm - 50) * 20;
            } else {
                threshold = Math.max(3650, distKm * 10);
            }

            // Use the same matching logic to find the actual drop-off point
            const routeMatch = checkRouteMatch(
                trip.route_polyline,
                pickupPoint.lat,
                pickupPoint.lng,
                passengerDestination.lat,
                passengerDestination.lng,
                threshold
            );

            if (!routeMatch) {
                return res.status(404).json({ message: 'No route match found' });
            }

            // Determine the actual drop-off point
            let actualDropoffPoint;
            if (routeMatch.isPartialRide && routeMatch.bestDropPoint) {
                // For partial rides, use the best drop point calculated by the matching algorithm
                actualDropoffPoint = routeMatch.bestDropPoint;
            } else {
                // For full rides, use the passenger's destination
                actualDropoffPoint = passengerDestination;
            }

            // Find closest point on route to pickup
            const pickupMatch = findClosestPointOnRoute(fullRoute, pickupPoint.lat, pickupPoint.lng);

            // For the drop-off, use the bestDropPointIndex from the route match result
            // This ensures we use the exact same point that was calculated during matching
            let dropoffIndex;
            if (routeMatch.isPartialRide && routeMatch.bestDropPointIndex !== -1) {
                // For partial rides, use the best drop point index directly
                dropoffIndex = routeMatch.bestDropPointIndex;
            } else {
                // For full rides, find the closest point to passenger destination
                const dropoffMatch = findClosestPointOnRoute(
                    fullRoute.slice(pickupMatch.index),
                    actualDropoffPoint.lat,
                    actualDropoffPoint.lng
                );
                dropoffIndex = pickupMatch.index + dropoffMatch.index;
            }

            // Extract the segment from pickup to actual drop-off
            const startIndex = pickupMatch.index;
            const endIndex = Math.min(dropoffIndex + 1, fullRoute.length); // Ensure we don't exceed array bounds
            const routeSegment = fullRoute.slice(startIndex, endIndex);

            // Add pickup and actual drop-off points to ensure smooth connection
            if (routeSegment.length > 0) {
                // Add exact pickup point at the beginning if it's not too close
                if (calculateDistance(
                    routeSegment[0].lat,
                    routeSegment[0].lng,
                    pickupPoint.lat,
                    pickupPoint.lng
                ) > 50) { // More than 50 meters
                    routeSegment.unshift(pickupPoint);
                }

                // Add actual drop-off point at the end if it's not too close
                const lastPoint = routeSegment[routeSegment.length - 1];
                if (calculateDistance(
                    lastPoint.lat,
                    lastPoint.lng,
                    actualDropoffPoint.lat,
                    actualDropoffPoint.lng
                ) > 50) { // More than 50 meters
                    routeSegment.push(actualDropoffPoint);
                }
            }

            // Calculate segment distance and estimated duration
            let segmentDistance = 0;
            for (let i = 1; i < routeSegment.length; i++) {
                segmentDistance += calculateDistance(
                    routeSegment[i - 1].lat,
                    routeSegment[i - 1].lng,
                    routeSegment[i].lat,
                    routeSegment[i].lng
                );
            }

            // Estimate duration (assuming average speed of 30 km/h in city)
            const segmentDuration = Math.round((segmentDistance / 1000) / 30 * 60); // minutes

            res.json({
                routeSegment: routeSegment,
                segmentDistance: Math.round(segmentDistance), // in meters
                segmentDuration: segmentDuration, // in minutes
                totalPoints: routeSegment.length,
                isPartialRide: routeMatch.isPartialRide,
                actualDropoffPoint: actualDropoffPoint,
                passengerDestination: passengerDestination,
                remainingDistance: routeMatch.isPartialRide ? Math.round(routeMatch.remainingDistance) : 0
            });

        } catch (error) {
            console.error('Error processing route segment:', error);
            res.status(500).json({ message: 'Error processing route segment' });
        }
    });
});

// Get passenger requests for a trip
app.get('/api/trips/:id/requests', requireAuth, (req, res) => {
    const tripId = parseInt(req.params.id, 10);
    if (!Number.isFinite(tripId) || tripId <= 0) {
        return res.status(400).json({ message: 'Invalid trip id' });
    }

    const ownershipSql = `SELECT id FROM trips WHERE id = ? AND driver_id = ? LIMIT 1`;
    db.get(ownershipSql, [tripId, req.session.userId], (ownershipErr, ownedTrip) => {
        if (ownershipErr) {
            return res.status(500).json({ message: 'Error checking trip ownership' });
        }
        if (!ownedTrip) {
            return res.status(403).json({ message: 'Not authorized to view this trip requests list' });
        }

        const sql = `SELECT m.id as match_id, m.trip_id, m.ride_request_id, m.pickup_point_lat, 
                     m.pickup_point_lng, m.dropoff_point_lat, m.dropoff_point_lng, 
                     m.fare_amount, m.status as match_status, m.created_at as match_created_at,
                     rr.pickup_location, rr.dropoff_location, rr.pickup_lat, rr.pickup_lng, 
                     rr.dropoff_lat, rr.dropoff_lng, rr.requested_time,
                     u.name as passenger_name 
                     FROM matches m 
                     JOIN ride_requests rr ON m.ride_request_id = rr.id 
                     JOIN users u ON rr.passenger_id = u.id 
                     WHERE m.trip_id = ? AND m.status = 'pending'`;

        db.all(sql, [tripId], (err, requests) => {
            if (err) {
                return res.status(500).json({ message: 'Error fetching requests' });
            }
            // Map the results to use match_id as id for easier access
            const mappedRequests = requests.map(req => ({
                ...req,
                id: req.match_id, // Use match_id as the primary id
                match_id: req.match_id, // Keep match_id for clarity
                ride_request_id: req.ride_request_id // Keep ride_request_id for reference
            }));
            res.json(mappedRequests);
        });
    });
});

// Cancel a trip (driver cancelling their trip)
app.put('/api/trips/:id/cancel', requireAuth, (req, res) => {
    const tripId = req.params.id;

    // First verify that the trip belongs to the current user
    const checkOwnershipSql = `SELECT driver_id FROM trips WHERE id = ? AND status = 'active'`;

    db.get(checkOwnershipSql, [tripId], (err, trip) => {
        if (err) {
            return res.status(500).json({ message: 'Error checking trip ownership' });
        }

        if (!trip) {
            return res.status(404).json({ message: 'Active trip not found' });
        }

        if (trip.driver_id !== req.session.userId) {
            return res.status(403).json({ message: 'You can only cancel your own trips' });
        }

        // Update trip status to cancelled
        const updateTripSql = `UPDATE trips SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP WHERE id = ?`;

        db.run(updateTripSql, [tripId], function (err) {
            if (err) {
                return res.status(500).json({ message: 'Error cancelling trip' });
            }

            // Also update any pending matches to rejected
            const updateMatchesSql = `UPDATE matches SET status = 'rejected', updated_at = CURRENT_TIMESTAMP 
                                     WHERE trip_id = ? AND status = 'pending'`;

            db.run(updateMatchesSql, [tripId], (err) => {
                if (err) {
                    console.error('Error updating matches:', err);
                }
            });

            res.json({ message: 'Trip cancelled successfully' });
        });
    });
});

// Complete a trip (driver marks ride as done)
app.put('/api/trips/:id/complete', requireAuth, (req, res) => {
    const tripId = req.params.id;

    const checkSql = `SELECT driver_id FROM trips WHERE id = ? AND status = 'active'`;
    db.get(checkSql, [tripId], (err, trip) => {
        if (err) {
            return res.status(500).json({ message: 'Error checking trip' });
        }
        if (!trip) {
            return res.status(404).json({ message: 'Active trip not found' });
        }
        if (trip.driver_id !== req.session.userId) {
            return res.status(403).json({ message: 'You can only complete your own trips' });
        }

        const updateSql = `UPDATE trips SET status = 'completed', updated_at = CURRENT_TIMESTAMP WHERE id = ?`;
        db.run(updateSql, [tripId], function (err) {
            if (err) {
                return res.status(500).json({ message: 'Error completing trip' });
            }

            // Mark accepted matches as completed too
            const updateMatchesSql = `UPDATE matches SET status = 'completed', updated_at = CURRENT_TIMESTAMP 
                                     WHERE trip_id = ? AND status = 'accepted'`;
            db.run(updateMatchesSql, [tripId], (matchErr) => {
                if (matchErr) console.error('Error updating matches:', matchErr);
            });

            // Reject any remaining pending matches
            const rejectPendingSql = `UPDATE matches SET status = 'rejected', updated_at = CURRENT_TIMESTAMP 
                                     WHERE trip_id = ? AND status = 'pending'`;
            db.run(rejectPendingSql, [tripId], (pendErr) => {
                if (pendErr) console.error('Error rejecting pending:', pendErr);
            });

            res.json({ message: 'Trip completed successfully' });
        });
    });
});

// ============= PASSENGER ENDPOINTS =============

// Create a ride request (passenger looking for ride)
app.post('/api/ride-requests', requireAuth, (req, res) => {
    const pickup_location = sanitizeString(req.body.pickup_location);
    const dropoff_location = sanitizeString(req.body.dropoff_location);
    const { pickup_lat, pickup_lng,
        dropoff_lat, dropoff_lng, requested_time } = req.body;

    if (!pickup_location || !dropoff_location ||
        !hasValue(pickup_lat) || !hasValue(pickup_lng) ||
        !hasValue(dropoff_lat) || !hasValue(dropoff_lng)) {
        return res.status(400).json({ message: 'Missing required location data' });
    }

    if (!isValidCoordinate(pickup_lat, pickup_lng) || !isValidCoordinate(dropoff_lat, dropoff_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

    const sql = `INSERT INTO ride_requests (passenger_id, pickup_location, dropoff_location, 
                 pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, requested_time) 
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`;

    db.run(sql, [req.session.userId, pickup_location, dropoff_location, pickup_lat,
        pickup_lng, dropoff_lat, dropoff_lng, requested_time || new Date().toISOString()],
        function (err) {
            if (err) {
                console.error('Error creating ride request:', err);
                return res.status(500).json({ message: 'Error creating ride request' });
            }
            res.status(201).json({
                message: 'Ride request created successfully',
                requestId: this.lastID
            });
        });
});

// Cancel a ride request (passenger)
app.put('/api/ride-requests/:id/cancel', requireAuth, (req, res) => {
    const requestId = req.params.id;
    db.get(`SELECT id, passenger_id, status FROM ride_requests WHERE id = ?`, [requestId], (err, rr) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        if (!rr) return res.status(404).json({ message: 'Ride request not found' });
        if (rr.passenger_id !== req.session.userId) {
            return res.status(403).json({ message: 'Not authorized' });
        }
        if (rr.status === 'cancelled' || rr.status === 'completed') {
            return res.status(400).json({ message: 'Cannot cancel — ride is already ' + rr.status });
        }
        db.run(`UPDATE ride_requests SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP WHERE id = ?`,
            [requestId], function (updateErr) {
                if (updateErr) return res.status(500).json({ message: 'Error cancelling request' });
                // Also reject any pending/accepted matches — wait for completion before responding
                db.run(`UPDATE matches SET status = 'rejected', updated_at = CURRENT_TIMESTAMP WHERE ride_request_id = ? AND status IN ('pending','accepted')`,
                    [requestId], function (matchErr) {
                        if (matchErr) {
                            console.error('Error rejecting matches during cancel:', matchErr);
                            // Ride request is already cancelled, report partial success
                        }
                        res.json({ message: 'Ride request cancelled successfully' });
                    });
            });
    });
});

// Find available drivers for a passenger
app.get('/api/available-drivers', requireAuth, (req, res) => {
    const { pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, distance_km } = req.query;

    if (!hasValue(pickup_lat) || !hasValue(pickup_lng) ||
        !hasValue(dropoff_lat) || !hasValue(dropoff_lng)) {
        return res.status(400).json({ message: 'Missing location parameters' });
    }

    if (!isValidCoordinate(pickup_lat, pickup_lng) || !isValidCoordinate(dropoff_lat, dropoff_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

    // Calculate dynamic threshold based on distance
    const requestedDistanceKm = parseFloat(distance_km);
    const distKm = Number.isFinite(requestedDistanceKm) && requestedDistanceKm > 0
        ? requestedDistanceKm
        : 10;
    let threshold = 800; // Base threshold in meters

    // More generous threshold calculation for longer distances
    if (distKm <= 5) {
        threshold = 800; // 800m for short trips
    } else if (distKm <= 20) {
        threshold = 1000 + (distKm - 5) * 50; // 1-1.75km for medium trips
    } else if (distKm <= 50) {
        threshold = 1750 + (distKm - 20) * 30; // 1.75-2.65km for longer trips
    } else if (distKm <= 100) {
        threshold = 2650 + (distKm - 50) * 20; // 2.65-3.65km for very long trips
    } else {
        // For trips over 100km, use 1% of distance as threshold (min 3.65km)
        threshold = Math.max(3650, distKm * 10);
    }

    console.log(`Distance: ${distKm}km, Threshold: ${threshold}m`);

    // Get all active trips (exclude user's own trips)
    const sql = `SELECT t.*, u.name as driver_name, u.email as driver_email
                 FROM trips t 
                 JOIN users u ON t.driver_id = u.id 
                 WHERE t.status = 'active' 
                 AND t.driver_id != ?
                 AND t.available_seats > 0
                 AND datetime(t.departure_time) > datetime('now')
                 AND t.route_polyline IS NOT NULL
                 AND t.route_polyline != ''`;

    db.all(sql, [req.session.userId], (err, trips) => {
        if (err) {
            console.error('Error finding drivers:', err);
            return res.status(500).json({ message: 'Error finding drivers' });
        }

        // Filter trips based on route matching
        const matchingDrivers = [];

        for (const trip of trips) {
            // Check if the driver's route passes near both pickup and dropoff
            const routeMatch = checkRouteMatch(
                trip.route_polyline,
                parseFloat(pickup_lat),
                parseFloat(pickup_lng),
                parseFloat(dropoff_lat),
                parseFloat(dropoff_lng),
                threshold
            );

            if (routeMatch) {
                // Calculate fare based on actual distance covered
                let actualDistanceKm = distKm;
                if (routeMatch.isPartialRide) {
                    // For partial rides, calculate fare based on distance covered
                    const coveredDistance = routeMatch.originalDistance - routeMatch.remainingDistance;
                    actualDistanceKm = coveredDistance / 1000; // Convert to km
                }

                const baseFare = actualDistanceKm * 3; // ₹3 per km
                const fareVariation = 0.8 + Math.random() * 0.4; // ±20% variation
                const estimatedFare = Math.round(baseFare * fareVariation);

                // Calculate ETA based on route position
                const routeProgress = routeMatch.pickupIndex / routeMatch.totalRoutePoints;
                const estimatedMinutesToPickup = Math.round(trip.duration_minutes * routeProgress);
                const eta = Math.max(3, Math.min(estimatedMinutesToPickup, 30)); // Between 3-30 minutes

                // Add driver to results
                matchingDrivers.push({
                    ...trip,
                    estimated_fare: estimatedFare,
                    eta: eta,
                    pickup_distance: Math.round(routeMatch.pickupDistance),
                    dropoff_distance: Math.round(routeMatch.dropoffDistance),
                    // Hide exact destination for privacy
                    end_location_masked: trip.end_location.split(',')[0] + ' area',
                    // Don't send sensitive data
                    route_polyline: undefined,
                    end_lat: undefined,
                    end_lng: undefined,
                    // Add partial ride information
                    is_partial_ride: routeMatch.isPartialRide,
                    remaining_distance: routeMatch.isPartialRide ? Math.round(routeMatch.remainingDistance / 1000) : 0, // in km
                    distance_saved_percentage: routeMatch.isPartialRide ? Math.round(routeMatch.distanceReduction) : 100,
                    // Add actual drop-off coordinates for partial rides
                    actual_dropoff_lat: routeMatch.isPartialRide ? routeMatch.bestDropPoint.lat : parseFloat(dropoff_lat),
                    actual_dropoff_lng: routeMatch.isPartialRide ? routeMatch.bestDropPoint.lng : parseFloat(dropoff_lng),
                    // Store the route match for later use (internal only)
                    route_match: undefined
                });
            }
        }

        // Sort by pickup distance
        matchingDrivers.sort((a, b) => a.pickup_distance - b.pickup_distance);

        // Limit results
        const limitedResults = matchingDrivers.slice(0, 20);

        res.json(limitedResults);
    });
});

// ============= MATCHING ENDPOINTS =============

// Create a match between driver and passenger
app.post('/api/matches', requireAuth, (req, res) => {
    const { trip_id, ride_request_id, pickup_lat, pickup_lng, dropoff_lat,
        dropoff_lng, fare_amount } = req.body;

    if (!hasValue(trip_id) || !hasValue(ride_request_id) ||
        !hasValue(pickup_lat) || !hasValue(pickup_lng) ||
        !hasValue(dropoff_lat) || !hasValue(dropoff_lng) ||
        !hasValue(fare_amount)) {
        return res.status(400).json({ message: 'Missing required match data' });
    }

    if (!isValidCoordinate(pickup_lat, pickup_lng) || !isValidCoordinate(dropoff_lat, dropoff_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

    const tripId = parseInt(trip_id, 10);
    const rideRequestId = parseInt(ride_request_id, 10);
    const fareAmount = parseFloat(fare_amount);

    if (!Number.isFinite(tripId) || tripId <= 0 || !Number.isFinite(rideRequestId) || rideRequestId <= 0) {
        return res.status(400).json({ message: 'Invalid trip or ride request id' });
    }

    if (!Number.isFinite(fareAmount) || fareAmount <= 0) {
        return res.status(400).json({ message: 'fare_amount must be a positive number' });
    }

    const verifySql = `SELECT rr.id as ride_request_id, rr.passenger_id, rr.status as request_status,
                       t.id as trip_id, t.status as trip_status, t.driver_id, t.available_seats
                       FROM ride_requests rr
                       JOIN trips t ON t.id = ?
                       WHERE rr.id = ?`;

    db.get(verifySql, [tripId, rideRequestId], (verifyErr, validationRow) => {
        if (verifyErr) {
            console.error('Error validating match request:', verifyErr);
            return res.status(500).json({ message: 'Error validating match request' });
        }

        if (!validationRow) {
            return res.status(404).json({ message: 'Trip or ride request not found' });
        }

        if (validationRow.passenger_id !== req.session.userId) {
            return res.status(403).json({ message: 'You can only create matches for your own ride request' });
        }

        if (validationRow.driver_id === req.session.userId) {
            return res.status(400).json({ message: 'You cannot match with your own trip' });
        }

        if (validationRow.trip_status !== 'active') {
            return res.status(409).json({ message: 'Trip is no longer active' });
        }

        if (!Number.isFinite(validationRow.available_seats) || validationRow.available_seats <= 0) {
            return res.status(409).json({ message: 'No seats available' });
        }

        if (!['searching', 'matched'].includes(validationRow.request_status)) {
            return res.status(409).json({ message: 'Ride request is not open for new matches' });
        }

        const existingActiveMatchSql = `SELECT id FROM matches
                                        WHERE ride_request_id = ?
                                          AND status IN ('pending', 'accepted')
                                        LIMIT 1`;
        db.get(existingActiveMatchSql, [rideRequestId], (activeMatchErr, existingMatch) => {
            if (activeMatchErr) {
                console.error('Error checking existing matches:', activeMatchErr);
                return res.status(500).json({ message: 'Error validating match state' });
            }
            if (existingMatch) {
                return res.status(409).json({ message: 'You already have an active match request' });
            }

            const sql = `INSERT INTO matches (trip_id, ride_request_id, pickup_point_lat, 
                         pickup_point_lng, dropoff_point_lat, dropoff_point_lng, fare_amount) 
                         VALUES (?, ?, ?, ?, ?, ?, ?)`;

            db.run(sql, [tripId, rideRequestId, pickup_lat, pickup_lng,
                dropoff_lat, dropoff_lng, fareAmount], function (err) {
                    if (err) {
                        if (err.code === 'SQLITE_CONSTRAINT') {
                            return res.status(409).json({ message: 'Match already exists' });
                        }
                        console.error('Error creating match:', err);
                        return res.status(500).json({ message: 'Error creating match' });
                    }

                    const matchId = this.lastID;

                    // Update ride request status
                    db.run(`UPDATE ride_requests SET status = 'matched', updated_at = CURRENT_TIMESTAMP WHERE id = ?`,
                        [rideRequestId], (err) => {
                            if (err) console.error('Error updating ride request status:', err);
                        });

                    // Instant WebSocket notification to driver
                    const getPassengerSql = `SELECT rr.*, u.name as passenger_name 
                                         FROM ride_requests rr 
                                         JOIN users u ON rr.passenger_id = u.id 
                                         WHERE rr.id = ?`;

                    db.get(getPassengerSql, [rideRequestId], (err, passengerData) => {
                        if (!err && passengerData) {
                            const notificationData = {
                                id: matchId,
                                passenger_name: passengerData.passenger_name,
                                pickup_location: passengerData.pickup_location,
                                dropoff_location: passengerData.dropoff_location,
                                pickup_lat: pickup_lat,
                                pickup_lng: pickup_lng,
                                dropoff_lat: dropoff_lat,
                                dropoff_lng: dropoff_lng,
                                fare_amount: fareAmount
                            };

                            // Send instant notification to driver via WebSocket
                            notifyDriverInstantly(tripId, notificationData);
                        }
                    });

                    res.status(201).json({
                        message: 'Match created successfully',
                        matchId: matchId
                    });
                });
        });
    });
});

// Accept or reject a match
app.put('/api/matches/:id/status', requireAuth, (req, res) => {
    const { status } = req.body;

    if (!['accepted', 'rejected'].includes(status)) {
        return res.status(400).json({ message: 'Invalid status' });
    }

    const matchId = parseInt(req.params.id, 10);
    if (!Number.isFinite(matchId) || matchId <= 0) {
        return res.status(400).json({ message: 'Invalid match id' });
    }

    // Verify the match exists, belongs to the current driver, and is still pending.
    const checkSql = `SELECT
                      m.id,
                      m.status as match_status,
                      m.trip_id,
                      m.ride_request_id,
                      t.driver_id,
                      t.status as trip_status,
                      t.available_seats,
                      rr.passenger_id
                      FROM matches m
                      JOIN trips t ON m.trip_id = t.id
                      JOIN ride_requests rr ON m.ride_request_id = rr.id
                      WHERE m.id = ?`;

    db.get(checkSql, [matchId], (err, match) => {
        if (err) {
            console.error('Error checking match:', err);
            return res.status(500).json({ message: 'Error checking match' });
        }

        if (!match) {
            console.log(`Match not found for ID: ${matchId}`);
            return res.status(404).json({ message: 'Match not found' });
        }

        // Only the driver can accept or reject a match
        if (match.driver_id !== req.session.userId) {
            return res.status(403).json({ message: 'Only the driver can accept or reject a match' });
        }

        if (match.match_status !== 'pending') {
            return res.status(409).json({ message: `Match is already ${match.match_status}` });
        }

        const emitMatchStatusToPassenger = (newStatus) => {
            try {
                if (match.passenger_id) {
                    io.to(`user-${match.passenger_id}`).emit('match-status-update', {
                        matchId,
                        status: newStatus
                    });
                }
            } catch (socketErr) {
                console.error('Socket emit error:', socketErr);
            }
        };

        const respondSuccess = (newStatus) => {
            res.json({
                message: `Match ${newStatus} successfully`,
                matchId,
                status: newStatus
            });
        };

        if (status === 'rejected') {
            const updateSql = `UPDATE matches
                               SET status = 'rejected', updated_at = CURRENT_TIMESTAMP
                               WHERE id = ? AND status = 'pending'`;

            db.run(updateSql, [matchId], function (updateErr) {
                if (updateErr) {
                    console.error('Error updating match status:', updateErr);
                    return res.status(500).json({ message: 'Error updating match status' });
                }
                if (this.changes === 0) {
                    return res.status(409).json({ message: 'Match is no longer pending' });
                }

                console.log(`Match ${matchId} rejected by user ${req.session.userId}`);

                // If rejected and no other active matches exist, reopen the ride request for searching.
                const activeCountSql = `SELECT COUNT(*) as active_count
                                        FROM matches
                                        WHERE ride_request_id = ?
                                          AND id != ?
                                          AND status IN ('pending', 'accepted')`;
                db.get(activeCountSql, [match.ride_request_id, matchId], (countErr, row) => {
                    if (countErr) {
                        console.error('Error checking remaining active matches:', countErr);
                        emitMatchStatusToPassenger('rejected');
                        return respondSuccess('rejected');
                    }
                    const activeCount = row?.active_count || 0;
                    if (activeCount > 0) {
                        emitMatchStatusToPassenger('rejected');
                        return respondSuccess('rejected');
                    }
                    db.run(
                        `UPDATE ride_requests SET status = 'searching', updated_at = CURRENT_TIMESTAMP WHERE id = ?`,
                        [match.ride_request_id],
                        (rrErr) => {
                            if (rrErr) console.error('Error reopening ride request:', rrErr);
                            emitMatchStatusToPassenger('rejected');
                            respondSuccess('rejected');
                        }
                    );
                });
            });
            return;
        }

        // Accept path: enforce seat availability + keep match + trip updates consistent.
        db.run('BEGIN IMMEDIATE', (beginErr) => {
            if (beginErr) {
                console.error('Failed to begin match accept transaction:', beginErr);
                return res.status(503).json({ message: 'Database busy, please retry' });
            }

            const rollbackAndRespond = (statusCode, message) => {
                db.run('ROLLBACK', () => res.status(statusCode).json({ message }));
            };

            // 1) Reserve a seat atomically.
            db.run(
                `UPDATE trips
                 SET available_seats = available_seats - 1, updated_at = CURRENT_TIMESTAMP
                 WHERE id = ? AND status = 'active' AND available_seats > 0`,
                [match.trip_id],
                function (seatErr) {
                    if (seatErr) {
                        console.error('Error decrementing seats:', seatErr);
                        return rollbackAndRespond(500, 'Error updating seat count');
                    }
                    if (this.changes === 0) {
                        return rollbackAndRespond(409, 'No seats available');
                    }

                    // 2) Accept the match only if it's still pending.
                    db.run(
                        `UPDATE matches
                         SET status = 'accepted', updated_at = CURRENT_TIMESTAMP
                         WHERE id = ? AND status = 'pending'`,
                        [matchId],
                        function (matchErr) {
                            if (matchErr) {
                                console.error('Error accepting match:', matchErr);
                                return rollbackAndRespond(500, 'Error accepting match');
                            }
                            if (this.changes === 0) {
                                return rollbackAndRespond(409, 'Match is no longer pending');
                            }

                            // 3) Confirm the ride request.
                            db.run(
                                `UPDATE ride_requests
                                 SET status = 'confirmed', updated_at = CURRENT_TIMESTAMP
                                 WHERE id = ?`,
                                [match.ride_request_id],
                                (rrErr) => {
                                    if (rrErr) {
                                        console.error('Error updating ride request status:', rrErr);
                                        return rollbackAndRespond(500, 'Error confirming ride request');
                                    }

                                    db.run('COMMIT', (commitErr) => {
                                        if (commitErr) {
                                            console.error('Failed to commit match accept transaction:', commitErr);
                                            return rollbackAndRespond(500, 'Error finalizing match acceptance');
                                        }

                                        console.log(`Match ${matchId} accepted by user ${req.session.userId}`);
                                        emitMatchStatusToPassenger('accepted');
                                        respondSuccess('accepted');
                                    });
                                }
                            );
                        }
                    );
                }
            );
        });
    });
});

// Get active matches for current user
app.get('/api/matches/active', requireAuth, (req, res) => {
    const sql = `SELECT
                 m.id as match_id,
                 m.trip_id,
                 m.ride_request_id,
                 m.pickup_point_lat,
                 m.pickup_point_lng,
                 m.dropoff_point_lat,
                 m.dropoff_point_lng,
                 m.fare_amount,
                 m.status as match_status,
                 m.created_at as match_created_at,
                 t.start_location,
                 t.end_location,
                 t.start_lat,
                 t.start_lng,
                 t.end_lat,
                 t.end_lng,
                 t.distance_km,
                 t.duration_minutes,
                 t.departure_time,
                 t.available_seats,
                 t.status as trip_status,
                 rr.pickup_location,
                 rr.dropoff_location,
                 rr.pickup_lat,
                 rr.pickup_lng,
                 rr.dropoff_lat,
                 rr.dropoff_lng,
                 rr.requested_time,
                 ud.name as driver_name,
                 up.name as passenger_name
                 FROM matches m 
                 JOIN trips t ON m.trip_id = t.id 
                 JOIN ride_requests rr ON m.ride_request_id = rr.id 
                 JOIN users ud ON t.driver_id = ud.id 
                 JOIN users up ON rr.passenger_id = up.id 
                 WHERE (t.driver_id = ? OR rr.passenger_id = ?) 
                 AND m.status IN ('pending', 'accepted')
                 AND t.status = 'active'`;

    db.all(sql, [req.session.userId, req.session.userId], (err, matches) => {
        if (err) {
            return res.status(500).json({ message: 'Error fetching matches' });
        }
        res.json(matches);
    });
});

// ============= COST SHARING ENDPOINTS =============

// Helper: load fuel prices from DB (falls back to defaults)
function loadFuelPrices(callback) {
    db.all(`SELECT vehicle_type, fuel_type, cost_per_km FROM fuel_config`, [], (err, rows) => {
        if (err || !rows || rows.length === 0) {
            return callback(DEFAULT_FUEL_PRICES);
        }
        // Build prices object from DB rows
        const prices = { '2W': {}, '4W': {} };
        rows.forEach(r => {
            if (prices[r.vehicle_type]) {
                prices[r.vehicle_type][r.fuel_type] = r.cost_per_km;
            }
        });
        // Merge with defaults (DB overrides defaults)
        const merged = {
            '2W': { ...DEFAULT_FUEL_PRICES['2W'], ...prices['2W'] },
            '4W': { ...DEFAULT_FUEL_PRICES['4W'], ...prices['4W'] }
        };
        callback(merged);
    });
}

// POST /api/cost-sharing/calculate — public, no auth needed
app.post('/api/cost-sharing/calculate', (req, res) => {
    const { distanceInKm, vehicleType, fuelType, numberOfPassengers } = req.body;
    const distance = parseFloat(distanceInKm);

    if (!Number.isFinite(distance) || distance <= 0) {
        return res.status(400).json({ message: 'distanceInKm is required and must be positive' });
    }

    loadFuelPrices((fuelPrices) => {
        try {
            const result = calculateCostSharing({
                distanceInKm: distance,
                vehicleType: vehicleType || '4W',
                fuelType: fuelType || 'petrol',
                numberOfPassengers: parseInt(numberOfPassengers) || 1,
                fuelPrices
            });
            res.json(result);
        } catch (error) {
            res.status(400).json({ message: error.message });
        }
    });
});

// GET /api/cost-sharing/fuel-prices — returns current config
app.get('/api/cost-sharing/fuel-prices', (req, res) => {
    loadFuelPrices((prices) => {
        res.json(prices);
    });
});

// PUT /api/cost-sharing/fuel-prices — admin update
app.put('/api/cost-sharing/fuel-prices', requireAuth, (req, res) => {
    const { vehicleType, fuelType, costPerKm } = req.body;
    const parsedCost = parseFloat(costPerKm);

    if (!vehicleType || !fuelType || !Number.isFinite(parsedCost) || parsedCost <= 0) {
        return res.status(400).json({ message: 'vehicleType, fuelType, and costPerKm are required' });
    }
    if (!['2W', '4W'].includes(vehicleType.toUpperCase())) {
        return res.status(400).json({ message: 'vehicleType must be 2W or 4W' });
    }
    if (!['petrol', 'diesel', 'cng', 'ev'].includes(fuelType.toLowerCase())) {
        return res.status(400).json({ message: 'fuelType must be petrol, diesel, cng, or ev' });
    }

    const sql = `INSERT INTO fuel_config (vehicle_type, fuel_type, cost_per_km, updated_at)
                 VALUES (?, ?, ?, datetime('now'))
                 ON CONFLICT(vehicle_type, fuel_type)
                 DO UPDATE SET cost_per_km = ?, updated_at = datetime('now')`;

    db.run(sql, [vehicleType.toUpperCase(), fuelType.toLowerCase(), parsedCost, parsedCost], function (err) {
        if (err) {
            return res.status(500).json({ message: 'Error updating fuel price' });
        }
        res.json({ message: 'Fuel price updated successfully', vehicleType, fuelType, costPerKm });
    });
});

// ============= RATINGS =============

// Submit a rating for a completed trip
app.post('/api/ratings', requireAuth, (req, res) => {
    const { match_id, rating, review } = req.body;

    if (!hasValue(match_id) || !hasValue(rating)) {
        return res.status(400).json({ message: 'match_id and rating are required' });
    }
    const ratingVal = parseInt(rating, 10);
    if (ratingVal < 1 || ratingVal > 5) {
        return res.status(400).json({ message: 'Rating must be between 1 and 5' });
    }

    // Verify match exists, is completed, and user is part of it
    const checkSql = `SELECT m.id, m.trip_id, m.status, t.driver_id, rr.passenger_id
                      FROM matches m
                      JOIN trips t ON m.trip_id = t.id
                      JOIN ride_requests rr ON m.ride_request_id = rr.id
                      WHERE m.id = ?`;

    db.get(checkSql, [match_id], (err, match) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        if (!match) return res.status(404).json({ message: 'Match not found' });
        if (match.status !== 'completed') {
            return res.status(400).json({ message: 'Can only rate completed rides' });
        }

        const userId = req.session.userId;
        const isDriver = match.driver_id === userId;
        const isPassenger = match.passenger_id === userId;
        if (!isDriver && !isPassenger) {
            return res.status(403).json({ message: 'Not authorized to rate this ride' });
        }

        // The person being rated is the opposite party
        const ratedId = isDriver ? match.passenger_id : match.driver_id;

        const insertSql = `INSERT INTO ratings (trip_id, match_id, rater_id, rated_id, rating, review)
                           VALUES (?, ?, ?, ?, ?, ?)`;
        db.run(insertSql, [match.trip_id, match_id, userId, ratedId, ratingVal, review || null], function (insertErr) {
            if (insertErr) {
                if (String(insertErr.message || '').includes('UNIQUE')) {
                    return res.status(409).json({ message: 'You have already rated this ride' });
                }
                return res.status(500).json({ message: 'Error submitting rating' });
            }
            res.json({ message: 'Rating submitted successfully', ratingId: this.lastID });
        });
    });
});

// Get average rating for a user
app.get('/api/ratings/:userId', requireAuth, (req, res) => {
    const sql = `SELECT ROUND(AVG(rating), 1) as average, COUNT(*) as count
                 FROM ratings WHERE rated_id = ?`;
    db.get(sql, [req.params.userId], (err, row) => {
        if (err) return res.status(500).json({ message: 'Database error' });
        res.json({
            average: row?.average || 0,
            count: row?.count || 0
        });
    });
});

// Check if user has already rated a specific match
app.get('/api/ratings/check/:matchId', requireAuth, (req, res) => {
    db.get(`SELECT id, rating, review FROM ratings WHERE match_id = ? AND rater_id = ?`,
        [req.params.matchId, req.session.userId], (err, row) => {
            if (err) return res.status(500).json({ message: 'Database error' });
            res.json({ hasRated: !!row, rating: row || null });
        });
});

// ─── Startup Validation ─────────────────────────────────────
(function validateEnv() {
    const key = process.env.OLA_MAPS_API_KEY;
    const nodeEnv = (process.env.NODE_ENV || 'development').toLowerCase();
    const sessionSecret = process.env.SESSION_SECRET;

    if (!key || key === 'your-ola-maps-api-key-here') {
        console.error('FATAL: OLA_MAPS_API_KEY is missing or still set to placeholder.');
        console.error('Copy .env.example to .env and add your real Ola Maps API key.');
        process.exit(1);
    }

    const hasStrongSessionSecret = !!sessionSecret && sessionSecret !== 'ridemate-dev-secret-change-in-production';
    if (nodeEnv === 'production' && !hasStrongSessionSecret) {
        console.error('FATAL: SESSION_SECRET must be set to a strong random value in production.');
        process.exit(1);
    }
    if (!hasStrongSessionSecret) {
        console.warn('WARNING: SESSION_SECRET is missing or weak. Set a strong random value in .env.');
    }

    if (!process.env.RESEND_API_KEY) {
        console.warn('WARNING: RESEND_API_KEY not set. OTP emails will fail.');
    }
})();

server.listen(port, () => {
    console.log(`✅ Server running at http://localhost:${port}`);
    console.log(`✅ WebSocket server ready for real-time notifications`);
    console.log(`✅ Cost sharing API ready at /api/cost-sharing/calculate`);
    console.log(`✅ Ola Maps proxy ready at /api/maps/*`);
});

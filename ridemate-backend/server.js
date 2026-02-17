require('dotenv').config();

const express = require('express');
const sqlite3 = require('sqlite3').verbose();
const bcrypt = require('bcrypt');
const path = require('path');
const session = require('express-session');
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
const saltRounds = 10;

// Temporary store for OTPs and user data
const otpStore = {};

app.use(express.json());

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
app.use(session({
    secret: process.env.SESSION_SECRET || 'ridemate-dev-secret-change-in-production',
    resave: false,
    saveUninitialized: false,
    cookie: {
        secure: process.env.NODE_ENV === 'production',
        httpOnly: true,
        sameSite: 'lax',
        maxAge: 24 * 60 * 60 * 1000 // 24 hours
    }
}));

const db = new sqlite3.Database('./database.db', (err) => {
    if (err) {
        console.error(err.message);
    }
    console.log('Connected to the SQLite database.');
});

db.serialize(() => {
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
    db.run(`CREATE INDEX IF NOT EXISTS idx_ride_requests_passenger_status ON ride_requests(passenger_id, status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_ride_requests_status ON ride_requests(status)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_trip ON matches(trip_id)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_request ON matches(ride_request_id)`);
    db.run(`CREATE INDEX IF NOT EXISTS idx_matches_status ON matches(status)`);

    // Create fuel_config table (admin-editable fuel prices)
    db.run(`CREATE TABLE IF NOT EXISTS fuel_config (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vehicle_type TEXT NOT NULL,
        fuel_type TEXT NOT NULL,
        cost_per_km REAL NOT NULL,
        updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
        UNIQUE(vehicle_type, fuel_type)
    )`);
});

// Initialize Resend with API key from environment variables
const resend = new Resend(process.env.RESEND_API_KEY);

// ============= WEBSOCKET REAL-TIME NOTIFICATIONS =============

// Store active driver connections
const activeDrivers = new Map(); // Map of tripId -> socketId
const driverSockets = new Map(); // Map of socketId -> {userId, tripId, socket}

// Socket.io connection handling
io.on('connection', (socket) => {
    console.log('Client connected:', socket.id);

    // Driver joins their trip room for notifications
    socket.on('driver-join-trip', (data) => {
        const payload = (data && typeof data === 'object') ? data : { tripId: data };
        const tripId = parseInt(payload.tripId, 10);
        const userId = payload.userId || null;

        if (!Number.isFinite(tripId) || tripId <= 0) {
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
    });

    // Driver leaves trip room
    socket.on('driver-leave-trip', (data) => {
        const payload = (data && typeof data === 'object') ? data : { tripId: data };
        const tripId = parseInt(payload.tripId, 10);
        if (!Number.isFinite(tripId) || tripId <= 0) {
            return;
        }
        console.log(`Driver left trip ${tripId} room`);

        // Remove from active drivers
        activeDrivers.delete(tripId);
        driverSockets.delete(socket.id);

        // Leave the trip room
        socket.leave(`trip-${tripId}`);
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
        const routePoints = decodePolyline(routePolyline);

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

app.post('/api/signup', (req, res) => {
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

app.post('/api/verify-otp', (req, res) => {
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
        res.status(400).json({ message: 'Invalid OTP' });
    }
});

app.post('/api/login', (req, res) => {
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

// Middleware to check authentication
function requireAuth(req, res, next) {
    if (!req.session.userId) {
        return res.status(401).json({ message: 'Authentication required' });
    }
    next();
}

// ============= CONFIG ENDPOINT =============

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
        route_polyline, distance_km, duration_minutes, departure_time } = req.body;

    if (!start_location || !end_location || !start_lat || !start_lng || !end_lat || !end_lng) {
        return res.status(400).json({ message: 'Missing required location data' });
    }

    if (!isValidCoordinate(start_lat, start_lng) || !isValidCoordinate(end_lat, end_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

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
                     end_lat, end_lng, route_polyline, distance_km, duration_minutes, departure_time) 
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`;

        db.run(sql, [req.session.userId, start_location, end_location, start_lat, start_lng,
            end_lat, end_lng, route_polyline, distance_km, duration_minutes,
        departure_time || new Date().toISOString()], function (err) {
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
                           ud.name as driver_name, m.fare_share
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
                 WHERE t.id = ?`;

    db.get(sql, [req.params.id], (err, trip) => {
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

    if (!pickup_lat || !pickup_lng || !dropoff_lat || !dropoff_lng) {
        return res.status(400).json({ message: 'Missing required coordinates' });
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
            const fullRoute = decodePolyline(trip.route_polyline);

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

    db.all(sql, [req.params.id], (err, requests) => {
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

// ============= PASSENGER ENDPOINTS =============

// Create a ride request (passenger looking for ride)
app.post('/api/ride-requests', requireAuth, (req, res) => {
    const pickup_location = sanitizeString(req.body.pickup_location);
    const dropoff_location = sanitizeString(req.body.dropoff_location);
    const { pickup_lat, pickup_lng,
        dropoff_lat, dropoff_lng, requested_time } = req.body;

    if (!pickup_location || !dropoff_location || !pickup_lat || !pickup_lng ||
        !dropoff_lat || !dropoff_lng) {
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

// Find available drivers for a passenger
app.get('/api/available-drivers', requireAuth, (req, res) => {
    const { pickup_lat, pickup_lng, dropoff_lat, dropoff_lng, distance_km } = req.query;

    if (!pickup_lat || !pickup_lng || !dropoff_lat || !dropoff_lng) {
        return res.status(400).json({ message: 'Missing location parameters' });
    }

    if (!isValidCoordinate(pickup_lat, pickup_lng) || !isValidCoordinate(dropoff_lat, dropoff_lng)) {
        return res.status(400).json({ message: 'Invalid coordinate values' });
    }

    // Calculate dynamic threshold based on distance
    const distKm = parseFloat(distance_km) || 10;
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

    // Get all active trips
    const sql = `SELECT t.*, u.name as driver_name, u.email as driver_email
                 FROM trips t 
                 JOIN users u ON t.driver_id = u.id 
                 WHERE t.status = 'active' 
                 AND t.departure_time > datetime('now')`;

    db.all(sql, [], (err, trips) => {
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
                    // Store the route match for later use
                    route_match: routeMatch
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

    if (!trip_id || !ride_request_id || !pickup_lat || !pickup_lng ||
        !dropoff_lat || !dropoff_lng || !fare_amount) {
        return res.status(400).json({ message: 'Missing required match data' });
    }

    const sql = `INSERT INTO matches (trip_id, ride_request_id, pickup_point_lat, 
                 pickup_point_lng, dropoff_point_lat, dropoff_point_lng, fare_amount) 
                 VALUES (?, ?, ?, ?, ?, ?, ?)`;

    db.run(sql, [trip_id, ride_request_id, pickup_lat, pickup_lng,
        dropoff_lat, dropoff_lng, fare_amount], function (err) {
            if (err) {
                if (err.code === 'SQLITE_CONSTRAINT') {
                    return res.status(409).json({ message: 'Match already exists' });
                }
                console.error('Error creating match:', err);
                return res.status(500).json({ message: 'Error creating match' });
            }

            const matchId = this.lastID;

            // Update ride request status
            db.run(`UPDATE ride_requests SET status = 'matched' WHERE id = ?`,
                [ride_request_id], (err) => {
                    if (err) console.error('Error updating ride request status:', err);
                });

            // 🚨 INSTANT WEBSOCKET NOTIFICATION TO DRIVER 🚨
            // Get passenger details for the notification
            const getPassengerSql = `SELECT rr.*, u.name as passenger_name 
                                 FROM ride_requests rr 
                                 JOIN users u ON rr.passenger_id = u.id 
                                 WHERE rr.id = ?`;

            db.get(getPassengerSql, [ride_request_id], (err, passengerData) => {
                if (!err && passengerData) {
                    // Create the notification data
                    const notificationData = {
                        id: matchId,
                        passenger_name: passengerData.passenger_name,
                        pickup_location: passengerData.pickup_location,
                        dropoff_location: passengerData.dropoff_location,
                        pickup_lat: pickup_lat,
                        pickup_lng: pickup_lng,
                        dropoff_lat: dropoff_lat,
                        dropoff_lng: dropoff_lng,
                        fare_amount: fare_amount
                    };

                    // Send instant notification to driver via WebSocket
                    notifyDriverInstantly(trip_id, notificationData);
                }
            });

            res.status(201).json({
                message: 'Match created successfully',
                matchId: matchId
            });
        });
});

// Accept or reject a match
app.put('/api/matches/:id/status', requireAuth, (req, res) => {
    const { status } = req.body;

    if (!['accepted', 'rejected'].includes(status)) {
        return res.status(400).json({ message: 'Invalid status' });
    }

    // First verify the match exists and get details
    const checkSql = `SELECT m.*, t.driver_id, rr.passenger_id 
                      FROM matches m 
                      JOIN trips t ON m.trip_id = t.id 
                      JOIN ride_requests rr ON m.ride_request_id = rr.id 
                      WHERE m.id = ?`;

    db.get(checkSql, [req.params.id], (err, match) => {
        if (err) {
            console.error('Error checking match:', err);
            return res.status(500).json({ message: 'Error checking match' });
        }

        if (!match) {
            console.log(`Match not found for ID: ${req.params.id}`);
            return res.status(404).json({ message: 'Match not found' });
        }

        // Verify the current user is either the driver or passenger
        if (match.driver_id !== req.session.userId && match.passenger_id !== req.session.userId) {
            return res.status(403).json({ message: 'Not authorized to update this match' });
        }

        // Update the match status
        const updateSql = `UPDATE matches SET status = ?, updated_at = CURRENT_TIMESTAMP 
                          WHERE id = ?`;

        db.run(updateSql, [status, req.params.id], function (err) {
            if (err) {
                console.error('Error updating match status:', err);
                return res.status(500).json({ message: 'Error updating match status' });
            }

            console.log(`Match ${req.params.id} ${status} by user ${req.session.userId}`);

            // If accepted, update ride request status
            if (status === 'accepted') {
                db.run(`UPDATE ride_requests SET status = 'confirmed' WHERE id = ?`,
                    [match.ride_request_id], (err) => {
                        if (err) console.error('Error updating ride request status:', err);
                    });
            }

            res.json({
                message: `Match ${status} successfully`,
                matchId: req.params.id,
                status: status
            });
        });
    });
});

// Get active matches for current user
app.get('/api/matches/active', requireAuth, (req, res) => {
    const sql = `SELECT m.*, t.*, rr.*, 
                 ud.name as driver_name, up.name as passenger_name
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

    if (!distanceInKm || distanceInKm <= 0) {
        return res.status(400).json({ message: 'distanceInKm is required and must be positive' });
    }

    loadFuelPrices((fuelPrices) => {
        try {
            const result = calculateCostSharing({
                distanceInKm: parseFloat(distanceInKm),
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

    if (!vehicleType || !fuelType || !costPerKm) {
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

    db.run(sql, [vehicleType.toUpperCase(), fuelType.toLowerCase(), parseFloat(costPerKm), parseFloat(costPerKm)], function (err) {
        if (err) {
            return res.status(500).json({ message: 'Error updating fuel price' });
        }
        res.json({ message: 'Fuel price updated successfully', vehicleType, fuelType, costPerKm });
    });
});

// ─── Startup Validation ─────────────────────────────────────
(function validateEnv() {
    const key = process.env.OLA_MAPS_API_KEY;
    if (!key || key === 'your-ola-maps-api-key-here') {
        console.error('❌  FATAL: OLA_MAPS_API_KEY is missing or still set to placeholder.');
        console.error('   Copy .env.example → .env and add your real Ola Maps API key.');
        process.exit(1);
    }
    if (!process.env.SESSION_SECRET || process.env.SESSION_SECRET === 'change-me-to-a-random-secret') {
        console.warn('⚠️  WARNING: SESSION_SECRET is missing or default. Set a strong random value in .env for production.');
    }
    if (!process.env.RESEND_API_KEY) {
        console.warn('⚠️  WARNING: RESEND_API_KEY not set. OTP emails will fail.');
    }
})();

server.listen(port, () => {
    console.log(`✅ Server running at http://localhost:${port}`);
    console.log(`✅ WebSocket server ready for real-time notifications`);
    console.log(`✅ Cost sharing API ready at /api/cost-sharing/calculate`);
    console.log(`✅ Ola Maps proxy ready at /api/maps/*`);
});

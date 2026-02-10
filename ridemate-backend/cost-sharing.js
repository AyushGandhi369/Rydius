/**
 * RideMate Cost Sharing Engine
 * ============================
 * Passengers collectively cover 75% of fuel cost.
 * Driver absorbs the remaining 25%.
 * No profit. No surge. No commission.
 */

// ========================
// DEFAULT FUEL PRICES (₹ per km) — Admin-editable via API
// ========================
const DEFAULT_FUEL_PRICES = {
    '2W': {
        petrol: 1.89,
        diesel: 2.01,
        cng: 2.75,
        ev: 0.20
    },
    '4W': {
        petrol: 6.31,
        diesel: 5.04,
        cng: 4.12,
        ev: 0.95
    }
};

// Taxi comparison rates (₹ per km) for savings estimation
const TAXI_RATES = {
    '2W': 9.0,   // Avg bike-taxi rate
    '4W': 14.0   // Avg cab rate
};

// CO₂ emission factors (grams per km)
const CO2_PER_KM = {
    '2W': { petrol: 40, diesel: 45, cng: 30, ev: 0 },
    '4W': { petrol: 150, diesel: 130, cng: 100, ev: 0 }
};

// Average car CO₂ per km (solo driving baseline for savings calc)
const SOLO_CO2_PER_KM = {
    '2W': 40,
    '4W': 150
};

// ========================
// PASSENGER MESSAGES
// ========================
const PASSENGER_MESSAGES = [
    (saved) => `You just saved ₹${saved} compared to a cab 🚀`,
    () => `Same route. Smarter choice.`,
    () => `Your daily commute just got cheaper and cleaner.`,
    () => `You didn't book a ride. You made a smart move.`,
    () => `One shared ride = less traffic, less pollution.`,
    (saved) => `That's ₹${saved} saved — treat yourself to a chai ☕`,
];

const PASSENGER_ECO_MESSAGES = [
    (co2) => `You avoided ~${co2} kg of CO₂ today 🌍`,
    () => `You avoided burning extra fuel today 🌱`,
    (co2) => `That's ~${co2} kg less CO₂ in the air today 🌍`,
];

// ========================
// DRIVER MESSAGES
// ========================
const DRIVER_MESSAGES = [
    (saved) => `You recovered ₹${saved} of your fuel cost today 👍`,
    () => `You helped reduce traffic without changing your routine.`,
    () => `Same commute, lighter expense.`,
    (saved) => `₹${saved} fuel cost recovered — smart driving! 🚗`,
];

const DRIVER_ECO_MESSAGES = [
    () => `You helped someone skip a solo ride today 🌱`,
    (co2) => `Together you saved ~${co2} kg of CO₂ 🌍`,
];

// ========================
// CORE CALCULATION
// ========================

/**
 * Calculate cost sharing for a ride.
 *
 * @param {Object} params
 * @param {number} params.distanceInKm       - Trip distance in kilometers
 * @param {string} params.vehicleType        - '2W' or '4W'
 * @param {string} params.fuelType           - 'petrol', 'diesel', 'cng', or 'ev'
 * @param {number} params.numberOfPassengers - Excluding driver (1 for 2W, 1-5 for 4W)
 * @param {Object} [params.fuelPrices]       - Override fuel prices (admin config)
 * @returns {Object} Complete cost breakdown + messages
 */
function calculateCostSharing({ distanceInKm, vehicleType, fuelType, numberOfPassengers, fuelPrices }) {
    // Validate inputs
    if (!distanceInKm || distanceInKm <= 0) {
        throw new Error('Distance must be a positive number');
    }
    vehicleType = (vehicleType || '4W').toUpperCase();
    if (!['2W', '4W'].includes(vehicleType)) {
        throw new Error('Vehicle type must be 2W or 4W');
    }
    fuelType = (fuelType || 'petrol').toLowerCase();
    if (!['petrol', 'diesel', 'cng', 'ev'].includes(fuelType)) {
        throw new Error('Fuel type must be petrol, diesel, cng, or ev');
    }
    // 2W always has exactly 1 passenger
    if (vehicleType === '2W') {
        numberOfPassengers = 1;
    }
    numberOfPassengers = Math.max(1, Math.min(5, numberOfPassengers || 1));

    // Get fuel cost per km
    const prices = fuelPrices || DEFAULT_FUEL_PRICES;
    const fuelCostPerKm = prices[vehicleType]?.[fuelType];
    if (!fuelCostPerKm) {
        throw new Error(`No fuel price found for ${vehicleType} ${fuelType}`);
    }

    // Step 1: Base trip cost
    const baseTripCost = distanceInKm * fuelCostPerKm;

    // Step 2: 75/25 split
    const passengerPool = baseTripCost * 0.75;
    const driverSaved = baseTripCost * 0.25;

    // Step 3: Per-passenger cost
    const costPerPassenger = passengerPool / numberOfPassengers;

    // Round all amounts to nearest ₹1 (no decimals, payable)
    const roundedBaseTripCost = Math.round(baseTripCost);
    const roundedPassengerPool = Math.round(passengerPool);
    const roundedCostPerPassenger = Math.round(costPerPassenger);
    const roundedDriverSaved = Math.round(baseTripCost - (roundedCostPerPassenger * numberOfPassengers));

    // Step 4: Savings vs taxi
    const taxiRate = TAXI_RATES[vehicleType];
    const taxiCost = Math.round(distanceInKm * taxiRate);
    const savingsVsTaxi = Math.max(0, taxiCost - roundedCostPerPassenger);

    // Step 5: CO₂ saved estimate
    const co2PerKm = CO2_PER_KM[vehicleType]?.[fuelType] || 0;
    const soloCo2 = SOLO_CO2_PER_KM[vehicleType] * distanceInKm;
    // Shared ride means 1 less solo vehicle on road
    const co2SavedGrams = soloCo2 * (numberOfPassengers / (numberOfPassengers + 1));
    const co2SavedKg = parseFloat((co2SavedGrams / 1000).toFixed(1));

    // Step 6: Generate human-friendly messages
    const passengerMsg = pickRandom(PASSENGER_MESSAGES)(savingsVsTaxi);
    const passengerEcoMsg = pickRandom(PASSENGER_ECO_MESSAGES)(co2SavedKg);
    const driverMsg = pickRandom(DRIVER_MESSAGES)(roundedPassengerPool);
    const driverEcoMsg = pickRandom(DRIVER_ECO_MESSAGES)(co2SavedKg);

    return {
        // Core numbers
        baseTripCost: roundedBaseTripCost,
        passengerPool: roundedPassengerPool,
        costPerPassenger: roundedCostPerPassenger,
        driverSaved: roundedDriverSaved,
        numberOfPassengers,

        // Context
        distanceInKm: parseFloat(distanceInKm.toFixed(1)),
        vehicleType,
        fuelType,
        fuelCostPerKm,

        // Comparisons
        savingsVsTaxi,
        taxiEstimate: taxiCost,
        co2SavedKg,

        // Human-friendly messages
        passengerMessage: passengerMsg,
        passengerEcoMessage: passengerEcoMsg,
        driverMessage: driverMsg,
        driverEcoMessage: driverEcoMsg,

        // Short user explanation
        explanation: `Your contribution of ₹${roundedCostPerPassenger} helps share ${Math.round(0.75 * 100)}% of the fuel cost with the driver who's already going your way.`
    };
}

/**
 * Pick a random item from an array
 */
function pickRandom(arr) {
    return arr[Math.floor(Math.random() * arr.length)];
}

// ========================
// EXPORTS
// ========================
module.exports = {
    calculateCostSharing,
    DEFAULT_FUEL_PRICES,
    TAXI_RATES,
    CO2_PER_KM
};

/**
 * Location Autocomplete for RideMate
 * Professional local autocomplete for Ahmedabad and major Indian cities
 */

// Ahmedabad areas and popular locations (Primary focus)
const AHMEDABAD_LOCATIONS = [
    // Major Areas
    "Satellite, Ahmedabad",
    "Vastrapur, Ahmedabad",
    "Prahladnagar, Ahmedabad",
    "Navrangpura, Ahmedabad",
    "CG Road, Ahmedabad",
    "SG Highway, Ahmedabad",
    "Ashram Road, Ahmedabad",
    "Maninagar, Ahmedabad",
    "Chandkheda, Ahmedabad",
    "Gota, Ahmedabad",
    "Bopal, Ahmedabad",
    "South Bopal, Ahmedabad",
    "Ghuma, Ahmedabad",
    "Thaltej, Ahmedabad",
    "Bodakdev, Ahmedabad",
    "Ambawadi, Ahmedabad",
    "Anand Nagar, Ahmedabad",
    "Jodhpur, Ahmedabad",
    "Vejalpur, Ahmedabad",
    "Jivraj Park, Ahmedabad",
    "Naranpura, Ahmedabad",
    "Memnagar, Ahmedabad",
    "Gurukul, Ahmedabad",
    "Drive-In Road, Ahmedabad",
    "Helmet Circle, Ahmedabad",
    "Shivranjani, Ahmedabad",
    "Judges Bungalow, Ahmedabad",
    "Nehru Nagar, Ahmedabad",
    "Shahibaug, Ahmedabad",
    "Kalupur, Ahmedabad",
    "Raipur, Ahmedabad",
    "Teen Darwaja, Ahmedabad",
    "Lal Darwaja, Ahmedabad",
    "Relief Road, Ahmedabad",
    "Paldi, Ahmedabad",
    "Vasna, Ahmedabad",
    "IIM Road, Ahmedabad",
    "University Road, Ahmedabad",
    "Law Garden, Ahmedabad",
    "Parimal Garden, Ahmedabad",
    "Gulbai Tekra, Ahmedabad",
    "Income Tax, Ahmedabad",
    "Stadium Road, Ahmedabad",
    "Ellis Bridge, Ahmedabad",
    "Usmanpura, Ahmedabad",
    "Vijay Cross Roads, Ahmedabad",
    "Ranip, Ahmedabad",
    "Sabarmati, Ahmedabad",
    "Motera, Ahmedabad",
    "Kankaria, Ahmedabad",
    "Isanpur, Ahmedabad",
    "Vastral, Ahmedabad",
    "Nikol, Ahmedabad",
    "Naroda, Ahmedabad",
    "Odhav, Ahmedabad",
    "Vatva, Ahmedabad",
    "Bapunagar, Ahmedabad",
    "Amraiwadi, Ahmedabad",
    "Ramol, Ahmedabad",
    "CTM, Ahmedabad",
    "Rakhial, Ahmedabad",
    "Meghaninagar, Ahmedabad",
    "Sardar Patel Stadium, Motera",
    "Sarkhej, Ahmedabad",
    "Sanand, Ahmedabad",
    "Chandola Lake, Ahmedabad",

    // Educational Institutions
    "IIM Ahmedabad",
    "IITRAM, Ahmedabad",
    "Gujarat University, Ahmedabad",
    "LD College of Engineering, Ahmedabad",
    "CEPT University, Ahmedabad",
    "NID Ahmedabad",
    "Nirma University, Ahmedabad",
    "PDPU, Gandhinagar",
    "GNLU, Gandhinagar",
    "DAIICT, Gandhinagar",

    // Transport Hubs
    "Ahmedabad Railway Station",
    "Kalupur Railway Station",
    "Gandhinagar Railway Station",
    "Sardar Vallabhbhai Patel Airport",
    "Ahmedabad Airport",
    "GSRTC Bus Stand, Geeta Mandir",
    "Paldi Bus Stand, Ahmedabad",
    "AMTS Central, Lal Darwaja",
    "Ahmedabad Metro, Apparel Park",
    "Ahmedabad Metro, Vastral",

    // Malls & Commercial
    "Ahmedabad One Mall, Vastrapur",
    "Alpha One Mall, Vastrapur",
    "Iscon Mega Mall, SG Highway",
    "Himalaya Mall, Drive-In",
    "Gulmohar Park Mall, Satellite",
    "The Acropolis Mall, Thaltej",
    "Shyamal Cross Roads, Ahmedabad",
    "Ankur Cross Roads, Naranpura",
    "Swastik Cross Roads, CG Road",
    "Vijay Char Rasta, Ahmedabad",

    // Hospitals
    "Civil Hospital, Ahmedabad",
    "VS Hospital, Ellis Bridge",
    "Sterling Hospital, Gurukul",
    "Zydus Hospital, Thaltej",
    "Apollo Hospital, Gandhinagar",
    "CIMS Hospital, Science City",
    "SAL Hospital, Thaltej",

    // Landmarks
    "Science City, Ahmedabad",
    "Sabarmati Ashram, Ahmedabad",
    "Adalaj Stepwell, Ahmedabad",
    "Akshardham Temple, Gandhinagar",
    "ISKCON Temple, SG Highway",
    "Riverfront, Ahmedabad",
    "Dandi Kutir, Gandhinagar",
    "Infocity, Gandhinagar",
    "GIFT City, Gandhinagar"
];

// Other Indian cities (Secondary)
const OTHER_CITIES = [
    "Gandhinagar, Gujarat",
    "Vadodara, Gujarat",
    "Surat, Gujarat",
    "Rajkot, Gujarat",
    "Mumbai, Maharashtra",
    "Delhi, NCT",
    "Bangalore, Karnataka",
    "Pune, Maharashtra",
    "Hyderabad, Telangana",
    "Chennai, Tamil Nadu",
    "Jaipur, Rajasthan",
    "Udaipur, Rajasthan"
];

// Combined locations - Ahmedabad first for priority
const LOCATIONS = [...AHMEDABAD_LOCATIONS, ...OTHER_CITIES];

// Flag to track if Google Maps is loaded
window.localAutocompleteDisabled = false;
window.googleMapsLoaded = false;

// Make initLocalAutocomplete globally available for fallback
window.initLocalAutocomplete = initLocalAutocomplete;

// Wait for DOM to be ready, then initialize immediately
// (Google Maps is currently disabled due to billing issues)
document.addEventListener('DOMContentLoaded', function () {
    console.log('RideMate Autocomplete: Initializing local suggestions...');

    // Small delay to ensure DOM is fully ready
    setTimeout(function () {
        initLocalAutocomplete();
    }, 100);
});

function initLocalAutocomplete() {
    // Skip if Google Maps is working
    if (window.googleMapsLoaded && window.localAutocompleteDisabled) {
        console.log('Google Maps is active - skipping local autocomplete');
        return;
    }

    const pickupInput = document.getElementById('pickup-location');
    const dropoffInput = document.getElementById('dropoff-location');
    const pickupSuggestions = document.getElementById('pickup-suggestions');
    const dropoffSuggestions = document.getElementById('dropoff-suggestions');

    if (!pickupInput || !dropoffInput) {
        console.error('Location inputs not found!');
        return;
    }

    // Setup autocomplete for both inputs
    setupInput(pickupInput, pickupSuggestions, 'pickup');
    setupInput(dropoffInput, dropoffSuggestions, 'dropoff');

    // Close suggestions when clicking outside
    document.addEventListener('click', function (e) {
        if (!e.target.closest('.input-group')) {
            document.querySelectorAll('.ridemate-suggestions').forEach(box => {
                box.style.display = 'none';
            });
        }
    });

    console.log('RideMate Autocomplete: Ready with', LOCATIONS.length, 'locations (Ahmedabad focused)');
}

function setupInput(input, existingSuggestionsBox, type) {
    // Create or use existing suggestion box
    let suggestionsBox = existingSuggestionsBox;

    if (!suggestionsBox) {
        suggestionsBox = document.createElement('div');
        suggestionsBox.id = `${type}-suggestions`;
        input.parentElement.appendChild(suggestionsBox);
    }

    // Apply professional styles
    suggestionsBox.className = 'ridemate-suggestions';
    Object.assign(suggestionsBox.style, {
        position: 'absolute',
        top: '100%',
        left: '0',
        right: '0',
        background: '#ffffff',
        border: '1px solid #e0e0e0',
        borderTop: 'none',
        borderRadius: '0 0 16px 16px',
        maxHeight: '300px',
        overflowY: 'auto',
        zIndex: '99999',
        boxShadow: '0 12px 40px rgba(0,0,0,0.2)',
        display: 'none',
        marginTop: '-1px'
    });

    // Ensure parent has relative positioning
    input.parentElement.style.position = 'relative';
    input.parentElement.style.zIndex = '1000';

    // Handle input typing
    input.addEventListener('input', function () {
        const query = this.value.toLowerCase().trim();

        if (query.length < 1) {
            suggestionsBox.style.display = 'none';
            return;
        }

        // Smart matching - prioritize starts with, then includes
        let matches = LOCATIONS.filter(loc =>
            loc.toLowerCase().startsWith(query)
        );

        // Add "includes" matches if not enough
        if (matches.length < 8) {
            const includesMatches = LOCATIONS.filter(loc =>
                !loc.toLowerCase().startsWith(query) &&
                loc.toLowerCase().includes(query)
            );
            matches = [...matches, ...includesMatches];
        }

        matches = matches.slice(0, 8);

        if (matches.length === 0) {
            suggestionsBox.innerHTML = `
                <div style="
                    padding: 20px;
                    color: #888;
                    text-align: center;
                    font-size: 14px;
                ">
                    <div style="font-size: 24px; margin-bottom: 8px;">🔍</div>
                    No locations found for "${query}"
                </div>
            `;
        } else {
            suggestionsBox.innerHTML = matches.map((loc, index) => `
                <div class="suggestion-item" data-index="${index}" style="
                    padding: 14px 18px;
                    cursor: pointer;
                    border-bottom: 1px solid #f5f5f5;
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    transition: all 0.15s ease;
                    font-size: 14px;
                    color: #333;
                ">
                    <span style="
                        width: 32px;
                        height: 32px;
                        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 14px;
                        flex-shrink: 0;
                    ">📍</span>
                    <span style="flex: 1;">${highlightMatch(loc, query)}</span>
                </div>
            `).join('');
        }

        suggestionsBox.style.display = 'block';

        // Add hover effects and click handlers
        suggestionsBox.querySelectorAll('.suggestion-item').forEach((item, idx) => {
            item.addEventListener('mouseenter', function () {
                this.style.background = '#f8f9ff';
                this.style.paddingLeft = '22px';
            });

            item.addEventListener('mouseleave', function () {
                this.style.background = 'white';
                this.style.paddingLeft = '18px';
            });

            item.addEventListener('click', function () {
                input.value = matches[idx];
                suggestionsBox.style.display = 'none';

                // Trigger visual feedback
                input.style.borderColor = '#4CAF50';
                setTimeout(() => {
                    input.style.borderColor = '';
                }, 500);

                console.log('Selected:', matches[idx]);
            });
        });
    });

    // Show suggestions on focus if there's content
    input.addEventListener('focus', function () {
        if (this.value.length >= 1) {
            this.dispatchEvent(new Event('input'));
        }
    });

    // Hide on blur with delay
    input.addEventListener('blur', function () {
        setTimeout(() => {
            suggestionsBox.style.display = 'none';
        }, 250);
    });
}

// Helper to highlight matching text
function highlightMatch(text, query) {
    const lowerText = text.toLowerCase();
    const lowerQuery = query.toLowerCase();
    const index = lowerText.indexOf(lowerQuery);

    if (index === -1) return text;

    const before = text.slice(0, index);
    const match = text.slice(index, index + query.length);
    const after = text.slice(index + query.length);

    return `${before}<strong style="color: #667eea;">${match}</strong>${after}`;
}

console.log('RideMate Location Autocomplete loaded');

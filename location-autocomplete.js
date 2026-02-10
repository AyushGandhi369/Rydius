/**
 * Location Autocomplete for RideMate
 * Simplified and reliable implementation
 */

// Ahmedabad areas and popular locations
const LOCATIONS = [
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
    "IIM Ahmedabad",
    "Gujarat University, Ahmedabad",
    "Nirma University, Ahmedabad",
    "Ahmedabad Railway Station",
    "Ahmedabad Airport",
    "Science City, Ahmedabad",
    "Sabarmati Ashram, Ahmedabad",
    "Gandhinagar, Gujarat",
    "Vadodara, Gujarat",
    "Surat, Gujarat",
    "Rajkot, Gujarat",
    "Mumbai, Maharashtra",
    "Delhi, NCT",
    "Bangalore, Karnataka",
    "Pune, Maharashtra"
];

// Global variable to track if selection is in progress
let isSelecting = false;

// Initialize when DOM is ready
document.addEventListener('DOMContentLoaded', function () {
    console.log('RideMate Autocomplete: Initializing...');

    // Wait a bit for all elements to be ready
    setTimeout(initAutocomplete, 200);
});

function initAutocomplete() {
    const pickupInput = document.getElementById('pickup-location');
    const dropoffInput = document.getElementById('dropoff-location');
    const pickupSuggestions = document.getElementById('pickup-suggestions');
    const dropoffSuggestions = document.getElementById('dropoff-suggestions');

    if (!pickupInput || !dropoffInput) {
        console.error('Location inputs not found!');
        return;
    }

    // Setup both inputs
    setupAutocomplete(pickupInput, pickupSuggestions);
    setupAutocomplete(dropoffInput, dropoffSuggestions);

    console.log('RideMate Autocomplete: Ready with', LOCATIONS.length, 'locations');
}

function setupAutocomplete(input, suggestionsContainer) {
    if (!suggestionsContainer) {
        suggestionsContainer = document.createElement('div');
        suggestionsContainer.className = 'suggestions-list';
        input.parentElement.appendChild(suggestionsContainer);
    }

    // Style the suggestions container
    Object.assign(suggestionsContainer.style, {
        position: 'absolute',
        top: '100%',
        left: '0',
        right: '0',
        background: '#ffffff',
        border: '1px solid #ddd',
        borderTop: 'none',
        borderRadius: '0 0 12px 12px',
        maxHeight: '250px',
        overflowY: 'auto',
        zIndex: '99999',
        boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
        display: 'none'
    });

    // Ensure parent has position relative
    input.parentElement.style.position = 'relative';

    // Handle input
    input.addEventListener('input', function () {
        const query = this.value.toLowerCase().trim();

        if (query.length < 1) {
            suggestionsContainer.style.display = 'none';
            return;
        }

        // Find matches
        const matches = LOCATIONS.filter(loc =>
            loc.toLowerCase().includes(query)
        ).slice(0, 8);

        if (matches.length === 0) {
            suggestionsContainer.innerHTML = '<div style="padding: 15px; color: #888; text-align: center;">No locations found</div>';
        } else {
            suggestionsContainer.innerHTML = matches.map(loc => {
                const safeLoc = loc.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                return `
                <div class="suggestion-item" data-value="${safeLoc}" style="
                    padding: 12px 16px;
                    cursor: pointer;
                    border-bottom: 1px solid #f0f0f0;
                    transition: background 0.15s;
                ">
                    📍 ${safeLoc}
                </div>
            `;
            }).join('');

            // Add click handlers to each suggestion
            suggestionsContainer.querySelectorAll('.suggestion-item').forEach(item => {
                // Use mousedown to capture before blur
                item.addEventListener('mousedown', function (e) {
                    e.preventDefault();
                    e.stopPropagation();

                    isSelecting = true;
                    const value = this.getAttribute('data-value');
                    input.value = value;
                    suggestionsContainer.style.display = 'none';

                    console.log('Location selected:', value);

                    // Visual feedback
                    input.style.borderColor = '#4CAF50';
                    setTimeout(() => {
                        input.style.borderColor = '';
                        isSelecting = false;
                    }, 300);
                });

                // Hover effect
                item.addEventListener('mouseenter', function () {
                    this.style.background = '#f5f5f5';
                });
                item.addEventListener('mouseleave', function () {
                    this.style.background = '#fff';
                });
            });
        }

        suggestionsContainer.style.display = 'block';
    });

    // Show on focus
    input.addEventListener('focus', function () {
        if (this.value.length >= 1) {
            this.dispatchEvent(new Event('input'));
        }
    });

    // Hide on blur (with delay)
    input.addEventListener('blur', function () {
        setTimeout(() => {
            if (!isSelecting) {
                suggestionsContainer.style.display = 'none';
            }
        }, 200);
    });
}

// Also setup the action buttons to ensure navigation works
document.addEventListener('DOMContentLoaded', function () {
    setTimeout(setupNavigationButtons, 300);
});

function setupNavigationButtons() {
    const headingToBtn = document.querySelector('.action-btn-driver');
    const bePassengerBtn = document.querySelector('.action-btn-passenger');

    if (headingToBtn) {
        // Remove any existing listeners by cloning
        const newHeadingBtn = headingToBtn.cloneNode(true);
        headingToBtn.parentNode.replaceChild(newHeadingBtn, headingToBtn);

        newHeadingBtn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            handleNavigation('driver');
        });
        console.log('Heading To button setup complete');
    }

    if (bePassengerBtn) {
        // Remove any existing listeners by cloning
        const newPassengerBtn = bePassengerBtn.cloneNode(true);
        bePassengerBtn.parentNode.replaceChild(newPassengerBtn, bePassengerBtn);

        newPassengerBtn.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            handleNavigation('passenger');
        });
        console.log('Be Passenger button setup complete');
    }
}

function handleNavigation(type) {
    const pickupInput = document.getElementById('pickup-location');
    const dropoffInput = document.getElementById('dropoff-location');

    const pickup = pickupInput ? pickupInput.value.trim() : '';
    const dropoff = dropoffInput ? dropoffInput.value.trim() : '';

    console.log('Navigation requested:', type);
    console.log('Pickup:', pickup);
    console.log('Dropoff:', dropoff);

    if (!pickup || !dropoff) {
        alert('Please enter both pickup and dropoff locations!');
        return;
    }

    // Store in localStorage
    localStorage.setItem('startLocation', pickup);
    localStorage.setItem('endLocation', dropoff);

    // Build URL
    const page = type === 'driver' ? 'driver-confirmation.html' : 'passenger-confirmation.html';
    const url = `${page}?start=${encodeURIComponent(pickup)}&end=${encodeURIComponent(dropoff)}`;

    console.log('Navigating to:', url);

    // Navigate immediately
    window.location.href = url;
}

console.log('RideMate Location Autocomplete loaded');

/**
 * Global Real-Time Notification System for RideMate
 * Works on all pages to provide instant driver notifications
 */

class GlobalNotificationManager {
    constructor() {
        this.socket = null;
        this.isDriverConnected = false;
        this.currentTripId = null;
        this.currentUserId = null;
        this.isInitialized = false;
        this.init();
    }

    async init() {
        try {
            // Check if user is authenticated and has an active trip
            await this.checkDriverStatus();
            
            // Initialize WebSocket if driver has active trip
            if (this.currentTripId) {
                this.initializeWebSocket();
                this.showGlobalIndicator();
            }
        } catch (error) {
            console.error('Error initializing global notification manager:', error);
        }
    }

    async checkDriverStatus() {
        try {
            // Check authentication
            const authResponse = await fetch('/api/auth/status');
            const authData = await authResponse.json();
            
            if (!authData.isAuthenticated) {
                return;
            }
            
            this.currentUserId = authData.user.id;
            
            // Check for active trip
            const tripResponse = await fetch('/api/trips/active');
            const tripData = await tripResponse.json();
            
            if (tripData.hasActiveTrip) {
                this.currentTripId = tripData.trip.id;
                console.log('🚗 Active trip detected for global notifications:', this.currentTripId);
            }
        } catch (error) {
            console.error('Error checking driver status:', error);
        }
    }

    initializeWebSocket() {
        try {
            // Check if socket.io is available
            if (typeof io === 'undefined') {
                console.error('Socket.io not available, falling back to polling');
                return;
            }

            this.socket = io();
            
            this.socket.on('connect', () => {
                console.log('🚀 Global WebSocket connected');
                this.joinTripRoom();
            });
            
            this.socket.on('disconnect', () => {
                console.log('❌ Global WebSocket disconnected');
                this.isDriverConnected = false;
                this.updateGlobalIndicator('Reconnecting...');
            });
            
            // Listen for instant passenger requests
            this.socket.on('passenger-request', (requestData) => {
                console.log('🚨 GLOBAL INSTANT NOTIFICATION: New passenger request!', requestData);
                this.handleInstantNotification(requestData);
            });
            
            this.socket.on('driver-connected', (data) => {
                console.log('✅ Driver globally connected to trip room:', data);
                this.isDriverConnected = true;
                this.updateGlobalIndicator('Live notifications active');
            });
            
        } catch (error) {
            console.error('Error initializing global WebSocket:', error);
        }
    }

    joinTripRoom() {
        if (this.socket && this.socket.connected && this.currentTripId && this.currentUserId) {
            this.socket.emit('driver-join-trip', {
                tripId: this.currentTripId,
                userId: this.currentUserId
            });
            console.log(`🔗 Driver globally joined WebSocket room for trip ${this.currentTripId}`);
        }
    }

    handleInstantNotification(requestData) {
        // Show instant notification popup
        this.showInstantPopup(requestData);
        
        // Play notification sound
        this.playNotificationSound();
        
        // Update global indicator
        this.updateGlobalIndicator('New passenger request!');
    }

    showInstantPopup(requestData) {
        // Remove any existing popup
        const existingPopup = document.getElementById('global-passenger-popup');
        if (existingPopup) {
            existingPopup.remove();
        }

        // Calculate driver earnings (86% of total fare)
        const driverEarnings = Math.round(requestData.fare_amount * 0.86);

        // Create the popup
        const popup = document.createElement('div');
        popup.id = 'global-passenger-popup';
        popup.style.cssText = `
            position: fixed;
            top: 20px;
            right: 20px;
            background: #1a1a1a;
            border: 2px solid #4CAF50;
            border-radius: 16px;
            padding: 0;
            max-width: 400px;
            width: 90%;
            z-index: 10000;
            box-shadow: 0 8px 32px rgba(76, 175, 80, 0.4);
            animation: slideInRight 0.5s ease-out;
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
        `;

        popup.innerHTML = `
            <!-- Header -->
            <div style="padding: 16px 20px; border-bottom: 1px solid #333; background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%); border-radius: 14px 14px 0 0;">
                <div style="display: flex; align-items: center; justify-content: space-between;">
                    <h3 style="color: #ffffff; font-size: 18px; font-weight: 700; margin: 0;">
                        🎉 New Ride Request!
                    </h3>
                    <button onclick="globalNotificationManager.closePopup()" style="
                        background: none;
                        border: none;
                        color: rgba(255,255,255,0.8);
                        font-size: 20px;
                        cursor: pointer;
                        padding: 4px;
                        border-radius: 50%;
                        width: 28px;
                        height: 28px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        transition: all 0.2s;
                    " onmouseover="this.style.background='rgba(255,255,255,0.2)'; this.style.color='#fff';" 
                       onmouseout="this.style.background='none'; this.style.color='rgba(255,255,255,0.8)';">×</button>
                </div>
            </div>

            <!-- Content -->
            <div style="padding: 20px;">
                <!-- Earnings Display -->
                <div style="text-align: center; margin-bottom: 16px;">
                    <p style="color: #a0a0a0; margin: 0 0 4px 0; font-size: 14px;">You'll Earn</p>
                    <div style="color: #4CAF50; font-size: 32px; font-weight: 900; margin: 0;">
                        ₹${driverEarnings}
                    </div>
                    <p style="color: #888; margin: 4px 0 0 0; font-size: 12px;">
                        (86% of ₹${requestData.fare_amount} total)
                    </p>
                </div>

                <!-- Passenger Info -->
                <div style="background: #2a2a2a; border-radius: 12px; padding: 16px; margin-bottom: 16px;">
                    <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px;">
                        <div style="
                            width: 40px;
                            height: 40px;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            border-radius: 50%;
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            color: white;
                            font-size: 16px;
                            font-weight: 600;
                        ">
                            ${requestData.passenger_name.charAt(0).toUpperCase()}
                        </div>
                        <div>
                            <h4 style="color: #ffffff; margin: 0; font-size: 16px; font-weight: 600;">
                                ${requestData.passenger_name}
                            </h4>
                            <p style="color: #a0a0a0; margin: 2px 0 0 0; font-size: 12px;">
                                wants to ride with you
                            </p>
                        </div>
                    </div>
                    
                    <!-- Trip Details -->
                    <div style="font-size: 13px;">
                        <div style="color: #a0a0a0; margin-bottom: 2px;">From:</div>
                        <div style="color: #ffffff; margin-bottom: 8px; font-weight: 500;">${requestData.pickup_location}</div>
                        <div style="color: #a0a0a0; margin-bottom: 2px;">To:</div>
                        <div style="color: #ffffff; font-weight: 500;">${requestData.dropoff_location}</div>
                    </div>
                </div>

                <!-- Action Buttons -->
                <div style="display: flex; gap: 10px;">
                    <button onclick="globalNotificationManager.declineRequest(${requestData.id})" style="
                        flex: 1;
                        padding: 12px 16px;
                        background: #333;
                        color: #ffffff;
                        border: 1px solid #555;
                        border-radius: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.2s;
                    " onmouseover="this.style.background='#444';" 
                       onmouseout="this.style.background='#333';">
                        Decline
                    </button>
                    <button onclick="globalNotificationManager.acceptRequest(${requestData.id})" style="
                        flex: 2;
                        padding: 12px 16px;
                        background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
                        color: #ffffff;
                        border: none;
                        border-radius: 8px;
                        font-size: 14px;
                        font-weight: 600;
                        cursor: pointer;
                        transition: all 0.2s;
                        box-shadow: 0 2px 8px rgba(76, 175, 80, 0.3);
                    " onmouseover="this.style.transform='translateY(-1px)'; this.style.boxShadow='0 4px 12px rgba(76, 175, 80, 0.4)';" 
                       onmouseout="this.style.transform='translateY(0)'; this.style.boxShadow='0 2px 8px rgba(76, 175, 80, 0.3)';">
                        Accept Ride
                    </button>
                </div>

                <!-- View Details Link -->
                <div style="text-align: center; margin-top: 12px;">
                    <a href="driver-confirmation.html" style="
                        color: #4CAF50;
                        text-decoration: none;
                        font-size: 13px;
                        font-weight: 500;
                    " onmouseover="this.style.textDecoration='underline';" 
                       onmouseout="this.style.textDecoration='none';">
                        View full details →
                    </a>
                </div>
            </div>
        `;

        document.body.appendChild(popup);

        // Add animation styles
        if (!document.getElementById('global-notification-animations')) {
            const style = document.createElement('style');
            style.id = 'global-notification-animations';
            style.textContent = `
                @keyframes slideInRight {
                    from { 
                        transform: translateX(100%); 
                        opacity: 0;
                    }
                    to { 
                        transform: translateX(0); 
                        opacity: 1;
                    }
                }
                @keyframes slideOutRight {
                    from { 
                        transform: translateX(0); 
                        opacity: 1;
                    }
                    to { 
                        transform: translateX(100%); 
                        opacity: 0;
                    }
                }
            `;
            document.head.appendChild(style);
        }

        // Auto-close after 30 seconds
        setTimeout(() => {
            this.closePopup();
        }, 30000);
    }

    closePopup() {
        const popup = document.getElementById('global-passenger-popup');
        if (popup) {
            popup.style.animation = 'slideOutRight 0.3s ease-in';
            setTimeout(() => {
                if (popup.parentNode) {
                    popup.parentNode.removeChild(popup);
                }
            }, 300);
        }
    }

    async acceptRequest(matchId) {
        try {
            this.closePopup();
            this.showToast('Processing your acceptance...', 'info');
            
            const response = await fetch(`/api/matches/${matchId}/status`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ status: 'accepted' })
            });

            if (response.ok) {
                this.showToast('Ride Accepted! 🎉 You\'ll earn your share when the trip completes.', 'success');
                this.updateGlobalIndicator('Ride accepted - passenger notified');
            } else {
                throw new Error('Failed to accept passenger');
            }
        } catch (error) {
            console.error('Error accepting passenger:', error);
            this.showToast('Error accepting passenger. Please try again.', 'error');
        }
    }

    async declineRequest(matchId) {
        try {
            this.closePopup();
            
            const response = await fetch(`/api/matches/${matchId}/status`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ status: 'rejected' })
            });

            if (response.ok) {
                this.showToast('Passenger request declined.', 'info');
            } else {
                throw new Error('Failed to decline passenger');
            }
        } catch (error) {
            console.error('Error declining passenger:', error);
            this.showToast('Error declining passenger. Please try again.', 'error');
        }
    }

    showGlobalIndicator() {
        // Remove existing indicator
        const existing = document.getElementById('global-driver-indicator');
        if (existing) {
            existing.remove();
        }

        // Create global indicator
        const indicator = document.createElement('div');
        indicator.id = 'global-driver-indicator';
        indicator.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
            color: white;
            padding: 12px 16px;
            border-radius: 25px;
            display: flex;
            align-items: center;
            gap: 8px;
            font-size: 14px;
            font-weight: 600;
            box-shadow: 0 4px 16px rgba(76, 175, 80, 0.3);
            z-index: 9999;
            cursor: pointer;
            transition: all 0.2s;
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
        `;

        indicator.innerHTML = `
            <div style="width: 8px; height: 8px; background: white; border-radius: 50%; animation: pulse 1.5s infinite;"></div>
            <span>Live notifications active</span>
        `;

        // Add pulse animation
        if (!document.getElementById('global-pulse-animation')) {
            const style = document.createElement('style');
            style.id = 'global-pulse-animation';
            style.textContent = `
                @keyframes pulse {
                    0%, 100% { transform: scale(1); opacity: 1; }
                    50% { transform: scale(1.5); opacity: 0.7; }
                }
            `;
            document.head.appendChild(style);
        }

        // Click to go to driver page
        indicator.addEventListener('click', () => {
            window.location.href = 'driver-confirmation.html';
        });

        // Hover effects
        indicator.addEventListener('mouseenter', () => {
            indicator.style.transform = 'translateY(-2px)';
            indicator.style.boxShadow = '0 6px 20px rgba(76, 175, 80, 0.4)';
        });

        indicator.addEventListener('mouseleave', () => {
            indicator.style.transform = 'translateY(0)';
            indicator.style.boxShadow = '0 4px 16px rgba(76, 175, 80, 0.3)';
        });

        document.body.appendChild(indicator);
    }

    updateGlobalIndicator(message) {
        const indicator = document.getElementById('global-driver-indicator');
        if (indicator) {
            const span = indicator.querySelector('span');
            if (span) {
                span.textContent = message;
            }
        }
    }

    playNotificationSound() {
        try {
            // Create a simple notification sound using Web Audio API
            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
            const oscillator = audioContext.createOscillator();
            const gainNode = audioContext.createGain();
            
            oscillator.connect(gainNode);
            gainNode.connect(audioContext.destination);
            
            oscillator.frequency.setValueAtTime(800, audioContext.currentTime);
            oscillator.frequency.setValueAtTime(600, audioContext.currentTime + 0.1);
            
            gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
            gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.3);
            
            oscillator.start(audioContext.currentTime);
            oscillator.stop(audioContext.currentTime + 0.3);
        } catch (error) {
            console.log('Could not play notification sound:', error);
        }
    }

    showToast(message, type = 'info') {
        // Remove existing toast
        const existing = document.getElementById('global-toast');
        if (existing) {
            existing.remove();
        }

        const toast = document.createElement('div');
        toast.id = 'global-toast';
        toast.style.cssText = `
            position: fixed;
            top: 20px;
            left: 50%;
            transform: translateX(-50%);
            background: ${type === 'error' ? '#f44336' : (type === 'success' ? '#4CAF50' : '#2196F3')};
            color: white;
            padding: 12px 20px;
            border-radius: 8px;
            font-size: 14px;
            font-weight: 500;
            z-index: 10001;
            animation: slideInDown 0.3s ease-out;
            font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
            max-width: 400px;
            text-align: center;
        `;

        toast.textContent = message;

        // Add animation
        if (!document.getElementById('global-toast-animation')) {
            const style = document.createElement('style');
            style.id = 'global-toast-animation';
            style.textContent = `
                @keyframes slideInDown {
                    from { transform: translateX(-50%) translateY(-100%); opacity: 0; }
                    to { transform: translateX(-50%) translateY(0); opacity: 1; }
                }
            `;
            document.head.appendChild(style);
        }

        document.body.appendChild(toast);

        // Auto-remove after 4 seconds
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 4000);
    }
}

// Initialize global notification manager when DOM is ready
let globalNotificationManager;

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        globalNotificationManager = new GlobalNotificationManager();
    });
} else {
    globalNotificationManager = new GlobalNotificationManager();
}

// Make it globally accessible
window.globalNotificationManager = globalNotificationManager;

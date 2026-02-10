document.addEventListener('DOMContentLoaded', () => {
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const email = document.getElementById('email').value.trim();
            const password = document.getElementById('password').value;

            if (!email || !password) {
                showNotification('Please fill in all fields', 'error');
                return;
            }

            try {
                const response = await fetch('/api/login', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ email, password })
                });

                const result = await response.json();

                if (response.ok) {
                    showNotification('Login successful! Redirecting...', 'success');
                    setTimeout(() => {
                        window.location.href = 'index.html';
                    }, 1000);
                } else {
                    showNotification(result.message || 'Login failed', 'error');
                }
            } catch (error) {
                console.error('Login error:', error);
                showNotification('Network error. Please check your connection.', 'error');
            }
        });
    }

    const signupForm = document.getElementById('signup-form');
    if (signupForm) {
        const otpSection = document.getElementById('otp-section');
        const signupButton = signupForm.querySelector('button[type="submit"]');

        signupForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = document.getElementById('name').value.trim();
            const email = document.getElementById('email').value.trim();
            const password = document.getElementById('password').value;

            if (!name || !email || !password) {
                showNotification('Please fill in all fields', 'error');
                return;
            }

            if (password.length < 6) {
                showNotification('Password must be at least 6 characters', 'error');
                return;
            }

            try {
                signupButton.disabled = true;
                signupButton.textContent = 'Sending OTP...';

                const response = await fetch('/api/signup', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({ name, email, password })
                });

                const result = await response.json();

                if (response.ok) {
                    showNotification(result.message || 'OTP sent! Check your email.', 'success');
                    otpSection.style.display = 'block';
                    signupButton.style.display = 'none';
                } else {
                    showNotification(result.message || 'Signup failed', 'error');
                    signupButton.disabled = false;
                    signupButton.textContent = 'Sign Up';
                }
            } catch (error) {
                console.error('Signup error:', error);
                showNotification('Network error. Please check your connection.', 'error');
                signupButton.disabled = false;
                signupButton.textContent = 'Sign Up';
            }
        });

        const verifyOtpBtn = document.getElementById('verify-otp-btn');
        if (verifyOtpBtn) {
            verifyOtpBtn.addEventListener('click', async () => {
                const email = document.getElementById('email').value.trim();
                const otp = document.getElementById('otp').value.trim();

                if (!otp) {
                    showNotification('Please enter the OTP', 'error');
                    return;
                }

                try {
                    verifyOtpBtn.disabled = true;
                    verifyOtpBtn.textContent = 'Verifying...';

                    const response = await fetch('/api/verify-otp', {
                        method: 'POST',
                        headers: {
                            'Content-Type': 'application/json'
                        },
                        body: JSON.stringify({ email, otp })
                    });

                    const result = await response.json();

                    if (response.ok) {
                        showNotification('Account created successfully! Redirecting to login...', 'success');
                        setTimeout(() => {
                            window.location.href = 'login.html';
                        }, 1500);
                    } else {
                        showNotification(result.message || 'OTP verification failed', 'error');
                        verifyOtpBtn.disabled = false;
                        verifyOtpBtn.textContent = 'Verify OTP';
                    }
                } catch (error) {
                    console.error('OTP verification error:', error);
                    showNotification('Network error. Please check your connection.', 'error');
                    verifyOtpBtn.disabled = false;
                    verifyOtpBtn.textContent = 'Verify OTP';
                }
            });
        }
    }
});

// Notification function - single source of truth
function showNotification(message, type = 'info') {
    // Remove any existing notification
    const existing = document.querySelector('.notification');
    if (existing) {
        existing.remove();
    }

    const notification = document.createElement('div');
    notification.className = `notification notification-${type}`;
    notification.textContent = message;

    Object.assign(notification.style, {
        position: 'fixed',
        top: '20px',
        right: '20px',
        padding: '12px 20px',
        borderRadius: '8px',
        color: 'white',
        fontWeight: '500',
        zIndex: '10000',
        transform: 'translateX(400px)',
        transition: 'transform 0.3s ease',
        maxWidth: '350px',
        fontSize: '14px',
        boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
        backgroundColor: type === 'success' ? '#4CAF50' :
            type === 'error' ? '#f44336' : '#2196f3'
    });

    document.body.appendChild(notification);

    // Animate in
    requestAnimationFrame(() => {
        notification.style.transform = 'translateX(0)';
    });

    // Remove after 4 seconds
    setTimeout(() => {
        notification.style.transform = 'translateX(400px)';
        setTimeout(() => {
            if (document.body.contains(notification)) {
                document.body.removeChild(notification);
            }
        }, 300);
    }, 4000);
}
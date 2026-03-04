/**
 * Re-examination Appointment validation and helpers
 */

document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('reexamForm');
    const dateInput = document.getElementById('appointmentDate');
    const startTimeInput = document.getElementById('startTime');
    const endTimeInput = document.getElementById('endTime');
    const deleteBtn = document.getElementById('deleteBtn');
    const deleteForm = document.getElementById('deleteForm');
    
    if (!form) return;
    
    // ===== VALIDATION HELPER FUNCTIONS =====
    
    /**
     * Check if time is at 30-minute intervals
     */
    function isValid30MinInterval(time) {
        if (!time) return true;
        const [hours, minutes] = time.split(':');
        const min = parseInt(minutes);
        return min === 0 || min === 30;
    }
    
    /**
     * Validate time format and value
     */
    function validateTime(input) {
        if (!input.value) return true;
        
        if (!isValid30MinInterval(input.value)) {
            showError(input, 'Time must be at 30-minute intervals (e.g., 08:00, 08:30, 09:00)');
            return false;
        }
        
        const [hours, minutes] = input.value.split(':');
        const hour = parseInt(hours);
        
        // Check working hours (08:00 - 17:00)
        if (hour < 8 || (hour === 17 && minutes !== '00') || hour > 17) {
            showError(input, 'Time must be between 08:00 and 17:00');
            return false;
        }
        
        clearError(input);
        return true;
    }
    
    /**
     * Validate date is not in past
     */
    function validateDate(input) {
        if (!input.value) return true;
        
        const selected = new Date(input.value);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        
        if (selected < today) {
            showError(input, 'Cannot schedule appointment in the past');
            return false;
        }
        
        clearError(input);
        return true;
    }
    
    /**
     * Validate current time for same-day appointments
     */
    function validateCurrentTime(dateInput, startTimeInput) {
        if (!dateInput.value || !startTimeInput.value) return true;
        
        const selected = new Date(dateInput.value);
        const today = new Date();
        today.setHours(0, 0, 0, 0);
        
        if (selected.getTime() === today.getTime()) {
            // Same day - check if start time is in future
            const now = new Date();
            const [hours, minutes] = startTimeInput.value.split(':');
            const startDateTime = new Date();
            startDateTime.setHours(parseInt(hours), parseInt(minutes), 0, 0);
            
            if (startDateTime <= now) {
                showError(startTimeInput, 'Start time must be in the future (current time: ' + 
                    now.getHours().toString().padStart(2, '0') + ':' + 
                    now.getMinutes().toString().padStart(2, '0') + ')');
                return false;
            }
        }
        
        clearError(startTimeInput);
        return true;
    }
    
    /**
     * Validate start < end time
     */
    function validateTimes(startInput, endInput) {
        if (!startInput.value || !endInput.value) return true;
        
        if (startInput.value >= endInput.value) {
            showError(endInput, 'End time must be after start time');
            return false;
        }
        
        clearError(endInput);
        return true;
    }
    
    /**
     * Show error message
     */
    function showError(input, message) {
        clearError(input);
        const errorDiv = document.createElement('div');
        errorDiv.className = 'form-error';
        errorDiv.style.cssText = 'color: #dc3545; font-size: 12px; margin-top: 4px;';
        errorDiv.textContent = message;
        input.parentNode.appendChild(errorDiv);
        input.style.borderColor = '#dc3545';
    }
    
    /**
     * Clear error message
     */
    function clearError(input) {
        const errorDiv = input.parentNode.querySelector('.form-error');
        if (errorDiv) {
            errorDiv.remove();
        }
        input.style.borderColor = '';
    }
    
    // ===== EVENT LISTENERS =====
    
    // Validate on blur
    dateInput.addEventListener('blur', () => validateDate(dateInput));
    startTimeInput.addEventListener('blur', () => {
        validateTime(startTimeInput);
        validateCurrentTime(dateInput, startTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });
    endTimeInput.addEventListener('blur', () => {
        validateTime(endTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });
    
    // Validate on change
    dateInput.addEventListener('change', () => {
        validateDate(dateInput);
        validateCurrentTime(dateInput, startTimeInput);
    });
    startTimeInput.addEventListener('change', () => {
        validateTime(startTimeInput);
        validateCurrentTime(dateInput, startTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });
    endTimeInput.addEventListener('change', () => {
        validateTime(endTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });
    
    // Delete button handler
    if (deleteBtn) {
        deleteBtn.addEventListener('click', function(e) {
            e.preventDefault();
            if (confirm('⚠️ Are you sure you want to delete this re-examination appointment?\n\nThis action cannot be undone and will free up the scheduled slot.')) {
                deleteForm.submit();
            }
        });
    }
    
    // Form submission validation
    form.addEventListener('submit', function(e) {
        let isValid = true;
        
        // Validate all fields
        if (!validateDate(dateInput)) isValid = false;
        if (!validateTime(startTimeInput)) isValid = false;
        if (!validateTime(endTimeInput)) isValid = false;
        if (!validateCurrentTime(dateInput, startTimeInput)) isValid = false;
        if (!validateTimes(startTimeInput, endTimeInput)) isValid = false;
        
        if (!isValid) {
            e.preventDefault();
            alert('Please fix the errors in the form');
            return false;
        }
        
        return true;
    });
});

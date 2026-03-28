/**
 * Xử lý kiểm tra biểu mẫu tái khám
 */

document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('reexamForm');
    const dateInput = document.getElementById('appointmentDate');
    const startTimeInput = document.getElementById('startTime');
    const endTimeInput = document.getElementById('endTime');
    const deleteBtn = document.getElementById('deleteBtn');
    const deleteForm = document.getElementById('deleteForm');
    const appointmentId = form?.dataset.appointmentId || '';
    const originalDate = dateInput?.dataset.originalDate || '';
    const originalStartTime = dateInput?.dataset.originalStartTime || '';
    let availableStartSlots = [];

    if (!form) return;

    function isValid30MinInterval(time) {
        if (!time) return true;
        const parts = time.split(':');
        const min = parseInt(parts[1], 10);
        return min === 0 || min === 30;
    }

    function validateTime(input) {
        if (!input.value) return true;

        if (!isValid30MinInterval(input.value)) {
            showError(input, 'Giờ phải theo mốc 30 phút, ví dụ 08:00, 08:30, 09:00.');
            return false;
        }

        const parts = input.value.split(':');
        const hour = parseInt(parts[0], 10);
        const minutes = parts[1];

        if (hour < 8 || (hour === 17 && minutes !== '00') || hour > 17) {
            showError(input, 'Giờ hẹn phải nằm trong khoảng 08:00 đến 17:00.');
            return false;
        }

        clearError(input);
        return true;
    }

    function validateDate(input) {
        if (!input.value) return true;

        const selected = new Date(input.value);
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        if (selected < today) {
            showError(input, 'Không thể tạo lịch ở thời điểm trong quá khứ.');
            return false;
        }

        clearError(input);
        return true;
    }

    function validateCurrentTime(dateField, startField) {
        if (!dateField.value || !startField.value) return true;

        const selected = new Date(dateField.value);
        const today = new Date();
        today.setHours(0, 0, 0, 0);

        if (selected.getTime() === today.getTime()) {
            const now = new Date();
            const parts = startField.value.split(':');
            const startDateTime = new Date();
            startDateTime.setHours(parseInt(parts[0], 10), parseInt(parts[1], 10), 0, 0);

            if (startDateTime <= now) {
                showError(
                    startField,
                    'Giờ bắt đầu phải ở tương lai (hiện tại: ' +
                    now.getHours().toString().padStart(2, '0') + ':' +
                    now.getMinutes().toString().padStart(2, '0') + ').'
                );
                return false;
            }
        }

        clearError(startField);
        return true;
    }

    function validateTimes(startField, endField) {
        if (!startField.value || !endField.value) return true;

        if (startField.value >= endField.value) {
            showError(endField, 'Giờ kết thúc phải sau giờ bắt đầu.');
            return false;
        }

        clearError(endField);
        return true;
    }

    function validateAgainstOriginalAppointment(dateField, startField) {
        if (!dateField.value || !startField.value || !originalDate || !originalStartTime) {
            return true;
        }

        if (dateField.value === originalDate && startField.value < originalStartTime) {
            showError(
                startField,
                'Nếu tái khám cùng ngày, giờ bắt đầu không được sớm hơn giờ của ca khám gốc.'
            );
            return false;
        }

        clearError(startField);
        return true;
    }

    function syncSameDayTimeOptions() {
        if (!dateInput || !startTimeInput || !endTimeInput) {
            return;
        }

        const sameDay = dateInput.value && originalDate && dateInput.value === originalDate;

        Array.from(startTimeInput.options).forEach(option => {
            if (!option.value) return;
            option.disabled = sameDay && originalStartTime && option.value < originalStartTime;
        });

        Array.from(endTimeInput.options).forEach(option => {
            if (!option.value) return;
            option.disabled = sameDay && originalStartTime && option.value <= originalStartTime;
        });

        if (startTimeInput.value && startTimeInput.selectedOptions[0]?.disabled) {
            startTimeInput.value = '';
        }

        if (endTimeInput.value && endTimeInput.selectedOptions[0]?.disabled) {
            endTimeInput.value = '';
        }
    }

    function syncEndTimeOptions() {
        if (!startTimeInput || !endTimeInput) {
            return;
        }

        const selectedStart = startTimeInput.value;
        const slotSet = new Set(availableStartSlots || []);
        Array.from(endTimeInput.options).forEach(option => {
            if (!option.value) return;
            if (!selectedStart) {
                option.disabled = false;
                return;
            }

            const requiresAfterStart = option.value > selectedStart;
            const hasContiguousSlots = requiresAfterStart && hasContinuousSlots(selectedStart, option.value, slotSet);
            option.disabled = !requiresAfterStart || !hasContiguousSlots;
        });

        if (endTimeInput.value && endTimeInput.selectedOptions[0]?.disabled) {
            endTimeInput.value = '';
        }
    }

    function syncAvailableStartOptions(availableSlots) {
        availableStartSlots = availableSlots || [];
        const slotSet = new Set(availableSlots || []);
        Array.from(startTimeInput.options).forEach(option => {
            if (!option.value) return;
            const allowedBySlot = slotSet.has(option.value);
            const sameDayBlocked = dateInput.value && originalDate && dateInput.value === originalDate
                && originalStartTime && option.value < originalStartTime;
            option.disabled = !allowedBySlot || sameDayBlocked;
        });

        if (startTimeInput.value && startTimeInput.selectedOptions[0]?.disabled) {
            startTimeInput.value = '';
        }

        syncEndTimeOptions();
    }

    function addMinutes(time, minutes) {
        const [hour, minute] = time.split(':').map(Number);
        const total = (hour * 60) + minute + minutes;
        const newHour = Math.floor(total / 60);
        const newMinute = total % 60;
        return `${String(newHour).padStart(2, '0')}:${String(newMinute).padStart(2, '0')}`;
    }

    function hasContinuousSlots(startTime, endTime, slotSet) {
        let current = startTime;
        while (current < endTime) {
            if (!slotSet.has(current)) {
                return false;
            }
            current = addMinutes(current, 30);
        }
        return true;
    }

    async function refreshAvailableStartTimes() {
        if (!appointmentId || !dateInput.value) {
            syncAvailableStartOptions([]);
            return;
        }

        try {
            const res = await fetch(`/dentist/reexam/slots/${appointmentId}?date=${encodeURIComponent(dateInput.value)}`, {
                headers: { 'X-Requested-With': 'XMLHttpRequest' }
            });
            if (!res.ok) {
                syncAvailableStartOptions([]);
                return;
            }
            const data = await res.json();
            syncAvailableStartOptions(data.availableSlots || []);
        } catch (e) {
            syncAvailableStartOptions([]);
            console.error('Failed to load available reexam slots', e);
        }
    }

    function showError(input, message) {
        clearError(input);
        const errorDiv = document.createElement('div');
        errorDiv.className = 'form-error';
        errorDiv.style.cssText = 'color: #dc3545; font-size: 12px; margin-top: 4px;';
        errorDiv.textContent = message;
        input.parentNode.appendChild(errorDiv);
        input.style.borderColor = '#dc3545';
    }

    function clearError(input) {
        const errorDiv = input.parentNode.querySelector('.form-error');
        if (errorDiv) {
            errorDiv.remove();
        }
        input.style.borderColor = '';
    }

    dateInput.addEventListener('blur', function () { validateDate(dateInput); });
    startTimeInput.addEventListener('blur', function () {
        validateTime(startTimeInput);
        validateCurrentTime(dateInput, startTimeInput);
        validateAgainstOriginalAppointment(dateInput, startTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });
    endTimeInput.addEventListener('blur', function () {
        validateTime(endTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });

    dateInput.addEventListener('change', function () {
        validateDate(dateInput);
        validateCurrentTime(dateInput, startTimeInput);
        syncSameDayTimeOptions();
        refreshAvailableStartTimes();
        validateAgainstOriginalAppointment(dateInput, startTimeInput);
    });
    startTimeInput.addEventListener('change', function () {
        validateTime(startTimeInput);
        validateCurrentTime(dateInput, startTimeInput);
        validateAgainstOriginalAppointment(dateInput, startTimeInput);
        syncEndTimeOptions();
        validateTimes(startTimeInput, endTimeInput);
    });
    endTimeInput.addEventListener('change', function () {
        validateTime(endTimeInput);
        validateTimes(startTimeInput, endTimeInput);
    });

    if (deleteBtn) {
        deleteBtn.addEventListener('click', function(e) {
            e.preventDefault();
            if (confirm('Bạn có chắc muốn xóa lịch tái khám này không?\n\nHành động này không thể hoàn tác và sẽ giải phóng khung giờ đã giữ.')) {
                deleteForm.submit();
            }
        });
    }

    form.addEventListener('submit', function(e) {
        let isValid = true;

        if (!validateDate(dateInput)) isValid = false;
        if (!validateTime(startTimeInput)) isValid = false;
        if (!validateTime(endTimeInput)) isValid = false;
        if (!validateCurrentTime(dateInput, startTimeInput)) isValid = false;
        if (!validateAgainstOriginalAppointment(dateInput, startTimeInput)) isValid = false;
        if (!validateTimes(startTimeInput, endTimeInput)) isValid = false;

        if (!isValid) {
            e.preventDefault();
            alert('Vui lòng kiểm tra lại các lỗi trong biểu mẫu.');
            return false;
        }

        return true;
    });

    syncSameDayTimeOptions();
    syncEndTimeOptions();
    if (dateInput.value) {
        refreshAvailableStartTimes();
    }
});

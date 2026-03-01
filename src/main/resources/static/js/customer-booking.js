(function () {
  'use strict';

  var state = {
    currentYear: new Date().getFullYear(),
    currentMonth: new Date().getMonth(),
    selectedDate: null,
    selectedSlot: null,
    currentStep: 1
  };

  function init() {
    var calendarBody = document.getElementById('customer-calendar-body');
    if (!calendarBody) return;

    bindStaticEvents();
    renderCalendar();
    setStep(1);
    updateSummary();
  }

  function bindStaticEvents() {
    var prevBtn = document.getElementById('customer-calendar-prev');
    var nextBtn = document.getElementById('customer-calendar-next');
    var nextStepBtn = document.getElementById('btn-next');
    var backStepBtn = document.getElementById('btn-back');
    var serviceSelect = document.getElementById('customer-booking-service');

    if (prevBtn) {
      prevBtn.addEventListener('click', function () {
        state.currentMonth--;
        if (state.currentMonth < 0) {
          state.currentMonth = 11;
          state.currentYear--;
        }
        renderCalendar();
      });
    }

    if (nextBtn) {
      nextBtn.addEventListener('click', function () {
        state.currentMonth++;
        if (state.currentMonth > 11) {
          state.currentMonth = 0;
          state.currentYear++;
        }
        renderCalendar();
      });
    }

    if (backStepBtn) {
      backStepBtn.addEventListener('click', function () {
        if (state.currentStep > 1) setStep(state.currentStep - 1);
      });
    }

    if (nextStepBtn) {
      nextStepBtn.addEventListener('click', function () {
        if (state.currentStep === 1) {
          if (!state.selectedDate) {
            alert('Vui lòng chọn ngày khám.');
            return;
          }
          setStep(2);
          return;
        }
        if (state.currentStep === 2) {
          if (!state.selectedSlot) {
            alert('Vui lòng chọn khung giờ khám.');
            return;
          }
          setStep(3);
          return;
        }
        if (state.currentStep === 3) {
          submitBooking(nextStepBtn);
        }
      });
    }

    if (serviceSelect) {
      serviceSelect.addEventListener('change', function () {
        state.selectedSlot = null;
        document.getElementById('customer-booking-slot-id').value = '';
        updateSummary();
        if (state.selectedDate) loadSlotsForDate(state.selectedDate);
      });
    }
  }

  function renderCalendar() {
    var tbody = document.getElementById('customer-calendar-body');
    var label = document.getElementById('customer-calendar-month-label');
    if (!tbody || !label) return;

    var monthNames = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6', 'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];
    var lastDay = new Date(state.currentYear, state.currentMonth + 1, 0).getDate();
    var firstDow = new Date(state.currentYear, state.currentMonth, 1).getDay();
    var today = new Date();
    today.setHours(0, 0, 0, 0);

    label.textContent = monthNames[state.currentMonth] + ' / ' + state.currentYear;
    tbody.innerHTML = '';

    var day = 1;
    for (var row = 0; row < 6; row++) {
      var tr = document.createElement('tr');
      for (var col = 0; col < 7; col++) {
        var td = document.createElement('td');
        var cellIndex = row * 7 + col;
        if (cellIndex < firstDow || day > lastDay) {
          td.className = 'customer-calendar-cell customer-calendar-cell--empty';
          tr.appendChild(td);
          continue;
        }

        var dateStr = state.currentYear + '-' + String(state.currentMonth + 1).padStart(2, '0') + '-' + String(day).padStart(2, '0');
        var cellDate = new Date(state.currentYear, state.currentMonth, day);
        cellDate.setHours(0, 0, 0, 0);

        td.className = 'customer-calendar-cell customer-calendar-cell--day';
        td.textContent = day;
        td.dataset.date = dateStr;

        if (cellDate < today) {
          td.classList.add('customer-calendar-cell--past');
        } else if (state.selectedDate === dateStr) {
          td.classList.add('selected');
        } else {
          td.addEventListener('click', function () {
            var picked = this.dataset.date;
            var serviceEl = document.getElementById('customer-booking-service');
            if (!serviceEl || !serviceEl.value) {
              alert('Vui lòng chọn dịch vụ trước.');
              return;
            }
            state.selectedDate = picked;
            state.selectedSlot = null;
            document.getElementById('customer-booking-slot-id').value = '';
            updateSummary();
            renderCalendar();
            loadSlotsForDate(picked);
          });
        }

        tr.appendChild(td);
        day++;
      }
      tbody.appendChild(tr);
    }
  }

  function loadSlotsForDate(dateStr) {
    var serviceId = (document.getElementById('customer-booking-service') || {}).value;
    if (!serviceId || !dateStr) return;

    var loading = document.getElementById('time-slots-loading');
    var grid = document.getElementById('time-slots-grid');
    var empty = document.getElementById('time-slots-empty');
    var selectedDateDisplay = document.getElementById('selected-date-display');

    if (selectedDateDisplay) selectedDateDisplay.textContent = '(' + formatDateDisplay(dateStr) + ')';
    if (loading) loading.style.display = '';
    if (grid) {
      grid.style.display = 'none';
      grid.innerHTML = '';
    }
    if (empty) empty.style.display = 'none';

    var params = new URLSearchParams({ date: dateStr, serviceId: serviceId });
    fetch('/customer/slots?' + params.toString(), { credentials: 'same-origin' })
      .then(function (res) {
        if (res.status === 401) {
          alert('Bạn cần đăng nhập để đặt lịch.');
          setStep(1);
          return null;
        }
        return res.json();
      })
      .then(function (slots) {
        if (loading) loading.style.display = 'none';
        if (!Array.isArray(slots) || !grid) return;

        var now = new Date();
        var p = dateStr.split('-');
        var d = new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10));
        var futureSlots = slots.filter(function (slot) {
          if (!slot.startTime) return false;
          var t = slot.startTime.split(':');
          var slotDateTime = new Date(d.getFullYear(), d.getMonth(), d.getDate(), parseInt(t[0], 10), parseInt(t[1], 10), 0);
          return slotDateTime > now;
        });

        if (futureSlots.length === 0) {
          if (empty) empty.style.display = '';
          setStep(2);
          return;
        }

        futureSlots.forEach(function (slot) {
          var btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'time-slot-btn';
          btn.dataset.slotId = slot.id;

          var hasSpotsValue = slot.availableSpots !== undefined && slot.availableSpots !== null;
          var availableSpots = hasSpotsValue ? Number(slot.availableSpots) : null;
          var isDisabled = slot.disabled === true;
          var isFull = hasSpotsValue ? availableSpots <= 0 : slot.available === false;
          var isAvailable = !isDisabled && !isFull;
          var statusText = 'Hết chỗ';
          if (isDisabled) {
            statusText = 'Đã có lịch';
          } else if (isAvailable) {
            statusText = hasSpotsValue ? ('Còn ' + availableSpots + ' chỗ') : 'Còn chỗ';
          }

          btn.innerHTML = formatTime(slot.startTime) + ' - ' + formatTime(slot.endTime) +
            '<span class="time-slot-status">' + statusText + '</span>';

          if (!isAvailable) {
            btn.classList.add('disabled');
          } else if (hasSpotsValue && availableSpots === 1) {
            btn.classList.add('almost-full');
          }

          if (state.selectedSlot && String(state.selectedSlot.id) === String(slot.id)) {
            btn.classList.add('selected');
          }

          btn.addEventListener('click', function () {
            if (isDisabled) {
              alert('Bạn đã có lịch hẹn trùng thời điểm này.');
              return;
            }
            if (!isAvailable) {
              alert('Khung giờ này đã đầy. Vui lòng chọn khung giờ khác.');
              return;
            }
            state.selectedSlot = slot;
            document.getElementById('customer-booking-slot-id').value = slot.id;
            updateSummary();

            grid.querySelectorAll('.time-slot-btn').forEach(function (el) {
              el.classList.remove('selected');
            });
            btn.classList.add('selected');
            setStep(3);
          });

          grid.appendChild(btn);
        });

        grid.style.display = '';
        setStep(2);
      })
      .catch(function () {
        if (loading) loading.style.display = 'none';
        if (empty) empty.style.display = '';
      });
  }

  function setStep(step) {
    state.currentStep = step;

    for (var i = 1; i <= 4; i++) {
      var panel = document.getElementById('booking-step-' + i);
      if (panel) panel.classList.toggle('active', i === step);

      var circle = document.getElementById('step-' + i + '-circle');
      var label = document.getElementById('step-' + i + '-label');
      if (circle) circle.classList.toggle('active', i <= step);
      if (label) label.classList.toggle('active', i <= step);
    }

    var nextBtn = document.getElementById('btn-next');
    var backBtn = document.getElementById('btn-back');
    var actions = document.getElementById('step-actions');

    if (actions) actions.style.display = step >= 4 ? 'none' : 'flex';
    if (backBtn) backBtn.style.display = step > 1 && step < 4 ? '' : 'none';
    if (nextBtn) {
      nextBtn.textContent = step === 3 ? 'Xác nhận đặt lịch' : 'Tiếp tục';
      nextBtn.disabled = step === 1 && !state.selectedDate;
    }
  }

  function updateSummary() {
    var selectedService = document.getElementById('customer-booking-service');
    var dateEl = document.getElementById('summary-date');
    var timeEl = document.getElementById('summary-time');
    var serviceEl = document.getElementById('summary-service');
    var durationEl = document.getElementById('summary-duration');
    var depositEl = document.getElementById('summary-deposit');
    var incomplete = document.getElementById('summary-incomplete');

    if (dateEl) dateEl.innerHTML = state.selectedDate ? formatDateDisplay(state.selectedDate) : '<span class="empty">Chưa chọn</span>';
    if (timeEl) {
      if (state.selectedSlot && state.selectedSlot.startTime && state.selectedSlot.endTime) {
        timeEl.innerHTML = formatTime(state.selectedSlot.startTime) + ' - ' + formatTime(state.selectedSlot.endTime);
      } else {
        timeEl.innerHTML = '<span class="empty">Chưa chọn</span>';
      }
    }
    if (serviceEl) {
      var serviceName = selectedService && selectedService.selectedIndex > 0
        ? selectedService.options[selectedService.selectedIndex].text
        : '';
      serviceEl.innerHTML = serviceName ? serviceName : '<span class="empty">Chưa chọn</span>';
    }

    if (durationEl) {
      var durationMinutes = 0;
      if (selectedService && selectedService.selectedIndex > 0) {
        var selectedOption = selectedService.options[selectedService.selectedIndex];
        durationMinutes = parseInt(selectedOption.getAttribute('data-duration') || '0', 10);
      }
      durationEl.innerHTML = durationMinutes > 0 ? (durationMinutes + ' phút') : '<span class="empty">--</span>';
    }
    if (depositEl) depositEl.innerHTML = '<span class="empty">--</span>';

    if (incomplete) {
      var ok = !!state.selectedDate && !!state.selectedSlot && !!(selectedService && selectedService.value);
      incomplete.style.display = ok ? 'none' : '';
    }
  }

  function submitBooking(nextStepBtn) {
    var slotId = (document.getElementById('customer-booking-slot-id') || {}).value;
    var serviceId = (document.getElementById('customer-booking-service') || {}).value;
    var contactChannel = (document.getElementById('customer-booking-contact-channel') || {}).value;
    var contactValue = (document.getElementById('customer-booking-contact-value') || {}).value.trim();
    var note = (document.getElementById('customer-booking-note') || {}).value;

    if (!slotId || !serviceId || !contactChannel || !contactValue) {
      alert('Vui lòng điền đầy đủ thông tin.');
      return;
    }

    nextStepBtn.disabled = true;

    fetch('/customer/appointments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({
        slotId: parseInt(slotId, 10),
        serviceId: parseInt(serviceId, 10),
        patientNote: note || null,
        contactChannel: contactChannel,
        contactValue: contactValue
      })
    })
      .then(function (r) {
        if (r.status === 401) {
          alert('Bạn cần đăng nhập.');
          nextStepBtn.disabled = false;
          return null;
        }
        if (!r.ok) {
          return r.json().then(function (e) {
            throw new Error(e.message || e.error || 'Đặt lịch thất bại.');
          });
        }
        return r.json();
      })
      .then(function (data) {
        nextStepBtn.disabled = false;
        if (!data) return;

        var summaryEl = document.getElementById('success-summary');
        if (summaryEl) {
          var dateText = data.date ? formatDateDisplay(data.date) : '';
          var timeText = data.startTime && data.endTime
            ? formatTime(data.startTime) + ' - ' + formatTime(data.endTime)
            : '';
          summaryEl.innerHTML =
            '<p><strong>Dịch vụ:</strong> ' + (data.serviceName || 'N/A') + '</p>' +
            '<p><strong>Bác sĩ:</strong> ' + (data.dentistName || 'Sẽ được gán sau') + '</p>' +
            '<p><strong>Ngày:</strong> ' + dateText + '</p>' +
            '<p><strong>Giờ:</strong> ' + timeText + '</p>';
        }
        setStep(4);
      })
      .catch(function (err) {
        nextStepBtn.disabled = false;
        alert(err.message || 'Đặt lịch thất bại.');
      });
  }

  function formatDateDisplay(dateStr) {
    if (!dateStr) return '';
    var parts = dateStr.split('-');
    if (parts.length === 3) return parts[2] + '/' + parts[1] + '/' + parts[0];
    return dateStr;
  }

  function formatTime(t) {
    if (!t) return '';
    var s = String(t);
    return s.length >= 5 ? s.substring(0, 5) : s;
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();

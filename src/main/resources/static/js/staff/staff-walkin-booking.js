(function () {
  'use strict';

  var SLOT_MINUTES = 30;
  var state = {
    currentYear: new Date().getFullYear(),
    currentMonth: new Date().getMonth(),
    selectedDate: null,
    selectedSlot: null,
    currentStep: 1,
    renderedSlots: []
  };

  function showToast(message, type, title) {
    if (window.CustomerFeedback) {
      window.CustomerFeedback.toast({
        message: String(message || ''),
        type: type || 'info',
        title: String(title || '')
      });
    }
  }

  function showAlert(message, type, title) {
    if (window.CustomerFeedback) {
      return window.CustomerFeedback.alert({
        message: String(message || ''),
        type: type || 'info',
        title: String(title || 'Thông báo')
      });
    }
    alert(String(message || ''));
    return Promise.resolve();
  }

  function readErrorMessage(response) {
    return response.text().then(function (text) {
      if (!text) {
        return 'Không thể tạo lịch hẹn.';
      }
      try {
        var data = JSON.parse(text);
        return String(data.message || data.error || 'Không thể tạo lịch hẹn.');
      } catch (err) {
        var stripped = String(text).replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
        return stripped || 'Không thể tạo lịch hẹn.';
      }
    });
  }

  function formatVnd(amount) {
    return Number(amount || 0).toLocaleString('vi-VN') + ' VNĐ';
  }

  function formatDateDisplay(dateStr) {
    if (!dateStr) return '';
    var parts = String(dateStr).split('-');
    if (parts.length !== 3) return dateStr;
    return parts[2] + '/' + parts[1] + '/' + parts[0];
  }

  function formatTime(timeValue) {
    if (!timeValue) return '';
    return String(timeValue).slice(0, 5);
  }

  function updateContactFieldMeta() {
    var channel = (document.getElementById('staff-walkin-contact-channel') || {}).value;
    var labelEl = document.getElementById('staff-walkin-contact-value-label');
    var inputEl = document.getElementById('staff-walkin-contact-value');
    if (!labelEl || !inputEl) return;

    if (channel === 'EMAIL') {
      labelEl.innerHTML = 'Email <span class="required">*</span>';
      inputEl.placeholder = 'Nhập email liên hệ';
      inputEl.type = 'email';
      return;
    }

    if (channel === 'PHONE') {
      labelEl.innerHTML = 'Số điện thoại <span class="required">*</span>';
      inputEl.placeholder = 'Nhập số điện thoại liên hệ';
      inputEl.type = 'text';
      return;
    }

    labelEl.innerHTML = 'Số điện thoại / Email <span class="required">*</span>';
    inputEl.placeholder = 'Nhập số điện thoại hoặc email';
    inputEl.type = 'text';
  }

  function parseTimeParts(timeValue) {
    if (!timeValue) return null;
    var parts = String(timeValue).split(':');
    if (parts.length < 2) return null;
    var hour = parseInt(parts[0], 10);
    var minute = parseInt(parts[1], 10);
    if (Number.isNaN(hour) || Number.isNaN(minute)) return null;
    return { hour: hour, minute: minute };
  }

  function addMinutesToTime(timeValue, minutesToAdd) {
    var time = parseTimeParts(timeValue);
    if (!time) return timeValue;
    var totalMinutes = (time.hour * 60) + time.minute + (minutesToAdd || 0);
    var normalized = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60);
    var hour = Math.floor(normalized / 60);
    var minute = normalized % 60;
    return String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0');
  }

  function isDuringLunchBreak(timeValue) {
    var time = parseTimeParts(timeValue);
    if (!time) return false;
    var minutes = (time.hour * 60) + time.minute;
    return minutes >= 12 * 60 && minutes < 13 * 60;
  }

  function getNextWorkingSlotStart(timeValue) {
    var next = addMinutesToTime(timeValue, SLOT_MINUTES);
    return isDuringLunchBreak(next) ? '13:00' : next;
  }

  function calculateSlotsNeeded(durationMinutes) {
    if (!durationMinutes || durationMinutes <= 0) return 0;
    return Math.ceil(durationMinutes / SLOT_MINUTES);
  }

  function buildRequiredSlotStartTimes(slotStartTime, slotsNeeded) {
    var result = [];
    if (!slotStartTime || !slotsNeeded || slotsNeeded <= 0) return result;

    var current = slotStartTime;
    for (var i = 0; i < slotsNeeded; i++) {
      result.push(current);
      current = getNextWorkingSlotStart(current);
    }
    return result;
  }

  function getDisplayEndTime(slotStartTime, slotsNeeded) {
    if (!slotStartTime) return null;
    if (!slotsNeeded || slotsNeeded <= 0) {
      return addMinutesToTime(slotStartTime, SLOT_MINUTES);
    }

    var requiredStarts = buildRequiredSlotStartTimes(slotStartTime, slotsNeeded);
    if (!requiredStarts.length) {
      return addMinutesToTime(slotStartTime, SLOT_MINUTES);
    }

    return addMinutesToTime(requiredStarts[requiredStarts.length - 1], SLOT_MINUTES);
  }

  function getSelectedServices() {
    return Array.from(document.querySelectorAll('.staff-walkin-service-checkbox:checked'))
      .map(function (cb) {
        return {
          id: parseInt(cb.value, 10),
          name: cb.getAttribute('data-name') || '',
          duration: parseInt(cb.getAttribute('data-duration') || '0', 10),
          price: parseFloat(cb.getAttribute('data-price') || '0')
        };
      })
      .filter(function (s) { return !Number.isNaN(s.id); });
  }

  function getSelectedDurationMinutes() {
    return getSelectedServices().reduce(function (sum, item) {
      return sum + (item.duration || 0);
    }, 0);
  }

  function getSelectedSlotDisplayEndTime(slot) {
    if (!slot || !slot.startTime) return null;
    var slotsNeeded = calculateSlotsNeeded(getSelectedDurationMinutes());
    return getDisplayEndTime(slot.startTime, slotsNeeded <= 0 ? 1 : slotsNeeded);
  }

  function renderSlotButtonContent(slot, statusText) {
    var endTime = slot.endTime || addMinutesToTime(slot.startTime, SLOT_MINUTES);
    return formatTime(slot.startTime) + ' - ' + formatTime(endTime) +
      '<span class="time-slot-status">' + statusText + '</span>';
  }

  function applySelectedSlotVisualState(grid) {
    if (!grid) return;
    grid.querySelectorAll('.time-slot-btn').forEach(function (btn) {
      var isSelected = state.selectedSlot && String(state.selectedSlot.id) === String(btn.dataset.slotId);
      btn.classList.toggle('selected', !!isSelected);
    });
  }

  function init() {
    if (!document.getElementById('staff-walkin-calendar-body')) return;
    bindStaticEvents();
    renderCalendar();
    setStep(1);
    updateSummary();
  }

  function bindStaticEvents() {
    var prevBtn = document.getElementById('staff-walkin-calendar-prev');
    var nextBtn = document.getElementById('staff-walkin-calendar-next');
    var nextStepBtn = document.getElementById('staff-walkin-btn-next');
    var backStepBtn = document.getElementById('staff-walkin-btn-back');
    var contactChannelEl = document.getElementById('staff-walkin-contact-channel');
    var serviceCheckboxes = document.querySelectorAll('.staff-walkin-service-checkbox');

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
          if (getSelectedServices().length === 0) {
            showAlert('Vui lòng chọn ít nhất một dịch vụ.', 'warning', 'Thiếu thông tin');
            return;
          }
          if (!state.selectedDate) {
            showAlert('Vui lòng chọn ngày khám.', 'warning', 'Thiếu thông tin');
            return;
          }
          setStep(2);
          loadSlotsForDate(state.selectedDate);
          return;
        }
        if (state.currentStep === 2) {
          if (!state.selectedSlot) {
            showAlert('Vui lòng chọn khung giờ khám.', 'warning', 'Thiếu thông tin');
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

    serviceCheckboxes.forEach(function (checkbox) {
      checkbox.addEventListener('change', function () {
        state.selectedSlot = null;
        document.getElementById('staff-walkin-slot-id').value = '';
        updateSummary();
        if (state.currentStep === 2 && state.selectedDate) {
          loadSlotsForDate(state.selectedDate);
        }
      });
    });

    if (contactChannelEl) {
      contactChannelEl.addEventListener('change', updateContactFieldMeta);
      updateContactFieldMeta();
    }
  }

  function renderCalendar() {
    var tbody = document.getElementById('staff-walkin-calendar-body');
    var label = document.getElementById('staff-walkin-calendar-month-label');
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
            if (getSelectedServices().length === 0) {
              showAlert('Vui lòng chọn ít nhất một dịch vụ trước.', 'warning', 'Thiếu thông tin');
              return;
            }
            state.selectedDate = this.dataset.date;
            state.selectedSlot = null;
            document.getElementById('staff-walkin-slot-id').value = '';
            updateSummary();
            renderCalendar();
            if (state.currentStep === 2) {
              loadSlotsForDate(state.selectedDate);
            }
          });
        }

        tr.appendChild(td);
        day++;
      }
      tbody.appendChild(tr);
    }
  }

  function loadSlotsForDate(dateStr) {
    var selectedServices = getSelectedServices();
    var totalDuration = getSelectedDurationMinutes();
    var slotsNeeded = calculateSlotsNeeded(totalDuration);
    var loading = document.getElementById('staff-walkin-time-slots-loading');
    var grid = document.getElementById('staff-walkin-time-slots-grid');
    var empty = document.getElementById('staff-walkin-time-slots-empty');
    var selectedDateDisplay = document.getElementById('staff-walkin-selected-date-display');

    if (selectedServices.length === 0 || !dateStr) {
      if (grid) {
        grid.innerHTML = '';
        grid.style.display = 'none';
      }
      if (loading) loading.style.display = 'none';
      if (empty) empty.style.display = '';
      return;
    }

    if (selectedDateDisplay) selectedDateDisplay.textContent = '(' + formatDateDisplay(dateStr) + ')';
    if (loading) loading.style.display = '';
    if (grid) {
      grid.style.display = 'none';
      grid.innerHTML = '';
    }
    if (empty) empty.style.display = 'none';

    var params = new URLSearchParams({ date: dateStr });
    selectedServices.forEach(function (service) {
      params.append('serviceIds', String(service.id));
    });

    Promise.all([
      fetch('/staff/walk-in/slots?' + params.toString(), { credentials: 'same-origin' }),
      fetch('/staff/walk-in/slots/all?date=' + encodeURIComponent(dateStr), { credentials: 'same-origin' })
    ])
      .then(function (responses) {
        return Promise.all(responses.map(function (res) {
          if (!res.ok) {
            return res.json()
              .then(function (err) {
                throw new Error(String(err.message || err.error || 'Không thể tải danh sách khung giờ.'));
              })
              .catch(function () {
                throw new Error('Không thể tải danh sách khung giờ.');
              });
          }
          return res.json();
        }));
      })
      .then(function (payloads) {
        if (loading) loading.style.display = 'none';
        if (!Array.isArray(payloads) || !grid) return;

        var availableStarts = Array.isArray(payloads[0]) ? payloads[0] : [];
        var allSlots = Array.isArray(payloads[1]) ? payloads[1] : [];
        var availableById = {};

        availableStarts.forEach(function (slot) {
          availableById[String(slot.id)] = slot;
        });

        var now = new Date();
        var p = dateStr.split('-');
        var d = new Date(parseInt(p[0], 10), parseInt(p[1], 10) - 1, parseInt(p[2], 10));
        var futureSlots = allSlots.filter(function (slot) {
          if (!slot.startTime) return false;
          var t = slot.startTime.split(':');
          var slotDateTime = new Date(d.getFullYear(), d.getMonth(), d.getDate(), parseInt(t[0], 10), parseInt(t[1], 10), 0);
          return slotDateTime > now;
        });

        if (futureSlots.length === 0) {
          if (empty) empty.style.display = '';
          return;
        }

        state.renderedSlots = futureSlots.map(function (slot) {
          return availableById[String(slot.id)] || slot;
        });

        futureSlots.forEach(function (rawSlot) {
          var slot = availableById[String(rawSlot.id)] || rawSlot;
          var btn = document.createElement('button');
          btn.type = 'button';
          btn.className = 'time-slot-btn';
          btn.dataset.slotId = slot.id;

          var hasSpotsValue = slot.availableSpots !== undefined && slot.availableSpots !== null;
          var availableSpots = hasSpotsValue ? Number(slot.availableSpots) : null;
          var ownHasSpotsValue = rawSlot.availableSpots !== undefined && rawSlot.availableSpots !== null;
          var ownAvailableSpots = ownHasSpotsValue ? Number(rawSlot.availableSpots) : null;
          var isOwnSlotFull = ownHasSpotsValue ? ownAvailableSpots <= 0 : rawSlot.available === false;
          var isSelectable = !!availableById[String(rawSlot.id)] && !(hasSpotsValue ? availableSpots <= 0 : slot.available === false);

          var statusText = 'Hết chỗ';
          if (isSelectable) {
            statusText = hasSpotsValue ? ('Còn ' + availableSpots + ' chỗ') : 'Còn chỗ';
          } else if (isOwnSlotFull) {
            statusText = 'Hết chỗ';
          } else if (slotsNeeded > 1) {
            statusText = 'Cần trống tới ' + formatTime(getDisplayEndTime(rawSlot.startTime, slotsNeeded));
          } else {
            statusText = 'Không thể chọn';
          }

          btn.innerHTML = renderSlotButtonContent(rawSlot, statusText);

          if (!isSelectable) btn.classList.add('disabled');
          else if (hasSpotsValue && availableSpots === 1) btn.classList.add('almost-full');

          btn.addEventListener('click', function () {
            if (!isSelectable) {
              var message = slotsNeeded > 1 && !isOwnSlotFull
                ? 'Khung giờ này không đủ slot liền kề cho toàn bộ thời lượng khám.'
                : 'Khung giờ này đã đầy. Vui lòng chọn khung giờ khác.';
              showToast(message, 'warning', 'Không thể chọn');
              return;
            }
            state.selectedSlot = slot;
            document.getElementById('staff-walkin-slot-id').value = slot.id;
            updateSummary();
            applySelectedSlotVisualState(grid);
          });

          grid.appendChild(btn);
        });

        applySelectedSlotVisualState(grid);
        grid.style.display = '';
      })
      .catch(function (err) {
        if (loading) loading.style.display = 'none';
        if (empty) empty.style.display = '';
        if (grid) grid.style.display = 'none';
        state.renderedSlots = [];
        showAlert(err && err.message ? err.message : 'Không thể tải danh sách khung giờ.', 'error', 'Tải khung giờ thất bại');
      });
  }

  function setStep(step) {
    state.currentStep = step;

    for (var i = 1; i <= 4; i++) {
      var panel = document.getElementById('staff-walkin-step-' + i);
      if (panel) panel.classList.toggle('active', i === step);

      var circle = document.getElementById('staff-walkin-step-' + i + '-circle');
      var label = document.getElementById('staff-walkin-step-' + i + '-label');
      if (circle) circle.classList.toggle('active', i <= step);
      if (label) label.classList.toggle('active', i <= step);
    }

    var nextBtn = document.getElementById('staff-walkin-btn-next');
    var backBtn = document.getElementById('staff-walkin-btn-back');
    var actions = document.getElementById('staff-walkin-step-actions');

    if (actions) actions.style.display = step >= 4 ? 'none' : 'flex';
    if (backBtn) backBtn.style.display = step > 1 && step < 4 ? '' : 'none';
    if (nextBtn) {
      nextBtn.textContent = step === 3 ? 'Tạo lịch hẹn' : 'Tiếp tục';
      updateNextButtonState();
    }
  }

  function updateNextButtonState() {
    var nextBtn = document.getElementById('staff-walkin-btn-next');
    if (!nextBtn) return;

    if (state.currentStep === 1) {
      nextBtn.disabled = !(state.selectedDate && getSelectedServices().length > 0);
      return;
    }
    if (state.currentStep === 2) {
      nextBtn.disabled = !state.selectedSlot;
      return;
    }
    nextBtn.disabled = false;
  }

  function updateSummary() {
    var selectedServices = getSelectedServices();
    var totalServices = selectedServices.length;
    var totalDuration = selectedServices.reduce(function (sum, item) { return sum + (item.duration || 0); }, 0);
    var totalAmount = selectedServices.reduce(function (sum, item) { return sum + (item.price || 0); }, 0);

    var dateEl = document.getElementById('staff-walkin-summary-date');
    var timeEl = document.getElementById('staff-walkin-summary-time');
    var serviceEl = document.getElementById('staff-walkin-summary-service');
    var durationEl = document.getElementById('staff-walkin-summary-duration');
    var totalEl = document.getElementById('staff-walkin-summary-total');
    var noteEl = document.getElementById('staff-walkin-summary-payment-note');
    var incomplete = document.getElementById('staff-walkin-summary-incomplete');

    if (dateEl) dateEl.innerHTML = state.selectedDate ? formatDateDisplay(state.selectedDate) : '<span class="empty">Chưa chọn</span>';
    if (timeEl) {
      if (state.selectedSlot && state.selectedSlot.startTime) {
        timeEl.innerHTML = formatTime(state.selectedSlot.startTime) + ' - ' + formatTime(getSelectedSlotDisplayEndTime(state.selectedSlot));
      } else {
        timeEl.innerHTML = '<span class="empty">Chưa chọn</span>';
      }
    }
    if (serviceEl) {
      if (totalServices > 0) {
        serviceEl.innerHTML = '<strong>' + totalServices + ' dịch vụ</strong>: ' + selectedServices.map(function (s) { return s.name; }).join(', ');
      } else {
        serviceEl.innerHTML = '<span class="empty">Chưa chọn</span>';
      }
    }
    if (durationEl) durationEl.innerHTML = totalDuration > 0 ? (totalDuration + ' phút') : '<span class="empty">--</span>';
    if (totalEl) totalEl.innerHTML = totalAmount > 0 ? formatVnd(totalAmount) : '<span class="empty">--</span>';
    if (noteEl) noteEl.textContent = totalAmount > 0 ? 'Khách vãng lai sẽ thanh toán tại staff sau khi hoàn tất khám.' : '---';
    if (incomplete) incomplete.style.display = (state.selectedDate && state.selectedSlot && totalServices > 0) ? 'none' : '';

    updateNextButtonState();
  }

  function submitBooking(nextStepBtn) {
    var slotId = (document.getElementById('staff-walkin-slot-id') || {}).value;
    var fullName = (document.getElementById('staff-walkin-full-name') || {}).value.trim();
    var selectedServices = getSelectedServices();
    var serviceIds = selectedServices.map(function (s) { return s.id; });
    var contactChannel = (document.getElementById('staff-walkin-contact-channel') || {}).value;
    var contactValue = (document.getElementById('staff-walkin-contact-value') || {}).value.trim();
    var note = (document.getElementById('staff-walkin-note') || {}).value;

    if (!slotId || serviceIds.length === 0 || !fullName || !contactChannel || !contactValue) {
      showAlert('Vui lòng điền đầy đủ thông tin khách và chọn ít nhất một dịch vụ.', 'warning', 'Thiếu thông tin');
      return;
    }

    nextStepBtn.disabled = true;
    var originalText = nextStepBtn.innerHTML;
    nextStepBtn.innerHTML = 'Đang tạo lịch hẹn...';

    fetch('/staff/walk-in/appointments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({
        fullName: fullName,
        phone: contactChannel === 'PHONE' ? contactValue : '',
        slotId: parseInt(slotId, 10),
        serviceIds: serviceIds,
        patientNote: note || null,
        contactChannel: contactChannel,
        contactValue: contactValue
      })
    })
      .then(function (response) {
        if (!response.ok) {
          return readErrorMessage(response).then(function (message) {
            throw new Error(message);
          });
        }
        return response.json();
      })
      .then(function (data) {
        renderSuccess(data);
        setStep(4);
      })
      .catch(function (err) {
        nextStepBtn.disabled = false;
        nextStepBtn.innerHTML = originalText;
        showAlert(err.message || 'Đặt lịch thất bại. Vui lòng thử lại.', 'error', 'Đặt lịch thất bại');
      });
  }

  function renderSuccess(data) {
    var summaryEl = document.getElementById('staff-walkin-success-summary');
    if (!summaryEl) return;

    var dateText = data.date ? formatDateDisplay(data.date) : '';
    var timeText = data.startTime && data.endTime
      ? formatTime(data.startTime) + ' - ' + formatTime(data.endTime)
      : '';

    summaryEl.innerHTML =
      '<p><strong>Mã lịch hẹn:</strong> #' + (data.id || '') + '</p>' +
      '<p><strong>Khách hàng:</strong> ' + (data.customerName || '') + '</p>' +
      '<p><strong>Dịch vụ:</strong> ' + (data.serviceName || 'N/A') + '</p>' +
      '<p><strong>Ngày:</strong> ' + dateText + '</p>' +
      '<p><strong>Giờ:</strong> ' + timeText + '</p>' +
      '<p><span style="color:#2f855a;font-weight:bold;">✓ Đã tạo lịch hẹn khách vãng lai thành công</span></p>';
  }

  document.addEventListener('DOMContentLoaded', init);
})();

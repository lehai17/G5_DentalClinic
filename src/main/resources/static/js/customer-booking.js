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
  var paymentSelection = null;

  function showToast(message, type, title) {
    if (window.CustomerFeedback) {
      window.CustomerFeedback.toast({ message: message, type: type || 'info', title: title || '' });
      return;
    }
  }

  function showAlert(message, type, title) {
    if (window.CustomerFeedback) {
      return window.CustomerFeedback.alert({ message: message, type: type || 'info', title: title || 'Thông báo' });
    }
    alert(message);
    return Promise.resolve();
  }

  function init() {
    var calendarBody = document.getElementById('customer-calendar-body');
    if (!calendarBody) return;

    bindStaticEvents();
    renderCalendar();
    setStep(1);
    updateSummary();

    var urlParams = new URLSearchParams(window.location.search);
    var status = urlParams.get('status');
    var pendingId = sessionStorage.getItem('pendingAppointmentId');

    if (status === 'success') {
      sessionStorage.removeItem('pendingAppointmentId');
      checkReturnStatus();
    } else if (pendingId) {
      handleCancellationOnBack(pendingId);
    } else {
      checkReturnStatus();
    }
  }

  function handleCancellationOnBack(appointmentId) {
    fetch('/customer/payment/appointments/cancel-back/' + appointmentId, {
      method: 'POST',
      credentials: 'same-origin'
    })
      .then(function (response) {
        sessionStorage.removeItem('pendingAppointmentId');
        if (response.ok) {
          showAlert('Giao dịch thanh toán đã bị gián đoạn. Vui lòng đặt lịch lại.', 'warning', 'Thanh toán bị gián đoạn')
            .then(function () {
              window.location.reload();
            });
        }
      })
      .catch(function () {
        sessionStorage.removeItem('pendingAppointmentId');
      });
  }

  function checkReturnStatus() {
    var urlParams = new URLSearchParams(window.location.search);
    var status = urlParams.get('status');
    var appointmentId = urlParams.get('id');

    if (status === 'success') {
      setStep(4);
      var summaryCol = document.getElementById('booking-summary');
      var actions = document.getElementById('step-actions');
      if (summaryCol) summaryCol.style.display = 'none';
      if (actions) actions.style.display = 'none';

      var bookingCard = document.querySelector('.booking-card');
      var leftCol = document.querySelector('.booking-left');
      if (bookingCard) bookingCard.style.display = 'block';
      if (leftCol) {
        leftCol.style.width = '100%';
        leftCol.style.textAlign = 'center';
      }

      var summaryEl = document.getElementById('success-summary');
      if (summaryEl && appointmentId) {
        summaryEl.innerHTML =
          '<div style="background:#f0fff4;border:1px solid #c6f6d5;padding:20px;border-radius:12px;margin:20px 0;">' +
          '<p style="color:#2f855a;margin-bottom:5px;">Mã số lịch hẹn của bạn</p>' +
          '<h2 style="color:#22543d;margin:0;">#' + appointmentId + '</h2>' +
          '</div>';
      }

      if (appointmentId) {
        fetch('/customer/appointments/detail/' + appointmentId)
          .then(function (res) { return res.json(); })
          .then(function (data) { updateSuccessSummary(data); });
      }
    } else if (status === 'fail') {
      showAlert('Thanh toán không thành công hoặc bạn đã hủy giao dịch.', 'error', 'Thanh toán thất bại');
      setStep(1);
    }
  }

  function updateSuccessSummary(data) {
    var summaryEl = document.getElementById('success-summary');
    if (!summaryEl) return;

    var dateText = data.date ? formatDateDisplay(data.date) : '';
    var timeText = data.startTime && data.endTime
      ? formatTime(data.startTime) + ' - ' + formatTime(data.endTime)
      : '';

    summaryEl.innerHTML =
      '<p><strong>Mã lịch hẹn:</strong> #' + (data.id || '') + '</p>' +
      '<p><strong>Dịch vụ:</strong> ' + (data.serviceName || 'N/A') + '</p>' +
      '<p><strong>Ngày:</strong> ' + dateText + '</p>' +
      '<p><strong>Giờ:</strong> ' + timeText + '</p>' +
      '<p><span style="color:green;font-weight:bold;">✓ Đã thanh toán tiền cọc</span></p>';
  }

  function getSelectedServices() {
    return Array.from(document.querySelectorAll('.customer-service-checkbox:checked'))
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

  function formatVnd(amount) {
    return Number(amount || 0).toLocaleString('vi-VN') + ' VNĐ';
  }

  function calculateSlotsNeeded(durationMinutes) {
    if (!durationMinutes || durationMinutes <= 0) return 0;
    return Math.ceil(durationMinutes / SLOT_MINUTES);
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

  function getSelectedDurationMinutes() {
    return getSelectedServices().reduce(function (sum, item) {
      return sum + (item.duration || 0);
    }, 0);
  }

  function getSelectedSlotDisplayEndTime(slot) {
    if (!slot || !slot.startTime) return null;
    var slotsNeeded = calculateSlotsNeeded(getSelectedDurationMinutes());
    if (slotsNeeded <= 0) {
      return slot.endTime || addMinutesToTime(slot.startTime, SLOT_MINUTES);
    }
    return addMinutesToTime(slot.startTime, slotsNeeded * SLOT_MINUTES);
  }

  function isSlotWithinSelectedRange(slot) {
    if (!slot || !state.selectedSlot || !slot.startTime || !state.selectedSlot.startTime) return false;
    var selectedStart = parseTimeParts(state.selectedSlot.startTime);
    var candidateStart = parseTimeParts(slot.startTime);
    var selectedEnd = parseTimeParts(getSelectedSlotDisplayEndTime(state.selectedSlot));
    if (!selectedStart || !candidateStart || !selectedEnd) return false;
    var selectedStartMinutes = (selectedStart.hour * 60) + selectedStart.minute;
    var candidateStartMinutes = (candidateStart.hour * 60) + candidateStart.minute;
    var selectedEndMinutes = (selectedEnd.hour * 60) + selectedEnd.minute;
    return candidateStartMinutes >= selectedStartMinutes && candidateStartMinutes < selectedEndMinutes;
  }

  function renderSlotButtonContent(slot, statusText, useExpandedRange) {
    var endTime = useExpandedRange ? getSelectedSlotDisplayEndTime(slot) : (slot.endTime || addMinutesToTime(slot.startTime, SLOT_MINUTES));
    return formatTime(slot.startTime) + ' - ' + formatTime(endTime) +
      '<span class="time-slot-status">' + statusText + '</span>';
  }

  function applySelectedSlotVisualState(grid) {
    if (!grid) return;
    grid.querySelectorAll('.time-slot-btn').forEach(function (btn) {
      var slotId = btn.dataset.slotId;
      var slot = state.renderedSlots.find(function (item) {
        return String(item.id) === String(slotId);
      });
      btn.classList.remove('selected', 'range-selected');
      if (!slot) return;
      var isSelected = state.selectedSlot && String(state.selectedSlot.id) === String(slot.id);
      var isInRange = isSlotWithinSelectedRange(slot);
      if (isSelected) {
        btn.classList.add('selected');
      } else if (isInRange) {
        btn.classList.add('range-selected');
      }
      btn.innerHTML = renderSlotButtonContent(slot, btn.dataset.statusText || '', false);
    });
  }

  function bindStaticEvents() {
    var prevBtn = document.getElementById('customer-calendar-prev');
    var nextBtn = document.getElementById('customer-calendar-next');
    var nextStepBtn = document.getElementById('btn-next');
    var backStepBtn = document.getElementById('btn-back');
    var serviceCheckboxes = document.querySelectorAll('.customer-service-checkbox');
    bindPaymentModalEvents();

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

    if (serviceCheckboxes.length > 0) {
      serviceCheckboxes.forEach(function (checkbox) {
        checkbox.addEventListener('change', function () {
          state.selectedSlot = null;
          document.getElementById('customer-booking-slot-id').value = '';
          updateSummary();
          if (state.currentStep === 2 && state.selectedDate) {
            loadSlotsForDate(state.selectedDate);
          }
        });
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
            if (getSelectedServices().length === 0) {
              showAlert('Vui lòng chọn ít nhất một dịch vụ trước.', 'warning', 'Thiếu thông tin');
              return;
            }
            state.selectedDate = this.dataset.date;
            state.selectedSlot = null;
            document.getElementById('customer-booking-slot-id').value = '';
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
    var loading = document.getElementById('time-slots-loading');
    var grid = document.getElementById('time-slots-grid');
    var empty = document.getElementById('time-slots-empty');
    var selectedDateDisplay = document.getElementById('selected-date-display');

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

    fetch('/customer/slots?' + params.toString(), { credentials: 'same-origin' })
      .then(function (res) {
        if (res.status === 401) {
          showAlert('Bạn cần đăng nhập để đặt lịch.', 'warning', 'Chưa đăng nhập');
          setStep(1);
          return null;
        }
        if (!res.ok) {
          return res.json()
            .then(function (err) {
              throw new Error(err.message || 'Không thể tải danh sách khung giờ.');
            })
            .catch(function () {
              throw new Error('Không thể tải danh sách khung giờ.');
            });
        }
        return res.json();
      })
      .then(function (slots) {
        if (loading) loading.style.display = 'none';
        if (!Array.isArray(slots) || !grid) return;
        state.renderedSlots = [];

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
          return;
        }

        state.renderedSlots = futureSlots.slice();

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
          if (isDisabled) statusText = 'Đã có lịch';
          if (!isDisabled && isAvailable) statusText = hasSpotsValue ? ('Còn ' + availableSpots + ' chỗ') : 'Còn chỗ';
          btn.dataset.statusText = statusText;

          btn.innerHTML = renderSlotButtonContent(slot, statusText, false);

          if (!isAvailable) btn.classList.add('disabled');
          else if (hasSpotsValue && availableSpots === 1) btn.classList.add('almost-full');

          btn.addEventListener('click', function () {
            if (isDisabled) {
              showToast('Bạn đã có lịch hẹn trùng thời điểm này.', 'warning', 'Không thể chọn');
              return;
            }
            if (!isAvailable) {
              showToast('Khung giờ này đã đầy. Vui lòng chọn khung giờ khác.', 'warning', 'Khung giờ đã đầy');
              return;
            }
            if (state.selectedSlot && String(state.selectedSlot.id) === String(slot.id)) {
              state.selectedSlot = null;
              document.getElementById('customer-booking-slot-id').value = '';
              updateSummary();
              applySelectedSlotVisualState(grid);
              return;
            }
            state.selectedSlot = slot;
            document.getElementById('customer-booking-slot-id').value = slot.id;
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
      nextBtn.textContent = step === 3 ? 'Đặt cọc' : 'Tiếp tục';
      updateNextButtonState();
    }
  }

  function updateNextButtonState() {
    var nextBtn = document.getElementById('btn-next');
    if (!nextBtn) return;

    var selectedServices = getSelectedServices();
    var isStep1Ready = !!state.selectedDate && selectedServices.length > 0;
    var isStep2Ready = !!state.selectedSlot;

    if (state.currentStep === 1) {
      nextBtn.disabled = !isStep1Ready;
      return;
    }

    if (state.currentStep === 2) {
      nextBtn.disabled = !isStep2Ready;
      return;
    }

    nextBtn.disabled = false;
  }

  function updateSummary() {
    var selectedServices = getSelectedServices();
    var totalServices = selectedServices.length;
    var totalDuration = selectedServices.reduce(function (sum, item) { return sum + (item.duration || 0); }, 0);
    var totalAmount = selectedServices.reduce(function (sum, item) { return sum + (item.price || 0); }, 0);
    var depositAmount = totalAmount * 0.5;

    var dateEl = document.getElementById('summary-date');
    var timeEl = document.getElementById('summary-time');
    var serviceEl = document.getElementById('summary-service');
    var durationEl = document.getElementById('summary-duration');
    var totalEl = document.getElementById('summary-total');
    var depositEl = document.getElementById('summary-deposit');
    var incomplete = document.getElementById('summary-incomplete');

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
    if (depositEl) depositEl.innerHTML = totalAmount > 0
      ? '<span style="color:#e67e22;font-weight:bold;">' + formatVnd(depositAmount) + '</span>'
      : '<span class="empty">--</span>';

    if (incomplete) {
      var ok = !!state.selectedDate && !!state.selectedSlot && totalServices > 0;
      incomplete.style.display = ok ? 'none' : '';
    }

    updateNextButtonState();
  }

  function submitBooking(nextStepBtn) {
    var slotId = (document.getElementById('customer-booking-slot-id') || {}).value;
    var selectedServices = getSelectedServices();
    var serviceIds = selectedServices.map(function (s) { return s.id; });
    var contactChannel = (document.getElementById('customer-booking-contact-channel') || {}).value;
    var contactValue = (document.getElementById('customer-booking-contact-value') || {}).value.trim();
    var note = (document.getElementById('customer-booking-note') || {}).value;

    if (!slotId || serviceIds.length === 0 || !contactChannel || !contactValue) {
      showAlert('Vui lòng điền đầy đủ thông tin và chọn ít nhất một dịch vụ.', 'warning', 'Thiếu thông tin');
      return;
    }

    nextStepBtn.disabled = true;
    var originalText = nextStepBtn.innerHTML;
    nextStepBtn.innerHTML = '<i class="bi bi-hourglass-split"></i> Đang tạo lịch hẹn...';

    fetch('/customer/appointments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({
        slotId: parseInt(slotId, 10),
        serviceIds: serviceIds,
        patientNote: note || null,
        contactChannel: contactChannel,
        contactValue: contactValue
      })
    })
      .then(function (response) {
        if (response.status === 401) {
          throw new Error('Bạn cần đăng nhập để tiếp tục.');
        }
        if (!response.ok) {
          return response.json().then(function (err) {
            throw new Error(err.message || 'Không thể tạo lịch hẹn.');
          });
        }
        return response.json();
      })
      .then(function (data) {
        if (data && data.id) {
          chooseDepositMethod(data, nextStepBtn, originalText);
        } else {
          throw new Error('Dữ liệu trả về không hợp lệ.');
        }
      })
      .catch(function (err) {
        nextStepBtn.disabled = false;
        nextStepBtn.innerHTML = originalText;
        showAlert(err.message || 'Đặt lịch thất bại. Vui lòng thử lại.', 'error', 'Đặt lịch thất bại');
      });
  }

  function chooseDepositMethod(appointment, nextStepBtn, originalText) {
    paymentSelection = {
      appointmentId: appointment.id,
      depositAmount: appointment && appointment.depositAmount ? appointment.depositAmount : 0,
      nextStepBtn: nextStepBtn,
      originalText: originalText
    };

    var amountEl = document.getElementById('booking-payment-deposit-amount');
    var modal = document.getElementById('booking-payment-modal');
    if (amountEl) amountEl.textContent = formatVnd(paymentSelection.depositAmount);
    if (modal) modal.hidden = false;
    document.body.style.overflow = 'hidden';
  }

  function payDepositWithWallet(appointmentId, nextStepBtn, originalText) {
    nextStepBtn.innerHTML = '<i class="bi bi-wallet2"></i> Đang thanh toán bằng ví...';

    fetch('/customer/payment/deposit/' + appointmentId + '/wallet', {
      method: 'POST',
      credentials: 'same-origin'
    })
      .then(function (response) {
        return response.json().catch(function () {
          return {};
        }).then(function (data) {
          if (!response.ok) {
            throw new Error(data.message || 'Không thể thanh toán bằng ví.');
          }
          return data;
        });
      })
      .then(function () {
        sessionStorage.removeItem('pendingAppointmentId');
        paymentSelection = null;
        window.location.href = '/customer/book?status=success&id=' + appointmentId;
      })
      .catch(function (err) {
        showAlert(err.message || 'Thanh toán bằng ví thất bại.', 'error', 'Thanh toán ví thất bại');
        if (nextStepBtn) {
          nextStepBtn.disabled = false;
          nextStepBtn.innerHTML = originalText;
        }
        if (paymentSelection) {
          chooseDepositMethod(
            {
              id: paymentSelection.appointmentId,
              depositAmount: paymentSelection.depositAmount
            },
            nextStepBtn,
            originalText
          );
        }
      });
  }

  function cancelPendingAppointment(appointmentId) {
    return fetch('/customer/payment/appointments/cancel-back/' + appointmentId, {
      method: 'POST',
      credentials: 'same-origin'
    })
      .then(function () {
        sessionStorage.removeItem('pendingAppointmentId');
        showToast('Đã hủy lịch chờ thanh toán.', 'info', 'Đã hủy');
      })
      .catch(function () {
        sessionStorage.removeItem('pendingAppointmentId');
      });
  }

  function closePaymentModal() {
    var modal = document.getElementById('booking-payment-modal');
    if (modal) modal.hidden = true;
    document.body.style.overflow = '';
  }

  function bindPaymentModalEvents() {
    var closeBtn = document.getElementById('booking-payment-close');
    var cancelBtn = document.getElementById('booking-payment-cancel');
    var vnpayBtn = document.getElementById('booking-payment-vnpay');
    var walletBtn = document.getElementById('booking-payment-wallet');

    document.querySelectorAll('[data-close-payment-modal]').forEach(function (node) {
      node.addEventListener('click', function () {
        if (!paymentSelection) {
          closePaymentModal();
          return;
        }

        cancelPendingAppointment(paymentSelection.appointmentId)
          .finally(function () {
            if (paymentSelection.nextStepBtn) {
              paymentSelection.nextStepBtn.disabled = false;
              paymentSelection.nextStepBtn.innerHTML = paymentSelection.originalText;
            }
            paymentSelection = null;
            closePaymentModal();
          });
      });
    });

    if (closeBtn) {
      closeBtn.addEventListener('click', function () {
        if (cancelBtn) cancelBtn.click();
      });
    }

    if (cancelBtn) {
      cancelBtn.addEventListener('click', function () {
        if (!paymentSelection) {
          closePaymentModal();
          return;
        }

        cancelPendingAppointment(paymentSelection.appointmentId)
          .finally(function () {
            if (paymentSelection.nextStepBtn) {
              paymentSelection.nextStepBtn.disabled = false;
              paymentSelection.nextStepBtn.innerHTML = paymentSelection.originalText;
            }
            paymentSelection = null;
            closePaymentModal();
          });
      });
    }

    if (vnpayBtn) {
      vnpayBtn.addEventListener('click', function () {
        if (!paymentSelection) return;
        sessionStorage.setItem('pendingAppointmentId', paymentSelection.appointmentId);
        closePaymentModal();
        window.location.href = '/customer/payment/create-deposit/' + paymentSelection.appointmentId;
      });
    }

    if (walletBtn) {
      walletBtn.addEventListener('click', function () {
        if (!paymentSelection) return;
        closePaymentModal();
        if (paymentSelection.nextStepBtn) {
          paymentSelection.nextStepBtn.disabled = true;
        }
        payDepositWithWallet(
          paymentSelection.appointmentId,
          paymentSelection.nextStepBtn,
          paymentSelection.originalText
        );
      });
    }

    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') {
        var modal = document.getElementById('booking-payment-modal');
        if (modal && !modal.hidden && cancelBtn) {
          cancelBtn.click();
        }
      }
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

    window.customerBookingAI = {
        applySuggestion: function (payload) {
            if (!payload) return;

            var selectedDateInput = document.getElementById('selected-date');
            var selectedTimeInput = document.getElementById('selected-time');

            if (payload.date) {
                state.selectedDate = payload.date;
                if (selectedDateInput) {
                    selectedDateInput.value = payload.date;
                }
            }

            if (payload.startTime) {
                state.selectedSlot = {
                    id: 'ai-selected-slot',
                    startTime: payload.startTime,
                    endTime: payload.endTime || null
                };

                if (selectedTimeInput) {
                    selectedTimeInput.value = payload.startTime;
                }
            }

            renderCalendar();
            updateSummary();

            if (payload.date) {
                setStep(2);
                loadSlotsForDate(payload.date).then(function () {
                    if (!payload.startTime || !Array.isArray(state.renderedSlots)) return;

                    var matched = state.renderedSlots.find(function (slot) {
                        var hasSpotsValue = slot.availableSpots !== undefined && slot.availableSpots !== null;
                        var availableSpots = hasSpotsValue ? Number(slot.availableSpots) : null;
                        var isDisabled = slot.disabled === true;
                        var isFull = hasSpotsValue ? availableSpots <= 0 : slot.available === false;
                        var isAvailable = !isDisabled && !isFull;

                        return String(slot.startTime) === String(payload.startTime) && isAvailable;
                    });

                    if (matched) {
                        state.selectedSlot = matched;

                        if (selectedTimeInput) {
                            selectedTimeInput.value = matched.startTime;
                        }

                        var slotGrid = document.getElementById('time-slots-grid');
                        if (slotGrid) {
                            applySelectedSlotVisualState(slotGrid);
                        }

                        updateSummary();
                    } else {
                        state.selectedSlot = null;

                        if (selectedTimeInput) {
                            selectedTimeInput.value = '';
                        }

                        updateSummary();
                    }
                });
            }

            showAlert('Đã áp dụng gợi ý AI vào form đặt lịch.', 'success', 'Thành công');
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();


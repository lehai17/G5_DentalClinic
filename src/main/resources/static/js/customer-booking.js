(function () {
  'use strict';

  function initCalendar() {
    var calendarBody = document.getElementById('customer-calendar-body');
    if (!calendarBody) {
      console.log('Calendar body not found, waiting...');
      setTimeout(initCalendar, 500);
      return;
    }

    var tbody = document.getElementById('customer-calendar-body');
    var label = document.getElementById('customer-calendar-month-label');
    if (!tbody || !label) {
      console.log('Calendar elements not found');
      return;
    }

    var currentCalendarYear = new Date().getFullYear();
    var currentCalendarMonth = new Date().getMonth();

    function renderCalendar() {
      if (!tbody || !label) return;
      var lastDay = new Date(currentCalendarYear, currentCalendarMonth + 1, 0).getDate();
      var firstDow = new Date(currentCalendarYear, currentCalendarMonth, 1).getDay();
      var today = new Date();
      today.setHours(0, 0, 0, 0);
      var monthNames = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6', 'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];
      label.textContent = monthNames[currentCalendarMonth] + ' / ' + currentCalendarYear;
      tbody.innerHTML = '';
      var day = 1;
      for (var row = 0; row < 6; row++) {
        var tr = document.createElement('tr');
        for (var col = 0; col < 7; col++) {
          var td = document.createElement('td');
          var cellIndex = row * 7 + col;
          if (cellIndex < firstDow || day > lastDay) {
            td.className = 'customer-calendar-cell customer-calendar-cell--empty';
          } else {
            td.className = 'customer-calendar-cell customer-calendar-cell--day';
            td.textContent = day;
            td.dataset.day = day;
            var dateStr = currentCalendarYear + '-' + String(currentCalendarMonth + 1).padStart(2, '0') + '-' + String(day).padStart(2, '0');
            td.dataset.date = dateStr;
            var cellDate = new Date(currentCalendarYear, currentCalendarMonth, day);
            cellDate.setHours(0, 0, 0, 0);
            if (cellDate < today) {
              td.classList.add('customer-calendar-cell--past');
            } else {
              (function (dStr) {
                td.addEventListener('click', function () {
                  var serviceEl = document.getElementById('customer-booking-service');
                  if (!serviceEl || !serviceEl.value) {
                    alert('Vui lòng chọn dịch vụ trước.');
                    return;
                  }
                  loadSlotsForDate(dStr);
                });
              })(dateStr);
            }
            day++;
          }
          tr.appendChild(td);
        }
        tbody.appendChild(tr);
      }
    }

    function loadSlotsForDate(dateStr) {
      var serviceId = (document.getElementById('customer-booking-service') || {}).value;
      if (!serviceId || !dateStr) return;
      var loading = document.getElementById('customer-times-loading');
      var wrap = document.getElementById('customer-times-wrap');
      var body = document.getElementById('customer-times-body');
      var empty = document.getElementById('customer-times-empty');
      if (loading) loading.style.display = '';
      if (wrap) wrap.style.display = 'none';
      if (body) body.innerHTML = '';
      if (empty) empty.style.display = 'none';
      setBookingStep(2);
      var label = document.getElementById('customer-selected-date-label');
      if (label) label.textContent = formatDateDisplay(dateStr);
      var params = new URLSearchParams({ date: dateStr, serviceId: serviceId });
      fetch('/customer/slots?' + params.toString(), { credentials: 'same-origin' })
        .then(function (r) {
          if (r.status === 401) { alert('Bạn cần đăng nhập để đặt lịch.'); setBookingStep(1); return null; }
          return r.json();
        })
        .then(function (data) {
          if (loading) loading.style.display = 'none';
          if (wrap) wrap.style.display = '';
          if (!data || !body) return;
          
          // CRITICAL FIX: Filter out slots that are in the past (even if server sends them)
          var now = new Date();
          var selectedDateParts = dateStr.split('-');
          var slotDate = new Date(selectedDateParts[0], selectedDateParts[1] - 1, selectedDateParts[2]);
          
          var futureSlots = data.filter(function (slot) {
            if (!slot.startTime) return false;
            
            // Parse slot time and create a Date object
            var timeParts = slot.startTime.split(':');
            var slotDateTime = new Date(slotDate.getFullYear(), slotDate.getMonth(), slotDate.getDate(), 
                                        parseInt(timeParts[0], 10), parseInt(timeParts[1], 10), 0);
            
            // Only show slots that are in the future
            return slotDateTime > now;
          });
          
          if (Array.isArray(futureSlots) && futureSlots.length > 0) {
            futureSlots.forEach(function (slot) {
              var tr = document.createElement('tr');
              tr.className = 'customer-time-row';
              tr.dataset.slotId = slot.id;
              
              // Check if slot is disabled due to overlap
              var isDisabled = slot.disabled === true;
              var isNoSpots = slot.availableSpots === 0 || slot.available === false;
              
              // Display available spots - show "Trống" if available, or specific count
              var availableText = 'Trống';
              if (isDisabled) {
                availableText = 'Đã đặt';
              } else if (slot.availableSpots !== undefined && slot.availableSpots !== null && slot.availableSpots > 0) {
                availableText = 'Còn ' + slot.availableSpots + ' chỗ';
              } else if (isNoSpots) {
                availableText = 'Hết chỗ';
              }
              
              tr.innerHTML = '<td>' + formatTime(slot.startTime) + ' - ' + formatTime(slot.endTime) + '</td><td>' + availableText + '</td>';
              
              // Style disabled slots
              if (isDisabled) {
                tr.style.backgroundColor = '#e9ecef';
                tr.style.opacity = '0.6';
                tr.style.cursor = 'not-allowed';
              }
              
              tr.addEventListener('click', function () {
                // Prevent selection if slot is disabled or full
                if (isDisabled) {
                  alert('Bạn đã có lịch khám trùng thời gian này.');
                  return;
                }
                if (isNoSpots) {
                  alert('Khung giờ này đã đầy. Vui lòng chọn khung giờ khác.');
                  return;
                }
                document.querySelectorAll('.customer-time-row').forEach(function (r) { r.classList.remove('selected'); });
                this.classList.add('selected');
                var slotIdInput = document.getElementById('customer-booking-slot-id');
                if (slotIdInput) slotIdInput.value = slot.id;
                setBookingStep(3);
              });
              body.appendChild(tr);
            });
          } else if (empty) empty.style.display = '';
        })
        .catch(function () {
          if (loading) loading.style.display = 'none';
          if (wrap) wrap.style.display = '';
          alert('Không thể tải khung giờ.');
        });
    }

    function formatDateDisplay(dateStr) {
      if (!dateStr) return '';
      var parts = dateStr.split('-');
      if (parts.length === 3) return parts[2] + '/' + parts[1] + '/' + parts[0];
      return dateStr;
    }

    var prevBtn = document.getElementById('customer-calendar-prev');
    var nextBtn = document.getElementById('customer-calendar-next');
    if (prevBtn) prevBtn.addEventListener('click', function () {
      currentCalendarMonth--;
      if (currentCalendarMonth < 0) { currentCalendarMonth = 11; currentCalendarYear--; }
      renderCalendar();
    });
    if (nextBtn) nextBtn.addEventListener('click', function () {
      currentCalendarMonth++;
      if (currentCalendarMonth > 11) { currentCalendarMonth = 0; currentCalendarYear++; }
      renderCalendar();
    });

    function setBookingStep(step) {
      document.querySelectorAll('.customer-stepper-step').forEach(function (s) {
        s.classList.toggle('active', parseInt(s.getAttribute('data-step'), 10) === step);
      });
      document.querySelectorAll('.customer-stepper-label').forEach(function (l) {
        l.style.display = parseInt(l.getAttribute('data-step-label'), 10) === step ? '' : 'none';
      });
      for (var i = 1; i <= 4; i++) {
        var el = document.getElementById('customer-booking-step-' + i);
        if (el) el.style.display = step === i ? '' : 'none';
      }
    }

    document.querySelectorAll('[data-back-step]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        setBookingStep(parseInt(btn.getAttribute('data-back-step'), 10));
      });
    });

    var submitBtn = document.getElementById('customer-booking-submit');
    if (submitBtn) {
      submitBtn.addEventListener('click', function () {
        var slotId = (document.getElementById('customer-booking-slot-id') || {}).value;
        var serviceId = (document.getElementById('customer-booking-service') || {}).value;
        var contactChannel = (document.getElementById('customer-booking-contact-channel') || {}).value;
        var contactValue = (document.getElementById('customer-booking-contact-value') || {}).value.trim();
        var note = (document.getElementById('customer-booking-note') || {}).value;
        if (!slotId || !serviceId || !contactChannel || !contactValue) {
          alert('Vui lòng điền đầy đủ thông tin.');
          return;
        }
        submitBtn.disabled = true;
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
            if (r.status === 401) { alert('Bạn cần đăng nhập.'); submitBtn.disabled = false; return null; }
            if (!r.ok) return r.json().then(function (e) { throw new Error(e.error || 'Lỗi'); });
            return r.json();
          })
          .then(function (data) {
            submitBtn.disabled = false;
            if (!data) return;
            window.__lastCreatedAppointmentId = data.id;
            var summaryEl = document.getElementById('customer-success-summary');
            if (summaryEl) {
              var dateStr = data.date ? formatDateDisplay(data.date) : '';
              var timeStr = data.startTime && data.endTime ? formatTime(data.startTime) + ' - ' + formatTime(data.endTime) : (data.startTime ? formatTime(data.startTime) : '');
              summaryEl.innerHTML = '<p class="customer-success-summary-title">Thông tin lịch vừa đặt:</p>' +
                '<p><strong>Dịch vụ:</strong> ' + (data.serviceName || '—') + '</p>' +
                '<p><strong>Bác sĩ:</strong> ' + (data.dentistName || 'Sẽ được gán sau') + '</p>' +
                '<p><strong>Ngày:</strong> ' + dateStr + '</p>' +
                '<p><strong>Giờ:</strong> ' + timeStr + '</p>';
            }
            setBookingStep(4);
            var openLink = document.getElementById('customer-booking-open-appointments');
            if (openLink) openLink.href = '/customer/my-appointments#highlight=' + data.id;
          })
          .catch(function (err) {
            submitBtn.disabled = false;
            alert(err.message || 'Đặt lịch thất bại.');
          });
      });
    }

    function formatTime(t) {
      if (!t) return '';
      var s = String(t);
      if (s.length >= 5) return s.substring(0, 5);
      return s;
    }

    renderCalendar();
  }

  // Initialize when DOM is ready
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initCalendar);
  } else {
    initCalendar();
  }
})();

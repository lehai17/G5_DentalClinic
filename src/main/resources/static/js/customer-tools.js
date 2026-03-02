(function () {
  'use strict';

  function openModal(id) {
    var el = document.getElementById(id);
    if (el) {
      el.classList.add('is-open');
      el.setAttribute('aria-hidden', 'false');
    }
  }

  function closeModal(id) {
    var el = document.getElementById(id);
    if (el) {
      el.classList.remove('is-open');
      el.setAttribute('aria-hidden', 'true');
    }
  }

  document.querySelectorAll('[data-close]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var target = btn.getAttribute('data-close');
      if (target) closeModal(target);
    });
  });

  document.querySelectorAll('.customer-modal-backdrop').forEach(function (backdrop) {
    backdrop.addEventListener('click', function () {
      var modal = backdrop.closest('.customer-modal');
      if (modal && modal.id) closeModal(modal.id);
    });
  });

  document.querySelectorAll('.customer-menu-item[data-action="book"]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openModal('customer-booking-modal');
      resetBookingStepper();
    });
  });

  document.querySelectorAll('.customer-menu-item[data-action="appointments"]').forEach(function (btn) {
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      openModal('customer-appointments-modal');
      loadAppointments();
    });
  });

  var currentCalendarYear = new Date().getFullYear();
  var currentCalendarMonth = new Date().getMonth();

  function renderCalendar() {
    var tbody = document.getElementById('customer-calendar-body');
    var label = document.getElementById('customer-calendar-month-label');
    if (!tbody || !label) return;
    var d = new Date(currentCalendarYear, currentCalendarMonth, 1);
    var lastDay = new Date(currentCalendarYear, currentCalendarMonth + 1, 0).getDate();
    var firstDow = d.getDay();
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var monthNames = ['Tháng 1', 'Tháng 2', 'Tháng 3', 'Tháng 4', 'Tháng 5', 'Tháng 6', 'Tháng 7', 'Tháng 8', 'Tháng 9', 'Tháng 10', 'Tháng 11', 'Tháng 12'];
    label.textContent = monthNames[currentCalendarMonth] + ' / ' + currentCalendarYear;
    tbody.innerHTML = '';
    var day = 1;
    var done = false;
    for (var row = 0; row < 6 && !done; row++) {
      var tr = document.createElement('tr');
      for (var col = 0; col < 7; col++) {
        var td = document.createElement('td');
        if (row === 0 && col < firstDow) {
          td.className = 'customer-calendar-cell customer-calendar-cell--empty';
        } else if (day <= lastDay) {
          td.className = 'customer-calendar-cell customer-calendar-cell--day';
          td.textContent = day;
          td.dataset.day = day;
          td.dataset.date = currentCalendarYear + '-' + String(currentCalendarMonth + 1).padStart(2, '0') + '-' + String(day).padStart(2, '0');
          var cellDate = new Date(currentCalendarYear, currentCalendarMonth, day);
          cellDate.setHours(0, 0, 0, 0);
          if (cellDate < today) td.classList.add('customer-calendar-cell--past');
          else {
            td.addEventListener('click', function () {
              var serviceEl = document.getElementById('customer-booking-service');
              if (!serviceEl || !serviceEl.value) {
                alert('Vui lòng chọn dịch vụ trước.');
                return;
              }
              var dateStr = this.dataset.date;
              window.__bookingSelectedDate = dateStr;
              loadSlotsForDate(dateStr);
            });
          }
          day++;
        } else {
          td.className = 'customer-calendar-cell customer-calendar-cell--empty';
          done = true;
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
    document.getElementById('customer-selected-date-label').textContent = formatDateDisplay(dateStr);
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
        if (Array.isArray(data) && data.length > 0) {
          data.forEach(function (slot) {
            var tr = document.createElement('tr');
            tr.className = 'customer-time-row';
            tr.dataset.slotId = slot.id;
            // Display available spots - show "Trống" if available, or specific count
            var availableText = 'Trống';
            if (slot.availableSpots !== undefined && slot.availableSpots !== null && slot.availableSpots > 0) {
              availableText = 'Còn ' + slot.availableSpots + ' chỗ';
            } else if (slot.availableSpots === 0 || slot.available === false) {
              availableText = 'Hết chỗ';
            }
            tr.innerHTML = '<td>' + formatTime(slot.startTime) + ' - ' + formatTime(slot.endTime) + '</td><td>' + availableText + '</td>';
            tr.addEventListener('click', function () {
              // Only allow selection if slot is available
              if (slot.availableSpots === 0 || slot.available === false) {
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

  function resetBookingStepper() {
    currentCalendarYear = new Date().getFullYear();
    currentCalendarMonth = new Date().getMonth();
    renderCalendar();
    setBookingStep(1);
    var slotIdEl = document.getElementById('customer-booking-slot-id');
    if (slotIdEl) slotIdEl.value = '';
    var ch = document.getElementById('customer-booking-contact-channel');
    if (ch) ch.value = '';
    var cv = document.getElementById('customer-booking-contact-value');
    if (cv) cv.value = '';
    var note = document.getElementById('customer-booking-note');
    if (note) note.value = '';
    window.__bookingSelectedDate = null;
    var s2 = document.getElementById('customer-booking-step-2');
    if (s2) s2.style.display = 'none';
    var s3 = document.getElementById('customer-booking-step-3');
    if (s3) s3.style.display = 'none';
    var s4 = document.getElementById('customer-booking-step-4');
    if (s4) s4.style.display = 'none';
    var s5 = document.getElementById('customer-booking-step-5');
    if (s5) s5.style.display = 'none';
  }

  function setBookingStep(step) {
    document.querySelectorAll('.customer-stepper-step').forEach(function (s) {
      s.classList.toggle('active', parseInt(s.getAttribute('data-step'), 10) === step);
    });
    document.querySelectorAll('.customer-stepper-label').forEach(function (l) {
      l.style.display = parseInt(l.getAttribute('data-step-label'), 10) === step ? '' : 'none';
    });
    for (var i = 1; i <= 5; i++) {
      var el = document.getElementById('customer-booking-step-' + i);
      if (el) el.style.display = step === i ? '' : 'none';
    }
  }

  document.querySelectorAll('[data-back-step]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      setBookingStep(parseInt(btn.getAttribute('data-back-step'), 10));
    });
  });

  var toDepositBtn = document.getElementById('customer-booking-to-deposit');
  if (toDepositBtn) toDepositBtn.addEventListener('click', function () {
    var ch = (document.getElementById('customer-booking-contact-channel') || {}).value;
    var cv = (document.getElementById('customer-booking-contact-value') || {}).value.trim();
    if (!ch || !cv) { alert('Vui lòng điền đầy đủ thông tin liên hệ.'); return; }
    setBookingStep(4);
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
          dentistId: null,
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
          setBookingStep(5);
        })
        .catch(function (err) {
          submitBtn.disabled = false;
          alert(err.message || 'Đặt lịch thất bại.');
        });
    });
  }

  var openAppointmentsBtn = document.getElementById('customer-booking-open-appointments');
  if (openAppointmentsBtn) {
    openAppointmentsBtn.addEventListener('click', function () {
      closeModal('customer-booking-modal');
      openModal('customer-appointments-modal');
      loadAppointments(true);
    });
  }

  function loadAppointments(highlightNew) {
    var listWrap = document.getElementById('customer-appointments-list-wrap');
    var list = document.getElementById('customer-appointments-list');
    var empty = document.getElementById('customer-appointments-empty');
    var loading = document.getElementById('customer-appointments-loading');
    if (listWrap) listWrap.style.display = 'none';
    if (empty) empty.style.display = 'none';
    if (list) list.innerHTML = '';
    if (loading) loading.style.display = '';
    fetch('/customer/appointments', { credentials: 'same-origin' })
      .then(function (r) {
        if (r.status === 401) {
          alert('Bạn cần đăng nhập để xem lịch hẹn.');
          if (loading) loading.style.display = 'none';
          return null;
        }
        return r.json();
      })
      .then(function (data) {
        if (loading) loading.style.display = 'none';
        if (listWrap) listWrap.style.display = '';
        if (!data) return;
        if (Array.isArray(data) && data.length > 0 && list) {
          data.forEach(function (apt) {
            var li = document.createElement('li');
            li.dataset.appointmentId = apt.id;
            if (highlightNew && window.__lastCreatedAppointmentId === apt.id) li.classList.add('highlight-new');
            li.innerHTML = '<span class="apt-date">' + formatDate(apt.date) + ' ' + formatTime(apt.startTime) + '</span><br><span class="apt-service">' + (apt.serviceName || '') + '</span><br><span class="apt-status">' + (apt.status || '') + '</span>';
            li.addEventListener('click', function () { openAppointmentDetail(apt.id); });
            list.appendChild(li);
          });
        } else if (empty) empty.style.display = '';
      })
      .catch(function () {
        if (loading) loading.style.display = 'none';
        if (listWrap) listWrap.style.display = '';
        alert('Không thể tải danh sách lịch hẹn.');
      });
  }

  function openAppointmentDetail(id) {
    openModal('customer-appointment-detail-modal');
    var content = document.getElementById('customer-detail-content');
    var loading = document.getElementById('customer-detail-loading');
    if (content) content.style.display = 'none';
    if (loading) loading.style.display = '';
    fetch('/customer/appointments/' + id, { credentials: 'same-origin' })
      .then(function (r) {
        if (r.status === 401) { alert('Bạn cần đăng nhập.'); if (loading) loading.style.display = 'none'; return null; }
        if (r.status === 404) { if (loading) loading.style.display = 'none'; return null; }
        return r.json();
      })
      .then(function (data) {
        if (loading) loading.style.display = 'none';
        if (content) content.style.display = '';
        if (!data) return;
        var set = function (id, text) { var el = document.getElementById(id); if (el) el.textContent = text; };
        set('customer-detail-service', data.serviceName || '');
        set('customer-detail-dentist', data.dentistName || '');
        set('customer-detail-date', formatDate(data.date));
        set('customer-detail-time', formatTime(data.startTime) + ' - ' + formatTime(data.endTime));
        set('customer-detail-status', data.status || '');
        set('customer-detail-contact', (data.contactChannel || '') + ': ' + (data.contactValue || ''));
        set('customer-detail-notes', data.notes || '—');
        var checkinWrap = document.getElementById('customer-detail-checkin-wrap');
        var checkinBtn = document.getElementById('customer-detail-checkin-btn');
        if (data.canCheckIn && checkinWrap && checkinBtn) {
          checkinWrap.style.display = '';
          checkinBtn.onclick = function () {
            checkinBtn.disabled = true;
            fetch('/customer/appointments/' + id + '/checkin', { method: 'POST', credentials: 'same-origin' })
              .then(function (res) {
                if (res.status === 401) { alert('Bạn cần đăng nhập.'); checkinBtn.disabled = false; return null; }
                if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || 'Check-in thất bại'); });
                return res.json();
              })
              .then(function (updated) {
                checkinBtn.disabled = false;
                if (updated) {
                  set('customer-detail-status', updated.status || 'CHECKED_IN');
                  checkinWrap.style.display = 'none';
                  loadAppointments();
                }
              })
              .catch(function (err) {
                checkinBtn.disabled = false;
                alert(err.message || 'Check-in thất bại.');
              });
          };
        } else if (checkinWrap) checkinWrap.style.display = 'none';
      })
      .catch(function () {
        if (loading) loading.style.display = 'none';
        alert('Không thể tải chi tiết.');
      });
  }

  function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    return d.getDate() + '/' + (d.getMonth() + 1) + '/' + d.getFullYear();
  }

  function formatTime(t) {
    if (!t) return '';
    var s = String(t);
    if (s.length >= 5) return s.substring(0, 5);
    return s;
  }

  if (document.getElementById('customer-calendar-body')) renderCalendar();
})();

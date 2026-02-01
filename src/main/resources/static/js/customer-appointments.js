(function () {
  'use strict';

  if (!document.getElementById('customer-appointments-list')) return;

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
            li.addEventListener('click', function () { showAppointmentDetail(apt.id); });
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

  function showAppointmentDetail(id) {
    var panel = document.getElementById('customer-detail-panel');
    var content = document.getElementById('customer-detail-content');
    var loading = document.getElementById('customer-detail-loading');
    if (panel) panel.style.display = '';
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

  var hash = window.location.hash;
  if (hash && hash.indexOf('highlight=') !== -1) {
    var m = hash.match(/highlight=(\d+)/);
    if (m) window.__lastCreatedAppointmentId = parseInt(m[1], 10);
  }
  loadAppointments(true);
})();

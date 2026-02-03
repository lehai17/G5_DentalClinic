(function () {
  'use strict';

  var listEl = document.getElementById('customer-appointments-list');
  if (!listEl) return;

  var currentOpen = {
    appointmentId: null,
    detailEl: null
  };

  function formatDate(dateStr) {
    if (!dateStr) return '';
    var d = new Date(dateStr);
    return d.getDate() + '/' + (d.getMonth() + 1) + '/' + d.getFullYear();
  }

  function formatTime(t) {
    if (!t) return '';
    var s = String(t);
    return s.length >= 5 ? s.substring(0, 5) : s;
  }

  function escapeHtml(s) {
    if (s == null) return '';
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
  }

  function closeCurrentDetail() {
    if (currentOpen.detailEl && currentOpen.detailEl.parentNode) {
      currentOpen.detailEl.parentNode.removeChild(currentOpen.detailEl);
    }
    currentOpen.appointmentId = null;
    currentOpen.detailEl = null;

    document.querySelectorAll('#customer-appointments-list li.cap-item-active')
        .forEach(function (li) { li.classList.remove('cap-item-active'); });
  }

  function createInlineDetailShell() {
    var wrap = document.createElement('div');
    wrap.className = 'cap-inline-detail';

    wrap.innerHTML = ''
        + '<div class="cap-inline-detail-card">'
        + '  <div class="cap-inline-head">'
        + '    <div class="cap-inline-title"><i class="bi bi-info-circle"></i> Chi tiết lịch hẹn</div>'
        + '    <button type="button" class="cap-inline-close" aria-label="Đóng">'
        + '      <i class="bi bi-x-lg"></i>'
        + '    </button>'
        + '  </div>'
        + '  <div class="cap-inline-loading"><span class="cap-spinner" aria-hidden="true"></span> Đang tải chi tiết...</div>'
        + '  <div class="cap-inline-content" style="display:none;"></div>'
        + '</div>';

    wrap.querySelector('.cap-inline-close').addEventListener('click', function (e) {
      e.stopPropagation();
      closeCurrentDetail();
    });

    return wrap;
  }

  function renderInlineDetail(detailWrap, data, appointmentId) {
    var content = detailWrap.querySelector('.cap-inline-content');
    var loading = detailWrap.querySelector('.cap-inline-loading');

    if (loading) loading.style.display = 'none';
    if (content) content.style.display = '';

    var dentistHtml = data.dentistName ? escapeHtml(data.dentistName) : '<span class="cap-muted">—</span>';
    var notesHtml = data.notes ? escapeHtml(data.notes) : '<span class="cap-muted">—</span>';

    var canCancel = (data.status !== 'CANCELLED' && data.status !== 'COMPLETED');
    var canCheckin = !!data.canCheckIn;

    content.innerHTML = ''
        + '<div class="cap-inline-grid">'
        + '  <div class="cap-inline-row"><div class="cap-inline-label">Dịch vụ</div><div class="cap-inline-value">' + escapeHtml(data.serviceName || '') + '</div></div>'
        + '  <div class="cap-inline-row"><div class="cap-inline-label">Bác sĩ</div><div class="cap-inline-value">' + dentistHtml + '</div></div>'
        + '  <div class="cap-inline-row"><div class="cap-inline-label">Ngày</div><div class="cap-inline-value">' + escapeHtml(formatDate(data.date)) + '</div></div>'
        + '  <div class="cap-inline-row"><div class="cap-inline-label">Giờ</div><div class="cap-inline-value">' + escapeHtml(formatTime(data.startTime)) + ' - ' + escapeHtml(formatTime(data.endTime)) + '</div></div>'
        + '  <div class="cap-inline-row"><div class="cap-inline-label">Trạng thái</div><div class="cap-inline-value"><span class="cap-status-badge">' + escapeHtml(data.status || '') + '</span></div></div>'
        + '  <div class="cap-inline-row"><div class="cap-inline-label">Liên hệ</div><div class="cap-inline-value">' + escapeHtml((data.contactChannel || '') + ': ' + (data.contactValue || '')) + '</div></div>'
        + '  <div class="cap-inline-row cap-inline-notes"><div class="cap-inline-label">Ghi chú</div><div class="cap-inline-value">' + notesHtml + '</div></div>'
        + '</div>'
        + '<div class="cap-inline-actions">'
        + (canCheckin ? '<button type="button" class="cap-btn cap-btn-primary" data-action="checkin"><i class="bi bi-check2-circle"></i> Check-in online</button>' : '')
        + (canCancel ? '<button type="button" class="cap-btn cap-btn-danger" data-action="cancel"><i class="bi bi-x-circle"></i> Hủy lịch</button>' : '')
        + '</div>';

    var checkinBtn = content.querySelector('[data-action="checkin"]');
    if (checkinBtn) {
      checkinBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        checkinBtn.disabled = true;

        fetch('/customer/appointments/' + appointmentId + '/checkin', {
          method: 'POST',
          credentials: 'same-origin'
        })
            .then(function (res) {
              if (res.status === 401) { alert('Bạn cần đăng nhập.'); checkinBtn.disabled = false; return null; }
              if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || 'Check-in thất bại'); });
              return res.json();
            })
            .then(function () {
              checkinBtn.disabled = false;
              // refresh list & reopen same detail
              loadAppointments(function () { openInlineDetail(appointmentId, true); });
            })
            .catch(function (err) {
              checkinBtn.disabled = false;
              alert(err.message || 'Check-in thất bại.');
            });
      });
    }

    var cancelBtn = content.querySelector('[data-action="cancel"]');
    if (cancelBtn) {
      cancelBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        if (!confirm('Bạn có chắc chắn muốn hủy lịch hẹn này không?')) return;

        cancelBtn.disabled = true;

        fetch('/customer/appointments/' + appointmentId + '/cancel', {
          method: 'POST',
          credentials: 'same-origin'
        })
            .then(function (res) {
              if (res.status === 401) { alert('Bạn cần đăng nhập.'); cancelBtn.disabled = false; return null; }
              if (!res.ok) return res.json().then(function (e) { throw new Error(e.error || 'Hủy lịch thất bại'); });
              return res.json();
            })
            .then(function () {
              cancelBtn.disabled = false;
              loadAppointments(function () { openInlineDetail(appointmentId, true); });
            })
            .catch(function (err) {
              cancelBtn.disabled = false;
              alert(err.message || 'Hủy lịch thất bại.');
            });
      });
    }
  }

  function openInlineDetail(appointmentId, keepOpenAfterReload) {
    // Toggle close if clicking same item
    if (!keepOpenAfterReload && currentOpen.appointmentId === appointmentId) {
      closeCurrentDetail();
      return;
    }

    closeCurrentDetail();

    var li = listEl.querySelector('li[data-appointment-id="' + appointmentId + '"]');
    if (!li) return;

    li.classList.add('cap-item-active');

    var detailWrap = createInlineDetailShell();
    li.insertAdjacentElement('afterend', detailWrap);

    currentOpen.appointmentId = appointmentId;
    currentOpen.detailEl = detailWrap;

    fetch('/customer/appointments/' + appointmentId, { credentials: 'same-origin' })
        .then(function (r) {
          if (r.status === 401) { alert('Bạn cần đăng nhập.'); return null; }
          if (r.status === 404) throw new Error('Không tìm thấy lịch hẹn.');
          if (!r.ok) return r.json().then(function (e) { throw new Error(e.error || 'Không thể tải chi tiết.'); });
          return r.json();
        })
        .then(function (data) {
          if (!data) return;
          renderInlineDetail(detailWrap, data, appointmentId);

          // scroll nhẹ cho user thấy detail
          detailWrap.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
        })
        .catch(function (err) {
          var loading = detailWrap.querySelector('.cap-inline-loading');
          if (loading) loading.textContent = err.message || 'Không thể tải chi tiết.';
        });
  }

  function loadAppointments(doneCb) {
    var listWrap = document.getElementById('customer-appointments-list-wrap');
    var empty = document.getElementById('customer-appointments-empty');
    var loading = document.getElementById('customer-appointments-loading');

    if (listWrap) listWrap.style.display = 'none';
    if (empty) empty.style.display = 'none';
    listEl.innerHTML = '';
    if (loading) loading.style.display = '';

    closeCurrentDetail();

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

          if (Array.isArray(data) && data.length > 0) {
            data.forEach(function (apt) {
              var li = document.createElement('li');
              li.dataset.appointmentId = apt.id;
              li.setAttribute('data-appointment-id', apt.id);

              if (window.__lastCreatedAppointmentId && window.__lastCreatedAppointmentId === apt.id) {
                li.classList.add('highlight-new');
              }

              li.innerHTML = ''
                  + '<div class="cap-item-row">'
                  + '  <div class="cap-item-main">'
                  + '    <div class="apt-date"><i class="bi bi-clock"></i> ' + escapeHtml(formatDate(apt.date)) + ' • ' + escapeHtml(formatTime(apt.startTime)) + '</div>'
                  + '    <div class="apt-service">' + escapeHtml(apt.serviceName || '') + '</div>'
                  + '  </div>'
                  + '  <div class="cap-item-side">'
                  + '    <span class="apt-status">' + escapeHtml(apt.status || '') + '</span>'
                  + '    <i class="bi bi-chevron-down cap-item-chevron"></i>'
                  + '  </div>'
                  + '</div>';

              li.addEventListener('click', function () {
                openInlineDetail(apt.id, false);
              });

              listEl.appendChild(li);
            });
          } else if (empty) {
            empty.style.display = '';
          }

          if (typeof doneCb === 'function') doneCb();
        })
        .catch(function () {
          if (loading) loading.style.display = 'none';
          if (listWrap) listWrap.style.display = '';
          alert('Không thể tải danh sách lịch hẹn.');
        });
  }

  // Optional: highlight created appointment
  var hash = window.location.hash;
  if (hash && hash.indexOf('highlight=') !== -1) {
    var m = hash.match(/highlight=(\d+)/);
    if (m) window.__lastCreatedAppointmentId = parseInt(m[1], 10);
  }

  loadAppointments();
})();

const STATE = {
  selectedDate: document.getElementById('selectedDateValue')?.value,
  currentMonth: document.getElementById('currentMonthValue')?.value,
  isDateSelected: document.getElementById('isDateSelected')?.value === 'true'
};

const AdminSlots = {
  init() {
    console.log('AdminSlots Initializing...', STATE);
    if (STATE.isDateSelected) {
      this.hydrateAgenda();
    } else {
      this.hydrateCalendar();
    }
  },

  async hydrateCalendar() {
    const tbody = document.getElementById('calendarBody');
    if (!tbody) return;

    try {
      const resp = await fetch(`/admin/slots/api/calendar?month=${STATE.currentMonth}`);
      if (!resp.ok) throw new Error('API partial failure');
      const badgeMap = await resp.json();

      this.renderCalendar(badgeMap);
    } catch (err) {
      console.error('Hydration Error:', err);
      tbody.innerHTML = '<tr><td colspan="7" style="padding:40px; text-align:center; color:red;">Lỗi tải dữ liệu. Vui lòng thử lại.</td></tr>';
    }
  },

  renderCalendar(badgeMap) {
    const tbody = document.getElementById('calendarBody');
    const [year, month] = STATE.currentMonth.split('-').map(Number);

    let date = new Date(year, month - 1, 1);
    let diff = date.getDay();
    if (diff === 0) diff = 7;
    date.setDate(date.getDate() - (diff - 1));

    let html = '';
    const now = new Date();
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;

    for (let w = 0; w < 6; w++) {
      html += '<tr>';
      for (let d = 0; d < 7; d++) {
        const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
        const isCurrentMonth = date.getMonth() === month - 1;
        const isToday = dateStr === todayStr;
        const badge = badgeMap[dateStr];

        let dotsHtml = '';
        if (badge && badge.capacity > 0) {
          const status = badge.densityStatus || 'GREEN';
          let colorClass = 'available';
          if (status === 'RED') colorClass = 'full';
          else if (status === 'YELLOW') colorClass = 'busy';
          dotsHtml = `<div class="dots-container"><div class="dot ${colorClass}"></div></div>`;
        }

        // Lấy trạng thái mặc định từ DB
        let isActive = !badge || badge.active !== false;


        const lockIcon = isActive ? 'fa-lock-open' : 'fa-lock';
        const lockClass = isActive ? 'unlocked' : 'locked';
        const lockTitle = isActive ? 'Khóa ngày này' : 'Mở khóa ngày này';

        let statusTagHtml = '';
        if (!isActive) {
          statusTagHtml = '<div class="status-tag closed">ĐÓNG</div>';
        } else if (!dotsHtml) {
          statusTagHtml = '<div class="status-tag open">MỞ</div>';
        }

        html += `
          <td class="calendar-cell ${!isCurrentMonth ? 'other-month' : ''} ${isToday ? 'today' : ''}"
              onclick="if(!event.target.closest('.lock-btn')) location.href='/admin/slots?date=${dateStr}'">
            <div class="day-num">${date.getDate()}</div>
            <div class="lock-btn ${lockClass}" title="${lockTitle}"
                 onclick="AdminSlots.quickToggleLock(event, '${dateStr}', ${isActive})">
              <i class="fa-solid ${lockIcon}"></i>
            </div>
            ${statusTagHtml}
            ${dotsHtml}
          </td>`;

        date.setDate(date.getDate() + 1);
      }
      html += '</tr>';
      if (date.getMonth() !== month - 1 && date.getDay() === 1) break;
    }
    tbody.innerHTML = html;
  },

  quickToggleLock(event, date, isActive) {
    event.stopPropagation();
    if (isActive) {
      this.lockDay(date);
    } else {
      this.unlockDay(date);
    }
  },

  async hydrateAgenda() {
    const wrapper = document.getElementById('agendaWrapper');
    const filter = document.getElementById('dentistFilter');
    if (!wrapper) return;

    const dentistId = filter ? filter.value : '';

    console.time('hydrateAgenda');
    try {
      console.log(`Đang tải lịch hẹn cho ngày ${STATE.selectedDate}...`);
      const resp = await fetch(`/admin/slots/api/agenda?date=${STATE.selectedDate}&dentistId=${dentistId}`);
      if (!resp.ok) throw new Error(`Lỗi HTTP: ${resp.status}`);

      let items = await resp.json();
      items = items.filter(item => item.status !== 'AVAILABLE');

      console.log(`Đã tải ${items.length} hạng mục hợp lệ.`);

      if (items.length === 0) {
        wrapper.innerHTML = '<div style="padding:40px; text-align:center; color:#64748b;">Trống</div>';
        return;
      }

      const statusMap = {
        PENDING: 'CHỜ XÁC NHẬN',
        CONFIRMED: 'ĐÃ XÁC NHẬN',
        CHECKED_IN: 'ĐÃ ĐẾN',
        EXAMINING: 'ĐANG KHÁM',
        WAITING_PAYMENT: 'CHO THANH TOÁN',
        COMPLETED: 'HOÀN THÀNH',
        CANCELLED: 'ĐÃ HỦY',
        REEXAM: 'HẸN TÁI KHÁM'
      };

      let html = '';
      items.forEach(item => {
        const displayStatus = statusMap[item.status] || item.status;

        html += `
          <div class="agenda-item" onclick="AdminSlots.showDetail('${item.appointmentId}')"
               style="cursor:pointer;">
            <div class="appt-info" style="padding:16px;">
              <div class="patient-name" style="font-weight:700; font-size:16px; color:#1e293b; margin-bottom:4px;">
                ${item.customerName || 'Bệnh nhân'}
              </div>
              <div style="font-size:13px; color:#64748b;">
                <i class="fa-solid fa-briefcase-medical" style="width:16px;"></i> ${item.serviceName || 'Dịch vụ'}
                <span style="margin:0 8px; opacity:0.3;">|</span>
                <i class="fa-solid fa-user-doctor" style="width:16px;"></i> BS: ${item.dentistName || 'N/A'}
              </div>
            </div>
            <div style="text-align:right; padding:16px;">
              <span class="time-tag">
                <i class="fa-regular fa-clock"></i> ${item.startTime} - ${item.endTime}
              </span>
              <div style="font-size:11px; margin-top:8px; font-weight:800; color:#475569;">${displayStatus}</div>
            </div>
          </div>`;
      });
      wrapper.innerHTML = html;
    } catch (err) {
      console.error('Hydrate Agenda Error:', err);
      wrapper.innerHTML = `
        <div style="padding:40px; text-align:center; color:#dc2626;">
          <i class="fa-solid fa-circle-exclamation" style="font-size:24px; margin-bottom:10px;"></i><br>
          Lỗi tải lịch hẹn: ${err.message}
        </div>`;
    } finally {
      console.timeEnd('hydrateAgenda');
    }
  },

  async showDetail(id) {
    try {
      const resp = await fetch(`/admin/slots/api/appointment/${id}`);
      const data = await resp.json();

      document.getElementById('mPatientName').innerText = data.customerName || 'N/A';
      document.getElementById('mServiceInfo').innerText = data.serviceName || 'N/A';
      document.getElementById('mStatusValue').innerText = data.status || 'N/A';
      document.getElementById('mTimeValue').innerText = `${data.startTime} - ${data.endTime}`;
      document.getElementById('mDentistName').innerText = data.dentistName || 'N/A';

      this.openModal('#apptModalBackdrop');
    } catch (e) {
      alert('Không thể tải chi tiết lịch hẹn.');
    }
  },

  openModal(selector) {
    const el = document.querySelector(selector);
    if (el) el.style.display = 'flex';
  },

  closeModal(selector) {
    const el = document.querySelector(selector);
    if (el) el.style.display = 'none';
  },

  async lockDay(date) {
    const reason = prompt(`Nhập lý do KHÓA ngày ${date}:`, 'Bảo trì / Nghỉ lễ');
    if (reason === null) return;

    try {
      const resp = await fetch(`/admin/slots/api/lock-day?date=${date}&reason=${encodeURIComponent(reason)}`, {
        method: 'POST'
      });
      const data = await resp.json();

      if (!resp.ok) {
        alert(`THẤT BẠI: ${data.error || 'Không thể khóa ngày này.'}`);
        return;
      }

      alert(`Đã KHÓA ngày ${date}`);
      if (STATE.isDateSelected) {
        location.reload();
      } else {
        await this.hydrateCalendar();
      }
    } catch (err) {
      console.error(err);
      alert('Lỗi kết nối máy chủ.');
    }
  },

  async unlockDay(date) {
    if (confirm(`Bạn có chắc muốn MỞ KHÓA toàn bộ ngày ${date}?`)) {
      try {
        const resp = await fetch(`/admin/slots/api/unlock-day?date=${date}`, {
          method: 'POST'
        });
        const data = await resp.json();

        if (!resp.ok) {
          alert(`THẤT BẠI: ${data.error || 'Không thể mở khóa.'}`);
          return;
        }

        alert(`Đã MỞ KHÓA ngày ${date}`);
        if (STATE.isDateSelected) {
          location.reload();
        } else {
          await this.hydrateCalendar();
        }
      } catch (err) {
        console.error(err);
        alert('Lỗi kết nối máy chủ.');
      }
    }
  },

  async generateMonthly() {
    const monthStr = prompt('Nhập Tháng/Năm để sinh lịch làm việc tự động (Định dạng: YYYY-MM)\n(Lưu ý: Hệ thống sẽ tự tạo Lịch phòng khám VÀ Lịch làm việc cho TẤT CẢ bác sĩ đang Active)', STATE.currentMonth);
    if (!monthStr) return;
    if (!monthStr.match(/^\d{4}-\d{2}$/)) {
      alert('Định dạng không hợp lệ. Vui lòng nhập đúng YYYY-MM (Ví dụ: 2026-05).');
      return;
    }
    if (confirm(`Xác nhận TẠO TỰ ĐỘNG toàn bộ lịch cho tháng ${monthStr}?`)) {
      try {
        const resp = await fetch(`/admin/slots/api/generate-monthly?month=${monthStr}`, { method: 'POST' });
        const data = await resp.json();

        if (!resp.ok) {
          alert(`THẤT BẠI: ${data.error || 'Lỗi server'}`);
          return;
        }

        alert(`THÀNH CÔNG: ${data.message}`);
        location.href = `/admin/slots?month=${monthStr}`;
      } catch (err) {
        console.error(err);
        alert('Lỗi kết nối máy chủ.');
      }
    }
  },

  async deleteMonthly() {
    const monthStr = prompt('Nhập Tháng/Năm để HỦY lịch làm việc (Định dạng: YYYY-MM)\n(Lưu ý: Hệ thống chỉ cho phép hủy nếu CHƯA có bất kỳ lịch hẹn nào trong tháng này)', STATE.currentMonth);
    if (!monthStr) return;
    if (!monthStr.match(/^\d{4}-\d{2}$/)) {
      alert('Định dạng không hợp lệ. Vui lòng nhập đúng YYYY-MM (Ví dụ: 2026-05).');
      return;
    }
    if (confirm(`Xác nhận HỦY TOÀN BỘ lịch cho tháng ${monthStr}?\nHành động này sẽ xóa sạch Slots và Lịch làm việc bác sĩ!`)) {
      try {
        const resp = await fetch(`/admin/slots/api/delete-monthly?month=${monthStr}`, { method: 'POST' });
        const data = await resp.json();

        if (!resp.ok) {
          alert(`THẤT BẠI: ${data.error || 'Lỗi server'}`);
          return;
        }

        alert(`THÀNH CÔNG: ${data.message}`);
        location.href = `/admin/slots?month=${monthStr}`;
      } catch (err) {
        console.error(err);
        alert('Lỗi kết nối máy chủ.');
      }
    }
  }
};

document.addEventListener('DOMContentLoaded', () => AdminSlots.init());

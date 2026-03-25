document.addEventListener('DOMContentLoaded', function () {
    const backdrop = document.getElementById('apptModalBackdrop');
    const btnClose = document.getElementById('btnCloseModal');
    const title = document.getElementById('apptModalTitle');
    const sub = document.getElementById('apptModalSub');
    const statusBadge = document.getElementById('apptModalStatus');
    const timeValue = document.getElementById('apptModalTime');
    const dateValue = document.getElementById('apptModalDate');
    const noteWrap = document.getElementById('apptModalNoteWrap');
    const noteValue = document.getElementById('apptModalNote');
    const btnExam = document.getElementById('btnExamination');
    const btnReexam = document.getElementById('btnAAAAA');
    const btnBilling = document.getElementById('btnBillingTransfer');

    if (!backdrop) return;

    const statusMeta = {
        CONFIRMED: { label: 'Đã xác nhận', className: 'status-confirmed' },
        CHECKED_IN: { label: 'Đã check-in', className: 'status-checked-in' },
        EXAMINING: { label: 'Đang khám', className: 'status-examining' },
        WAITING_PAYMENT: { label: 'Chờ thanh toán', className: 'status-waiting-payment' },
        COMPLETED: { label: 'Đã hoàn tất', className: 'status-completed' }
    };

    function formatDate(dateStr) {
        if (!dateStr) return '--/--/----';
        const parts = dateStr.split('-');
        if (parts.length !== 3) return dateStr;
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }

    function applyStatusBadge(status) {
        if (!statusBadge) return;
        const meta = statusMeta[status] || { label: status || 'Chưa cập nhật', className: 'status-default' };
        statusBadge.textContent = meta.label;
        statusBadge.className = `status-pill ${meta.className}`;
    }

    function applyAppointmentNote(noteText) {
        if (!noteWrap || !noteValue) return;
        const normalized = (noteText || '').trim();
        if (!normalized) {
            noteWrap.hidden = true;
            noteValue.textContent = '';
            return;
        }
        noteValue.textContent = normalized;
        noteWrap.hidden = false;
    }

    function closeModal() {
        backdrop.hidden = true;
    }

    btnClose?.addEventListener('click', closeModal);
    backdrop.addEventListener('click', e => {
        if (e.target === backdrop) closeModal();
    });

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    document.querySelectorAll('.appt').forEach(el => {
        const status = el.dataset.status;
        if (status === 'CONFIRMED') {
            el.classList.add('appt-future');
            return;
        }

        const dateStr = el.dataset.date;
        if (!dateStr) return;

        const apptDate = new Date(dateStr);
        apptDate.setHours(0, 0, 0, 0);

        if (apptDate > today) {
            el.classList.add('appt-future');
        }
    });

    document.querySelectorAll('.appt').forEach(el => {
        el.addEventListener('click', () => {
            if (el.classList.contains('appt-future')) {
                return;
            }

            const status = el.dataset.status;
            if (status === 'CONFIRMED') {
                return;
            }

            const appointmentId = el.dataset.appointmentId;
            const customerUserId = el.dataset.customerUserId;
            const weekStart = document.getElementById('weekStartHidden')?.value || '';
            const startTime = el.dataset.startTime || '--:--';
            const endTime = el.dataset.endTime || '--:--';
            const dateStr = el.dataset.date || '';
            const noteText = el.dataset.note || '';

            title.textContent = el.dataset.patientName || 'Lịch hẹn';
            sub.textContent = el.dataset.serviceName || 'Không có thông tin dịch vụ';
            timeValue.textContent = `${startTime} - ${endTime}`;
            dateValue.textContent = formatDate(dateStr);
            applyStatusBadge(status);
            applyAppointmentNote(noteText);

            btnExam.href =
                `/dentist/appointments/${appointmentId}/examination`
                + `?customerUserId=${customerUserId}`
                + `&weekStart=${encodeURIComponent(weekStart)}`;

            const reexamAvailableStatuses = ['EXAMINING', 'COMPLETED', 'WAITING_PAYMENT'];
            if (reexamAvailableStatuses.includes(status)) {
                btnReexam.style.display = '';
                btnReexam.href =
                    `/dentist/reexam/${appointmentId}`
                    + `?weekStart=${encodeURIComponent(weekStart)}`;
            } else {
                btnReexam.style.display = 'none';
                btnReexam.removeAttribute('href');
            }

            btnBilling.href =
                `/dentist/appointments/${appointmentId}/billing-transfer`
                + `?customerUserId=${customerUserId}`
                + `&weekStart=${encodeURIComponent(weekStart)}`;

            if (status === 'CHECKED_IN') {
                btnBilling.classList.add('action-card-disabled');
                btnBilling.style.pointerEvents = 'none';
                btnBilling.title = 'Chỉ mở khi đang khám';
            } else {
                btnBilling.classList.remove('action-card-disabled');
                btnBilling.style.pointerEvents = '';
                btnBilling.title = '';
            }

            backdrop.hidden = false;
        });
    });

    const weekSelect = document.getElementById('weekSelect');
    weekSelect?.addEventListener('change', e => {
        const weekStart = e.target.value;
        window.location.href = `/dentist/work-schedule?weekStart=${encodeURIComponent(weekStart)}`;
    });
});

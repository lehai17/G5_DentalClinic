(function () {
    const sidebar = document.getElementById('sidebar');
    const toggle = document.getElementById('sidebarToggle');
    if (toggle && sidebar) {
        toggle.addEventListener('click', () => sidebar.classList.toggle('collapsed'));
    }

    const backdrop = document.getElementById('apptModalBackdrop');
    const btnClose = document.getElementById('btnCloseModal');
    const title = document.getElementById('apptModalTitle');
    const sub = document.getElementById('apptModalSub');
    const btnExam = document.getElementById('btnExamination');
    const btnBilling = document.getElementById('btnBillingTransfer');

    function closeModal() {
        if (!backdrop) return;
        backdrop.hidden = true;
    }

    if (btnClose) btnClose.addEventListener('click', closeModal);
    if (backdrop) backdrop.addEventListener('click', (e) => {
        if (e.target === backdrop) closeModal();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeModal();
    });

    // Click appointment → open modal with 2 links
    document.querySelectorAll('.appt').forEach(el => {
        el.addEventListener('click', () => {
            const appointmentId = el.dataset.appointmentId;
            const patientName = el.dataset.patientName || 'Patient';
            const serviceName = el.dataset.serviceName || '';
            const customerUserId = el.dataset.customerUserId || '';
            const dentistUserId = el.dataset.dentistUserId || '';

            if (title) title.textContent = patientName;
            if (sub) sub.textContent = serviceName ? `Service: ${serviceName}` : 'Choose action';

            // ✅ 2 options (đúng yêu cầu link tách biệt)
            if (btnExam) {
                btnExam.href = `/dentist/appointments/${appointmentId}/examination?customerUserId=${customerUserId}&dentistUserId=${dentistUserId}`;
            }
            if (btnBilling) {
                btnBilling.href = `/dentist/appointments/${appointmentId}/billing-transfer?customerUserId=${customerUserId}&dentistUserId=${dentistUserId}`;
            }

            if (backdrop) backdrop.hidden = false;
        });
    });
})();

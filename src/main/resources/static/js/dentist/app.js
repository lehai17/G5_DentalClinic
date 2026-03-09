document.addEventListener('DOMContentLoaded', function () {

    const backdrop   = document.getElementById('apptModalBackdrop');
    const btnClose   = document.getElementById('btnCloseModal');
    const title      = document.getElementById('apptModalTitle');
    const sub        = document.getElementById('apptModalSub');
    const btnExam    = document.getElementById('btnExamination');
    const btnReexam  = document.getElementById('btnAAAAA');
    const btnBilling = document.getElementById('btnBillingTransfer');

    if (!backdrop) return; // trang khác thì bỏ qua

    /* ===== CLOSE MODAL ===== */
    function closeModal() {
        backdrop.hidden = true;
    }

    btnClose?.addEventListener('click', closeModal);
    backdrop.addEventListener('click', e => {
        if (e.target === backdrop) closeModal();
    });

    /* ===== CLICK APPOINTMENT ===== */
    document.querySelectorAll('.appt').forEach(el => {
        el.addEventListener('click', () => {

            const appointmentId = el.dataset.appointmentId;
            const customerUserId = el.dataset.customerUserId;
            const status = el.dataset.status;

            // âœ… lấy weekStart từ hidden input (server truyền xuống)
            const weekStart =
                document.getElementById('weekStartHidden')?.value || '';

            title.textContent = el.dataset.patientName;
            sub.textContent   = el.dataset.serviceName;

            btnExam.href =
                `/dentist/appointments/${appointmentId}/examination`
                + `?customerUserId=${customerUserId}`
                + `&weekStart=${encodeURIComponent(weekStart)}`;

            // Configure REEXAM button based on status
            const reexamAvailableStatuses = ['EXAMINING', 'DONE', 'COMPLETED'];
            if (reexamAvailableStatuses.includes(status)) {
                btnReexam.style.display = '';
                btnReexam.href = 
                    `/dentist/reexam/${appointmentId}`
                    + `?weekStart=${encodeURIComponent(weekStart)}`;
            } else {
                btnReexam.style.display = 'none';
            }

            btnBilling.href =
                `/dentist/appointments/${appointmentId}/billing-transfer`
                + `?customerUserId=${customerUserId}`
                + `&weekStart=${encodeURIComponent(weekStart)}`;

            backdrop.hidden = false;
        });
    });

    /* ===== WEEK SELECT ===== */
    const weekSelect = document.getElementById('weekSelect');
    weekSelect?.addEventListener('change', e => {
        const weekStart = e.target.value;
        window.location.href =
            `/dentist/work-schedule?weekStart=${encodeURIComponent(weekStart)}`;
    });

});


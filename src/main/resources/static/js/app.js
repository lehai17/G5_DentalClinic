document.querySelectorAll('.appt').forEach(el => {
    el.addEventListener('click', () => {
        const appointmentId = el.dataset.appointmentId;
        const customerUserId = el.dataset.customerUserId;
        const dentistUserId = el.dataset.dentistUserId;

        btnExam.href =
            `/dentist/appointments/${appointmentId}/examination?customerUserId=${customerUserId}&dentistUserId=${dentistUserId}`;

        btnBilling.href =
            `/dentist/appointments/${appointmentId}/billing-transfer?customerUserId=${customerUserId}&dentistUserId=${dentistUserId}`;

        backdrop.hidden = false;
    });
});

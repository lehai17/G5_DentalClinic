function confirmAppointment(id) {
    fetch(`/staff/appointments/confirm?id=${id}`, { method: 'POST' })
        .then(() => location.reload());
}

function assignDentist(id) {
    const dentistId = prompt("Nhập ID bác sĩ:");
    if (!dentistId) return;

    fetch(`/staff/appointments/assign?appointmentId=${id}&dentistId=${dentistId}`, {
        method: 'POST'
    }).then(() => location.reload());
}

function checkIn(id) {
    fetch(`/staff/appointments/checkin?id=${id}`, { method: 'POST' })
        .then(() => location.reload());
}

function cancelAppointment(id) {
    const reason = prompt("Lý do hủy:");
    if (!reason) return;

    fetch(`/staff/appointments/cancel?id=${id}&reason=${reason}`, {
        method: 'POST'
    }).then(() => location.reload());
}

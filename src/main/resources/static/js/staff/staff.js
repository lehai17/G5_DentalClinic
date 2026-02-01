function confirmAppointment(id) {
    fetch(`/staff/appointments/confirm?id=${id}`, { method: 'POST' })
        .then(() => location.reload());
}

function assignDentist(appointmentId) {
    fetch("/staff/schedules/dentists")
        .then(res => res.json())
        .then(dentists => {
            let options = dentists.map(d =>
                `<option value="${d.id}">${d.fullName}</option>`
            ).join("");

            const modal = `
                <div class="modal-overlay">
                    <div class="modal">
                        <h3>Chọn bác sĩ</h3>
                        <select id="dentistSelect">
                            ${options}
                        </select>
                        <div class="modal-actions">
                            <button onclick="submitAssign(${appointmentId})">Xác nhận</button>
                            <button onclick="closeModal()">Hủy</button>
                        </div>
                    </div>
                </div>
            `;

            document.body.insertAdjacentHTML("beforeend", modal);
        });
}

function submitAssign(appointmentId) {
    const dentistId = document.getElementById("dentistSelect").value;

    fetch(`/staff/appointments/assign?appointmentId=${appointmentId}&dentistId=${dentistId}`, {
        method: 'POST'
    }).then(() => location.reload());
}

function closeModal() {
    document.querySelector(".modal-overlay")?.remove();
}


function completeAppointment(id) {
    if (!confirm("Xác nhận hoàn thành lịch khám này?")) return;

    fetch(`/staff/appointments/complete?id=${id}`, {
        method: 'POST'
    }).then(() => location.reload());
}


function cancelAppointment(id) {
    const reason = prompt("Lý do hủy:");
    if (!reason) return;

    fetch(`/staff/appointments/cancel?id=${id}&reason=${reason}`, {
        method: 'POST'
    }).then(() => location.reload());
}

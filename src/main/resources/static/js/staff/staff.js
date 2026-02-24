function confirmAppointment(id) {
    fetch(`/staff/appointments/confirm?id=${id}`, { method: 'POST' })
        .then(() => location.reload());
}

function assignDentist(appointmentId) {
    fetch("/staff/schedules/dentists")
        .then(res => res.json())
        .then(dentists => {

            document.querySelector(".modal-overlay")?.remove();

            let options = dentists.map(d =>
                `<option value="${d.id}">${d.fullName}</option>`
            ).join("");

            const modal = `
                <div class="modal-overlay">
                    <div class="modal">
                        <h3>Chọn bác sĩ</h3>

                        <div id="assignError"
                             style="display:none;color:red;margin-bottom:10px;">
                        </div>

                        <select id="dentistSelect">
                            ${options}
                        </select>

                        <div class="modal-actions">
                            <button type="button"
                                onclick="submitAssign(${appointmentId})">
                                Xác nhận
                            </button>
                            <button type="button"
                                onclick="closeModal()">Hủy</button>
                        </div>
                    </div>
                </div>
            `;

            document.body.insertAdjacentHTML("beforeend", modal);
        });
}



function submitAssign(appointmentId) {
    const dentistId = document.getElementById("dentistSelect").value;
    const errorBox = document.getElementById("assignError");

    fetch(`/staff/appointments/assign?appointmentId=${appointmentId}&dentistId=${dentistId}`, {
        method: "POST"
    })
    .then(async res => {
        if (!res.ok) {
            const msg = await res.text();
            errorBox.innerText = msg;
            errorBox.style.display = "block";
            return;
        }

        closeModal();
        location.reload();
    })
    .catch(() => {
        errorBox.innerText = "Không thể kết nối server";
        errorBox.style.display = "block";
    });
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
function checkInAppointment(id) {
    fetch('/staff/appointments/checkin', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({ id })
    }).then(res => {
        if (!res.ok) throw new Error("Check-in failed");
        window.location.reload();
    }).catch(err => alert(err.message));
}
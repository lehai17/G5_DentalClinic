function closeModal() {
    const modal = document.querySelector(".modal-overlay");
    if (modal) {
        modal.remove();
    }
}

function buildModal(contentHtml) {
    closeModal();
    const modalHtml = `
        <div class="modal-overlay">
            <div class="modal modal-wide">
                ${contentHtml}
            </div>
        </div>
    `;
    document.body.insertAdjacentHTML("beforeend", modalHtml);
}

function escapeHtml(value) {
    if (value == null) return "";
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

function renderAvailableDentistModal(appointmentId, dentists, title) {
    const options = dentists.map((dentist) =>
        `<option value="${dentist.id}">${escapeHtml(dentist.fullName)}</option>`
    ).join("");

    buildModal(`
        <h3>${title}</h3>
        <p class="modal-text">Chi hien thi cac nha si dang co lich lam va trong khung gio nay.</p>
        <div id="assignError" class="modal-error" style="display:none;"></div>
        <select id="dentistSelect">
            ${options}
        </select>
        <div class="modal-actions">
            <button type="button" class="btn-assign" onclick="submitAssign(${appointmentId})">Xac nhan</button>
            <button type="button" class="btn-secondary" onclick="closeModal()">Dong</button>
        </div>
    `);
}

function renderNoDentistModal(appointmentId) {
    buildModal(`
        <h3>Không thể đổi nha sĩ</h3>
        <p class="modal-text">Không còn nha sĩ có khung giờ trống này.</p>
        <div class="modal-actions modal-actions-column">
            <button type="button" class="btn-cancel" onclick="cancelAppointmentBySystem(${appointmentId})">Hủy lịch</button>
            <button type="button" class="btn-secondary" onclick="closeModal()">Đóng</button>
        </div>
    `);
}

function loadAvailableDentists(appointmentId, title) {
    fetch(`/staff/appointments/available-dentists?appointmentId=${appointmentId}`)
        .then((res) => {
            if (!res.ok) {
                throw new Error("Khong lay duoc danh sach nha si trong");
            }
            return res.json();
        })
        .then((dentists) => {
            if (!Array.isArray(dentists) || dentists.length === 0) {
                renderNoDentistModal(appointmentId);
                return;
            }
            renderAvailableDentistModal(appointmentId, dentists, title);
        })
        .catch((err) => alert("Loi: " + err.message));
}

function assignDentist(appointmentId) {
    loadAvailableDentists(appointmentId, "Gan nha si phu trach");
}

function changeDentist(appointmentId) {
    loadAvailableDentists(appointmentId, "Doi nha si phu trach");
}

function submitAssign(appointmentId) {
    const dentistSelect = document.getElementById("dentistSelect");
    const errorBox = document.getElementById("assignError");
    const dentistId = dentistSelect ? dentistSelect.value : null;

    fetch(`/staff/appointments/assign?appointmentId=${appointmentId}&dentistId=${dentistId}`, {
        method: "POST"
    })
        .then(async (res) => {
            if (!res.ok) {
                const msg = await res.text();
                if (errorBox) {
                    errorBox.innerText = msg || "Khong the doi nha si";
                    errorBox.style.display = "block";
                }
                return;
            }
            closeModal();
            location.reload();
        })
        .catch(() => {
            if (errorBox) {
                errorBox.innerText = "Khong the ket noi server";
                errorBox.style.display = "block";
            }
        });
}

function confirmAppointment(id) {
    fetch(`/staff/appointments/confirm?id=${id}`, { method: "POST" })
        .then(() => location.reload());
}

function completeAppointment(id) {
    if (!confirm("Xac nhan hoan thanh lich kham nay?")) return;
    fetch(`/staff/appointments/complete?id=${id}`, { method: "POST" })
        .then(() => location.reload());
}

function checkin(id) {
    fetch("/staff/appointments/checkin", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ id: id })
    })
        .then((res) => {
            if (!res.ok) throw new Error("Check-in that bai");
            location.reload();
        })
        .catch((err) => alert(err.message));
}

function cancelAppointment(id) {
    const reason = prompt("Ly do huy:");
    if (!reason) return;
    fetch(`/staff/appointments/cancel?id=${id}&reason=${encodeURIComponent(reason)}`, { method: "POST" })
        .then(() => location.reload());
}

function cancelAppointmentBySystem(id) {
    const reason = "Không còn nha sĩ có khung giờ trống này. Vui lòng đặt lại lịch khác";
    fetch(`/staff/appointments/cancel?id=${id}&reason=${encodeURIComponent(reason)}`, { method: "POST" })
        .then(() => {
            closeModal();
            location.reload();
        });
}

function goToPayment(id) {
    if (confirm("Xac nhan chuyen sang trang thai Cho thanh toan (Waiting Payment)?")) {
        fetch("/staff/appointments/process-payment?id=" + id, {
            method: "POST"
        }).then((res) => {
            if (res.ok) {
                location.reload();
            } else {
                res.text().then((text) => alert("Loi: " + text));
            }
        });
    }
}

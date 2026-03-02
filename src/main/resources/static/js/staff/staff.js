// --- QUẢN LÝ MODAL ---
function closeModal() {
    const modal = document.querySelector(".modal-overlay");
    if (modal) modal.remove();
}

// --- GÁN BÁC SĨ ---
function assignDentist(appointmentId) {
    // Lưu ý: Đảm bảo bạn có API này trả về danh sách bác sĩ [ {id:1, fullName:"abc"}, ... ]
    fetch("/staff/schedules/dentists")
        .then(res => {
            if (!res.ok) throw new Error("Không lấy được danh sách bác sĩ");
            return res.json();
        })
        .then(dentists => {
            closeModal(); // Xóa modal cũ nếu có

            let options = dentists.map(d =>
                `<option value="${d.id}">${d.fullName}</option>`
            ).join("");

            const modalHtml = `
                <div class="modal-overlay" style="position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); display:flex; justify-content:center; align-items:center; z-index:9999;">
                    <div class="modal" style="background:white; padding:20px; border-radius:8px; width:300px;">
                        <h3 style="margin-top:0;">Chọn bác sĩ</h3>
                        <div id="assignError" style="display:none; color:red; font-size:12px; margin-bottom:10px;"></div>
                        <select id="dentistSelect" style="width:100%; padding:8px; margin-bottom:15px;">
                            ${options}
                        </select>
                        <div class="modal-actions" style="display:flex; justify-content:flex-end; gap:10px;">
                            <button type="button" onclick="submitAssign(${appointmentId})" style="background:#28a745; color:white; border:none; padding:5px 15px; border-radius:4px; cursor:pointer;">Xác nhận</button>
                            <button type="button" onclick="closeModal()" style="background:#6c757d; color:white; border:none; padding:5px 15px; border-radius:4px; cursor:pointer;">Hủy</button>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML("beforeend", modalHtml);
        })
        .catch(err => alert("Lỗi: " + err.message));
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
        } else {
            closeModal();
            location.reload();
        }
    })
    .catch(() => {
        errorBox.innerText = "Không thể kết nối server";
        errorBox.style.display = "block";
    });
}

// --- CÁC HÀM TRẠNG THÁI KHÁC ---
function confirmAppointment(id) {
    fetch(`/staff/appointments/confirm?id=${id}`, { method: 'POST' })
        .then(() => location.reload());
}

function completeAppointment(id) {
    if (!confirm("Xác nhận hoàn thành lịch khám này?")) return;
    fetch(`/staff/appointments/complete?id=${id}`, { method: 'POST' })
        .then(() => location.reload());
}

function checkin(id) { // Đổi tên cho khớp với file HTML bạn gửi (onclick="checkin")
    fetch('/staff/appointments/checkin', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({ id: id })
    })
    .then(res => {
        if (!res.ok) throw new Error("Check-in thất bại");
        location.reload();
    })
    .catch(err => alert(err.message));
}

function cancelAppointment(id) {
    const reason = prompt("Lý do hủy:");
    if (!reason) return;
    fetch(`/staff/appointments/cancel?id=${id}&reason=${reason}`, { method: 'POST' })
        .then(() => location.reload());
}

function goToPayment(id) {
    window.location.href = "/staff/payments/" + id;
}
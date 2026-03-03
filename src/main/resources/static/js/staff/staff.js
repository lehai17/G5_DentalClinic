// --- QUẢN LÝ MODAL ---
function closeModal() {
    const modal = document.querySelector(".modal-overlay");
    if (modal) modal.remove();
}

// --- GÁN BÁC SĨ ---
function assignDentist(appointmentId) {
    // SỬA TẠI ĐÂY: Gọi API mới có kèm theo appointmentId để lọc bác sĩ rảnh cho ngày đó
    fetch(`/staff/appointments/available-dentists?appointmentId=${appointmentId}`)
        .then(res => {
            if (!res.ok) throw new Error("Không lấy được danh sách bác sĩ rảnh");
            return res.json();
        })
        .then(dentists => {
            closeModal();

            // Kiểm tra nếu không có bác sĩ nào rảnh
            if (dentists.length === 0) {
                alert("Không có bác sĩ nào sẵn sàng (tất cả đều nghỉ hoặc bận) vào ngày này!");
                return;
            }

            let options = dentists.map(d =>
                `<option value="${d.id}">${d.fullName}</option>`
            ).join("");

            const modalHtml = `
                <div class="modal-overlay" style="position:fixed; top:0; left:0; width:100%; height:100%; background:rgba(0,0,0,0.5); display:flex; justify-content:center; align-items:center; z-index:9999;">
                    <div class="modal" style="background:white; padding:20px; border-radius:8px; width:350px; box-shadow: 0 4px 15px rgba(0,0,0,0.2);">
                        <h3 style="margin-top:0; color:#1A237E;">Gán bác sĩ phụ trách</h3>
                        <p style="font-size:13px; color:#666;">Danh sách hiển thị các bác sĩ có lịch làm việc và không xin nghỉ vào ngày này.</p>
                        <div id="assignError" style="display:none; color:red; font-size:12px; margin-bottom:10px; padding:8px; background:#fff5f5; border-radius:4px;"></div>
                        <select id="dentistSelect" style="width:100%; padding:10px; margin-bottom:15px; border-radius:5px; border:1px solid #ddd;">
                            ${options}
                        </select>
                        <div class="modal-actions" style="display:flex; justify-content:flex-end; gap:10px;">
                            <button type="button" onclick="submitAssign(${appointmentId})" style="background:#28a745; color:white; border:none; padding:8px 20px; border-radius:4px; cursor:pointer; font-weight:bold;">Xác nhận</button>
                            <button type="button" onclick="closeModal()" style="background:#6c757d; color:white; border:none; padding:8px 20px; border-radius:4px; cursor:pointer;">Hủy</button>
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
    fetch(`/staff/appointments/${id}/complete`, { method: 'POST' })
        .then(r => {
            if (!r.ok) return r.text().then(t => { throw new Error(t); });
            location.reload();
        })
        .catch(err => alert("Hoàn thành lỗi: " + err.message));
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
function checkInAppointment(id) {
    fetch(`/staff/appointments/${id}/check-in`, { method: "POST" })
        .then(r => {
            if (!r.ok) return r.text().then(t => { throw new Error(t); });
            location.reload();
        })
        .catch(err => alert("Check-in lỗi: " + err.message));
}

function viewResult(id) {
    window.location.href = `/staff/appointments/${id}/result`;
}


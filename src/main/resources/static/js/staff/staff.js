function closeModal() {
    const modal = document.querySelector(".modal-overlay");
    if (modal) {
        modal.remove();
    }
}

function buildModal(contentHtml, extraClass) {
    closeModal();
    const modalClass = extraClass ? "modal modal-wide " + extraClass : "modal modal-wide";
    const modalHtml = `
        <div class="modal-overlay">
            <div class="${modalClass}">
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

function formatMoney(value) {
    const amount = Number(value || 0);
    return amount.toLocaleString("vi-VN") + " VND";
}

function formatDate(dateStr) {
    if (!dateStr) return "";
    const d = new Date(dateStr);
    if (Number.isNaN(d.getTime())) return String(dateStr);
    return d.toLocaleDateString("vi-VN");
}

function formatTime(timeStr) {
    if (!timeStr) return "";
    return String(timeStr).slice(0, 5);
}

function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return "";
    const d = new Date(dateTimeStr);
    if (Number.isNaN(d.getTime())) return String(dateTimeStr);
    return d.toLocaleString("vi-VN");
}

let activeInvoiceData = null;
let paymentOptionState = null;

function buildPaymentMethodsHtml() {
    if (!paymentOptionState || !activeInvoiceData) return "";

    const selectedMethod = paymentOptionState.selectedMethod || "";
    const feedback = paymentOptionState.feedback;
    const methodClass = (method) => selectedMethod === method ? " staff-payment-method--active" : "";

    return `
        <div class="staff-payment-section">
            <div class="staff-payment-section__title">Phuong thuc thanh toan</div>
            <div class="staff-payment-method-grid">
                <button type="button" class="staff-payment-method${methodClass("QR")}" onclick="selectPaymentMethod('QR')">
                    <strong>QR</strong>
                    <span>Quet ma VietQR va xac nhan thu cong</span>
                </button>
                <button type="button" class="staff-payment-method${methodClass("WALLET")}" onclick="payInvoiceWithWallet(${activeInvoiceData.id})">
                    <strong>Vi customer</strong>
                    <span>So du hien tai: ${formatMoney(paymentOptionState.walletBalance)}</span>
                </button>
                <button type="button" class="staff-payment-method${methodClass("CASH")}" onclick="selectPaymentMethod('CASH')">
                    <strong>Tien mat</strong>
                    <span>Xac nhan da thu tai quay</span>
                </button>
            </div>
            ${feedback ? `<div class="staff-payment-feedback staff-payment-feedback--${feedback.type}">${escapeHtml(feedback.message)}</div>` : ""}
            ${selectedMethod === "QR" ? buildQrMethodHtml() : ""}
            ${selectedMethod === "CASH" ? buildCashMethodHtml() : ""}
        </div>
    `;
}

function buildQrMethodHtml() {
    if (!paymentOptionState) return "";
    return `
        <div class="staff-payment-detail">
            <div class="staff-payment-detail__title">VietQR thanh toan so tien con lai</div>
            <img class="staff-payment-qr" src="${paymentOptionState.qrImageUrl}" alt="VietQR thanh toan">
            <div class="staff-payment-meta">Noi dung chuyen khoan: <strong>${escapeHtml(paymentOptionState.transferContent)}</strong></div>
            <div class="staff-payment-meta">Tai khoan nhan tien: <strong>${escapeHtml(paymentOptionState.accountNo)}</strong></div>
            <div class="staff-payment-actions">
                <button type="button" class="btn-payment" onclick="confirmManualPayment(${activeInvoiceData.id}, 'QR')">Xac nhan da nhan chuyen khoan</button>
            </div>
        </div>
    `;
}

function buildCashMethodHtml() {
    if (!activeInvoiceData) return "";
    return `
        <div class="staff-payment-detail">
            <div class="staff-payment-detail__title">Thanh toan tien mat</div>
            <div class="staff-payment-meta">So tien can thu: <strong>${formatMoney(activeInvoiceData.remainingAmount)}</strong></div>
            <div class="staff-payment-actions">
                <button type="button" class="btn-payment" onclick="confirmManualPayment(${activeInvoiceData.id}, 'CASH')">Xac nhan thanh toan bang tien mat</button>
            </div>
        </div>
    `;
}

function buildInvoiceHtml(data) {
    const items = Array.isArray(data && data.invoiceItems) ? data.invoiceItems : [];
    const prescriptionItems = Array.isArray(data && data.prescriptionItems) ? data.prescriptionItems : [];
    const billedTotal = Number((data && data.billedTotal) || 0);
    const depositAmount = Number((data && data.depositAmount) || 0);
    const originalRemainingAmount = Number((data && data.originalRemainingAmount) || 0);
    const discountAmount = Number((data && data.discountAmount) || 0);
    const remainingAmount = Number((data && data.remainingAmount) || 0);
    const invoiceStatus = String((data && data.invoiceStatus) || "").toUpperCase();
    const appointmentStatus = String((data && data.status) || "").toUpperCase();
    const isPaid = invoiceStatus === "PAID" || appointmentStatus === "COMPLETED";
    const appointmentTime = [formatTime(data && data.startTime), formatTime(data && data.endTime)]
        .filter(Boolean)
        .join(" - ");

    const itemsHtml = items.length
        ? items.map((item) => {
            const meta = [];
            if (item.qty != null) meta.push("SL: " + item.qty);
            if (item.unitPrice != null) meta.push("Don gia: " + formatMoney(item.unitPrice));
            return `
                <div class="staff-invoice-item">
                    <div>
                        <div class="staff-invoice-item__name">${escapeHtml(item.name || "Dich vu")}</div>
                        <div class="staff-invoice-item__meta">${escapeHtml(meta.join(" | "))}</div>
                    </div>
                    <div class="staff-invoice-item__amount">${formatMoney(item.amount)}</div>
                </div>
            `;
        }).join("")
        : `<div class="staff-invoice-empty">Chua co dong hoa don chi tiet.</div>`;

    const prescriptionHtml = prescriptionItems.length
        ? `
            <div class="staff-invoice-section">
                <div class="staff-invoice-section__title">Thuoc ke don</div>
                <div class="staff-invoice-prescription-list">
                    ${prescriptionItems.map((item) => `
                        <div class="staff-invoice-prescription-item">
                            <strong>${escapeHtml(item.medicineName || "Thuoc")}</strong>
                            ${item.dosage ? `<span>${escapeHtml(item.dosage)}</span>` : ""}
                            ${item.note ? `<small>${escapeHtml(item.note)}</small>` : ""}
                        </div>
                    `).join("")}
                </div>
            </div>
        `
        : "";

    const noteHtml = data && data.billingNoteNote
        ? `
            <div class="staff-invoice-section">
                <div class="staff-invoice-section__title">Ghi chu tu bac si</div>
                <div class="staff-invoice-note">${escapeHtml(data.billingNoteNote)}</div>
                ${data.billingNoteUpdatedAt ? `<div class="staff-invoice-note-time">Cap nhat: ${escapeHtml(formatDateTime(data.billingNoteUpdatedAt))}</div>` : ""}
            </div>
        `
        : "";

    return `
        <div class="staff-invoice-card">
            <div class="staff-invoice-head">
                <div>
                    <div class="staff-invoice-title">Hoa don thanh toan</div>
                    <div class="staff-invoice-subtitle">
                        ${data && data.invoiceId ? `Hoa don #${escapeHtml(data.invoiceId)}` : "Hoa don se duoc tao khi xac nhan thanh toan"}
                    </div>
                </div>
                <div class="staff-invoice-chip">${isPaid ? "Da thanh toan" : "Chua thanh toan"}</div>
            </div>

            <div class="staff-invoice-overview">
                <div class="staff-invoice-overview__item"><span>Lich hen</span><strong>#${escapeHtml(data && data.id)}</strong></div>
                <div class="staff-invoice-overview__item"><span>Dich vu</span><strong>${escapeHtml((data && data.serviceName) || "Chua cap nhat")}</strong></div>
                <div class="staff-invoice-overview__item"><span>Bac si</span><strong>${escapeHtml((data && data.dentistName) || "Chua phan cong")}</strong></div>
                <div class="staff-invoice-overview__item"><span>Ngay kham</span><strong>${escapeHtml(formatDate(data && data.date) || "Chua cap nhat")}</strong></div>
                <div class="staff-invoice-overview__item"><span>Khung gio</span><strong>${escapeHtml(appointmentTime || "Chua cap nhat")}</strong></div>
            </div>

            <div class="staff-invoice-section">
                <div class="staff-invoice-section__title">Chi tiet dich vu</div>
                <div class="staff-invoice-list">${itemsHtml}</div>
            </div>

            ${prescriptionHtml}
            ${noteHtml}

            <div class="staff-invoice-section">
                <div class="staff-invoice-section__title">Tong ket thanh toan</div>
                <div class="staff-invoice-total">
                    <div class="staff-invoice-total__line"><span>Tong billing</span><strong>${formatMoney(billedTotal)}</strong></div>
                    <div class="staff-invoice-total__line"><span>Dat coc ban dau</span><strong>${formatMoney(depositAmount)}</strong></div>
                    <div class="staff-invoice-total__line"><span>Tam tinh sau dat coc</span><strong>${formatMoney(originalRemainingAmount)}</strong></div>
                    ${discountAmount > 0 ? `<div class="staff-invoice-total__line"><span>Giam gia</span><strong>-${formatMoney(discountAmount)}</strong></div>` : ""}
                    <div class="staff-invoice-total__line staff-invoice-total__line--due"><span>Con lai</span><strong>${formatMoney(remainingAmount)}</strong></div>
                </div>
            </div>

            ${buildPaymentMethodsHtml()}

            <div class="modal-actions staff-invoice-actions">
                <button type="button" class="btn-secondary" onclick="closeModal()">Dong</button>
                ${data && data.canPayRemaining && !paymentOptionState ? `<button type="button" class="btn-payment" onclick="showPaymentMethods(${data.id})">Thanh toan</button>` : ""}
            </div>
        </div>
    `;
}

function viewInvoice(id) {
    fetch(`/staff/appointments/${id}/invoice-preview`)
        .then(async (res) => {
            if (!res.ok) {
                throw new Error(await res.text() || "Khong the tai hoa don");
            }
            return res.json();
        })
        .then((data) => {
            activeInvoiceData = data;
            paymentOptionState = null;
            buildModal(buildInvoiceHtml(data), "staff-invoice-modal");
        })
        .catch((err) => alert("Loi: " + err.message));
}

function rerenderInvoiceModal() {
    if (!activeInvoiceData) return;
    buildModal(buildInvoiceHtml(activeInvoiceData), "staff-invoice-modal");
}

function showPaymentMethods(id) {
    fetch(`/staff/appointments/${id}/payment-options`, {
        method: "POST"
    }).then((res) => {
        if (res.ok) {
            return res.json();
        } else {
            return res.text().then((text) => Promise.reject(new Error(text || "Khong the tai phuong thuc thanh toan")));
        }
    }).then((payload) => {
        activeInvoiceData = payload.invoice;
        paymentOptionState = {
            walletBalance: payload.walletBalance,
            qrImageUrl: payload.qrImageUrl,
            transferContent: payload.transferContent,
            accountNo: payload.accountNo,
            selectedMethod: null,
            feedback: null
        };
        rerenderInvoiceModal();
    }).catch((err) => {
        alert("Loi: " + err.message);
    });
}

function selectPaymentMethod(method) {
    if (!paymentOptionState) return;
    paymentOptionState.selectedMethod = method;
    paymentOptionState.feedback = null;
    rerenderInvoiceModal();
}

function payInvoiceWithWallet(id) {
    if (!paymentOptionState) return;
    paymentOptionState.selectedMethod = "WALLET";
    paymentOptionState.feedback = null;
    rerenderInvoiceModal();

    fetch(`/staff/appointments/${id}/pay-wallet`, {
        method: "POST"
    }).then(async (res) => {
        if (!res.ok) {
            throw new Error(await res.text() || "Khong the thanh toan bang vi");
        }
        paymentOptionState.feedback = {
            type: "success",
            message: "Thanh toan bang vi thanh cong."
        };
        rerenderInvoiceModal();
        setTimeout(() => location.reload(), 1000);
    }).catch((err) => {
        const message = (err.message || "").toLowerCase().includes("khong du")
            ? "So du vi khong du. Vui long chon phuong thuc khac."
            : (err.message || "Khong the thanh toan bang vi.");
        paymentOptionState.feedback = {
            type: "error",
            message: message
        };
        rerenderInvoiceModal();
    });
}

function confirmManualPayment(id, method) {
    const confirmMessage = method === "CASH"
        ? "Xac nhan da nhan du so tien con lai bang tien mat?"
        : "Xac nhan da nhan chuyen khoan QR cho hoa don nay?";
    if (!confirm(confirmMessage)) return;

    fetch(`/staff/appointments/${id}/confirm-manual-payment`, {
        method: "POST"
    }).then(async (res) => {
        if (!res.ok) {
            throw new Error(await res.text() || "Khong the xac nhan thanh toan");
        }
        if (paymentOptionState) {
            paymentOptionState.feedback = {
                type: "success",
                message: method === "CASH"
                    ? "Da xac nhan thanh toan tien mat thanh cong."
                    : "Da xac nhan thanh toan QR thanh cong."
            };
        }
        rerenderInvoiceModal();
        setTimeout(() => location.reload(), 1000);
    }).catch((err) => {
        if (paymentOptionState) {
            paymentOptionState.feedback = {
                type: "error",
                message: err.message || "Khong the xac nhan thanh toan."
            };
            rerenderInvoiceModal();
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".btn-payment").forEach((button) => {
        const action = button.getAttribute("onclick") || "";
        if (action.includes("viewInvoice")) {
            button.textContent = "Xem hoa don";
        }
    });
});

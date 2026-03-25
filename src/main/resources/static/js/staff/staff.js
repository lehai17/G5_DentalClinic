function closeModal() {
    const modal = document.querySelector(".modal-overlay");
    stopPayOsPolling();
    if (modal) {
        modal.remove();
    }
}

function buildModal(contentHtml, extraClass, options) {
    const modalOptions = options || {};
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

    const modalElement = document.querySelector(".modal-overlay ." + modalClass.split(" ").join("."));
    if (modalElement && typeof modalOptions.scrollTop === "number") {
        modalElement.scrollTop = modalOptions.scrollTop;
    }

    if (modalElement && modalOptions.scrollToPaymentSection) {
        requestAnimationFrame(() => {
            const paymentSection = modalElement.querySelector(".staff-payment-section");
            if (paymentSection) {
                paymentSection.scrollIntoView({ block: "start", behavior: "auto" });
            }
        });
    }
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
            <button type="button" class="btn-assign" onclick="submitAssign(${appointmentId})">Xác nhận</button>
            <button type="button" class="btn-secondary" onclick="closeModal()">Đóng</button>
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
                throw new Error("Không lấy được danh sách nha sĩ trống");
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
                    errorBox.innerText = msg || "Không thể đổi nha sĩ";
                    errorBox.style.display = "block";
                }
                return;
            }
            closeModal();
            location.reload();
        })
        .catch(() => {
            if (errorBox) {
                errorBox.innerText = "Không thể kết nối server";
                errorBox.style.display = "block";
            }
        });
}

function confirmAppointment(id) {
    fetch(`/staff/appointments/confirm?id=${id}`, { method: "POST" })
        .then(() => location.reload());
}

function completeAppointment(id) {
    if (!confirm("Xác nhận hoàn thành lịch khám này?")) return;
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
let payOsPollTimer = null;

function buildPaymentMethodsHtml() {
    if (!paymentOptionState || !activeInvoiceData) return "";

    const selectedMethod = paymentOptionState.selectedMethod || "";
    const feedback = paymentOptionState.feedback;
    const methodClass = (method) => selectedMethod === method ? " staff-payment-method--active" : "";

    return `
        <div class="staff-payment-section">
            <div class="staff-payment-section__title">Phương thức thanh toán</div>
            <div class="staff-payment-method-grid">
                <button type="button" class="staff-payment-method${methodClass("QR")}" onclick="selectPaymentMethod('QR')">
                    <strong>QR</strong>
                    <span>Quét mã payOS và tự động xác nhận</span>
                </button>
                <button type="button" class="staff-payment-method${methodClass("WALLET")}" onclick="payInvoiceWithWallet(${activeInvoiceData.id})">
                    <strong>Ví customer</strong>
                    <span>Số dư hiện tại: ${formatMoney(paymentOptionState.walletBalance)}</span>
                </button>
                <button type="button" class="staff-payment-method${methodClass("CASH")}" onclick="selectPaymentMethod('CASH')">
                    <strong>Tiền mặt</strong>
                    <span>Xác nhận đã thu tại quầy</span>
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
    if (!paymentOptionState.qrImageUrl) {
        return `
            <div class="staff-payment-detail">
                <div class="staff-payment-detail__title">Thanh toán QR qua payOS</div>
                <div class="staff-payment-meta">Tạo mã QR cho hóa đơn này và chờ hệ thống tự động cập nhật khi thanh toán thành công.</div>
                <div class="staff-payment-actions">
                    <button type="button" class="btn-payment" onclick="loadPayOsQr(${activeInvoiceData.id})">Tạo mã QR</button>
                </div>
            </div>
        `;
    }
    return `
        <div class="staff-payment-detail">
            <div class="staff-payment-detail__title">Thanh toán QR qua payOS</div>
            <img class="staff-payment-qr" src="${paymentOptionState.qrImageUrl}" alt="VietQR thanh toán">
            <div class="staff-payment-meta">Mã đơn: <strong>${escapeHtml(paymentOptionState.orderCode || "")}</strong></div>
            <div class="staff-payment-meta">Trạng thái payOS: <strong>${escapeHtml(paymentOptionState.payOsStatus || "PENDING")}</strong></div>
            <div class="staff-payment-actions">
                ${paymentOptionState.checkoutUrl ? `<a class="btn-payment staff-payment-link" href="${paymentOptionState.checkoutUrl}" target="_blank" rel="noopener noreferrer">Mở trang thanh toán</a>` : ""}
            </div>
        </div>
    `;
}

function buildCashMethodHtml() {
    if (!activeInvoiceData) return "";
    const remaining = activeInvoiceData.remainingAmount || 0;
    return `
        <div class="staff-payment-detail">
            <div class="staff-payment-detail__title">Thanh toán tiền mặt</div>
            <div class="staff-payment-meta">Số tiền cần thu: <strong>${formatMoney(activeInvoiceData.remainingAmount)}</strong></div>
            <div class="staff-payment-actions">
                <button type="button" class="btn-payment" onclick="confirmManualPayment(${activeInvoiceData.id}, 'CASH')">Xác nhận thanh toán bằng tiền mặt</button>
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
            if (item.unitPrice != null) meta.push("Đơn giá: " + formatMoney(item.unitPrice));
            return `
                <div class="cap-invoice-item">
                    <div>
                        <div class="cap-invoice-name">${escapeHtml(item.name || "Dịch vụ")}</div>
                        <div class="cap-invoice-meta">${escapeHtml(meta.join(" | "))}</div>
                    </div>
                    <div class="cap-invoice-amount">${formatMoney(item.amount)}</div>
                </div>
            `;
        }).join("")
        : `<div class="staff-invoice-empty">Chưa có dòng hóa đơn chi tiết.</div>`;

    const prescriptionHtml = prescriptionItems.length
        ? `
            <div class="cap-invoice-section">
                <div class="cap-invoice-section__title">Thuốc kê đơn</div>
                <div class="cap-invoice-prescription-list">
                    ${prescriptionItems.map((item) => `
                        <div class="cap-invoice-prescription-item">
                            <strong>${escapeHtml(item.medicineName || "Thuốc")}</strong>
                            ${item.dosage ? `<span class="cap-invoice-prescription-item__dosage">${escapeHtml(item.dosage)}</span>` : ""}
                            ${item.note ? `<small class="cap-invoice-prescription-item__note">${escapeHtml(item.note)}</small>` : ""}
                        </div>
                    `).join("")}
                </div>
            </div>
        `
        : "";

    const noteHtml = data && data.billingNoteNote
        ? `
            <div class="cap-invoice-section">
                <div class="cap-invoice-section__title">Ghi chú từ bác sĩ</div>
                <div class="cap-invoice-note">${escapeHtml(data.billingNoteNote)}</div>
                ${data.billingNoteUpdatedAt ? `<div class="cap-invoice-note-time">Cập nhật: ${escapeHtml(formatDateTime(data.billingNoteUpdatedAt))}</div>` : ""}
            </div>
        `
        : "";

    return `
        <div class="cap-invoice-card">
            <div class="cap-invoice-head">
                <div class="cap-invoice-title"><i class="bi bi-file-earmark-text"></i> Hóa đơn thanh toán</div>
                <div class="cap-invoice-chip">${isPaid ? "Đã thanh toán" : "Chưa thanh toán"}</div>
            </div>

            <div class="cap-invoice-overview">
                <div class="cap-invoice-overview__item"><span>Mã hóa đơn</span><strong>${data && data.invoiceId ? "#" + escapeHtml(data.invoiceId) : "Sẽ tạo khi cần"}</strong></div>
                <div class="cap-invoice-overview__item"><span>Trạng thái</span><strong>${isPaid ? "Đã thanh toán" : "Chưa thanh toán"}</strong></div>
                <div class="cap-invoice-overview__item"><span>Dịch vụ</span><strong>${escapeHtml((data && data.serviceName) || "Chưa cập nhật")}</strong></div>
                <div class="cap-invoice-overview__item"><span>Bác sĩ</span><strong>${escapeHtml((data && data.dentistName) || "Chưa phân công")}</strong></div>
                <div class="cap-invoice-overview__item"><span>Ngày khám</span><strong>${escapeHtml(formatDate(data && data.date) || "Chưa cập nhật")}</strong></div>
                <div class="cap-invoice-overview__item"><span>Khung giờ</span><strong>${escapeHtml(appointmentTime || "Chưa cập nhật")}</strong></div>
            </div>

            <div class="cap-invoice-section">
                <div class="cap-invoice-section__title">Chi tiết dịch vụ</div>
                <div class="cap-invoice-list">${itemsHtml}</div>
            </div>

            ${prescriptionHtml}
            ${noteHtml}

            <div class="cap-invoice-section">
                <div class="cap-invoice-section__title">Tổng kết thanh toán</div>
                <div class="cap-invoice-total">
                    <div class="cap-invoice-total-line"><span>Tổng billing</span><strong>${formatMoney(billedTotal)}</strong></div>
                    <div class="cap-invoice-total-line"><span>Đặt cọc ban đầu</span><strong>${formatMoney(depositAmount)}</strong></div>
                    <div class="cap-invoice-total-line"><span>Trả sau đặt cọc</span><strong>${formatMoney(originalRemainingAmount)}</strong></div>
                    ${discountAmount > 0 ? `<div class="cap-invoice-total-line"><span>Giảm giá</span><strong>-${formatMoney(discountAmount)}</strong></div>` : ""}
                    ${isPaid
                        ? `<div class="cap-invoice-settled"><i class="bi bi-patch-check-fill"></i><span>Hóa đơn đã được thanh toán xong.</span></div>`
                        : `<div class="cap-invoice-total-line cap-invoice-total-line--due"><span>Còn lại</span><strong>${formatMoney(remainingAmount)}</strong></div>`}
                </div>
            </div>

            ${buildPaymentMethodsHtml()}

            <div class="modal-actions staff-invoice-actions">
                <button type="button" class="btn-secondary" onclick="closeModal()">Đóng</button>
                ${data && data.canPayRemaining && !paymentOptionState ? `<button type="button" class="btn-payment" onclick="showPaymentMethods(${data.id})">Thanh toán</button>` : ""}
            </div>
        </div>
    `;
}

function viewInvoice(id) {
    fetch(`/staff/appointments/${id}/invoice-preview`)
        .then(async (res) => {
            if (!res.ok) {
                throw new Error(await res.text() || "Không thể tải hóa đơn");
            }
            return res.json();
        })
        .then((data) => {
            activeInvoiceData = data;
            paymentOptionState = null;
            buildModal(buildInvoiceHtml(data), "staff-invoice-modal");
        })
        .catch((err) => alert("Lỗi: " + err.message));
}

function rerenderInvoiceModal() {
    if (!activeInvoiceData) return;
    const existingModal = document.querySelector(".staff-invoice-modal");
    const scrollTop = existingModal ? existingModal.scrollTop : 0;
    buildModal(buildInvoiceHtml(activeInvoiceData), "staff-invoice-modal", { scrollTop });
    syncPaymentMethodVisibility();
}

function stopPayOsPolling() {
    if (payOsPollTimer) {
        clearInterval(payOsPollTimer);
        payOsPollTimer = null;
    }
}

function markPaymentCompleted(reloadDelay) {
    if (!paymentOptionState) return;
    paymentOptionState.feedback = {
        type: "success",
        message: "Đã thanh toán thành công! Hoàn tất lịch hẹn."
    };
    rerenderInvoiceModal();
    setTimeout(() => location.reload(), reloadDelay || 1000);
}

function syncPaymentMethodVisibility() {
    if (!paymentOptionState || paymentOptionState.allowWalletPayment !== false) {
        return;
    }

    if (paymentOptionState.selectedMethod === "WALLET") {
        paymentOptionState.selectedMethod = null;
    }

    const walletButton = document.querySelector('.staff-payment-method-grid .staff-payment-method[onclick*="payInvoiceWithWallet"]');
    if (walletButton) {
        walletButton.remove();
    }
}

function showPaymentMethods(id) {
    fetch(`/staff/appointments/${id}/payment-options`, {
        method: "POST"
    }).then((res) => {
        if (res.ok) {
            return res.json();
        } else {
            return res.text().then((text) => Promise.reject(new Error(text || "Không thể tải phương thức thanh toán")));
        }
    }).then((payload) => {
        activeInvoiceData = payload.invoice;
        paymentOptionState = {
            walletBalance: payload.walletBalance,
            allowWalletPayment: payload.allowWalletPayment,
            transferContent: payload.transferContent,
            selectedMethod: null,
            feedback: null,
            qrImageUrl: null,
            checkoutUrl: null,
            orderCode: null,
            payOsStatus: null
        };
        stopPayOsPolling();
        const existingModal = document.querySelector(".staff-invoice-modal");
        const scrollTop = existingModal ? existingModal.scrollTop : 0;
        buildModal(buildInvoiceHtml(activeInvoiceData), "staff-invoice-modal", {
            scrollTop,
            scrollToPaymentSection: true
        });
        syncPaymentMethodVisibility();
    }).catch((err) => {
        alert("Lỗi: " + err.message);
    });
}

function selectPaymentMethod(method) {
    if (!paymentOptionState) return;
    paymentOptionState.selectedMethod = method;
    paymentOptionState.feedback = null;
    if (method !== "QR") {
        stopPayOsPolling();
    }
    rerenderInvoiceModal();
}

function loadPayOsQr(id) {
    if (!paymentOptionState) return;
    paymentOptionState.feedback = null;
    rerenderInvoiceModal();

    fetch(`/staff/appointments/${id}/payos-link`, {
        method: "POST"
    }).then(async (res) => {
        if (!res.ok) {
            throw new Error(await res.text() || "Không thể tạo QR payOS");
        }
        return res.json();
    }).then((payload) => {
        paymentOptionState.qrImageUrl = payload.qrImageUrl;
        paymentOptionState.checkoutUrl = payload.checkoutUrl;
        paymentOptionState.orderCode = payload.orderCode;
        paymentOptionState.payOsStatus = payload.status || "PENDING";
        paymentOptionState.feedback = {
            type: "success",
            message: "Đã tạo mã QR payOS. Hệ thống đang chờ xác nhận thanh toán."
        };
        rerenderInvoiceModal();
        startPayOsPolling(id);
    }).catch((err) => {
        paymentOptionState.feedback = {
            type: "error",
            message: err.message || "Không thể tạo QR payOS."
        };
        rerenderInvoiceModal();
    });
}

function startPayOsPolling(id) {
    stopPayOsPolling();
    payOsPollTimer = setInterval(() => {
        fetch(`/staff/appointments/${id}/payos-status`)
            .then(async (res) => {
                if (!res.ok) {
                    throw new Error(await res.text() || "Không thể kiểm tra thanh toán payOS");
                }
                return res.json();
            })
            .then((payload) => {
                if (!paymentOptionState) return;
                paymentOptionState.payOsStatus = payload.payOsStatus || paymentOptionState.payOsStatus;
                if (payload.paid) {
                    stopPayOsPolling();
                    activeInvoiceData.invoiceStatus = payload.invoiceStatus || "PAID";
                    activeInvoiceData.status = payload.appointmentStatus || "COMPLETED";
                    markPaymentCompleted(1200);
                }
            })
            .catch(() => {});
    }, 5000);
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
            throw new Error(await res.text() || "Không thể thanh toán bằng ví");
        }
        markPaymentCompleted(1000);
    }).catch((err) => {
        const message = (err.message || "").toLowerCase().includes("khong du")
            ? "Số dư ví không đủ. Vui lòng chọn phương thức khác."
            : (err.message || "Không thể thanh toán bằng ví.");
        paymentOptionState.feedback = {
            type: "error",
            message: message
        };
        rerenderInvoiceModal();
    });
}

function submitManualPayment(id, method) {
    const input = document.getElementById("cashPaidAmount");
    const paidAmount = input ? input.value : (activeInvoiceData ? activeInvoiceData.remainingAmount : 0);

    const confirmMessage = method === "CASH"
        ? "Xác nhận đã nhận đủ số tiền còn lại bằng tiền mặt?"
        : "Xác nhận đã nhận chuyển khoản QR cho hóa đơn này?";
    if (!confirm(confirmMessage)) return;

    const params = new URLSearchParams();
    params.append("paidAmount", paidAmount);

    fetch(`/staff/appointments/${id}/confirm-manual-payment?${params.toString()}`, {
        method: "POST"
    }).then(async (res) => {
        if (!res.ok) {
            throw new Error(await res.text() || "Không thể xác nhận thanh toán");
        }
        markPaymentCompleted(1000);
    }).catch((err) => {
        if (paymentOptionState) {
            paymentOptionState.feedback = {
                type: "error",
                message: err.message || "Không thể xác nhận thanh toán."
            };
            rerenderInvoiceModal();
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".btn-payment").forEach((button) => {
        const action = button.getAttribute("onclick") || "";
        if (action.includes("viewInvoice")) {
            button.textContent = "Xem hóa đơn";
        }
    });
});
function toggleAppointmentDetails(id, button) {
    const detailRow = document.getElementById(`appointment-detail-${id}`);
    if (!detailRow) return;

    const isOpen = detailRow.classList.toggle("is-open");
    if (button) {
        button.classList.toggle("is-open", isOpen);
        button.setAttribute("aria-expanded", isOpen ? "true" : "false");
    }
}

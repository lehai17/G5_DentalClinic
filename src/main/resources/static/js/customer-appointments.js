(function () {
  "use strict";

  var listEl = document.getElementById("customer-appointments-list");
  if (!listEl) return;

  var paginationEl = document.getElementById(
    "customer-appointments-pagination",
  );
  var summaryTotalEl = document.getElementById("cap-summary-total");
  var summaryPageEl = document.getElementById("cap-summary-page");
  var searchInputEl = document.getElementById("customer-appointments-search");
  var clearSearchEl = document.getElementById("customer-appointments-clear");
  var sortSelectEl = document.getElementById("customer-appointments-sort");
  var paymentModalEl = document.getElementById("cap-payment-modal");
  var paymentCloseEl = document.getElementById("cap-payment-close");
  var paymentAppointmentIdEl = document.getElementById(
    "cap-payment-appointment-id",
  );
  var paymentInvoiceIdEl = document.getElementById("cap-payment-invoice-id");
  var paymentAmountEl = document.getElementById("cap-payment-amount");
  var paymentWalletBtn = document.getElementById("cap-payment-wallet");
  var paymentVnpayBtn = document.getElementById("cap-payment-vnpay");
  var invoiceModalEl = document.getElementById("cap-invoice-modal");
  var invoiceModalCloseEl = document.getElementById("cap-invoice-close");
  var invoiceModalContentEl = document.getElementById("cap-invoice-modal-content");
  var searchTimer = null;
  var state = { page: 0, size: 5, totalPages: 0, keyword: "", sort: "newest" };
  var queryParams = new URLSearchParams(window.location.search);
  var remainingPaymentSelection = null;

  var currentOpen = {
    appointmentId: null,
    detailEl: null,
  };

  function showToast(message, type, title) {
    if (window.CustomerFeedback) {
      window.CustomerFeedback.toast({
        message: message,
        type: type || "info",
        title: title || "",
      });
      return;
    }
  }

  function showAlert(message, type, title) {
    if (window.CustomerFeedback) {
      return window.CustomerFeedback.alert({
        message: message,
        type: type || "info",
        title: title || "Thông báo",
      });
    }
    alert(message);
    return Promise.resolve();
  }

  function askConfirm(message, options) {
    if (window.CustomerFeedback) {
      return window.CustomerFeedback.confirm(
        Object.assign({ message: message }, options || {}),
      );
    }
    return Promise.resolve(confirm(message));
  }

  function formatDate(dateStr) {
    if (!dateStr) return "";
    var d = new Date(dateStr);
    return d.getDate() + "/" + (d.getMonth() + 1) + "/" + d.getFullYear();
  }

  function formatTime(t) {
    if (!t) return "";
    var s = String(t);
    return s.length >= 5 ? s.substring(0, 5) : s;
  }

  function formatDateTime(dateTimeStr) {
    if (!dateTimeStr) return "";
    var d = new Date(dateTimeStr);
    if (isNaN(d.getTime())) return String(dateTimeStr);
    var dd = String(d.getDate()).padStart(2, "0");
    var mm = String(d.getMonth() + 1).padStart(2, "0");
    var yyyy = d.getFullYear();
    var hh = String(d.getHours()).padStart(2, "0");
    var min = String(d.getMinutes()).padStart(2, "0");
    return dd + "/" + mm + "/" + yyyy + " " + hh + ":" + min;
  }

  function escapeHtml(s) {
    if (s == null) return "";
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  function getStatusMeta(status) {
    var key = String(status || "").toUpperCase();
    var map = {
      PENDING: { label: "Chờ khám", className: "pending" },
      CONFIRMED: { label: "Đã xác nhận", className: "confirmed" },
      CHECKED_IN: { label: "Đã check-in", className: "checked-in" },
      EXAMINING: { label: "Đang khám", className: "examining" },
      IN_PROGRESS: { label: "Đang xử lý", className: "examining" },
      WAITING_PAYMENT: {
        label: "Chờ thanh toán",
        className: "waiting-payment",
      },
      COMPLETED: { label: "Đã hoàn thành", className: "completed" },
      DONE: { label: "Hoàn tất ca khám", className: "completed" },
      CANCELLED: { label: "Đã hủy", className: "cancelled" },
      REEXAM: { label: "Tái khám", className: "reexam" },
    };
    return map[key] || { label: key || "Không xác định", className: "default" };
  }

  function formatInvoiceStatus(status) {
    var key = String(status || "").toUpperCase();
    var map = {
      PAID: "Đã thanh toán",
      UNPAID: "Chưa thanh toán",
    };
    return map[key] || status || "Không xác định";
  }

  function extractApiError(payload, fallbackMessage) {
    if (!payload) return fallbackMessage;
    if (payload.message) return payload.message;
    if (payload.error) return payload.error;
    return fallbackMessage;
  }

  function toNumber(value) {
    var num = Number(value);
    return isNaN(num) ? 0 : num;
  }

  function isCompletedStatus(status) {
    var key = String(status || "").toUpperCase();
    return key === "COMPLETED" || key === "DONE";
  }

  function isSettledInvoice(data) {
    var invoicePaid = String(data && data.invoiceStatus || "").toUpperCase() === "PAID";
    return invoicePaid || isCompletedStatus(data && data.status);
  }

  function shouldShowRemaining(data) {
    return !isSettledInvoice(data) && toNumber(data && data.remainingAmount) > 0;
  }

  function closeCurrentDetail() {
    if (currentOpen.detailEl && currentOpen.detailEl.parentNode) {
      currentOpen.detailEl.parentNode.removeChild(currentOpen.detailEl);
    }

    currentOpen.appointmentId = null;
    currentOpen.detailEl = null;

    document
      .querySelectorAll("#customer-appointments-list li.cap-item-active")
      .forEach(function (li) {
        li.classList.remove("cap-item-active");
      });
  }

  function closeRemainingPaymentModal() {
    if (!paymentModalEl) return;
    paymentModalEl.hidden = true;
    document.body.classList.remove("cap-payment-modal-open");
    remainingPaymentSelection = null;
    if (paymentWalletBtn) paymentWalletBtn.disabled = false;
    if (paymentVnpayBtn) paymentVnpayBtn.disabled = false;
  }

  function closeInvoiceModal() {
    if (!invoiceModalEl) return;
    invoiceModalEl.hidden = true;
    document.body.classList.remove("cap-payment-modal-open");
    if (invoiceModalContentEl) invoiceModalContentEl.innerHTML = "";
  }

  function openInvoiceModal(data) {
    if (!invoiceModalEl || !invoiceModalContentEl || !data || !data.invoiceId) return;
    invoiceModalContentEl.innerHTML = buildInvoiceHtml(data);
    invoiceModalEl.hidden = false;
    document.body.classList.add("cap-payment-modal-open");
  }

  function openRemainingPaymentModal(data, appointmentId) {
    if (!paymentModalEl || !data || !data.canPayRemaining) return;

    remainingPaymentSelection = {
      appointmentId: appointmentId,
      invoiceId: data.invoiceId,
      amount: data.remainingAmount,
    };

    if (paymentAppointmentIdEl)
      paymentAppointmentIdEl.textContent = "#" + appointmentId;
    if (paymentInvoiceIdEl)
      paymentInvoiceIdEl.textContent = data.invoiceId
        ? "#" + data.invoiceId
        : "Chưa có";
    if (paymentAmountEl)
      paymentAmountEl.textContent = formatMoney(data.remainingAmount);
    if (paymentWalletBtn) paymentWalletBtn.disabled = false;
    if (paymentVnpayBtn) paymentVnpayBtn.disabled = false;

    paymentModalEl.hidden = false;
    document.body.classList.add("cap-payment-modal-open");
  }

  function buildInvoiceHtml(data) {
    if (!data || !data.invoiceId) return "";

    var items = Array.isArray(data.invoiceItems) ? data.invoiceItems : [];
    var hasRemaining = shouldShowRemaining(data);
    var settled = isSettledInvoice(data);
    var billedTotal = toNumber(data.billedTotal);
    var depositAmount = toNumber(data.depositAmount);
    var remainingAmount = toNumber(data.remainingAmount);
    var paidAmount = settled
      ? billedTotal
      : Math.max(billedTotal - remainingAmount, 0);
    var statusLabel = settled
      ? "Đã thanh toán"
      : formatInvoiceStatus(data.invoiceStatus);
    var appointmentDate = formatDate(data.date);
    var appointmentTime =
      [formatTime(data.startTime), formatTime(data.endTime)]
        .filter(function (value) {
          return value;
        })
        .join(" - ");
    var billingNoteText = data.billingNoteNote ? String(data.billingNoteNote).trim() : "";
    var prescriptionItems = Array.isArray(data.prescriptionItems)
      ? data.prescriptionItems
      : [];
    var overviewHtml =
      '<div class="cap-invoice-overview">' +
      '<div class="cap-invoice-overview__item"><span>Mã hóa đơn</span><strong>#' +
      escapeHtml(data.invoiceId) +
      "</strong></div>" +
      '<div class="cap-invoice-overview__item"><span>Trạng thái</span><strong>' +
      escapeHtml(statusLabel) +
      "</strong></div>" +
      '<div class="cap-invoice-overview__item"><span>Dịch vụ</span><strong>' +
      escapeHtml(data.serviceName || "Chưa cập nhật") +
      "</strong></div>" +
      '<div class="cap-invoice-overview__item"><span>Bác sĩ</span><strong>' +
      escapeHtml(data.dentistName || "Chưa phân công") +
      "</strong></div>" +
      '<div class="cap-invoice-overview__item"><span>Ngày khám</span><strong>' +
      escapeHtml(appointmentDate || "Chưa cập nhật") +
      "</strong></div>" +
      '<div class="cap-invoice-overview__item"><span>Khung giờ</span><strong>' +
      escapeHtml(appointmentTime || "Chưa cập nhật") +
      "</strong></div>" +
      "</div>";
    var prescriptionHtml = prescriptionItems.length
      ? '<div class="cap-invoice-section">' +
        '<div class="cap-invoice-section__title">Thuốc kê đơn</div>' +
        '<div class="cap-invoice-prescription-list">' +
        prescriptionItems
          .map(function (item) {
            return (
              '<div class="cap-invoice-prescription-item">' +
              '<div class="cap-invoice-prescription-item__head">' +
              '<strong>' + escapeHtml(item.medicineName || "Thuốc") + "</strong>" +
              (item.dosage
                ? '<span class="cap-invoice-prescription-item__dosage">' +
                  escapeHtml(item.dosage) +
                  "</span>"
                : "") +
              "</div>" +
              (item.note
                ? '<div class="cap-invoice-prescription-item__note">' +
                  escapeHtml(item.note) +
                  "</div>"
                : "") +
              "</div>"
            );
          })
          .join("") +
        "</div>" +
        "</div>"
      : "";
    var billNoteHtml = billingNoteText
      ? '<div class="cap-invoice-section">' +
        '<div class="cap-invoice-section__title">Ghi chú từ bác sĩ</div>' +
        '<div class="cap-invoice-note">' + escapeHtml(billingNoteText) + "</div>" +
        (data.billingNoteUpdatedAt
          ? '<div class="cap-invoice-note-time">Cập nhật: ' +
            escapeHtml(formatDateTime(data.billingNoteUpdatedAt)) +
            "</div>"
          : "") +
        "</div>"
      : "";
    var itemsHtml = items.length
      ? '<div class="cap-invoice-section">' +
        '<div class="cap-invoice-section__title">Chi tiết dịch vụ</div>' +
        '<div class="cap-invoice-list">' +
        items
          .map(function (item) {
            var meta = [];
            if (item.qty != null) meta.push("SL: " + item.qty);
            if (item.unitPrice != null)
              meta.push("Đơn giá: " + formatMoney(item.unitPrice));
            if (item.toothNo) meta.push("Răng: " + item.toothNo);
            return (
              '<div class="cap-invoice-item">' +
              "<div>" +
              '<div class="cap-invoice-name">' +
              escapeHtml(item.name || "Dịch vụ") +
              "</div>" +
              '<div class="cap-invoice-meta">' +
              escapeHtml(meta.join(" • ")) +
              "</div>" +
              "</div>" +
              '<div class="cap-invoice-amount">' +
              escapeHtml(formatMoney(item.amount)) +
              "</div>" +
              "</div>"
            );
          })
          .join("") +
        "</div>" +
        "</div>"
      : '<div class="cap-invoice-section"><div class="cap-invoice-section__title">Chi tiết dịch vụ</div><div class="cap-muted">Chưa có dòng hóa đơn chi tiết.</div></div>';

    var totalsHtml =
      '<div class="cap-invoice-section">' +
      '<div class="cap-invoice-section__title">Tổng kết thanh toán</div>' +
      '<div class="cap-invoice-total">' +
      '<div class="cap-invoice-total-line"><span>Tổng billing</span><strong>' +
      escapeHtml(formatMoney(billedTotal)) +
      "</strong></div>" +
      '<div class="cap-invoice-total-line"><span>Đặt cọc ban đầu</span><strong>' +
      escapeHtml(formatMoney(depositAmount)) +
      "</strong></div>" +
      '<div class="cap-invoice-total-line"><span>Đã thanh toán</span><strong>' +
      escapeHtml(formatMoney(paidAmount)) +
      "</strong></div>" +
      (hasRemaining
        ? '<div class="cap-invoice-total-line cap-invoice-total-line--due"><span>Còn lại</span><strong>' +
          escapeHtml(formatMoney(remainingAmount)) +
          "</strong></div>"
        : '<div class="cap-invoice-settled"><i class="bi bi-patch-check-fill"></i><span>Hóa đơn đã được thanh toán xong.</span></div>') +
      "</div>" +
      "</div>";

    return (
      '<div class="cap-invoice-card">' +
      '<div class="cap-invoice-head">' +
      '<div class="cap-invoice-title"><i class="bi bi-file-earmark-text"></i> Hóa đơn thanh toán</div>' +
      '<div class="cap-invoice-chip">' +
      escapeHtml(statusLabel) +
      "</div>" +
      "</div>" +
      overviewHtml +
      itemsHtml +
      prescriptionHtml +
      billNoteHtml +
      totalsHtml +
      "</div>"
    );
  }

  function bindRemainingPaymentModal() {
    if (!paymentModalEl) return;

    if (paymentCloseEl) {
      paymentCloseEl.addEventListener("click", function () {
        closeRemainingPaymentModal();
      });
    }

    paymentModalEl
      .querySelectorAll("[data-payment-close]")
      .forEach(function (el) {
        el.addEventListener("click", function () {
          closeRemainingPaymentModal();
        });
      });

    paymentModalEl.addEventListener("click", function (e) {
      if (e.target === paymentModalEl) {
        closeRemainingPaymentModal();
      }
    });

    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && paymentModalEl && !paymentModalEl.hidden) {
        closeRemainingPaymentModal();
      }
    });

    if (paymentVnpayBtn) {
      paymentVnpayBtn.addEventListener("click", function () {
        if (!remainingPaymentSelection) return;
        paymentVnpayBtn.disabled = true;
        window.location.href =
          "/customer/payment/create-final-payment/" +
          remainingPaymentSelection.appointmentId;
      });
    }

    if (paymentWalletBtn) {
      paymentWalletBtn.addEventListener("click", function () {
        if (!remainingPaymentSelection) return;

        var appointmentId = remainingPaymentSelection.appointmentId;
        paymentWalletBtn.disabled = true;
        if (paymentVnpayBtn) paymentVnpayBtn.disabled = true;

        fetch("/customer/payment/final-payment/" + appointmentId + "/wallet", {
          method: "POST",
          credentials: "same-origin",
        })
          .then(function (res) {
            if (res.status === 401) {
              throw new Error("Bạn cần đăng nhập.");
            }
            return res.json().then(function (payload) {
              if (!res.ok || payload.success === false) {
                throw new Error(
                  payload.message || "Không thể thanh toán bằng ví.",
                );
              }
              return payload;
            });
          })
          .then(function () {
            closeRemainingPaymentModal();
            showToast(
              "Thanh toán phần còn lại thành công.",
              "success",
              "Thanh toán thành công",
            );
            loadAppointments(function () {
              openInlineDetail(appointmentId, true);
            }, state.page);
          })
          .catch(function (err) {
            if (paymentWalletBtn) paymentWalletBtn.disabled = false;
            if (paymentVnpayBtn) paymentVnpayBtn.disabled = false;
            showAlert(
              err.message || "Không thể thanh toán bằng ví.",
              "error",
              "Thanh toán ví thất bại",
            );
          });
      });
    }
  }

  function bindInvoiceModal() {
    if (!invoiceModalEl) return;

    if (invoiceModalCloseEl) {
      invoiceModalCloseEl.addEventListener("click", function () {
        closeInvoiceModal();
      });
    }

    invoiceModalEl.querySelectorAll("[data-invoice-close]").forEach(function (el) {
      el.addEventListener("click", function () {
        closeInvoiceModal();
      });
    });

    invoiceModalEl.addEventListener("click", function (e) {
      if (e.target === invoiceModalEl) {
        closeInvoiceModal();
      }
    });

    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape" && invoiceModalEl && !invoiceModalEl.hidden) {
        closeInvoiceModal();
      }
    });
  }

  function createInlineDetailShell() {
    var wrap = document.createElement("div");
    wrap.className = "cap-inline-detail";
    wrap.innerHTML =
      '<div class="cap-inline-detail-card">' +
      '  <div class="cap-inline-head">' +
      '    <div class="cap-inline-title"><i class="bi bi-info-circle"></i> Chi tiết lịch hẹn</div>' +
      '    <button type="button" class="cap-inline-close" aria-label="Đóng">' +
      '      <i class="bi bi-x-lg"></i>' +
      "    </button>" +
      "  </div>" +
      '  <div class="cap-inline-loading">Đang tải chi tiết...</div>' +
      '  <div class="cap-inline-content" style="display:none;"></div>' +
      "</div>";

    wrap
      .querySelector(".cap-inline-close")
      .addEventListener("click", function (e) {
        e.stopPropagation();
        closeCurrentDetail();
      });

    return wrap;
  }

  function renderInlineDetail(detailWrap, data, appointmentId) {
    var content = detailWrap.querySelector(".cap-inline-content");
    var loading = detailWrap.querySelector(".cap-inline-loading");
    var statusMeta = getStatusMeta(data.status);

    if (loading) loading.style.display = "none";
    if (content) content.style.display = "";

    var dentistHtml = data.dentistName
      ? escapeHtml(data.dentistName)
      : '<span class="cap-muted">Chưa phân công</span>';
    var notesValue = data.notes == null ? "" : String(data.notes).trim();
    var notesHtml = notesValue
      ? escapeHtml(notesValue)
      : '<span class="cap-muted">Không có ghi chú</span>';
    var contactHtml =
      data.contactChannel && data.contactValue
        ? escapeHtml(data.contactChannel + ": " + data.contactValue)
        : '<span class="cap-muted">Không có thông tin</span>';

    var canCancel =
      data.status !== "CANCELLED" &&
      data.status !== "COMPLETED" &&
      data.status !== "WAITING_PAYMENT";
    var canCheckin = !!data.canCheckIn;
    var canPayRemaining = !!data.canPayRemaining;
    var depositPaidHtml =
      data.status === "PENDING" ||
      data.status === "CONFIRMED" ||
      data.status === "CHECKED_IN" ||
      data.status === "EXAMINING" ||
      data.status === "DONE" ||
      data.status === "WAITING_PAYMENT" ||
      data.status === "REEXAM" ||
      data.status === "IN_PROGRESS"
        ? '<div class="cap-inline-note cap-inline-note-success"><i class="bi bi-check-circle-fill"></i><span>Đã thanh toán đặt cọc 50%</span></div>'
        : "";
    var remainingPaymentHtml = canPayRemaining
      ? '<div class="cap-inline-note cap-inline-note-warning">' +
        '<i class="bi bi-receipt"></i>' +
        "<span>Còn lại cần thanh toán: <strong>" +
        escapeHtml(formatMoney(data.remainingAmount)) +
        "</strong>" +
        (data.invoiceId ? " - Mã hóa đơn #" + escapeHtml(data.invoiceId) : "") +
        "</span></div>"
      : "";
    var invoicePreviewMeta = ['Tổng billing ' + escapeHtml(formatMoney(data.billedTotal))];
    if (shouldShowRemaining(data)) {
      invoicePreviewMeta.push(
        'Còn lại ' + escapeHtml(formatMoney(data.remainingAmount)),
      );
    } else {
      invoicePreviewMeta.push("Đã thanh toán xong");
    }
    var invoicePreviewHtml = data.invoiceId
      ? '<div class="cap-invoice-preview">' +
        '<div class="cap-invoice-preview__copy">' +
        '<div class="cap-invoice-preview__title"><i class="bi bi-file-earmark-text"></i> Hóa đơn #' + escapeHtml(data.invoiceId) + '</div>' +
        '<div class="cap-invoice-preview__meta">' + invoicePreviewMeta.join(" • ") + '</div>' +
        '</div>' +
        '<button type="button" class="cap-btn cap-btn-neutral" data-action="view-invoice"><i class="bi bi-receipt-cutoff"></i> Xem hóa đơn</button>' +
        "</div>"
      : "";

    content.innerHTML =
      '<div class="cap-inline-grid">' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Dịch vụ</div><div class="cap-inline-value">' +
      escapeHtml(data.serviceName || "") +
      "</div></div>" +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Bác sĩ</div><div class="cap-inline-value">' +
      dentistHtml +
      "</div></div>" +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Ngày khám</div><div class="cap-inline-value">' +
      escapeHtml(formatDate(data.date)) +
      "</div></div>" +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Giờ khám</div><div class="cap-inline-value">' +
      escapeHtml(formatTime(data.startTime)) +
      " - " +
      escapeHtml(formatTime(data.endTime)) +
      "</div></div>" +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Trạng thái</div><div class="cap-inline-value"><span class="cap-status-badge ' +
      statusMeta.className +
      '">' +
      escapeHtml(statusMeta.label) +
      "</span></div></div>" +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Liên hệ</div><div class="cap-inline-value">' +
      contactHtml +
      "</div></div>" +
      '  <div class="cap-inline-row cap-inline-notes"><div class="cap-inline-label">Ghi chú</div><div class="cap-inline-value">' +
      notesHtml +
      "</div></div>" +
      depositPaidHtml +
      remainingPaymentHtml +
      invoicePreviewHtml +
      "</div>" +
      '<div class="cap-inline-actions">' +
      (canCancel
        ? '<button type="button" class="cap-btn cap-btn-danger" data-action="cancel"><i class="bi bi-x-circle"></i> Hủy lịch</button>'
        : "") +
      (canPayRemaining
        ? '<button type="button" class="cap-btn cap-btn-warning" data-action="pay-remaining"><i class="bi bi-credit-card"></i> Thanh toán phần còn lại</button>'
        : "") +
      "</div>";

    var checkinBtn = content.querySelector('[data-action="checkin"]');
    if (checkinBtn) {
      checkinBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        checkinBtn.disabled = true;

        fetch("/customer/appointments/" + appointmentId + "/checkin", {
          method: "POST",
          credentials: "same-origin",
        })
          .then(function (res) {
            if (res.status === 401) {
              showAlert("Bạn cần đăng nhập.", "warning", "Chưa đăng nhập");
              checkinBtn.disabled = false;
              return null;
            }
            if (!res.ok) {
              return res.json().then(function (er) {
                throw new Error(extractApiError(er, "Check-in thất bại"));
              });
            }
            return res.json();
          })
          .then(function () {
            checkinBtn.disabled = false;
            loadAppointments(function () {
              openInlineDetail(appointmentId, true);
            }, state.page);
          })
          .catch(function (err) {
            checkinBtn.disabled = false;
            showAlert(
              err.message || "Check-in thất bại.",
              "error",
              "Check-in thất bại",
            );
          });
      });
    }

    var cancelBtn = content.querySelector('[data-action="cancel"]');
    if (cancelBtn) {
      cancelBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        askConfirm("Bạn có chắc chắn muốn hủy lịch hẹn này không?", {
          title: "Xác nhận hủy lịch",
          type: "warning",
          confirmText: "Hủy lịch",
          cancelText: "Quay lại",
        }).then(function (confirmed) {
          if (!confirmed) return;

          cancelBtn.disabled = true;

          fetch("/customer/appointments/" + appointmentId + "/cancel", {
            method: "POST",
            credentials: "same-origin",
          })
            .then(function (res) {
              if (res.status === 401) {
                showAlert("Bạn cần đăng nhập.", "warning", "Chưa đăng nhập");
                cancelBtn.disabled = false;
                return null;
              }
              if (!res.ok) {
                return res.json().then(function (er) {
                  throw new Error(extractApiError(er, "Hủy lịch thất bại"));
                });
              }
              return res.json();
            })
            .then(function () {
              cancelBtn.disabled = false;
              showToast("Hủy lịch thành công.", "success", "Đã cập nhật");
              loadAppointments(function () {
                openInlineDetail(appointmentId, true);
              }, state.page);
            })
            .catch(function (err) {
              cancelBtn.disabled = false;
              showAlert(
                err.message || "Hủy lịch thất bại.",
                "error",
                "Hủy lịch thất bại",
              );
            });
        });
      });
    }

    var payRemainingBtn = content.querySelector(
      '[data-action="pay-remaining"]',
    );
    if (payRemainingBtn) {
      payRemainingBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        openRemainingPaymentModal(data, appointmentId);
      });
    }

    var viewInvoiceBtn = content.querySelector('[data-action="view-invoice"]');
    if (viewInvoiceBtn) {
      viewInvoiceBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        openInvoiceModal(data);
      });
    }
  }

  function formatMoney(value) {
    var num = toNumber(value);
    return num.toLocaleString("vi-VN") + " VND";
  }

  function openInlineDetail(appointmentId, keepOpenAfterReload) {
    if (!keepOpenAfterReload && currentOpen.appointmentId === appointmentId) {
      closeCurrentDetail();
      return;
    }

    closeCurrentDetail();

    var li = listEl.querySelector(
      'li[data-appointment-id="' + appointmentId + '"]',
    );
    if (!li) return;

    li.classList.add("cap-item-active");

    var detailWrap = createInlineDetailShell();
    li.insertAdjacentElement("afterend", detailWrap);

    currentOpen.appointmentId = appointmentId;
    currentOpen.detailEl = detailWrap;

    fetch("/customer/appointments/" + appointmentId, {
      credentials: "same-origin",
    })
      .then(function (r) {
        if (r.status === 401) {
          showAlert("Bạn cần đăng nhập.", "warning", "Chưa đăng nhập");
          return null;
        }
        if (r.status === 404) throw new Error("Không tìm thấy lịch hẹn.");
        if (!r.ok) {
          return r.json().then(function (e) {
            throw new Error(e.error || "Không thể tải chi tiết.");
          });
        }
        return r.json();
      })
      .then(function (data) {
        if (!data) return;
        renderInlineDetail(detailWrap, data, appointmentId);
        detailWrap.scrollIntoView({ behavior: "smooth", block: "nearest" });
      })
      .catch(function (err) {
        var loading = detailWrap.querySelector(".cap-inline-loading");
        if (loading)
          loading.textContent = err.message || "Không thể tải chi tiết.";
      });
  }

  function renderPagination() {
    if (!paginationEl) return;

    paginationEl.innerHTML = "";
    if (state.totalPages <= 1) {
      paginationEl.style.display = "none";
      return;
    }
    paginationEl.style.display = "";

    function addBtn(label, page, disabled, active) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "cap-page-btn" + (active ? " active" : "");
      btn.textContent = label;
      btn.disabled = !!disabled;
      if (!disabled) {
        btn.addEventListener("click", function () {
          loadAppointments(null, page);
        });
      }
      paginationEl.appendChild(btn);
    }

    addBtn("Trước", state.page - 1, state.page <= 0, false);
    for (var i = 0; i < state.totalPages; i++) {
      addBtn(String(i + 1), i, false, i === state.page);
    }
    addBtn("Sau", state.page + 1, state.page >= state.totalPages - 1, false);
  }

  function renderAppointmentItem(apt) {
    var statusMeta = getStatusMeta(apt.status);
    var li = document.createElement("li");
    li.dataset.appointmentId = apt.id;
    li.setAttribute("data-appointment-id", apt.id);

    if (
      window.__lastCreatedAppointmentId &&
      window.__lastCreatedAppointmentId === apt.id
    ) {
      li.classList.add("highlight-new");
    }

    li.innerHTML =
      '<div class="cap-item-row">' +
      '  <div class="cap-item-main">' +
      '    <div class="apt-date"><i class="bi bi-calendar3"></i> ' +
      escapeHtml(formatDate(apt.date)) +
      ' <span class="cap-dot"></span> ' +
      escapeHtml(formatTime(apt.startTime)) +
      "</div>" +
      '    <div class="apt-service">' +
      escapeHtml(apt.serviceName || "") +
      "</div>" +
      '    <div class="apt-meta">Mã lịch hẹn #' +
      escapeHtml(apt.id) +
      "</div>" +
      "  </div>" +
      '  <div class="cap-item-side">' +
      '    <span class="apt-status ' +
      statusMeta.className +
      '" data-status="' +
      escapeHtml(apt.status || "") +
      '">' +
      escapeHtml(statusMeta.label) +
      "</span>" +
      '    <i class="bi bi-chevron-down cap-item-chevron"></i>' +
      "  </div>" +
      "</div>";

    li.addEventListener("click", function () {
      openInlineDetail(apt.id, false);
    });

    return li;
  }

  function loadAppointments(doneCb, targetPage) {
    var listWrap = document.getElementById("customer-appointments-list-wrap");
    var empty = document.getElementById("customer-appointments-empty");
    var loading = document.getElementById("customer-appointments-loading");

    var requestedPage =
      typeof targetPage === "number" ? targetPage : state.page;
    if (requestedPage < 0) requestedPage = 0;

    if (listWrap) listWrap.style.display = "none";
    if (empty) empty.style.display = "none";
    if (paginationEl) paginationEl.style.display = "none";
    listEl.innerHTML = "";
    if (loading) loading.style.display = "";

    closeCurrentDetail();

    var query =
      "/customer/appointments?page=" + requestedPage + "&size=" + state.size;
    if (state.keyword) {
      query += "&keyword=" + encodeURIComponent(state.keyword);
    }
    if (state.sort) {
      query += "&sort=" + encodeURIComponent(state.sort);
    }

    fetch(query, {
      credentials: "same-origin",
    })
      .then(function (r) {
        if (r.status === 401) {
          showAlert(
            "Bạn cần đăng nhập để xem lịch hẹn.",
            "warning",
            "Chưa đăng nhập",
          );
          if (loading) loading.style.display = "none";
          return null;
        }
        return r.json();
      })
      .then(function (data) {
        if (!data) return;
        if (!Array.isArray(data.content)) {
          data = {
            content: Array.isArray(data) ? data : [],
            page: 0,
            size: state.size,
            totalPages: Array.isArray(data) && data.length > 0 ? 1 : 0,
            totalElements: Array.isArray(data) ? data.length : 0,
          };
        }

        state.page = data.page || 0;
        state.totalPages = data.totalPages || 0;
        state.sort = data.sort || state.sort || "newest";
        if (sortSelectEl) {
          sortSelectEl.value = state.sort;
        }

        if (summaryTotalEl)
          summaryTotalEl.textContent = String(
            data.totalElements || data.content.length || 0,
          );
        if (summaryPageEl)
          summaryPageEl.textContent = String((state.page || 0) + 1);

        if (loading) loading.style.display = "none";
        if (listWrap) listWrap.style.display = "";

        if (data.content.length > 0) {
          data.content.forEach(function (apt) {
            listEl.appendChild(renderAppointmentItem(apt));
          });
          renderPagination();
        } else if (empty) {
          empty.style.display = "";
          var emptyTitle = empty.querySelector(".cap-empty-title");
          var emptyDesc = empty.querySelector(".cap-empty-desc");
          if (emptyTitle && emptyDesc) {
            if (state.keyword) {
              emptyTitle.textContent = "Không tìm thấy lịch hẹn phù hợp";
              emptyDesc.textContent =
                "Hãy thử đổi từ khóa hoặc xóa tìm kiếm để xem lại toàn bộ lịch hẹn.";
            } else {
              emptyTitle.textContent = "Bạn chưa có lịch hẹn nào";
              emptyDesc.textContent =
                "Hãy đặt lịch khám để phòng khám có thể sắp xếp thời gian hỗ trợ bạn.";
            }
          }
        }

        if (typeof doneCb === "function") doneCb();
      })
      .catch(function () {
        if (loading) loading.style.display = "none";
        if (listWrap) listWrap.style.display = "";
        showAlert(
          "Không thể tải danh sách lịch hẹn.",
          "error",
          "Tải dữ liệu thất bại",
        );
      });
  }

  var hash = window.location.hash;
  var openFromNotificationId = null;
  if (hash && hash.indexOf("highlight=") !== -1) {
    var m = hash.match(/highlight=(\d+)/);
    if (m) {
      openFromNotificationId = parseInt(m[1], 10);
      window.__lastCreatedAppointmentId = openFromNotificationId;
    }
  }

  var paymentStatus = queryParams.get("payment");
  if (paymentStatus === "success") {
    showToast(
      "Thanh toán phần còn lại thành công.",
      "success",
      "Thanh toán thành công",
    );
    queryParams.delete("payment");
    history.replaceState(
      null,
      "",
      window.location.pathname + (window.location.hash || ""),
    );
  } else if (paymentStatus === "fail") {
    showAlert(
      "Thanh toán chưa hoàn tất hoặc đã bị hủy.",
      "warning",
      "Thanh toán chưa hoàn tất",
    );
    queryParams.delete("payment");
    history.replaceState(
      null,
      "",
      window.location.pathname + (window.location.hash || ""),
    );
  }

  function applySearch(keyword) {
    state.keyword = String(keyword || "").trim();
    if (clearSearchEl) {
      clearSearchEl.disabled = state.keyword.length === 0;
    }
    loadAppointments(null, 0);
  }

  if (searchInputEl) {
    searchInputEl.addEventListener("input", function () {
      if (searchTimer) {
        window.clearTimeout(searchTimer);
      }
      searchTimer = window.setTimeout(function () {
        applySearch(searchInputEl.value);
      }, 250);
    });
  }

  if (clearSearchEl) {
    clearSearchEl.addEventListener("click", function () {
      if (searchInputEl) {
        searchInputEl.value = "";
      }
      applySearch("");
    });
    clearSearchEl.disabled = true;
  }

  if (sortSelectEl) {
    sortSelectEl.addEventListener("change", function () {
      state.sort = sortSelectEl.value || "newest";
      loadAppointments(null, 0);
    });
  }

  bindRemainingPaymentModal();
  bindInvoiceModal();

  loadAppointments(function () {
    if (!openFromNotificationId) return;
    var target = listEl.querySelector(
      'li[data-appointment-id="' + openFromNotificationId + '"]',
    );
    if (target) {
      openInlineDetail(openFromNotificationId, false);
      history.replaceState(null, "", window.location.pathname);
    }
  });
})();

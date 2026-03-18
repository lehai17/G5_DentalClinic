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
  var viewDefaultBtnEl = document.getElementById(
    "customer-appointments-view-default",
  );
  var viewCancelledBtnEl = document.getElementById(
    "customer-appointments-view-cancelled",
  );
  var paymentModalEl = document.getElementById("cap-payment-modal");
  var paymentCloseEl = document.getElementById("cap-payment-close");
  var paymentAppointmentIdEl = document.getElementById(
    "cap-payment-appointment-id",
  );
  var paymentInvoiceIdEl = document.getElementById("cap-payment-invoice-id");
  var paymentAmountEl = document.getElementById("cap-payment-amount");
  var paymentOriginalLineEl = document.getElementById("cap-payment-original-line");
  var paymentOriginalAmountEl = document.getElementById("cap-payment-original-amount");
  var paymentDiscountLineEl = document.getElementById("cap-payment-discount-line");
  var paymentDiscountAmountEl = document.getElementById("cap-payment-discount-amount");
  var paymentVoucherInputEl = document.getElementById("cap-payment-voucher-code");
  var paymentVoucherApplyBtn = document.getElementById("cap-payment-voucher-apply");
  var paymentVoucherFeedbackEl = document.getElementById("cap-payment-voucher-feedback");
  var paymentWalletBtn = document.getElementById("cap-payment-wallet");
  var paymentVnpayBtn = document.getElementById("cap-payment-vnpay");
  var invoiceModalEl = document.getElementById("cap-invoice-modal");
  var invoiceModalCloseEl = document.getElementById("cap-invoice-close");
  var invoiceModalContentEl = document.getElementById("cap-invoice-modal-content");
    var reviewModalEl = document.getElementById("cap-review-modal");
    var reviewModalCloseEl = document.getElementById("cap-review-close");
    var reviewSubmitEl = document.getElementById("cap-review-submit");
    var reviewCommentEl = document.getElementById("cap-review-comment");

    var reviewDentistHintEl = document.getElementById("cap-review-dentist-hint");
    var reviewServiceHintEl = document.getElementById("cap-review-service-hint");

    var reviewDentistStarEls = Array.prototype.slice.call(
        document.querySelectorAll(".dentist-star")
    );
    var reviewServiceStarEls = Array.prototype.slice.call(
        document.querySelectorAll(".service-star")
    );
  var searchTimer = null;
  var queryParams = new URLSearchParams(window.location.search);
  var state = {
    page: 0,
    size: 5,
    totalPages: 0,
    keyword: "",
    sort: "date_desc",
    view: queryParams.get("view") === "cancelled" ? "cancelled" : "default",
  };
  var remainingPaymentSelection = null;
  var reviewSelection = { appointmentId: null, rating: 0 };

  var currentOpen = {
    appointmentId: null,
    detailEl: null,
  };

  function normalizeText(value) {
    if (value == null) return "";
    var text = String(value);
    if (!/[ÃÂÄÆÐï]/.test(text)) return text;
    try {
      return decodeURIComponent(escape(text));
    } catch (err) {
      return text;
    }
  }

  function showToast(message, type, title) {
    if (window.CustomerFeedback) {
      window.CustomerFeedback.toast({
        message: normalizeText(message),
        type: type || "info",
        title: normalizeText(title || ""),
      });
      return;
    }
  }

  function showAlert(message, type, title) {
    if (window.CustomerFeedback) {
      return window.CustomerFeedback.alert({
        message: normalizeText(message),
        type: type || "info",
        title: normalizeText(title || "Thông báo"),
      });
    }
    alert(normalizeText(message));
    return Promise.resolve();
  }

  function askConfirm(message, options) {
    var normalizedOptions = Object.assign({}, options || {});
    if (normalizedOptions.title) {
      normalizedOptions.title = normalizeText(normalizedOptions.title);
    }
    if (normalizedOptions.confirmText) {
      normalizedOptions.confirmText = normalizeText(normalizedOptions.confirmText);
    }
    if (normalizedOptions.cancelText) {
      normalizedOptions.cancelText = normalizeText(normalizedOptions.cancelText);
    }
    if (window.CustomerFeedback) {
      return window.CustomerFeedback.confirm(
        Object.assign({ message: normalizeText(message) }, normalizedOptions),
      );
    }
    return Promise.resolve(confirm(normalizeText(message)));
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
    return normalizeText(String(s))
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
    var result = map[key] || { label: key || "Không xác định", className: "default" };
    result.label = normalizeText(result.label);
    return result;
  }

  function formatInvoiceStatus(status) {
    var key = String(status || "").toUpperCase();
    var map = {
      PAID: "Đã thanh toán",
      UNPAID: "Chưa thanh toán",
    };
    return normalizeText(map[key] || status || "Không xác định");
  }

  function extractApiError(payload, fallbackMessage) {
    if (!payload) return fallbackMessage;
    if (payload.message) return normalizeText(payload.message);
    if (payload.error) return normalizeText(payload.error);
    return normalizeText(fallbackMessage);
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

  function isRefundEligibleForCancel(data) {
    if (!data || !data.date) return false;
    var appointmentDate = new Date(data.date + "T00:00:00");
    if (isNaN(appointmentDate.getTime())) return false;
    var now = new Date();
    now.setHours(0, 0, 0, 0);
    return now < appointmentDate;
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
    if (paymentVoucherApplyBtn) paymentVoucherApplyBtn.disabled = false;
    if (paymentVoucherInputEl) paymentVoucherInputEl.value = "";
    if (paymentVoucherFeedbackEl) paymentVoucherFeedbackEl.textContent = "";
    if (paymentOriginalLineEl) paymentOriginalLineEl.hidden = true;
    if (paymentDiscountLineEl) paymentDiscountLineEl.hidden = true;
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
      originalAmount:
        data.originalRemainingAmount != null
          ? data.originalRemainingAmount
          : data.remainingAmount,
      discountAmount: data.discountAmount || 0,
      voucherCode: data.voucherCode || "",
    };

    if (paymentAppointmentIdEl)
      paymentAppointmentIdEl.textContent = "#" + appointmentId;
    if (paymentInvoiceIdEl)
      paymentInvoiceIdEl.textContent = data.invoiceId
        ? "#" + data.invoiceId
        : normalizeText("Chưa có");
    if (paymentAmountEl)
      paymentAmountEl.textContent = formatMoney(data.remainingAmount);
    if (paymentOriginalAmountEl)
      paymentOriginalAmountEl.textContent = formatMoney(
        remainingPaymentSelection.originalAmount,
      );
    if (paymentDiscountAmountEl)
      paymentDiscountAmountEl.textContent = formatMoney(
        remainingPaymentSelection.discountAmount,
      );
    if (paymentOriginalLineEl)
      paymentOriginalLineEl.hidden =
        toNumber(remainingPaymentSelection.originalAmount) <=
        toNumber(data.remainingAmount);
    if (paymentDiscountLineEl)
      paymentDiscountLineEl.hidden =
        toNumber(remainingPaymentSelection.discountAmount) <= 0;
    if (paymentVoucherInputEl)
      paymentVoucherInputEl.value = remainingPaymentSelection.voucherCode || "";
    if (paymentVoucherFeedbackEl) {
      paymentVoucherFeedbackEl.textContent = data.voucherCode
        ? "Đã áp dụng voucher " + data.voucherCode + "."
        : "";
    }
    if (paymentWalletBtn) paymentWalletBtn.disabled = false;
    if (paymentVnpayBtn) paymentVnpayBtn.disabled = false;
    if (paymentVoucherApplyBtn) paymentVoucherApplyBtn.disabled = false;

    paymentModalEl.hidden = false;
    document.body.classList.add("cap-payment-modal-open");
  }

  function updateRemainingPaymentPreview(preview) {
    if (!remainingPaymentSelection || !preview) return;

    remainingPaymentSelection.invoiceId = preview.invoiceId;
    remainingPaymentSelection.amount = preview.payableAmount;
    remainingPaymentSelection.originalAmount = preview.originalAmount;
    remainingPaymentSelection.discountAmount = preview.discountAmount || 0;
    remainingPaymentSelection.voucherCode = preview.voucherCode || "";

    if (paymentInvoiceIdEl)
      paymentInvoiceIdEl.textContent = preview.invoiceId
        ? "#" + preview.invoiceId
        : normalizeText("Chưa có");
    if (paymentAmountEl)
      paymentAmountEl.textContent = formatMoney(preview.payableAmount);
    if (paymentOriginalAmountEl)
      paymentOriginalAmountEl.textContent = formatMoney(preview.originalAmount);
    if (paymentDiscountAmountEl)
      paymentDiscountAmountEl.textContent = formatMoney(preview.discountAmount || 0);
    if (paymentOriginalLineEl)
      paymentOriginalLineEl.hidden =
        toNumber(preview.originalAmount) <= toNumber(preview.payableAmount);
    if (paymentDiscountLineEl)
      paymentDiscountLineEl.hidden = toNumber(preview.discountAmount) <= 0;
    if (paymentVoucherInputEl)
      paymentVoucherInputEl.value = preview.voucherCode || "";
    if (paymentVoucherFeedbackEl) {
      paymentVoucherFeedbackEl.textContent = preview.voucherApplied
        ? normalizeText(
            "Áp dụng voucher " +
              preview.voucherCode +
              (preview.voucherDescription
                ? ": " + preview.voucherDescription
                : "."),
          )
        : normalizeText(
            (paymentVoucherInputEl && paymentVoucherInputEl.value.trim())
              ? "Đã bỏ voucher khỏi hóa đơn."
              : "",
          );
    }
  }

  function applyVoucherPreview() {
    if (!remainingPaymentSelection) return Promise.resolve();

    var voucherCode = paymentVoucherInputEl ? paymentVoucherInputEl.value.trim() : "";
    if (paymentVoucherApplyBtn) paymentVoucherApplyBtn.disabled = true;
    if (paymentWalletBtn) paymentWalletBtn.disabled = true;
    if (paymentVnpayBtn) paymentVnpayBtn.disabled = true;

    var url =
      "/customer/payment/final-payment/" +
      remainingPaymentSelection.appointmentId +
      "/preview";
    if (voucherCode) {
      url += "?voucherCode=" + encodeURIComponent(voucherCode);
    }

    return fetch(url, { credentials: "same-origin" })
      .then(function (res) {
        if (res.status === 401) {
          throw new Error("Bạn cần đăng nhập.");
        }
        return res.json().then(function (payload) {
          if (!res.ok || payload.success === false || !payload.data) {
            throw new Error(
              extractApiError(payload, "Không thể áp dụng voucher."),
            );
          }
          return payload.data;
        });
      })
      .then(function (preview) {
        updateRemainingPaymentPreview(preview);
      })
      .catch(function (err) {
        if (paymentVoucherFeedbackEl) {
          paymentVoucherFeedbackEl.textContent = err.message || "Không thể áp dụng voucher.";
        }
        throw err;
      })
      .finally(function () {
        if (paymentVoucherApplyBtn) paymentVoucherApplyBtn.disabled = false;
        if (paymentWalletBtn) paymentWalletBtn.disabled = false;
        if (paymentVnpayBtn) paymentVnpayBtn.disabled = false;
      });
  }

  function buildInvoiceHtml(data) {
    if (!data || !data.invoiceId) return "";

    var items = Array.isArray(data.invoiceItems) ? data.invoiceItems : [];
    var hasRemaining = shouldShowRemaining(data);
    var settled = isSettledInvoice(data);
    var billedTotal = toNumber(data.billedTotal);
    var depositAmount = toNumber(data.depositAmount);
    var originalRemaining = toNumber(
      data.originalRemainingAmount != null
        ? data.originalRemainingAmount
        : data.remainingAmount,
    );
    var discountAmount = toNumber(data.discountAmount);
    var remainingAmount = toNumber(data.remainingAmount);
    var patientTotal = Math.max(billedTotal - discountAmount, 0);
    var paidAmount = settled
      ? patientTotal
      : Math.max(patientTotal - remainingAmount, 0);
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
      '<div class="cap-invoice-total-line"><span>Tạm tính sau đặt cọc</span><strong>' +
      escapeHtml(formatMoney(originalRemaining)) +
      "</strong></div>" +
      (discountAmount > 0
        ? '<div class="cap-invoice-total-line"><span>Voucher giảm giá' +
          (data.voucherCode ? " (" + escapeHtml(data.voucherCode) + ")" : "") +
          '</span><strong>-' +
          escapeHtml(formatMoney(discountAmount)) +
          "</strong></div>"
        : "") +
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

    return normalizeText(
      (
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
      )
    );
  }

    function closeReviewModal() {
        if (!reviewModalEl) return;

        reviewModalEl.hidden = true;
        reviewSelection = {
            appointmentId: null,
            dentistRating: 0,
            serviceRating: 0
        };

        if (reviewCommentEl) reviewCommentEl.value = "";
        updateDentistReviewStars(0);
        updateServiceReviewStars(0);

        document.body.classList.remove("cap-payment-modal-open");

        if (reviewSubmitEl) reviewSubmitEl.disabled = false;
    }

    function updateDentistReviewStars(rating) {
        reviewSelection.dentistRating = rating || 0;
        reviewDentistStarEls.forEach(function (starEl) {
            var starRating = Number(starEl.dataset.rating || 0);
            starEl.classList.toggle("active", starRating <= reviewSelection.dentistRating);
        });

        if (reviewDentistHintEl) {
            reviewDentistHintEl.textContent = reviewSelection.dentistRating > 0
                ? "Bạn đang chọn " + reviewSelection.dentistRating + " sao cho bác sĩ"
                : "Chọn số sao cho bác sĩ";
        }
    }

    function updateServiceReviewStars(rating) {
        reviewSelection.serviceRating = rating || 0;
        reviewServiceStarEls.forEach(function (starEl) {
            var starRating = Number(starEl.dataset.rating || 0);
            starEl.classList.toggle("active", starRating <= reviewSelection.serviceRating);
        });

        if (reviewServiceHintEl) {
            reviewServiceHintEl.textContent = reviewSelection.serviceRating > 0
                ? "Bạn đang chọn " + reviewSelection.serviceRating + " sao cho dịch vụ"
                : "Chọn số sao cho dịch vụ";
        }
    }

    function openReviewModal(appointmentId) {
        if (!reviewModalEl) return;
        reviewSelection = { appointmentId: appointmentId, dentistRating: 0, serviceRating: 0 };
        if (reviewCommentEl) reviewCommentEl.value = "";
        updateDentistReviewStars(0);
        updateServiceReviewStars(0);
        reviewModalEl.hidden = false;
        document.body.classList.add("cap-payment-modal-open");
        if (reviewCommentEl) reviewCommentEl.focus();
    }

    reviewDentistStarEls.forEach(function (starEl) {
        starEl.addEventListener("click", function () {
            updateDentistReviewStars(Number(starEl.dataset.rating || 0));
        });
    });

    reviewServiceStarEls.forEach(function (starEl) {
        starEl.addEventListener("click", function () {
            updateServiceReviewStars(Number(starEl.dataset.rating || 0));
        });
    });

    if (reviewSubmitEl) {
        reviewSubmitEl.addEventListener("click", function () {
            if (!reviewSelection.appointmentId) return;

            if (!reviewSelection.dentistRating) {
                showAlert("Vui lòng chọn số sao đánh giá bác sĩ.", "warning", "Thiếu thông tin");
                return;
            }

            if (!reviewSelection.serviceRating) {
                showAlert("Vui lòng chọn số sao đánh giá dịch vụ.", "warning", "Thiếu thông tin");
                return;
            }

            reviewSubmitEl.disabled = true;

            fetch("/customer/appointments/" + reviewSelection.appointmentId + "/review", {
                method: "POST",
                credentials: "same-origin",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    dentistRating: reviewSelection.dentistRating,
                    serviceRating: reviewSelection.serviceRating,
                    comment: reviewCommentEl ? reviewCommentEl.value : ""
                })
            })
                .then(function (res) {
                    if (res.status === 401) {
                        throw new Error("Bạn cần đăng nhập.");
                    }
                    return res.json().then(function (payload) {
                        if (!res.ok || payload.success === false) {
                            throw new Error(payload.message || "Không thể gửi đánh giá.");
                        }
                        return payload;
                    });
                })
                .then(function (payload) {
                    var reviewedAppointmentId = reviewSelection.appointmentId;
                    closeReviewModal();
                    showToast(
                        payload.message || "Đã gửi đánh giá thành công.",
                        "success",
                        "Cảm ơn bạn"
                    );
                    loadAppointments(function () {
                        openInlineDetail(reviewedAppointmentId, true);
                    }, state.page);
                })
                .catch(function (err) {
                    if (reviewSubmitEl) reviewSubmitEl.disabled = false;
                    showAlert(
                        err.message || "Không thể gửi đánh giá.",
                        "error",
                        "Gửi đánh giá thất bại"
                    );
                });
        });
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

    if (paymentVoucherApplyBtn) {
      paymentVoucherApplyBtn.addEventListener("click", function () {
        applyVoucherPreview().catch(function () {});
      });
    }

    if (paymentVnpayBtn) {
      paymentVnpayBtn.addEventListener("click", function () {
        if (!remainingPaymentSelection) return;
        paymentVnpayBtn.disabled = true;
        var url =
          "/customer/payment/create-final-payment/" +
          remainingPaymentSelection.appointmentId;
        if (remainingPaymentSelection.voucherCode) {
          url +=
            "?voucherCode=" +
            encodeURIComponent(remainingPaymentSelection.voucherCode);
        }
        window.location.href = url;
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
          headers: {
            "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
          },
          body:
            "voucherCode=" +
            encodeURIComponent(remainingPaymentSelection.voucherCode || ""),
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
    wrap.innerHTML = normalizeText(
      (
      '<div class="cap-inline-detail-card">' +
      '  <div class="cap-inline-head">' +
      '    <div class="cap-inline-title"><i class="bi bi-info-circle"></i> Chi tiết lịch hẹn</div>' +
      '    <button type="button" class="cap-inline-close" aria-label="Đóng">' +
      '      <i class="bi bi-x-lg"></i>' +
      "    </button>" +
      "  </div>" +
      '  <div class="cap-inline-loading">Đang tải chi tiết...</div>' +
      '  <div class="cap-inline-content" style="display:none;"></div>' +
      "</div>"
      )
    );

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
    if (
      data.status === "CANCELLED" &&
      data.cancellationReason &&
      notesValue === String(data.cancellationReason).trim()
    ) {
      notesValue = "";
    }
    var notesHtml = notesValue
      ? escapeHtml(notesValue)
      : '<span class="cap-muted">Không có ghi chú</span>';
    var bookedAtHtml = data.createdAt
      ? escapeHtml(formatDateTime(data.createdAt))
      : '<span class="cap-muted">Chưa có dữ liệu</span>';
    var contactHtml =
      data.contactChannel && data.contactValue
        ? escapeHtml(data.contactChannel + ": " + data.contactValue)
        : '<span class="cap-muted">Không có thông tin</span>';

    var canCancel =
      data.status !== "CANCELLED" &&
      data.status !== "COMPLETED" &&
      data.status !== "CHECKED_IN" &&
      data.status !== "WAITING_PAYMENT" &&
      data.status !== "DONE" &&
      data.status !== "EXAMINING" &&
      data.status !== "IN_PROGRESS";
    var canPayRemaining = !!data.canPayRemaining;
    var canReview = !!data.canReview;
    var hasDepositReceipt = toNumber(data.depositAmount) > 0;
    var paymentHistoryHtml =
      '<a class="cap-btn cap-btn-neutral" href="/customer/payments"><i class="bi bi-clock-history"></i> Lịch sử thanh toán</a>';
    var depositReceiptHtml = hasDepositReceipt
      ? '<a class="cap-btn cap-btn-neutral" href="/customer/payments/deposit/' +
        appointmentId +
        '"><i class="bi bi-receipt"></i> Xem biên nhận đặt cọc</a>'
      : "";
    var invoiceReceiptHtml = data.invoiceId
      ? '<a class="cap-btn cap-btn-neutral" href="/customer/payments/invoice/' +
        escapeHtml(data.invoiceId) +
        '"><i class="bi bi-file-earmark-text"></i> Xem chi tiết thanh toán</a>'
      : "";
    var rebookHtml = data.canRebook
      ? '<a class="cap-btn cap-btn-neutral" href="/customer/appointments/' +
        appointmentId +
        '/rebook"><i class="bi bi-arrow-repeat"></i> Đặt lại lịch</a>'
      : "";
    var reviewActionHtml = canReview
      ? '<button type="button" class="cap-btn cap-btn-primary" data-action="review"><i class="bi bi-star"></i> Đánh giá bác sĩ</button>'
      : "";
      var reviewSummaryHtml = data.reviewed
          ? '<div class="cap-inline-note cap-inline-note-review">' +
          '<i class="bi bi-star-fill"></i>' +
          '<span>Bạn đã đánh giá <strong>bác sĩ ' +
          escapeHtml(String(data.dentistReviewRating || "")) +
          '/5 sao</strong> và <strong>dịch vụ ' +
          escapeHtml(String(data.serviceReviewRating || "")) +
          '/5 sao</strong>' +
          (data.reviewComment ? ': ' + escapeHtml(data.reviewComment) : '.') +
          '</span></div>'
          : "";
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
    var cancelledDepositHtml =
      data.status === "CANCELLED" && data.depositRefunded
        ? '<div class="cap-inline-note cap-inline-note-success"><i class="bi bi-wallet2"></i><span>Đã hoàn tiền cọc vào ví của bạn.</span></div>'
        : "";
    var cancellationReasonHtml =
      data.status === "CANCELLED" && data.cancellationReason
        ? '<div class="cap-inline-note cap-inline-note-warning"><i class="bi bi-info-circle"></i><span>' +
          escapeHtml(data.cancellationReason) +
          "</span></div>"
        : "";
    var remainingPaymentHtml = canPayRemaining
      ? '<div class="cap-inline-note cap-inline-note-warning">' +
        '<i class="bi bi-receipt"></i>' +
        "<span>Còn lại cần thanh toán: <strong>" +
        escapeHtml(formatMoney(data.remainingAmount)) +
        "</strong>" +
        (toNumber(data.discountAmount) > 0
          ? " - Đã áp dụng voucher giảm " +
            escapeHtml(formatMoney(data.discountAmount))
          : "") +
        (data.invoiceId ? " - Mã thanh toán #" + escapeHtml(data.invoiceId) : "") +
        "</span></div>"
      : "";
    var invoicePreviewMeta = [
      "Tổng billing " + escapeHtml(formatMoney(data.billedTotal)),
    ];
    if (shouldShowRemaining(data)) {
      invoicePreviewMeta.push(
        "Còn lại " + escapeHtml(formatMoney(data.remainingAmount)),
      );
    } else {
      invoicePreviewMeta.push("Đã thanh toán xong");
    }
    var invoicePreviewHtml = data.invoiceId
      ? '<div class="cap-invoice-preview">' +
        '<div class="cap-invoice-preview__copy">' +
        '<div class="cap-invoice-preview__title"><i class="bi bi-file-earmark-text"></i> Hóa đơn #' +
        escapeHtml(data.invoiceId) +
        "</div>" +
        '<div class="cap-invoice-preview__meta">' +
        invoicePreviewMeta.join(" • ") +
        "</div>" +
        "</div>" +
        '<button type="button" class="cap-btn cap-btn-neutral" data-action="view-invoice"><i class="bi bi-receipt-cutoff"></i> Xem hóa đơn</button>' +
        "</div>"
      : "";

    content.innerHTML = normalizeText(
      (
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
      '  <div class="cap-inline-row"><div class="cap-inline-label">Thời gian đặt lịch</div><div class="cap-inline-value">' +
      bookedAtHtml +
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
      reviewSummaryHtml +
      depositPaidHtml +
      cancelledDepositHtml +
      cancellationReasonHtml +
      remainingPaymentHtml +
      invoicePreviewHtml +
      "</div>" +
      '<div class="cap-inline-actions">' +
      paymentHistoryHtml +
      depositReceiptHtml +
      invoiceReceiptHtml +
      rebookHtml +
      reviewActionHtml +
      (canCancel
        ? '<button type="button" class="cap-btn cap-btn-danger" data-action="cancel"><i class="bi bi-x-circle"></i> Hủy lịch</button>'
        : "") +
      (canPayRemaining
        ? '<button type="button" class="cap-btn cap-btn-warning" data-action="pay-remaining"><i class="bi bi-credit-card"></i> Thanh toán phần còn lại</button>'
        : "") +
      "</div>"
      )
    );

    var cancelBtn = content.querySelector('[data-action="cancel"]');
    if (cancelBtn) {
      cancelBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        var cancelMessage = isRefundEligibleForCancel(data)
          ? "B\u1ea1n c\u00f3 ch\u1eafc ch\u1eafn mu\u1ed1n h\u1ee7y l\u1ecbch h\u1eb9n n\u00e0y kh\u00f4ng? Ti\u1ec1n c\u1ecdc s\u1ebd \u0111\u01b0\u1ee3c ho\u00e0n v\u1ec1 v\u00ed."
          : "B\u1ea1n c\u00f3 ch\u1eafc ch\u1eafn mu\u1ed1n h\u1ee7y l\u1ecbch h\u1eb9n n\u00e0y kh\u00f4ng? N\u1ebfu h\u1ee7y trong ng\u00e0y kh\u00e1m, ti\u1ec1n c\u1ecdc s\u1ebd kh\u00f4ng \u0111\u01b0\u1ee3c ho\u00e0n l\u1ea1i.";
        askConfirm(cancelMessage, {
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
            .then(function (result) {
              if (!result) return;
              cancelBtn.disabled = false;
              showToast("H\u1ee7y l\u1ecbch th\u00e0nh c\u00f4ng.", "success", "\u0110\u00e3 c\u1eadp nh\u1eadt");
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

    var reviewBtn = content.querySelector('[data-action="review"]');
    if (reviewBtn) {
      reviewBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        openReviewModal(appointmentId);
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
          loading.textContent = normalizeText(err.message || "Không thể tải chi tiết.");
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
      btn.textContent = normalizeText(label);
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

    li.innerHTML = normalizeText(
      (
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
      '    <div class="apt-meta">Thời gian đặt lịch: ' +
      escapeHtml(formatDateTime(apt.createdAt) || "Chưa có dữ liệu") +
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
      "</div>"
      )
    );

    li.addEventListener("click", function () {
      openInlineDetail(apt.id, false);
    });

    return li;
  }

  function updateViewSwitch() {
    if (viewDefaultBtnEl) {
      viewDefaultBtnEl.classList.toggle("active", state.view !== "cancelled");
    }
    if (viewCancelledBtnEl) {
      viewCancelledBtnEl.classList.toggle("active", state.view === "cancelled");
    }
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
    if (state.view) {
      query += "&view=" + encodeURIComponent(state.view);
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
        state.sort = data.sort || state.sort || "date_desc";
        state.view = data.view || state.view || "default";
        if (sortSelectEl) {
          sortSelectEl.value = state.sort;
        }
        updateViewSwitch();

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
              emptyTitle.textContent = normalizeText("Không tìm thấy lịch hẹn phù hợp");
              emptyDesc.textContent =
                normalizeText("Hãy thử đổi từ khóa hoặc xóa tìm kiếm để xem lại toàn bộ lịch hẹn.");
            } else if (state.view === "cancelled") {
              emptyTitle.textContent = normalizeText("Bạn chưa có lịch hẹn đã hủy");
              emptyDesc.textContent =
                normalizeText("Các lịch đã hủy sẽ xuất hiện tại đây để bạn tra cứu lại khi cần.");
            } else {
              emptyTitle.textContent = normalizeText("Bạn chưa có lịch hẹn nào");
              emptyDesc.textContent =
                normalizeText("H\u00e3y \u0111\u1eb7t l\u1ecbch kh\u00e1m \u0111\u1ec3 ph\u00f2ng kh\u00e1m c\u00f3 th\u1ec3 s\u1eafp x\u1ebfp th\u1eddi gian h\u1ed7 tr\u1ee3 b\u1ea1n.");
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
      state.sort = sortSelectEl.value || "date_desc";
      loadAppointments(null, 0);
    });
  }

  if (viewDefaultBtnEl) {
    viewDefaultBtnEl.addEventListener("click", function () {
      if (state.view === "default") return;
      state.view = "default";
      loadAppointments(null, 0);
    });
  }

  if (viewCancelledBtnEl) {
    viewCancelledBtnEl.addEventListener("click", function () {
      if (state.view === "cancelled") return;
      state.view = "cancelled";
      loadAppointments(null, 0);
    });
  }
    function bindReviewModal() {
        if (!reviewModalEl) return;

        if (reviewModalCloseEl) {
            reviewModalCloseEl.addEventListener("click", function (e) {
                e.preventDefault();
                e.stopPropagation();
                closeReviewModal();
            });
        }

        document.querySelectorAll("[data-review-close]").forEach(function (el) {
            el.addEventListener("click", function (e) {
                e.preventDefault();
                e.stopPropagation();
                closeReviewModal();
            });
        });

        document.addEventListener("keydown", function (e) {
            if (e.key === "Escape" && reviewModalEl && !reviewModalEl.hidden) {
                closeReviewModal();
            }
        });

        reviewDentistStarEls.forEach(function (starEl) {
            starEl.addEventListener("click", function () {
                updateDentistReviewStars(Number(starEl.dataset.rating || 0));
            });
        });

        reviewServiceStarEls.forEach(function (starEl) {
            starEl.addEventListener("click", function () {
                updateServiceReviewStars(Number(starEl.dataset.rating || 0));
            });
        });

        if (reviewSubmitEl) {
            reviewSubmitEl.addEventListener("click", function () {
                if (!reviewSelection.appointmentId) return;

                if (!reviewSelection.dentistRating) {
                    showAlert("Vui lòng chọn số sao đánh giá bác sĩ.", "warning", "Thiếu thông tin");
                    return;
                }

                if (!reviewSelection.serviceRating) {
                    showAlert("Vui lòng chọn số sao đánh giá dịch vụ.", "warning", "Thiếu thông tin");
                    return;
                }

                reviewSubmitEl.disabled = true;

                fetch("/customer/appointments/" + reviewSelection.appointmentId + "/review", {
                    method: "POST",
                    credentials: "same-origin",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        dentistRating: reviewSelection.dentistRating,
                        serviceRating: reviewSelection.serviceRating,
                        comment: reviewCommentEl ? reviewCommentEl.value : ""
                    })
                })
                    .then(function (res) {
                        if (res.status === 401) {
                            throw new Error("Bạn cần đăng nhập.");
                        }
                        return res.json().then(function (payload) {
                            if (!res.ok || payload.success === false) {
                                throw new Error(payload.message || "Không thể gửi đánh giá.");
                            }
                            return payload;
                        });
                    })
                    .then(function (payload) {
                        var reviewedAppointmentId = reviewSelection.appointmentId;
                        closeReviewModal();
                        showToast(
                            payload.message || "Đã gửi đánh giá thành công.",
                            "success",
                            "Cảm ơn bạn"
                        );
                        loadAppointments(function () {
                            openInlineDetail(reviewedAppointmentId, true);
                        }, state.page);
                    })
                    .catch(function (err) {
                        if (reviewSubmitEl) reviewSubmitEl.disabled = false;
                        showAlert(
                            err.message || "Không thể gửi đánh giá.",
                            "error",
                            "Gửi đánh giá thất bại"
                        );
                    });
            });
        }
    }

  bindRemainingPaymentModal();
  bindInvoiceModal();
  bindReviewModal();
  updateViewSwitch();

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

(function () {
  "use strict";

  var listEl = document.getElementById("customer-appointments-list");
  if (!listEl) return;

  var paginationEl = document.getElementById("customer-appointments-pagination");
  var summaryTotalEl = document.getElementById("cap-summary-total");
  var summaryPageEl = document.getElementById("cap-summary-page");
  var searchInputEl = document.getElementById("customer-appointments-search");
  var clearSearchEl = document.getElementById("customer-appointments-clear");
  var sortSelectEl = document.getElementById("customer-appointments-sort");
  var searchTimer = null;
  var state = { page: 0, size: 5, totalPages: 0, keyword: "", sort: "newest" };

  var currentOpen = {
    appointmentId: null,
    detailEl: null
  };

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
      COMPLETED: { label: "Đã hoàn thành", className: "completed" },
      DONE: { label: "Đã hoàn thành", className: "completed" },
      CANCELLED: { label: "Đã hủy", className: "cancelled" },
      REEXAM: { label: "Tái khám", className: "reexam" }
    };
    return map[key] || { label: key || "Không xác định", className: "default" };
  }

  function closeCurrentDetail() {
    if (currentOpen.detailEl && currentOpen.detailEl.parentNode) {
      currentOpen.detailEl.parentNode.removeChild(currentOpen.detailEl);
    }

    currentOpen.appointmentId = null;
    currentOpen.detailEl = null;

    document.querySelectorAll("#customer-appointments-list li.cap-item-active").forEach(function (li) {
      li.classList.remove("cap-item-active");
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
      '    </button>' +
      '  </div>' +
      '  <div class="cap-inline-loading">Đang tải chi tiết...</div>' +
      '  <div class="cap-inline-content" style="display:none;"></div>' +
      '</div>';

    wrap.querySelector(".cap-inline-close").addEventListener("click", function (e) {
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
    var contactHtml = data.contactChannel && data.contactValue
      ? escapeHtml(data.contactChannel + ": " + data.contactValue)
      : '<span class="cap-muted">Không có thông tin</span>';

    var canCancel = data.status !== "CANCELLED" && data.status !== "COMPLETED";
    var canCheckin = !!data.canCheckIn;
    var depositPaidHtml =
      data.status === "PENDING" ||
      data.status === "CONFIRMED" ||
      data.status === "CHECKED_IN" ||
      data.status === "EXAMINING" ||
      data.status === "DONE" ||
      data.status === "REEXAM" ||
      data.status === "IN_PROGRESS" ||
      data.status === "COMPLETED"
        ? '<div class="cap-inline-note cap-inline-note-success"><i class="bi bi-check-circle-fill"></i><span>Đã thanh toán đặt cọc 50%</span></div>'
        : "";

    content.innerHTML =
      '<div class="cap-inline-grid">' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Dịch vụ</div><div class="cap-inline-value">' + escapeHtml(data.serviceName || "") + '</div></div>' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Bác sĩ</div><div class="cap-inline-value">' + dentistHtml + '</div></div>' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Ngày khám</div><div class="cap-inline-value">' + escapeHtml(formatDate(data.date)) + '</div></div>' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Giờ khám</div><div class="cap-inline-value">' + escapeHtml(formatTime(data.startTime)) + ' - ' + escapeHtml(formatTime(data.endTime)) + '</div></div>' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Trạng thái</div><div class="cap-inline-value"><span class="cap-status-badge ' + statusMeta.className + '">' + escapeHtml(statusMeta.label) + '</span></div></div>' +
      '  <div class="cap-inline-row"><div class="cap-inline-label">Liên hệ</div><div class="cap-inline-value">' + contactHtml + '</div></div>' +
      '  <div class="cap-inline-row cap-inline-notes"><div class="cap-inline-label">Ghi chú</div><div class="cap-inline-value">' + notesHtml + '</div></div>' +
      depositPaidHtml +
      '</div>' +
      '<div class="cap-inline-actions">' +
      (canCheckin
        ? '<button type="button" class="cap-btn cap-btn-primary" data-action="checkin"><i class="bi bi-check2-circle"></i> Check-in online</button>'
        : '') +
      (canCancel
        ? '<button type="button" class="cap-btn cap-btn-danger" data-action="cancel"><i class="bi bi-x-circle"></i> Hủy lịch</button>'
        : '') +
      '</div>';

    var checkinBtn = content.querySelector('[data-action="checkin"]');
    if (checkinBtn) {
      checkinBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        checkinBtn.disabled = true;

        fetch("/customer/appointments/" + appointmentId + "/checkin", {
          method: "POST",
          credentials: "same-origin"
        })
          .then(function (res) {
            if (res.status === 401) {
              alert("Bạn cần đăng nhập.");
              checkinBtn.disabled = false;
              return null;
            }
            if (!res.ok) {
              return res.json().then(function (er) {
                throw new Error(er.error || "Check-in thất bại");
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
            alert(err.message || "Check-in thất bại.");
          });
      });
    }

    var cancelBtn = content.querySelector('[data-action="cancel"]');
    if (cancelBtn) {
      cancelBtn.addEventListener("click", function (e) {
        e.stopPropagation();
        if (!confirm("Bạn có chắc chắn muốn hủy lịch hẹn này không?")) return;

        cancelBtn.disabled = true;

        fetch("/customer/appointments/" + appointmentId + "/cancel", {
          method: "POST",
          credentials: "same-origin"
        })
          .then(function (res) {
            if (res.status === 401) {
              alert("Bạn cần đăng nhập.");
              cancelBtn.disabled = false;
              return null;
            }
            if (!res.ok) {
              return res.json().then(function (er) {
                throw new Error(er.error || "Hủy lịch thất bại");
              });
            }
            return res.json();
          })
          .then(function () {
            cancelBtn.disabled = false;
            loadAppointments(function () {
              openInlineDetail(appointmentId, true);
            }, state.page);
          })
          .catch(function (err) {
            cancelBtn.disabled = false;
            alert(err.message || "Hủy lịch thất bại.");
          });
      });
    }
  }

  function openInlineDetail(appointmentId, keepOpenAfterReload) {
    if (!keepOpenAfterReload && currentOpen.appointmentId === appointmentId) {
      closeCurrentDetail();
      return;
    }

    closeCurrentDetail();

    var li = listEl.querySelector('li[data-appointment-id="' + appointmentId + '"]');
    if (!li) return;

    li.classList.add("cap-item-active");

    var detailWrap = createInlineDetailShell();
    li.insertAdjacentElement("afterend", detailWrap);

    currentOpen.appointmentId = appointmentId;
    currentOpen.detailEl = detailWrap;

    fetch("/customer/appointments/" + appointmentId, {
      credentials: "same-origin"
    })
      .then(function (r) {
        if (r.status === 401) {
          alert("Bạn cần đăng nhập.");
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
        if (loading) loading.textContent = err.message || "Không thể tải chi tiết.";
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

    if (window.__lastCreatedAppointmentId && window.__lastCreatedAppointmentId === apt.id) {
      li.classList.add("highlight-new");
    }

    li.innerHTML =
      '<div class="cap-item-row">' +
      '  <div class="cap-item-main">' +
      '    <div class="apt-date"><i class="bi bi-calendar3"></i> ' +
      escapeHtml(formatDate(apt.date)) +
      ' <span class="cap-dot"></span> ' +
      escapeHtml(formatTime(apt.startTime)) +
      '</div>' +
      '    <div class="apt-service">' + escapeHtml(apt.serviceName || "") + '</div>' +
      '    <div class="apt-meta">Mã lịch hẹn #' + escapeHtml(apt.id) + '</div>' +
      '  </div>' +
      '  <div class="cap-item-side">' +
      '    <span class="apt-status ' + statusMeta.className + '" data-status="' + escapeHtml(apt.status || "") + '">' +
      escapeHtml(statusMeta.label) +
      '</span>' +
      '    <i class="bi bi-chevron-down cap-item-chevron"></i>' +
      '  </div>' +
      '</div>';

    li.addEventListener("click", function () {
      openInlineDetail(apt.id, false);
    });

    return li;
  }

  function loadAppointments(doneCb, targetPage) {
    var listWrap = document.getElementById("customer-appointments-list-wrap");
    var empty = document.getElementById("customer-appointments-empty");
    var loading = document.getElementById("customer-appointments-loading");

    var requestedPage = typeof targetPage === "number" ? targetPage : state.page;
    if (requestedPage < 0) requestedPage = 0;

    if (listWrap) listWrap.style.display = "none";
    if (empty) empty.style.display = "none";
    if (paginationEl) paginationEl.style.display = "none";
    listEl.innerHTML = "";
    if (loading) loading.style.display = "";

    closeCurrentDetail();

    var query = "/customer/appointments?page=" + requestedPage + "&size=" + state.size;
    if (state.keyword) {
      query += "&keyword=" + encodeURIComponent(state.keyword);
    }
    if (state.sort) {
      query += "&sort=" + encodeURIComponent(state.sort);
    }

    fetch(query, {
      credentials: "same-origin"
    })
      .then(function (r) {
        if (r.status === 401) {
          alert("Bạn cần đăng nhập để xem lịch hẹn.");
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
            totalElements: Array.isArray(data) ? data.length : 0
          };
        }

        state.page = data.page || 0;
        state.totalPages = data.totalPages || 0;
        state.sort = data.sort || state.sort || "newest";
        if (sortSelectEl) {
          sortSelectEl.value = state.sort;
        }

        if (summaryTotalEl) summaryTotalEl.textContent = String(data.totalElements || data.content.length || 0);
        if (summaryPageEl) summaryPageEl.textContent = String((state.page || 0) + 1);

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
              emptyDesc.textContent = "Hãy thử đổi từ khóa hoặc xóa tìm kiếm để xem lại toàn bộ lịch hẹn.";
            } else {
              emptyTitle.textContent = "Bạn chưa có lịch hẹn nào";
              emptyDesc.textContent = "Hãy đặt lịch khám để phòng khám có thể sắp xếp thời gian hỗ trợ bạn.";
            }
          }
        }

        if (typeof doneCb === "function") doneCb();
      })
      .catch(function () {
        if (loading) loading.style.display = "none";
        if (listWrap) listWrap.style.display = "";
        alert("Không thể tải danh sách lịch hẹn.");
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

  loadAppointments(function () {
    if (!openFromNotificationId) return;
    var target = listEl.querySelector('li[data-appointment-id="' + openFromNotificationId + '"]');
    if (target) {
      openInlineDetail(openFromNotificationId, false);
      history.replaceState(null, "", window.location.pathname);
    }
  });
})();



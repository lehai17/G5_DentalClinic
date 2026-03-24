const WALLET_TOPUP_MIN = 10000;
const WALLET_TOPUP_MAX = 100000000;
const WALLET_TRANSACTIONS_PER_PAGE = 15;

document.addEventListener("DOMContentLoaded", function () {
  bindTopupModal();
  showWalletStatusFromQuery();
  loadWalletData();
});

function showToast(message, type, title) {
  if (window.CustomerFeedback) {
    window.CustomerFeedback.toast({
      message: message,
      type: type || "info",
      title: title || "",
    });
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

function loadWalletData() {
  const loading = document.getElementById("loading");
  const list = document.getElementById("transactions-list");
  const empty = document.getElementById("empty-state");
  const pagination = document.getElementById("transactions-pagination");

  if (loading) loading.style.display = "";
  if (list) list.innerHTML = "";
  if (pagination) pagination.innerHTML = "";

  fetch("/customer/wallet/api", { credentials: "same-origin" })
    .then((response) => {
      if (response.status === 401) {
        showAlert("Bạn cần đăng nhập.", "warning", "Chưa đăng nhập");
        window.location.href = "/login";
        return null;
      }
      return response.json();
    })
    .then((data) => {
      if (!data) return;

      const balanceEl = document.getElementById("wallet-balance");
      if (balanceEl) {
        balanceEl.textContent = formatCurrency(data.balance);
      }

      if (loading) loading.style.display = "none";

      if (data.transactions && data.transactions.length > 0) {
        if (empty) empty.style.display = "none";
        renderTransactionsPage(data.transactions, 1);
      } else {
        if (empty) empty.style.display = "";
        if (list) list.innerHTML = "";
        if (pagination) pagination.classList.add("wallet-pagination-hidden");
      }
    })
    .catch((error) => {
      if (loading) loading.style.display = "none";
      console.error("Load wallet error:", error);
      showAlert(
        "Không thể tải dữ liệu ví. Vui lòng thử lại sau.",
        "error",
        "Tải ví thất bại",
      );
    });
}

function createTransactionHTML(transaction) {
  const typeInfo = getTypeInfo(transaction.type);
  const date = new Date(transaction.createdAt);

  return `
    <div class="transaction-item">
      <div class="transaction-type">
        <div class="transaction-icon ${typeInfo.class}">
          <i class="${typeInfo.icon}"></i>
        </div>
        <div class="transaction-details">
          <h4>${typeInfo.name}</h4>
          <p>${transaction.description || ""}</p>
          <p>${formatDate(date)}</p>
        </div>
      </div>
      <div class="transaction-amount ${typeInfo.class}">
        ${typeInfo.prefix}${formatCurrency(transaction.amount)}
      </div>
    </div>
  `;
}

function renderTransactionsPage(transactions, page) {
  const list = document.getElementById("transactions-list");
  const pagination = document.getElementById("transactions-pagination");

  if (!list) return;

  const totalPages = Math.ceil(transactions.length / WALLET_TRANSACTIONS_PER_PAGE);
  const safePage = Math.min(Math.max(page || 1, 1), Math.max(totalPages, 1));
  const startIndex = (safePage - 1) * WALLET_TRANSACTIONS_PER_PAGE;
  const pageItems = transactions.slice(startIndex, startIndex + WALLET_TRANSACTIONS_PER_PAGE);

  list.innerHTML = pageItems.map((item) => createTransactionHTML(item)).join("");

  if (!pagination) return;

  if (totalPages <= 1) {
    pagination.innerHTML = "";
    pagination.classList.add("wallet-pagination-hidden");
    return;
  }

  pagination.classList.remove("wallet-pagination-hidden");
  pagination.innerHTML = createPaginationHTML(safePage, totalPages);

  pagination.querySelectorAll("[data-page]").forEach((button) => {
    button.addEventListener("click", function () {
      const nextPage = Number(this.getAttribute("data-page"));
      renderTransactionsPage(transactions, nextPage);
    });
  });
}

function createPaginationHTML(currentPage, totalPages) {
  let buttons = "";

  buttons += `
    <button type="button" class="wallet-pagination-btn" data-page="${currentPage - 1}" ${currentPage === 1 ? "disabled" : ""}>
      Trước
    </button>
  `;

  for (let page = 1; page <= totalPages; page += 1) {
    buttons += `
      <button
        type="button"
        class="wallet-pagination-btn ${page === currentPage ? "active" : ""}"
        data-page="${page}"
      >
        ${page}
      </button>
    `;
  }

  buttons += `
    <button type="button" class="wallet-pagination-btn" data-page="${currentPage + 1}" ${currentPage === totalPages ? "disabled" : ""}>
      Sau
    </button>
  `;

  return buttons;
}

function getTypeInfo(type) {
  const types = {
    REFUND: {
      name: "Hoàn cọc",
      icon: "bi bi-arrow-counterclockwise",
      class: "refund",
      prefix: "+",
    },
    DEPOSIT: {
      name: "Nạp tiền",
      icon: "bi bi-arrow-down-left",
      class: "deposit",
      prefix: "+",
    },
    PAYMENT: {
      name: "Thanh toán",
      icon: "bi bi-arrow-up-right",
      class: "payment",
      prefix: "-",
    },
    ADJUSTMENT: {
      name: "Điều chỉnh",
      icon: "bi bi-arrow-left-right",
      class: "deposit",
      prefix: "",
    },
  };

  return (
    types[type] || {
      name: type,
      icon: "bi bi-circle",
      class: "deposit",
      prefix: "",
    }
  );
}

function formatCurrency(amount) {
  if (!amount) return "0 VND";
  const num = parseFloat(amount);
  return num.toLocaleString("vi-VN") + " VND";
}

function formatVndInput(value) {
  const digits = String(value || "").replace(/\D/g, "");
  if (!digits) return "";
  return Number(digits).toLocaleString("vi-VN");
}

function parseVndInput(value) {
  const digits = String(value || "").replace(/\D/g, "");
  return digits ? Number(digits) : 0;
}

function formatDate(date) {
  const day = date.getDate().toString().padStart(2, "0");
  const month = (date.getMonth() + 1).toString().padStart(2, "0");
  const year = date.getFullYear();
  const hours = date.getHours().toString().padStart(2, "0");
  const minutes = date.getMinutes().toString().padStart(2, "0");
  return `${day}/${month}/${year} ${hours}:${minutes}`;
}

function bindTopupModal() {
  const openBtn = document.getElementById("wallet-open-topup");
  const modal = document.getElementById("wallet-topup-modal");
  const closeBtn = document.getElementById("wallet-topup-close");
  const amountInput = document.getElementById("wallet-topup-amount");
  const creditPreview = document.getElementById("wallet-topup-credit-preview");
  const submitBtn = document.getElementById("wallet-topup-submit");

  function updateTopupCreditPreview() {
    if (!creditPreview) return;
    const amount = parseVndInput(amountInput ? amountInput.value : 0);
    creditPreview.textContent = "Ví sẽ nhận: " + formatCurrency(amount) + ".";
  }

  function openModal() {
    if (!modal) return;
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    updateTopupCreditPreview();
    if (amountInput) amountInput.focus();
  }

  function closeModal() {
    if (!modal) return;
    modal.hidden = true;
    document.body.style.overflow = "";
  }

  if (openBtn) {
    openBtn.addEventListener("click", function (event) {
      event.preventDefault();
      openModal();
    });
  }

  if (closeBtn) {
    closeBtn.addEventListener("click", closeModal);
  }

  document.querySelectorAll("[data-wallet-close]").forEach((node) => {
    node.addEventListener("click", closeModal);
  });

  document.querySelectorAll("[data-amount]").forEach((node) => {
    node.addEventListener("click", function () {
      if (amountInput) {
        amountInput.value = formatVndInput(this.getAttribute("data-amount"));
        updateTopupCreditPreview();
      }
    });
  });

  if (amountInput) {
    amountInput.addEventListener("input", function () {
      this.value = formatVndInput(this.value);
      updateTopupCreditPreview();
    });
  }

  if (submitBtn) {
    submitBtn.addEventListener("click", function () {
      const amount = parseVndInput(amountInput ? amountInput.value : 0);

      if (!amount) {
        showAlert(
          "Vui lòng nhập số tiền cần nạp",
          "warning",
          "Số tiền không hợp lệ",
        );
        return;
      }

      if (amount < WALLET_TOPUP_MIN) {
        showAlert(
          "Vui lòng nhập số tiền từ 10.000 VND trở lên.",
          "warning",
          "Số tiền không hợp lệ",
        );
        return;
      }

      if (amount > WALLET_TOPUP_MAX) {
        showAlert(
          "Số tiền nạp tối đa mỗi lần là 100.000.000 VND.",
          "warning",
          "Vượt hạn mức nạp",
        );
        return;
      }

      submitBtn.disabled = true;
      submitBtn.textContent = "Đang kết nối VNPay...";

      fetch("/customer/payment/wallet/topup/create", {
        method: "POST",
        credentials: "same-origin",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
        },
        body: new URLSearchParams({ amount: String(amount) }).toString(),
      })
        .then((response) =>
          response
            .json()
            .catch(() => ({}))
            .then((data) => {
              if (!response.ok) {
                throw new Error(data.message || "Không thể tạo giao dịch nạp tiền.");
              }
              return data;
            }),
        )
        .then((data) => {
          if (!data.paymentUrl) {
            throw new Error("Không nhận được liên kết thanh toán.");
          }
          window.location.href = data.paymentUrl;
        })
        .catch((error) => {
          submitBtn.disabled = false;
          submitBtn.textContent = "Tiếp tục với VNPay";
          showAlert(
            error.message || "Không thể tạo giao dịch nạp tiền.",
            "error",
            "Nạp tiền thất bại",
          );
        });
    });
  }

  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape" && modal && !modal.hidden) {
      closeModal();
    }
  });
}

function showWalletStatusFromQuery() {
  const banner = document.getElementById("wallet-status-banner");
  if (!banner) return;

  const type = (banner.dataset.statusType || "").trim().toLowerCase();
  const title = (banner.dataset.statusTitle || "").trim();
  const message = (banner.dataset.statusMessage || "").trim();
  if (!type || !message) return;

  banner.className = "wallet-status-banner " + (type === "warning" ? "fail" : type);
  banner.textContent = message;
  banner.style.display = "";

  if (type === "success") {
    showToast(message, "success", title || "Nạp tiền thành công");
  } else if (type === "warning") {
    showAlert(message, "warning", title || "Nạp tiền chưa hoàn tất");
  } else {
    showToast(message, "info", title || "Thông báo");
  }

  if (window.history && typeof window.history.replaceState === "function") {
    window.history.replaceState({}, document.title, window.location.pathname);
  }
}

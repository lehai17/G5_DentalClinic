const WITHDRAW_BANKS = [
        "Vietcombank",
        "Techcombank",
        "BIDV",
        "ACB",
        "MB Bank",
        "VPBank",
      ];

      document.addEventListener("DOMContentLoaded", function () {
        bindTopupModal();
        bindWithdrawModal();
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

      let savedWithdrawAccountState = null;
      let walletSecurityState = {
        hasPin: false,
        walletLocked: false,
        pinLockedUntil: null,
        dailyWithdrawLimit: 10000000,
        withdrawnToday: 0,
        remainingDailyLimit: 10000000,
        pinRequiredThreshold: 1000000,
      };

      function updateWalletSecurityInfo() {
        const dailyLimitEl = document.getElementById("wallet-daily-limit-info");
        const lockInfoEl = document.getElementById("wallet-lock-info");

        if (dailyLimitEl) {
          dailyLimitEl.innerHTML =
            'Hạn mức còn lại hôm nay: <strong>' +
            formatCurrency(walletSecurityState.remainingDailyLimit || 0) +
            "</strong>";
        }

        if (lockInfoEl) {
          if (walletSecurityState.walletLocked && walletSecurityState.pinLockedUntil) {
            lockInfoEl.hidden = false;
            lockInfoEl.textContent =
              "Chức năng ví đang bị khóa đến " +
              walletSecurityState.pinLockedUntil.replace("T", " ").slice(0, 16) +
              ".";
          } else {
            lockInfoEl.hidden = true;
            lockInfoEl.textContent = "";
          }
        }
      }

      function loadWalletData() {
        const loading = document.getElementById("loading");
        const list = document.getElementById("transactions-list");
        const empty = document.getElementById("empty-state");

        if (loading) loading.style.display = "";
        if (list) list.innerHTML = "";

        fetch("/customer/wallet/api", { credentials: "same-origin" })
          .then((r) => {
            if (r.status === 401) {
              showAlert("Bạn cần đăng nhập.", "warning", "Chưa đăng nhập");
              window.location.href = "/login";
              return null;
            }
            return r.json();
          })
          .then((data) => {
            if (!data) return;

            // Update balance
            const balanceEl = document.getElementById("wallet-balance");
            if (balanceEl) {
              balanceEl.textContent = formatCurrency(data.balance);
            }
            savedWithdrawAccountState = data.savedWithdrawAccount || null;
            walletSecurityState = Object.assign(walletSecurityState, data.walletSecurity || {});
            updateWalletSecurityInfo();
            updateSavedDemoAccountCard(savedWithdrawAccountState);
            if (typeof window.__applySavedWithdrawAccount === "function") {
              window.__applySavedWithdrawAccount(savedWithdrawAccountState);
            }

            if (loading) loading.style.display = "none";

            // Render transactions
            if (data.transactions && data.transactions.length > 0) {
              if (empty) empty.style.display = "none";
              if (list)
                list.innerHTML = data.transactions
                  .map((t) => createTransactionHTML(t))
                  .join("");
            } else {
              if (empty) empty.style.display = "";
              if (list) list.innerHTML = "";
            }
          })
          .catch((err) => {
            if (loading) loading.style.display = "none";
            console.error("Load wallet error:", err);
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
        const dateStr = formatDate(date);

        return `
            <div class="transaction-item">
                <div class="transaction-type">
                    <div class="transaction-icon ${typeInfo.class}">
                        <i class="${typeInfo.icon}"></i>
                    </div>
                    <div class="transaction-details">
                        <h4>${typeInfo.name}</h4>
                        <p>${transaction.description || ""}</p>
                        <p>${dateStr}</p>
                    </div>
                </div>
                <div class="transaction-amount ${typeInfo.class}">
                    ${typeInfo.prefix}${formatCurrency(transaction.amount)}
                </div>
            </div>
        `;
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
          WITHDRAW: {
            name: "Rút tiền",
            icon: "bi bi-cash-stack",
            class: "withdraw",
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

      function updateSavedDemoAccountCard(account) {
        const card = document.getElementById("wallet-demo-account-card");
        const bankEl = document.getElementById("wallet-demo-card-bank");
        const accountEl = document.getElementById("wallet-demo-card-account");
        const holderEl = document.getElementById("wallet-demo-card-holder");
        const balanceEl = document.getElementById("wallet-demo-card-balance");

        if (!card) return;

        if (!account) {
          card.style.display = "none";
          return;
        }

        card.style.display = "";
        if (bankEl) bankEl.textContent = account.bankName || "--";
        if (accountEl) accountEl.textContent = account.bankAccountNo || "--";
        if (holderEl) holderEl.textContent = account.accountHolder || "--";
        if (balanceEl) balanceEl.textContent = formatCurrency(account.balance || 0);
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
        const submitBtn = document.getElementById("wallet-topup-submit");

        function openModal() {
          if (!modal) return;
          modal.hidden = false;
          document.body.style.overflow = "hidden";
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
            }
          });
        });

        if (amountInput) {
          amountInput.addEventListener("input", function () {
            this.value = formatVndInput(this.value);
          });
        }

        if (submitBtn) {
          submitBtn.addEventListener("click", function () {
            const amount = parseVndInput(amountInput ? amountInput.value : 0);
            if (!amount || amount < 10000) {
              showAlert(
                "Vui lòng nhập số tiền từ 10.000 VND trở lên.",
                "warning",
                "Số tiền không hợp lệ",
              );
              return;
            }

            submitBtn.disabled = true;
            submitBtn.textContent = "Đang kết nối VNPay...";

            fetch("/customer/payment/wallet/topup/create", {
              method: "POST",
              credentials: "same-origin",
              headers: {
                "Content-Type":
                  "application/x-www-form-urlencoded;charset=UTF-8",
              },
              body: new URLSearchParams({ amount: String(amount) }).toString(),
            })
              .then((response) =>
                response
                  .json()
                  .catch(() => ({}))
                  .then((data) => {
                    if (!response.ok) {
                      throw new Error(
                        data.message || "Không thể tạo giao dịch nạp tiền.",
                      );
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
        const params = new URLSearchParams(window.location.search);
        const status = params.get("topup");
        const banner = document.getElementById("wallet-status-banner");
        if (!banner || !status) return;

        if (status === "success") {
          banner.className = "wallet-status-banner success";
          banner.textContent = "Nạp tiền vào ví thành công.";
          banner.style.display = "";
          showToast("Nạp tiền vào ví thành công.", "success", "Nạp tiền thành công");
        } else if (status === "fail") {
          banner.className = "wallet-status-banner fail";
          banner.textContent =
            "Giao dịch nạp tiền chưa hoàn tất hoặc đã bị hủy.";
          banner.style.display = "";
          showAlert(
            "Giao dịch nạp tiền chưa hoàn tất hoặc đã bị hủy.",
            "warning",
            "Nạp tiền chưa hoàn tất",
          );
        }
      }

      function bindWithdrawModal() {
        const openBtn = document.getElementById("wallet-open-withdraw");
        const modal = document.getElementById("wallet-withdraw-modal");
        const closeBtn = document.getElementById("wallet-withdraw-close");
        const amountInput = document.getElementById("wallet-withdraw-amount");
        const modeManualBtn = document.getElementById("wallet-withdraw-mode-manual");
        const modePersonalBtn = document.getElementById("wallet-withdraw-mode-personal");
        const manualPanel = document.getElementById("wallet-withdraw-manual-panel");
        const personalPanel = document.getElementById("wallet-withdraw-personal-panel");
        const bankSearchInput = document.getElementById("wallet-withdraw-bank-search");
        const bankListEl = document.getElementById("wallet-withdraw-bank-list");
        const accountNoInput = document.getElementById("wallet-withdraw-account-no");
        const accountHolderInput = document.getElementById("wallet-withdraw-account-holder");
        const personalBankSearchInput = document.getElementById("wallet-personal-bank-search");
        const personalBankListEl = document.getElementById("wallet-personal-bank-list");
        const personalAccountNoInput = document.getElementById("wallet-personal-account-no");
        const personalAccountHolderInput = document.getElementById("wallet-personal-account-holder");
        const personalSavedView = document.getElementById("wallet-personal-saved-view");
        const personalFormView = document.getElementById("wallet-personal-form-view");
        const personalBankValue = document.getElementById("wallet-personal-bank-value");
        const personalAccountValue = document.getElementById("wallet-personal-account-value");
        const personalHolderValue = document.getElementById("wallet-personal-holder-value");
        const personalBalanceValue = document.getElementById("wallet-personal-balance-value");
        const manualDemoBalance = document.getElementById("wallet-withdraw-demo-balance");
        const personalDemoBalance = document.getElementById("wallet-personal-demo-balance");
        const personalSaveBtn = document.getElementById("wallet-personal-save-btn");
        const personalEditBtn = document.getElementById("wallet-personal-edit-btn");
        const submitBtn = document.getElementById("wallet-withdraw-submit");
        const pinModal = document.getElementById("wallet-pin-modal");
        const pinCloseBtn = document.getElementById("wallet-pin-close");
        const pinTitle = document.getElementById("wallet-pin-title");
        const pinSubtitle = document.getElementById("wallet-pin-subtitle");
        const pinCodeInput = document.getElementById("wallet-pin-code");
        const pinCodeDots = document.getElementById("wallet-pin-code-dots");
        const pinConfirmGroup = document.getElementById("wallet-pin-confirm-group");
        const pinConfirmInput = document.getElementById("wallet-pin-confirm");
        const pinConfirmDots = document.getElementById("wallet-pin-confirm-dots");
        const pinOtpGroup = document.getElementById("wallet-pin-otp-group");
        const pinOtpInput = document.getElementById("wallet-pin-otp");
        const pinSubmitBtn = document.getElementById("wallet-pin-submit");
        const pinForgotBtn = document.getElementById("wallet-pin-forgot-btn");
        let currentMode = "manual";
        let selectedBank = "";
        let personalSelectedBank = "";
        let lookupTimer = null;
        let personalLookupTimer = null;
        let resolvedAccount = null;
        let personalResolvedAccount = null;
        let personalEditing = !savedWithdrawAccountState;
        let pinFlowMode = "verify";
        let pendingWithdrawPayload = null;

        function renderBanks(keyword, listEl, activeBank, onSelect) {
          if (!listEl) return;
          const normalizedKeyword = String(keyword || "").trim().toLowerCase();
          const banks = WITHDRAW_BANKS.filter((bank) =>
            !normalizedKeyword || bank.toLowerCase().includes(normalizedKeyword),
          );

          listEl.innerHTML = banks
            .map((bank) => {
              const activeClass = bank === activeBank ? " active" : "";
              return (
                '<button type="button" class="wallet-bank-option' +
                activeClass +
                '" data-bank-name="' +
                bank +
                '">' +
                bank +
                "</button>"
              );
            })
            .join("");

          listEl.querySelectorAll("[data-bank-name]").forEach((node) => {
            node.addEventListener("click", function () {
              onSelect(this.getAttribute("data-bank-name") || "");
            });
          });
        }

        function digitsOnly(value, maxLength) {
          return String(value || "").replace(/\D/g, "").slice(0, maxLength || 6);
        }

        function renderPinDots(inputEl, dotsWrap) {
          if (!inputEl || !dotsWrap) return;
          const value = String(inputEl.value || "");
          const dots = dotsWrap.querySelectorAll(".wallet-pin-dot");
          dots.forEach((dot, index) => {
            dot.classList.toggle("filled", index < value.length);
            dot.classList.toggle("active", index === value.length && value.length < dots.length);
          });
        }

        function openPinModal(mode, payload) {
          pinFlowMode = mode;
          pendingWithdrawPayload = payload || pendingWithdrawPayload;
          if (!pinModal) return;

          if (pinCodeInput) pinCodeInput.value = "";
          if (pinConfirmInput) pinConfirmInput.value = "";
          if (pinOtpInput) pinOtpInput.value = "";
          renderPinDots(pinCodeInput, pinCodeDots);
          renderPinDots(pinConfirmInput, pinConfirmDots);

          if (mode === "setup") {
            if (pinTitle) pinTitle.textContent = "Thiết lập Digital OTP";
            if (pinSubtitle) pinSubtitle.textContent = "Lần đầu rút trên 1.000.000 VND, bạn cần đặt PIN ví 6 số và nhập lại để xác nhận.";
            if (pinConfirmGroup) pinConfirmGroup.hidden = false;
            if (pinOtpGroup) pinOtpGroup.hidden = true;
            if (pinForgotBtn) pinForgotBtn.hidden = true;
            if (pinSubmitBtn) pinSubmitBtn.textContent = "Lưu PIN và tiếp tục";
          } else if (mode === "otp") {
            if (pinTitle) pinTitle.textContent = "Xác thực Digital OTP";
            if (pinSubtitle) pinSubtitle.textContent = "Nhập OTP được gửi về email để đặt lại PIN ví.";
            if (pinConfirmGroup) pinConfirmGroup.hidden = true;
            if (pinOtpGroup) pinOtpGroup.hidden = false;
            if (pinForgotBtn) pinForgotBtn.hidden = true;
            if (pinSubmitBtn) pinSubmitBtn.textContent = "Xác thực OTP";
          } else if (mode === "reset") {
            if (pinTitle) pinTitle.textContent = "Đặt lại mã PIN";
            if (pinSubtitle) pinSubtitle.textContent = "OTP đã hợp lệ. Hãy nhập PIN mới 2 lần để hoàn tất.";
            if (pinConfirmGroup) pinConfirmGroup.hidden = false;
            if (pinOtpGroup) pinOtpGroup.hidden = true;
            if (pinForgotBtn) pinForgotBtn.hidden = true;
            if (pinSubmitBtn) pinSubmitBtn.textContent = "Lưu PIN mới";
          } else {
            if (pinTitle) pinTitle.textContent = "Xác thực Digital OTP";
            if (pinSubtitle) pinSubtitle.textContent = "Vui lòng nhập mã PIN Digital OTP để nhận mã xác thực giao dịch.";
            if (pinConfirmGroup) pinConfirmGroup.hidden = true;
            if (pinOtpGroup) pinOtpGroup.hidden = true;
            if (pinForgotBtn) pinForgotBtn.hidden = false;
            if (pinSubmitBtn) pinSubmitBtn.textContent = "Xác nhận PIN";
          }

          pinModal.hidden = false;
          document.body.style.overflow = "hidden";
          if (mode === "otp" && pinOtpInput) {
            pinOtpInput.focus();
          } else if (pinCodeInput) {
            pinCodeInput.focus();
          }
        }

        function closePinModal() {
          if (!pinModal) return;
          pinModal.hidden = true;
          document.body.style.overflow = modal && !modal.hidden ? "hidden" : "";
        }

        function proceedWithdraw(payload, pinCode) {
          if (pinCode) {
            payload.set("pinCode", pinCode);
          } else {
            payload.delete("pinCode");
          }

          submitBtn.disabled = true;
          submitBtn.textContent = "Đang xử lý...";

          fetch("/customer/wallet/api/withdraw", {
            method: "POST",
            credentials: "same-origin",
            headers: {
              "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
            },
            body: payload.toString(),
          })
            .then((response) =>
              response
                .json()
                .catch(() => ({}))
                .then((data) => {
                  data.__status = response.status;
                  if (!response.ok || data.success === false) {
                    throw data;
                  }
                  return data;
                }),
            )
            .then((data) => {
              const submittedMode = payload.get("mode");
              if (submittedMode === "personal") {
                applySavedWithdrawAccount({
                  bankName: data.destinationBank,
                  bankAccountNo: data.destinationAccountNo,
                  accountHolder: data.destinationAccountHolder,
                  balance: data.destinationBalance,
                });
              }
              closePinModal();
              closeModal();
              clearWithdrawForm();
              loadWalletData();
              showToast(
                (data.message || "Rút tiền thành công.") +
                  " Chu tai khoan: " +
                  (data.destinationAccountHolder || "") +
                  " | So du demo: " +
                  formatCurrency(data.destinationBalance || 0),
                "success",
                "Rút tiền thành công",
              );
            })
            .catch((error) => {
              if (error && error.code === "PIN_INVALID") {
                loadWalletData();
                openPinModal("verify", payload);
                showAlert(error.message || "PIN không đúng.", "warning", "PIN không đúng");
                return;
              }
              if (error && error.code === "PIN_REQUIRED") {
                openPinModal("verify", payload);
                return;
              }
              if (error && error.code === "PIN_SETUP_REQUIRED") {
                openPinModal("setup", payload);
                return;
              }
              if (error && error.code === "WALLET_LOCKED") {
                loadWalletData();
                showAlert(error.message || "Chức năng ví đang bị khóa.", "error", "Ví đang bị khóa");
                return;
              }
              showAlert(
                (error && error.message) || "Không thể rút tiền.",
                "error",
                "Rút tiền thất bại",
              );
            })
            .finally(() => {
              submitBtn.disabled = false;
              submitBtn.textContent = "Xác nhận rút tiền";
            });
        }

        function clearManualBankSelection() {
          selectedBank = "";
          if (bankSearchInput) bankSearchInput.value = "";
          resetResolvedAccount();
          renderBanks("", bankListEl, selectedBank, handleManualBankSelect);
        }

        function clearPersonalBankSelection() {
          personalSelectedBank = "";
          if (personalBankSearchInput) personalBankSearchInput.value = "";
          resetPersonalResolvedAccount();
          renderBanks("", personalBankListEl, personalSelectedBank, handlePersonalBankSelect);
        }

        function resetResolvedAccount() {
          resolvedAccount = null;
          if (accountHolderInput) accountHolderInput.value = "";
          if (manualDemoBalance) {
            manualDemoBalance.textContent = "Số dư tài khoản demo hiện tại: 0 VND";
          }
        }

        function resetPersonalResolvedAccount() {
          personalResolvedAccount = null;
          if (personalAccountHolderInput) personalAccountHolderInput.value = "";
          if (personalDemoBalance) {
            personalDemoBalance.textContent = "Số dư tài khoản demo hiện tại: 0 VND";
          }
        }

        function updateManualDemoBalance(balance) {
          if (!manualDemoBalance) return;
          manualDemoBalance.textContent =
            "Số dư tài khoản demo hiện tại: " + formatCurrency(balance || 0);
        }

        function updatePersonalDemoBalance(balance) {
          const text = "Số dư tài khoản demo hiện tại: " + formatCurrency(balance || 0);
          if (personalDemoBalance) {
            personalDemoBalance.textContent = text;
          }
          if (personalBalanceValue) {
            personalBalanceValue.textContent = formatCurrency(balance || 0);
          }
        }

        function triggerLookup() {
          const accountNo = accountNoInput ? accountNoInput.value.trim() : "";
          resetResolvedAccount();

          if (!selectedBank || !/^\d{10}$/.test(accountNo)) {
            return;
          }

          if (accountHolderInput) {
            accountHolderInput.value = "Dang tra cuu...";
          }

          fetch(
            "/customer/wallet/api/withdraw/account-lookup?" +
              new URLSearchParams({
                bankName: selectedBank,
                bankAccountNo: accountNo,
              }).toString(),
            { credentials: "same-origin" },
          )
            .then((response) =>
              response
                .json()
                .catch(() => ({}))
                .then((data) => {
                  if (!response.ok || data.success === false) {
                    throw new Error(data.message || "Khong the tra cuu chu tai khoan.");
                  }
                  return data;
                }),
            )
            .then((data) => {
              resolvedAccount = data;
              if (accountHolderInput) {
                accountHolderInput.value = data.accountHolder || "";
              }
              updateManualDemoBalance(data.balance);
            })
            .catch((error) => {
              resetResolvedAccount();
              showAlert(
                error.message || "Khong the tra cuu chu tai khoan.",
                "error",
                "Tra cuu that bai",
              );
            });
        }

        function triggerPersonalLookup() {
          const accountNo = personalAccountNoInput ? personalAccountNoInput.value.trim() : "";
          resetPersonalResolvedAccount();

          if (!personalSelectedBank || !/^\d{10}$/.test(accountNo)) {
            return;
          }

          if (personalAccountHolderInput) {
            personalAccountHolderInput.value = "Dang tra cuu...";
          }

          fetch(
            "/customer/wallet/api/withdraw/account-lookup?" +
              new URLSearchParams({
                bankName: personalSelectedBank,
                bankAccountNo: accountNo,
              }).toString(),
            { credentials: "same-origin" },
          )
            .then((response) =>
              response
                .json()
                .catch(() => ({}))
                .then((data) => {
                  if (!response.ok || data.success === false) {
                    throw new Error(data.message || "Khong the tra cuu chu tai khoan.");
                  }
                  return data;
                }),
            )
            .then((data) => {
              personalResolvedAccount = data;
              if (personalAccountHolderInput) {
                personalAccountHolderInput.value = data.accountHolder || "";
              }
              updatePersonalDemoBalance(data.balance);
            })
            .catch((error) => {
              resetPersonalResolvedAccount();
              showAlert(
                error.message || "Khong the tra cuu chu tai khoan.",
                "error",
                "Tra cuu that bai",
              );
            });
        }

        function applySavedWithdrawAccount(account) {
          savedWithdrawAccountState = account || null;
          updateSavedDemoAccountCard(savedWithdrawAccountState);
          if (!savedWithdrawAccountState) {
            if (personalSavedView) personalSavedView.hidden = true;
            if (personalFormView) personalFormView.hidden = false;
            personalEditing = true;
            return;
          }

          if (personalBankValue) personalBankValue.textContent = savedWithdrawAccountState.bankName || "--";
          if (personalAccountValue) personalAccountValue.textContent = savedWithdrawAccountState.bankAccountNo || "--";
          if (personalHolderValue) personalHolderValue.textContent = savedWithdrawAccountState.accountHolder || "--";
          updatePersonalDemoBalance(savedWithdrawAccountState.balance);

          if (!personalEditing) {
            if (personalSavedView) personalSavedView.hidden = false;
            if (personalFormView) personalFormView.hidden = true;
          }
        }

        function setPersonalEditing(editing) {
          personalEditing = editing;
          if (personalSavedView) personalSavedView.hidden = editing || !savedWithdrawAccountState;
          if (personalFormView) personalFormView.hidden = !editing && !!savedWithdrawAccountState;

          if (editing && savedWithdrawAccountState) {
            personalSelectedBank = savedWithdrawAccountState.bankName || "";
            if (personalBankSearchInput) personalBankSearchInput.value = personalSelectedBank;
            if (personalAccountNoInput) personalAccountNoInput.value = savedWithdrawAccountState.bankAccountNo || "";
            if (personalAccountHolderInput) personalAccountHolderInput.value = savedWithdrawAccountState.accountHolder || "";
            personalResolvedAccount = { ...savedWithdrawAccountState };
            updatePersonalDemoBalance(savedWithdrawAccountState.balance);
          }

          renderBanks(
            personalBankSearchInput ? personalBankSearchInput.value : "",
            personalBankListEl,
            personalSelectedBank,
            handlePersonalBankSelect,
          );
        }

        function setMode(mode) {
          currentMode = mode;
          if (modeManualBtn) modeManualBtn.classList.toggle("active", mode === "manual");
          if (modePersonalBtn) modePersonalBtn.classList.toggle("active", mode === "personal");
          if (manualPanel) manualPanel.hidden = mode !== "manual";
          if (personalPanel) personalPanel.hidden = mode !== "personal";
          if (mode === "personal") {
            setPersonalEditing(!savedWithdrawAccountState);
          }
        }

        function openModal() {
          if (!modal) return;
          modal.hidden = false;
          document.body.style.overflow = "hidden";
          modal.scrollTop = 0;
          if (amountInput) amountInput.focus();
        }

        function closeModal() {
          if (!modal) return;
          modal.hidden = true;
          document.body.style.overflow = "";
        }

        function clearWithdrawForm() {
          selectedBank = "";
          resetResolvedAccount();
          resetPersonalResolvedAccount();
          if (lookupTimer) {
            clearTimeout(lookupTimer);
            lookupTimer = null;
          }
          if (personalLookupTimer) {
            clearTimeout(personalLookupTimer);
            personalLookupTimer = null;
          }
          if (amountInput) amountInput.value = "";
          if (bankSearchInput) bankSearchInput.value = "";
          if (accountNoInput) accountNoInput.value = "";
          if (savedWithdrawAccountState) {
            personalEditing = false;
            personalSelectedBank = savedWithdrawAccountState.bankName || "";
            if (personalBankSearchInput) personalBankSearchInput.value = savedWithdrawAccountState.bankName || "";
            if (personalAccountNoInput) personalAccountNoInput.value = savedWithdrawAccountState.bankAccountNo || "";
            if (personalAccountHolderInput) personalAccountHolderInput.value = savedWithdrawAccountState.accountHolder || "";
            personalResolvedAccount = { ...savedWithdrawAccountState };
            updatePersonalDemoBalance(savedWithdrawAccountState.balance);
          } else {
            personalEditing = true;
            personalSelectedBank = "";
            if (personalBankSearchInput) personalBankSearchInput.value = "";
            if (personalAccountNoInput) personalAccountNoInput.value = "";
            if (personalAccountHolderInput) personalAccountHolderInput.value = "";
            updatePersonalDemoBalance(0);
          }
          updateManualDemoBalance(0);
          renderBanks("", bankListEl, selectedBank, handleManualBankSelect);
          renderBanks(
            personalBankSearchInput ? personalBankSearchInput.value : "",
            personalBankListEl,
            personalSelectedBank,
            handlePersonalBankSelect,
          );
          setMode("manual");
        }

        function handleManualBankSelect(bank) {
          if (bank === selectedBank) {
            clearManualBankSelection();
            return;
          }
          selectedBank = bank;
          if (bankSearchInput) bankSearchInput.value = bank;
          renderBanks("", bankListEl, selectedBank, handleManualBankSelect);
          triggerLookup();
        }

        function handlePersonalBankSelect(bank) {
          if (bank === personalSelectedBank) {
            clearPersonalBankSelection();
            return;
          }
          personalSelectedBank = bank;
          if (personalBankSearchInput) personalBankSearchInput.value = bank;
          renderBanks("", personalBankListEl, personalSelectedBank, handlePersonalBankSelect);
          triggerPersonalLookup();
        }

        window.__applySavedWithdrawAccount = applySavedWithdrawAccount;
        applySavedWithdrawAccount(savedWithdrawAccountState);

        if (openBtn) {
          openBtn.addEventListener("click", function (event) {
            event.preventDefault();
            clearWithdrawForm();
            openModal();
          });
        }

        if (closeBtn) {
          closeBtn.addEventListener("click", closeModal);
        }

        if (pinCloseBtn) {
          pinCloseBtn.addEventListener("click", closePinModal);
        }

        document.querySelectorAll("[data-wallet-withdraw-close]").forEach((node) => {
          node.addEventListener("click", closeModal);
        });

        document.querySelectorAll("[data-wallet-pin-close]").forEach((node) => {
          node.addEventListener("click", closePinModal);
        });

        if (bankSearchInput) {
          bankSearchInput.addEventListener("input", function () {
            renderBanks(this.value, bankListEl, selectedBank, handleManualBankSelect);
          });
        }

        if (personalBankSearchInput) {
          personalBankSearchInput.addEventListener("input", function () {
            renderBanks(this.value, personalBankListEl, personalSelectedBank, handlePersonalBankSelect);
          });
        }

        if (accountNoInput) {
          accountNoInput.addEventListener("input", function () {
            this.value = this.value.replace(/\D/g, "").slice(0, 10);
            resetResolvedAccount();

            if (lookupTimer) {
              clearTimeout(lookupTimer);
            }
            if (selectedBank && /^\d{10}$/.test(this.value.trim())) {
              lookupTimer = setTimeout(triggerLookup, 350);
            }
          });
        }

        if (personalAccountNoInput) {
          personalAccountNoInput.addEventListener("input", function () {
            this.value = this.value.replace(/\D/g, "").slice(0, 10);
            resetPersonalResolvedAccount();

            if (personalLookupTimer) {
              clearTimeout(personalLookupTimer);
            }
            if (personalSelectedBank && /^\d{10}$/.test(this.value.trim())) {
              personalLookupTimer = setTimeout(triggerPersonalLookup, 350);
            }
          });
        }

        if (amountInput) {
          amountInput.addEventListener("input", function () {
            this.value = formatVndInput(this.value);
          });
        }

        if (pinCodeInput) {
          pinCodeInput.addEventListener("input", function () {
            this.value = digitsOnly(this.value, 6);
            renderPinDots(this, pinCodeDots);
          });
          pinCodeDots?.addEventListener("click", function () {
            pinCodeInput.focus();
          });
        }

        if (pinConfirmInput) {
          pinConfirmInput.addEventListener("input", function () {
            this.value = digitsOnly(this.value, 6);
            renderPinDots(this, pinConfirmDots);
          });
          pinConfirmDots?.addEventListener("click", function () {
            pinConfirmInput.focus();
          });
        }

        if (pinOtpInput) {
          pinOtpInput.addEventListener("input", function () {
            this.value = digitsOnly(this.value, 6);
          });
        }

        if (modeManualBtn) {
          modeManualBtn.addEventListener("click", function () {
            setMode("manual");
          });
        }

        if (modePersonalBtn) {
          modePersonalBtn.addEventListener("click", function () {
            setMode("personal");
          });
        }

        if (personalEditBtn) {
          personalEditBtn.addEventListener("click", function () {
            setPersonalEditing(true);
          });
        }

        if (pinForgotBtn) {
          pinForgotBtn.addEventListener("click", function () {
            pinForgotBtn.disabled = true;
            fetch("/customer/wallet/api/pin/forgot/request", {
              method: "POST",
              credentials: "same-origin",
            })
              .then((response) =>
                response
                  .json()
                  .catch(() => ({}))
                  .then((data) => {
                    if (!response.ok || data.success === false) {
                      throw new Error(data.message || "Không thể gửi OTP.");
                    }
                    return data;
                  }),
              )
              .then((data) => {
                showToast(data.message || "Đã gửi OTP về email.", "success", "OTP đã gửi");
                openPinModal("otp", pendingWithdrawPayload);
              })
              .catch((error) => {
                showAlert(error.message || "Không thể gửi OTP.", "error", "Gửi OTP thất bại");
              })
              .finally(() => {
                pinForgotBtn.disabled = false;
              });
          });
        }

        if (pinSubmitBtn) {
          pinSubmitBtn.addEventListener("click", function () {
            const pinCode = pinCodeInput ? pinCodeInput.value.trim() : "";
            const confirmPin = pinConfirmInput ? pinConfirmInput.value.trim() : "";
            const otp = pinOtpInput ? pinOtpInput.value.trim() : "";

            if (pinFlowMode === "otp") {
              if (!/^\d{6}$/.test(otp)) {
                showAlert("Vui lòng nhập OTP gồm đúng 6 chữ số.", "warning", "OTP không hợp lệ");
                return;
              }
              pinSubmitBtn.disabled = true;
              fetch("/customer/wallet/api/pin/forgot/verify", {
                method: "POST",
                credentials: "same-origin",
                headers: {
                  "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                },
                body: new URLSearchParams({ otp: otp }).toString(),
              })
                .then((response) =>
                  response
                    .json()
                    .catch(() => ({}))
                    .then((data) => {
                      if (!response.ok || data.success === false) {
                        throw new Error(data.message || "OTP không hợp lệ.");
                      }
                      return data;
                    }),
                )
                .then((data) => {
                  showToast(data.message || "OTP hợp lệ.", "success", "Xác thực thành công");
                  openPinModal("reset", pendingWithdrawPayload);
                })
                .catch((error) => {
                  showAlert(error.message || "OTP không hợp lệ.", "error", "Xác thực thất bại");
                })
                .finally(() => {
                  pinSubmitBtn.disabled = false;
                });
              return;
            }

            if (!/^\d{6}$/.test(pinCode)) {
              showAlert("PIN phải gồm đúng 6 chữ số.", "warning", "PIN không hợp lệ");
              return;
            }

            if (pinFlowMode === "setup" || pinFlowMode === "reset") {
              if (!/^\d{6}$/.test(confirmPin)) {
                showAlert("Vui lòng nhập lại PIN xác nhận gồm đúng 6 chữ số.", "warning", "PIN xác nhận không hợp lệ");
                return;
              }
              pinSubmitBtn.disabled = true;
              fetch("/customer/wallet/api/pin/setup", {
                method: "POST",
                credentials: "same-origin",
                headers: {
                  "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                },
                body: new URLSearchParams({
                  pinCode: pinCode,
                  confirmPinCode: confirmPin,
                }).toString(),
              })
                .then((response) =>
                  response
                    .json()
                    .catch(() => ({}))
                    .then((data) => {
                      if (!response.ok || data.success === false) {
                        throw new Error(data.message || "Không thể lưu PIN.");
                      }
                      return data;
                    }),
                )
                .then(() => {
                  walletSecurityState.hasPin = true;
                  closePinModal();
                  proceedWithdraw(pendingWithdrawPayload, pinCode);
                })
                .catch((error) => {
                  showAlert(error.message || "Không thể lưu PIN.", "error", "Thiết lập PIN thất bại");
                })
                .finally(() => {
                  pinSubmitBtn.disabled = false;
                });
              return;
            }

            closePinModal();
            proceedWithdraw(pendingWithdrawPayload, pinCode);
          });
        }

        if (personalSaveBtn) {
          personalSaveBtn.addEventListener("click", function () {
            const bankAccountNo = (personalAccountNoInput ? personalAccountNoInput.value : "").trim();
            if (!personalSelectedBank) {
              showAlert("Vui lòng chọn ngân hàng cá nhân.", "warning", "Thiếu thông tin");
              return;
            }
            if (!/^\d{10}$/.test(bankAccountNo)) {
              showAlert("Vui lòng nhập số tài khoản cá nhân gồm đúng 10 chữ số.", "warning", "Số tài khoản không hợp lệ");
              return;
            }
            if (!personalResolvedAccount || !personalResolvedAccount.accountHolder) {
              showAlert("Vui lòng chờ hệ thống tra cứu chủ tài khoản mock.", "warning", "Thiếu thông tin");
              return;
            }

            personalSaveBtn.disabled = true;
            personalSaveBtn.textContent = "Dang luu...";

            fetch("/customer/wallet/api/withdraw/personal-account", {
              method: "POST",
              credentials: "same-origin",
              headers: {
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
              },
              body: new URLSearchParams({
                bankName: personalSelectedBank,
                bankAccountNo: bankAccountNo,
              }).toString(),
            })
              .then((response) =>
                response
                  .json()
                  .catch(() => ({}))
                  .then((data) => {
                    if (!response.ok || data.success === false) {
                      throw new Error(data.message || "Khong the luu tai khoan ca nhan.");
                    }
                    return data;
                  }),
              )
              .then((data) => {
                applySavedWithdrawAccount(data);
                setPersonalEditing(false);
                showToast("Da luu tai khoan ca nhan thanh cong.", "success", "Da luu");
              })
              .catch((error) => {
                showAlert(error.message || "Khong the luu tai khoan ca nhan.", "error", "Luu that bai");
              })
              .finally(() => {
                personalSaveBtn.disabled = false;
                personalSaveBtn.textContent = "Lưu tài khoản cá nhân";
              });
          });
        }

        if (submitBtn) {
          submitBtn.addEventListener("click", function () {
            const amount = parseVndInput(amountInput ? amountInput.value : 0);

            if (!amount || amount < 10000) {
              showAlert(
                "Vui lòng nhập số tiền rút từ 10.000 VND trở lên.",
                "warning",
                "Số tiền không hợp lệ",
              );
              return;
            }

            const payload = new URLSearchParams({ amount: String(amount), mode: currentMode });

            if (currentMode === "manual") {
              const bankAccountNo = (accountNoInput ? accountNoInput.value : "").trim();
              if (!selectedBank) {
                showAlert("Vui lòng chọn ngân hàng nhận tiền.", "warning", "Thiếu thông tin");
                return;
              }
              if (!/^\d{10}$/.test(bankAccountNo)) {
                showAlert("Vui lòng nhập số tài khoản gồm đúng 10 chữ số.", "warning", "Số tài khoản không hợp lệ");
                return;
              }
              if (!resolvedAccount || !resolvedAccount.accountHolder) {
                showAlert("Vui lòng chờ hệ thống tra cứu chủ tài khoản mock.", "warning", "Thiếu thông tin");
                return;
              }
              payload.append("bankName", selectedBank);
              payload.append("bankAccountNo", bankAccountNo);
            } else {
              if (personalEditing) {
                const personalAccountNo = (personalAccountNoInput ? personalAccountNoInput.value : "").trim();
                if (!personalSelectedBank) {
                  showAlert("Vui lòng chọn ngân hàng cá nhân.", "warning", "Thiếu thông tin");
                  return;
                }
                if (!/^\d{10}$/.test(personalAccountNo)) {
                  showAlert("Vui lòng nhập số tài khoản cá nhân gồm đúng 10 chữ số.", "warning", "Số tài khoản không hợp lệ");
                  return;
                }
                if (!personalResolvedAccount || !personalResolvedAccount.accountHolder) {
                  showAlert("Vui lòng chờ hệ thống tra cứu chủ tài khoản mock.", "warning", "Thiếu thông tin");
                  return;
                }
                payload.append("bankName", personalSelectedBank);
                payload.append("bankAccountNo", personalAccountNo);
              } else if (!savedWithdrawAccountState) {
                showAlert("Vui lòng lưu tài khoản cá nhân trước khi rút.", "warning", "Chưa có tài khoản");
                return;
              }
            }

            if (walletSecurityState.walletLocked) {
              showAlert(
                "Chức năng ví đang bị khóa tạm thời. Vui lòng thử lại sau.",
                "error",
                "Ví đang bị khóa",
              );
              return;
            }

            if (amount > Number(walletSecurityState.remainingDailyLimit || 0)) {
              showAlert(
                "Bạn đã vượt hạn mức rút tối đa 10.000.000 VND trong ngày.",
                "warning",
                "Vượt hạn mức ngày",
              );
              return;
            }

            pendingWithdrawPayload = payload;
            if (amount >= Number(walletSecurityState.pinRequiredThreshold || 1000000)) {
              openPinModal(walletSecurityState.hasPin ? "verify" : "setup", payload);
              return;
            }

            proceedWithdraw(payload, null);
          });
        }

        renderBanks("", bankListEl, selectedBank, handleManualBankSelect);
        renderBanks("", personalBankListEl, personalSelectedBank, handlePersonalBankSelect);
      }


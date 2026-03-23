(function () {
  "use strict";

  var root;
  var toastStack;
  var modal;
  var modalBadge;
  var modalTitle;
  var modalMessage;
  var modalCancel;
  var modalConfirm;
  var modalResolver = null;

  function ensureUi() {
    if (root) return;

    root = document.createElement("div");
    root.className = "cfb-root";
    root.innerHTML =
      '<div class="cfb-toast-stack" id="cfb-toast-stack"></div>' +
      '<div class="cfb-modal" id="cfb-modal" hidden>' +
      '  <div class="cfb-modal__backdrop"></div>' +
      '  <div class="cfb-modal__dialog" role="dialog" aria-modal="true" aria-labelledby="cfb-modal-title">' +
      '    <div class="cfb-modal__badge cfb-modal__badge--info" id="cfb-modal-badge"></div>' +
      '    <h3 class="cfb-modal__title" id="cfb-modal-title"></h3>' +
      '    <p class="cfb-modal__message" id="cfb-modal-message"></p>' +
      '    <div class="cfb-modal__actions">' +
      '      <button type="button" class="cfb-btn cfb-btn--secondary" id="cfb-modal-cancel">Đóng</button>' +
      '      <button type="button" class="cfb-btn cfb-btn--primary" id="cfb-modal-confirm">Đã hiểu</button>' +
      '    </div>' +
      '  </div>' +
      '</div>';

    document.body.appendChild(root);

    toastStack = document.getElementById("cfb-toast-stack");
    modal = document.getElementById("cfb-modal");
    modalBadge = document.getElementById("cfb-modal-badge");
    modalTitle = document.getElementById("cfb-modal-title");
    modalMessage = document.getElementById("cfb-modal-message");
    modalCancel = document.getElementById("cfb-modal-cancel");
    modalConfirm = document.getElementById("cfb-modal-confirm");

    root.querySelector(".cfb-modal__backdrop").addEventListener("click", function () {
      resolveModal(false);
    });
    modalCancel.addEventListener("click", function () {
      resolveModal(false);
    });
    modalConfirm.addEventListener("click", function () {
      resolveModal(true);
    });

    document.addEventListener("keydown", function (event) {
      if (event.key === "Escape" && modal && !modal.hidden) {
        resolveModal(false);
      }
    });
  }

  function normalize(input, defaults) {
    if (typeof input === "string") {
      return Object.assign({ message: input }, defaults || {});
    }
    return Object.assign({}, defaults || {}, input || {});
  }

  function getTypeMeta(type) {
    var key = String(type || "info").toLowerCase();
    var map = {
      success: { icon: "bi bi-check2-circle", badge: "Thành công", btn: "primary" },
      error: { icon: "bi bi-x-octagon", badge: "Lỗi", btn: "danger" },
      warning: { icon: "bi bi-exclamation-triangle", badge: "Lưu ý", btn: "primary" },
      info: { icon: "bi bi-info-circle", badge: "Thông báo", btn: "primary" }
    };
    return map[key] || map.info;
  }

  function resolveModal(result) {
    if (modal) modal.hidden = true;
    document.body.classList.remove("cfb-modal-open");
    if (modalResolver) {
      modalResolver(result);
      modalResolver = null;
    }
  }

  function toast(options) {
    ensureUi();
    var cfg = normalize(options, { type: "info", title: "" });
    var meta = getTypeMeta(cfg.type);
    var node = document.createElement("div");
    node.className = "cfb-toast cfb-toast--" + cfg.type;
    node.innerHTML =
      '<div class="cfb-toast__icon"><i class="' + meta.icon + '"></i></div>' +
      '<div>' +
      '  <div class="cfb-toast__title">' + escapeHtml(cfg.title || meta.badge) + '</div>' +
      '  <p class="cfb-toast__message">' + escapeHtml(cfg.message || "") + "</p>" +
      "</div>" +
      '<button type="button" class="cfb-toast__close" aria-label="Đóng"><i class="bi bi-x-lg"></i></button>';

    var remove = function () {
      if (node.parentNode) node.parentNode.removeChild(node);
    };

    node.querySelector(".cfb-toast__close").addEventListener("click", remove);
    toastStack.appendChild(node);
    window.setTimeout(remove, cfg.duration || 3200);
  }

  function alertModal(options) {
    ensureUi();
    var cfg = normalize(options, { type: "info", title: "Thông báo", confirmText: "Đã hiểu" });
    var meta = getTypeMeta(cfg.type);
    modal.hidden = false;
    document.body.classList.add("cfb-modal-open");
    modalBadge.className = "cfb-modal__badge cfb-modal__badge--" + cfg.type;
    modalBadge.innerHTML = '<i class="' + meta.icon + '"></i><span>' + escapeHtml(cfg.badgeText || meta.badge) + "</span>";
    modalTitle.textContent = cfg.title || "Thông báo";
    modalMessage.textContent = cfg.message || "";
    modalCancel.hidden = true;
    modalConfirm.textContent = cfg.confirmText || "Đã hiểu";
    modalConfirm.className = "cfb-btn cfb-btn--" + meta.btn;

    return new Promise(function (resolve) {
      modalResolver = function () {
        resolve(true);
      };
    });
  }

  function confirmModal(options) {
    ensureUi();
    var cfg = normalize(options, {
      type: "warning",
      title: "Xác nhận",
      confirmText: "Xác nhận",
      cancelText: "Hủy"
    });
    var meta = getTypeMeta(cfg.type);
    modal.hidden = false;
    document.body.classList.add("cfb-modal-open");
    modalBadge.className = "cfb-modal__badge cfb-modal__badge--" + cfg.type;
    modalBadge.innerHTML = '<i class="' + meta.icon + '"></i><span>' + escapeHtml(cfg.badgeText || meta.badge) + "</span>";
    modalTitle.textContent = cfg.title || "Xác nhận";
    modalMessage.textContent = cfg.message || "";
    modalCancel.hidden = false;
    modalCancel.textContent = cfg.cancelText || "Hủy";
    modalConfirm.textContent = cfg.confirmText || "Xác nhận";
    modalConfirm.className = "cfb-btn cfb-btn--" + (cfg.type === "error" ? "danger" : "primary");

    return new Promise(function (resolve) {
      modalResolver = resolve;
    });
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#039;");
  }

  window.CustomerFeedback = {
    toast: toast,
    alert: alertModal,
    confirm: confirmModal
  };
})();

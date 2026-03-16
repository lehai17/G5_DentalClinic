(function () {
  if (window.__customerChatWidgetInitialized) return;
  window.__customerChatWidgetInitialized = true;

  const toggleBtn = document.getElementById("chat-toggle");
  const closeBtn = document.getElementById("chat-close");
  const windowEl = document.getElementById("chat-window");
  const headerEl = windowEl?.querySelector(".chat-header");
  const messagesEl = document.getElementById("chat-messages");
  const inputEl = document.getElementById("chat-text");
  const sendBtn = document.getElementById("chat-send");
  const badgeEl = document.getElementById("chat-badge");
  const fileInputEl = document.getElementById("chat-attachment");
  const attachBtn = document.getElementById("chat-attach");
  const attachmentBarEl = document.getElementById("chat-attachment-bar");
  const attachmentNameEl = document.getElementById("chat-attachment-name");
  const attachmentClearEl = document.getElementById("chat-attachment-clear");
  const maxAttachmentSize = 5 * 1024 * 1024;

  if (!toggleBtn || !windowEl || !headerEl || !messagesEl || !inputEl || !sendBtn || !badgeEl || !fileInputEl || !attachBtn || !attachmentBarEl || !attachmentNameEl || !attachmentClearEl) {
    return;
  }

  let threadId = null;
  let threadSummary = null;
  let pollTimer = null;
  let lastMessageId = null;
  let loadingMessages = false;
  let sending = false;
  let selectedAttachment = null;

  function normalizeText(value) {
    if (value == null) return "";
    const text = String(value);
    if (!/[ÃÂÄÆÐï]/.test(text)) return text;
    try {
      return decodeURIComponent(escape(text));
    } catch (err) {
      return text;
    }
  }

  decorateWidget();

  const statusEl = document.getElementById("chat-status");
  const assigneeEl = document.getElementById("chat-assignee");
  const refreshBtn = document.getElementById("chat-refresh");
  const counterEl = document.getElementById("chat-counter");
  const helperEl = document.getElementById("chat-helper");

  function isOpen() {
    return !windowEl.classList.contains("hidden");
  }

  function escapeHtml(value) {
    return normalizeText(String(value || ""))
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function formatDateTime(value) {
    if (!value) return "";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
      day: "2-digit",
      month: "2-digit"
    });
  }

  function formatFileSize(value) {
    const size = Number(value || 0);
    if (!size) return "";
    if (size >= 1024 * 1024) {
      return `${(size / (1024 * 1024)).toFixed(1)} MB`;
    }
    return `${Math.max(1, Math.round(size / 1024))} KB`;
  }

  function scrollToBottom(force) {
    if (force) {
      messagesEl.scrollTop = messagesEl.scrollHeight;
      return;
    }
    const distance = messagesEl.scrollHeight - messagesEl.scrollTop - messagesEl.clientHeight;
    if (distance < 80) {
      messagesEl.scrollTop = messagesEl.scrollHeight;
    }
  }

  function setStatus(text, tone) {
    statusEl.textContent = normalizeText(text);
    statusEl.dataset.tone = tone || "neutral";
  }

  function setAssignee(text) {
    assigneeEl.textContent = normalizeText(text || "Lễ tân sẽ phản hồi sớm nhất có thể");
  }

  function setHelper(text, tone) {
    helperEl.textContent = normalizeText(text || "");
    helperEl.dataset.tone = tone || "neutral";
  }

  function renderSelectedAttachment() {
    if (!selectedAttachment) {
      attachmentBarEl.classList.add("hidden");
      attachmentNameEl.textContent = "";
      return;
    }
    attachmentBarEl.classList.remove("hidden");
    const sizeText = formatFileSize(selectedAttachment.size);
    attachmentNameEl.textContent = sizeText
      ? `${selectedAttachment.name} (${sizeText})`
      : selectedAttachment.name;
  }

  function clearSelectedAttachment() {
    selectedAttachment = null;
    fileInputEl.value = "";
    renderSelectedAttachment();
    updateCounter();
  }

  function updateCounter() {
    const length = inputEl.value.trim().length;
    counterEl.textContent = selectedAttachment ? `${length}/1000 + 1 tệp` : `${length}/1000`;
    sendBtn.disabled = sending || !threadId || (length === 0 && !selectedAttachment);
    attachBtn.disabled = sending;
  }

  function setLoadingState(isLoading) {
    refreshBtn.disabled = isLoading;
    if (isLoading) {
      messagesEl.innerHTML = normalizeText('<div class="chat-empty">Đang tải cuộc trò chuyện...</div>');
    }
  }

  function renderMessages(messages) {
    if (!Array.isArray(messages) || messages.length === 0) {
      messagesEl.innerHTML = normalizeText('<div class="chat-empty">Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện với lễ tân.</div>');
      lastMessageId = null;
      return;
    }

    const html = messages
      .map((message) => {
        const customerSide = message.senderRole === "CUSTOMER";
        const senderLabel = customerSide ? normalizeText("Bạn") : normalizeText(message.senderName || "Lễ tân");
        const textHtml = message.content
          ? `<div class="chat-text">${escapeHtml(message.content)}</div>`
          : "";
        const attachmentHtml = message.hasAttachment && message.attachmentDownloadUrl
          ? `
            <a class="chat-attachment-link" href="${message.attachmentDownloadUrl}" target="_blank" rel="noopener">
              <span class="chat-attachment-icon">📎</span>
              <span>${escapeHtml(message.attachmentOriginalName || normalizeText("Tệp đính kèm"))}</span>
              ${message.attachmentSize ? `<small>${escapeHtml(formatFileSize(message.attachmentSize))}</small>` : ""}
            </a>
          `
          : "";
        return `
          <div class="chat-row ${customerSide ? "customer" : "staff"}">
            <div class="chat-bubble">
              <div class="chat-author">${escapeHtml(senderLabel)}</div>
              ${textHtml}
              ${attachmentHtml}
              <span class="chat-meta">${formatDateTime(message.createdAt)}</span>
            </div>
          </div>
        `;
      })
      .join("");

    const newestId = messages[messages.length - 1].id;
    const hadNew = newestId && newestId !== lastMessageId;
    messagesEl.innerHTML = html;
    scrollToBottom(hadNew || isOpen());
    lastMessageId = newestId || null;
  }

  async function ensureThread() {
    if (threadId && threadSummary) return threadId;
    setStatus("\u0110ang k\u1ebft n\u1ed1i", "warning");
    const response = await fetch("/customer/chat/thread", { credentials: "same-origin" });
    if (!response.ok) throw new Error(normalizeText("Không thể khởi tạo cuộc trò chuyện."));
    threadSummary = await response.json();
    threadId = threadSummary.id;
    setStatus("\u0110ang ho\u1ea1t \u0111\u1ed9ng", "success");
    const assignee = threadSummary.assignedStaffName || threadSummary.assignedStaffEmail;
    setAssignee(assignee ? "Đang phụ trách: " + assignee : "");
    updateCounter();
    return threadId;
  }

  function renderBadge(count) {
    const safeCount = Number(count || 0);
    if (safeCount <= 0) {
      badgeEl.classList.add("hidden");
      badgeEl.textContent = "0";
      return;
    }
    badgeEl.classList.remove("hidden");
    badgeEl.textContent = safeCount > 99 ? "99+" : String(safeCount);
  }

  async function loadUnreadCount() {
    if (!threadId) return;
    const response = await fetch(`/customer/chat/unread-count?threadId=${threadId}`, {
      credentials: "same-origin"
    });
    if (!response.ok) return;
    const data = await response.json();
    renderBadge(data.unreadCount);
  }

  async function loadMessages(manual) {
    if (!threadId || loadingMessages) return;
    loadingMessages = true;
    if (manual) {
      setHelper("\u0110ang \u0111\u1ed3ng b\u1ed9 tin nh\u1eafn m\u1edbi...", "neutral");
    }
    try {
      const response = await fetch(`/customer/chat/messages?threadId=${threadId}`, {
        credentials: "same-origin"
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(normalizeText(error.error || "Không thể tải tin nhắn."));
      }
      const data = await response.json();
      renderMessages(data);
      renderBadge(0);
      setStatus("\u0110ang ho\u1ea1t \u0111\u1ed9ng", "success");
      setHelper("Tin nhắn được cập nhật tự động mỗi 3 giây.", "neutral");
    } finally {
      loadingMessages = false;
    }
  }

  async function sendMessage() {
    const content = inputEl.value.trim();
    if ((!content && !selectedAttachment) || !threadId || sending) return;
    sending = true;
    sendBtn.disabled = true;
    sendBtn.textContent = "Đang gửi...";
    setHelper(selectedAttachment ? "\u0110ang g\u1eedi tin nh\u1eafn v\u00e0 t\u1ec7p \u0111\u00ednh k\u00e8m..." : "\u0110ang g\u1eedi tin nh\u1eafn...", "neutral");
    try {
      const formData = new FormData();
      formData.append("threadId", String(threadId));
      if (content) {
        formData.append("content", content);
      }
      if (selectedAttachment) {
        formData.append("attachment", selectedAttachment);
      }

      const response = await fetch("/customer/chat/send", {
        method: "POST",
        credentials: "same-origin",
        body: formData
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(normalizeText(error.error || "Gửi tin nhắn thất bại."));
      }
      inputEl.value = "";
      clearSelectedAttachment();
      updateCounter();
      await loadMessages();
      inputEl.focus();
      setHelper("\u0110\u00e3 g\u1eedi tin nh\u1eafn cho l\u1ec5 t\u00e2n.", "success");
    } catch (error) {
      setHelper(error.message || "Gửi tin nhắn thất bại.", "error");
    } finally {
      sending = false;
      sendBtn.textContent = "Gửi";
      updateCounter();
    }
  }

  function openWindow() {
    windowEl.classList.remove("hidden");
    loadMessages(true).catch((error) => {
      setHelper(error.message || "Không thể tải cuộc trò chuyện.", "error");
    });
    inputEl.focus();
  }

  function closeWindow() {
    windowEl.classList.add("hidden");
  }

  function startPolling() {
    if (pollTimer) return;
    pollTimer = window.setInterval(async () => {
      try {
        await ensureThread();
        if (isOpen()) {
          await loadMessages(false);
        } else {
          await loadUnreadCount();
        }
      } catch (error) {
        setStatus("M\u1ea5t k\u1ebft n\u1ed1i t\u1ea1m th\u1eddi", "error");
      }
    }, 3000);
  }

  async function init() {
    try {
      setLoadingState(true);
      await ensureThread();
      await loadUnreadCount();
      setHelper("B\u1ea1n c\u00f3 th\u1ec3 h\u1ecfi v\u1ec1 l\u1ecbch h\u1eb9n, d\u1ecbch v\u1ee5 ho\u1eb7c chi ph\u00ed kh\u00e1m.", "neutral");
      startPolling();
    } catch (error) {
      toggleBtn.classList.add("hidden");
      windowEl.classList.add("hidden");
    } finally {
      setLoadingState(false);
      renderSelectedAttachment();
      updateCounter();
    }
  }

  function decorateWidget() {
    toggleBtn.setAttribute("aria-label", "Mở chat với lễ tân");
    const toggleText = toggleBtn.querySelector("span");
    if (toggleText) {
      toggleText.innerHTML = normalizeText("&#128172; Chat với lễ tân");
    }
    inputEl.setAttribute("placeholder", normalizeText("Nhập tin nhắn..."));
    sendBtn.textContent = normalizeText("Gửi");

    const titleEl = headerEl.querySelector("span");
    if (titleEl) {
      titleEl.innerHTML = normalizeText('Chat với lễ tân <small class="chat-title-note">Hỗ trợ trực tuyến</small>');
    }

    const actionWrap = document.createElement("div");
    actionWrap.className = "chat-header-actions";
    actionWrap.innerHTML = `
      <button id="chat-refresh" class="chat-icon-btn" type="button" aria-label="Tải lại">
        <i class="bi bi-arrow-clockwise"></i>
      </button>
    `;
    const close = closeBtn?.cloneNode(true);
    if (close) {
      close.id = "chat-close";
      actionWrap.appendChild(close);
      closeBtn.replaceWith(actionWrap);
    }

    const subheader = document.createElement("div");
    subheader.className = "chat-subheader";
    subheader.innerHTML = normalizeText(`
      <div id="chat-status" class="chat-status" data-tone="neutral">Đang khởi tạo</div>
      <div id="chat-assignee" class="chat-assignee">Lễ tân sẽ phản hồi sớm nhất có thể</div>
    `);
    headerEl.insertAdjacentElement("afterend", subheader);

    const helper = document.createElement("div");
    helper.id = "chat-helper";
    helper.className = "chat-helper";
    helper.dataset.tone = "neutral";
    messagesEl.insertAdjacentElement("afterend", helper);

    const counter = document.createElement("div");
    counter.className = "chat-footer-tools";
    counter.innerHTML = '<span id="chat-counter">0/1000</span>';
    inputEl.parentElement.insertAdjacentElement("afterend", counter);
  }

  toggleBtn.addEventListener("click", () => {
    if (isOpen()) {
      closeWindow();
      return;
    }
    openWindow();
  });

  windowEl.addEventListener("click", (event) => {
    const target = event.target.closest("#chat-refresh, #chat-close");
    if (!target) return;
    if (target.id === "chat-close") {
      closeWindow();
      return;
    }
    if (target.id === "chat-refresh") {
      loadMessages(true).catch((error) => {
        setHelper(error.message || "Không thể tải cuộc trò chuyện.", "error");
      });
    }
  });

  sendBtn.addEventListener("click", () => {
    sendMessage().catch(() => {});
  });

  attachBtn.addEventListener("click", () => {
    if (!sending) {
      fileInputEl.click();
    }
  });

  fileInputEl.addEventListener("change", () => {
    const file = fileInputEl.files && fileInputEl.files[0] ? fileInputEl.files[0] : null;
    if (!file) {
      clearSelectedAttachment();
      return;
    }
    if (file.size > maxAttachmentSize) {
      clearSelectedAttachment();
      setHelper("Tệp đính kèm không được vượt quá 5MB.", "error");
      return;
    }
    selectedAttachment = file;
    renderSelectedAttachment();
    updateCounter();
    setHelper("Tệp đã sẵn sàng để gửi.", "neutral");
  });

  attachmentClearEl.addEventListener("click", clearSelectedAttachment);

  inputEl.addEventListener("input", updateCounter);
  inputEl.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      sendMessage().catch(() => {});
    }
    if (event.key === "Escape") {
      closeWindow();
    }
  });

  init();
})();

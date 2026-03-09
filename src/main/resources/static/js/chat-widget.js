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

  if (!toggleBtn || !windowEl || !headerEl || !messagesEl || !inputEl || !sendBtn || !badgeEl) return;

  let threadId = null;
  let threadSummary = null;
  let pollTimer = null;
  let lastMessageId = null;
  let loadingMessages = false;
  let sending = false;

  decorateWidget();

  const statusEl = document.getElementById("chat-status");
  const assigneeEl = document.getElementById("chat-assignee");
  const refreshBtn = document.getElementById("chat-refresh");
  const counterEl = document.getElementById("chat-counter");
  const helperEl = document.getElementById("chat-helper");

  function isOpen() {
    return !windowEl.classList.contains("hidden");
  }

  function authJsonOptions(method, body) {
    const options = {
      method,
      headers: { "Content-Type": "application/json" },
      credentials: "same-origin"
    };
    if (body !== undefined) {
      options.body = JSON.stringify(body);
    }
    return options;
  }

  function escapeHtml(value) {
    return String(value || "")
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
    statusEl.textContent = text;
    statusEl.dataset.tone = tone || "neutral";
  }

  function setAssignee(text) {
    assigneeEl.textContent = text || "Lễ tân sẽ phản hồi sớm nhất có thể";
  }

  function setHelper(text, tone) {
    helperEl.textContent = text || "";
    helperEl.dataset.tone = tone || "neutral";
  }

  function updateCounter() {
    const length = inputEl.value.trim().length;
    counterEl.textContent = length + "/1000";
    sendBtn.disabled = sending || !threadId || length === 0;
  }

  function setLoadingState(isLoading) {
    refreshBtn.disabled = isLoading;
    if (isLoading) {
      messagesEl.innerHTML = '<div class="chat-empty">Đang tải cuộc trò chuyện...</div>';
    }
  }

  function renderMessages(messages) {
    if (!Array.isArray(messages) || messages.length === 0) {
      messagesEl.innerHTML = '<div class="chat-empty">Chưa có tin nhắn nào. Hãy bắt đầu cuộc trò chuyện với lễ tân.</div>';
      lastMessageId = null;
      return;
    }

    const html = messages
      .map((message) => {
        const customerSide = message.senderRole === "CUSTOMER";
        const senderLabel = customerSide ? "Bạn" : (message.senderName || "Lễ tân");
        return `
          <div class="chat-row ${customerSide ? "customer" : "staff"}">
            <div class="chat-bubble">
              <div class="chat-author">${escapeHtml(senderLabel)}</div>
              <div>${escapeHtml(message.content)}</div>
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
    setStatus("Đang kết nối", "warning");
    const response = await fetch("/customer/chat/thread", { credentials: "same-origin" });
    if (!response.ok) throw new Error("Không thể khởi tạo cuộc trò chuyện.");
    threadSummary = await response.json();
    threadId = threadSummary.id;
    setStatus("Đang hoạt động", "success");
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
      setHelper("Đang đồng bộ tin nhắn mới...", "neutral");
    }
    try {
      const response = await fetch(`/customer/chat/messages?threadId=${threadId}`, {
        credentials: "same-origin"
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || "Không thể tải tin nhắn.");
      }
      const data = await response.json();
      renderMessages(data);
      renderBadge(0);
      setStatus("Đang hoạt động", "success");
      setHelper("Tin nhắn được cập nhật tự động mỗi 3 giây.", "neutral");
    } finally {
      loadingMessages = false;
    }
  }

  async function sendMessage() {
    const content = inputEl.value.trim();
    if (!content || !threadId || sending) return;
    sending = true;
    sendBtn.disabled = true;
    sendBtn.textContent = "Đang gửi...";
    setHelper("Đang gửi tin nhắn...", "neutral");
    try {
      const response = await fetch(
        "/customer/chat/send",
        authJsonOptions("POST", { threadId, content })
      );
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || "Gửi tin nhắn thất bại.");
      }
      inputEl.value = "";
      updateCounter();
      await loadMessages();
      inputEl.focus();
      setHelper("Đã gửi tin nhắn cho lễ tân.", "success");
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
        setStatus("Mất kết nối tạm thời", "error");
      }
    }, 3000);
  }

  async function init() {
    try {
      setLoadingState(true);
      await ensureThread();
      await loadUnreadCount();
      setHelper("Bạn có thể hỏi về lịch hẹn, dịch vụ hoặc chi phí khám.", "neutral");
      startPolling();
    } catch (error) {
      toggleBtn.classList.add("hidden");
      windowEl.classList.add("hidden");
    } finally {
      setLoadingState(false);
      updateCounter();
    }
  }

  function decorateWidget() {
    toggleBtn.setAttribute("aria-label", "Mở chat với lễ tân");
    const toggleText = toggleBtn.querySelector("span");
    if (toggleText) {
      toggleText.innerHTML = "&#128172; Chat với lễ tân";
    }
    inputEl.setAttribute("placeholder", "Nhập tin nhắn...");
    sendBtn.textContent = "Gửi";

    const titleEl = headerEl.querySelector("span");
    if (titleEl) {
      titleEl.innerHTML = 'Chat với lễ tân <small class="chat-title-note">Hỗ trợ trực tuyến</small>';
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
    subheader.innerHTML = `
      <div id="chat-status" class="chat-status" data-tone="neutral">Đang khởi tạo</div>
      <div id="chat-assignee" class="chat-assignee">Lễ tân sẽ phản hồi sớm nhất có thể</div>
    `;
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

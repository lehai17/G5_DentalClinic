(function () {
  if (window.__customerChatWidgetInitialized) return;
  window.__customerChatWidgetInitialized = true;

  const toggleBtn = document.getElementById("chat-toggle");
  const closeBtn = document.getElementById("chat-close");
  const windowEl = document.getElementById("chat-window");
  const messagesEl = document.getElementById("chat-messages");
  const inputEl = document.getElementById("chat-text");
  const sendBtn = document.getElementById("chat-send");
  const badgeEl = document.getElementById("chat-badge");

  if (!toggleBtn || !windowEl || !messagesEl || !inputEl || !sendBtn) return;

  let threadId = null;
  let pollTimer = null;
  let lastMessageId = null;
  let loadingMessages = false;

  function isOpen() {
    return !windowEl.classList.contains("hidden");
  }

  function authJsonOptions(method, body) {
    const options = {
      method,
      headers: { "Content-Type": "application/json" },
      credentials: "same-origin",
    };
    if (body !== undefined) {
      options.body = JSON.stringify(body);
    }
    return options;
  }

  function escapeHtml(value) {
    return (value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
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
      month: "2-digit",
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

  function renderMessages(messages) {
    if (!Array.isArray(messages) || messages.length === 0) {
      messagesEl.innerHTML = '<div class="chat-empty">Chưa có tin nhắn. Hãy bắt đầu cuộc trò chuyện.</div>';
      lastMessageId = null;
      return;
    }

    const html = messages
      .map((m) => {
        const customerSide = m.senderRole === "CUSTOMER";
        return `
          <div class="chat-row ${customerSide ? "customer" : "staff"}">
            <div class="chat-bubble">
              ${escapeHtml(m.content)}
              <span class="chat-meta">${formatDateTime(m.createdAt)}</span>
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
    if (threadId) return threadId;
    const response = await fetch("/customer/chat/thread", { credentials: "same-origin" });
    if (!response.ok) throw new Error("Không thể khởi tạo cuộc trò chuyện.");
    const data = await response.json();
    threadId = data.id;
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
      credentials: "same-origin",
    });
    if (!response.ok) return;
    const data = await response.json();
    renderBadge(data.unreadCount);
  }

  async function loadMessages() {
    if (!threadId || loadingMessages) return;
    loadingMessages = true;
    try {
      const response = await fetch(`/customer/chat/messages?threadId=${threadId}`, {
        credentials: "same-origin",
      });
      if (!response.ok) return;
      const data = await response.json();
      renderMessages(data);
      renderBadge(0);
    } finally {
      loadingMessages = false;
    }
  }

  async function sendMessage() {
    const content = inputEl.value.trim();
    if (!content || !threadId) return;
    sendBtn.disabled = true;
    try {
      const response = await fetch(
        "/customer/chat/send",
        authJsonOptions("POST", { threadId, content })
      );
      if (!response.ok) {
        return;
      }
      inputEl.value = "";
      await loadMessages();
      inputEl.focus();
    } finally {
      sendBtn.disabled = false;
    }
  }

  function openWindow() {
    windowEl.classList.remove("hidden");
    loadMessages().catch(() => {});
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
          await loadMessages();
        } else {
          await loadUnreadCount();
        }
      } catch (e) {
        // Ignore polling errors to avoid noisy UI.
      }
    }, 3000);
  }

  async function init() {
    try {
      await ensureThread();
      await loadUnreadCount();
      startPolling();
    } catch (e) {
      toggleBtn.classList.add("hidden");
      windowEl.classList.add("hidden");
    }
  }

  toggleBtn.addEventListener("click", () => {
    if (isOpen()) {
      closeWindow();
      return;
    }
    openWindow();
  });

  closeBtn?.addEventListener("click", closeWindow);
  sendBtn.addEventListener("click", () => {
    sendMessage().catch(() => {});
  });

  inputEl.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      sendMessage().catch(() => {});
    }
  });

  init();
})();

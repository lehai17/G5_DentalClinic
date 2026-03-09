(function () {
  const listEl = document.getElementById("chat-thread-list");
  const messagesEl = document.getElementById("staff-chat-messages");
  const inputEl = document.getElementById("staff-chat-input");
  const sendBtn = document.getElementById("staff-chat-send");
  const titleEl = document.getElementById("staff-chat-title");

  if (!listEl || !messagesEl || !inputEl || !sendBtn || !titleEl) return;

  let threads = [];
  let currentThreadId = null;
  let pollTimer = null;
  let loadingMessages = false;

  function escapeHtml(value) {
    return (value || "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function formatDateTime(value) {
    if (!value) return "--";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "--";
    return date.toLocaleString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
      day: "2-digit",
      month: "2-digit"
    });
  }

  function setConversationEnabled(enabled) {
    inputEl.disabled = !enabled;
    sendBtn.disabled = !enabled;
  }

  function renderThreadList() {
    if (!threads.length) {
      listEl.innerHTML = '<div class="conversation-empty" style="padding:16px;">Chưa có cuộc hội thoại nào.</div>';
      return;
    }

    listEl.innerHTML = threads
      .map((thread) => {
        const isActive = currentThreadId === thread.id;
        const unread = Number(thread.unreadCount || 0);
        const customerLabel = thread.customerName || thread.customerEmail || "Khách hàng";
        return `
          <button class="thread-item ${isActive ? "active" : ""}" data-thread-id="${thread.id}" type="button">
            <span class="thread-top">
              <span class="thread-email">${escapeHtml(customerLabel)}</span>
              <span class="thread-time">${formatDateTime(thread.lastMessageAt)}</span>
            </span>
            <span class="thread-last">${escapeHtml(thread.lastMessage || "(Chưa có tin nhắn)")}</span>
            ${unread > 0 ? `<span class="thread-unread">${unread > 99 ? "99+" : unread}</span>` : ""}
          </button>
        `;
      })
      .join("");

    listEl.querySelectorAll(".thread-item").forEach((item) => {
      item.addEventListener("click", () => {
        const threadId = Number(item.dataset.threadId);
        if (!threadId) return;
        selectThread(threadId).catch(() => {});
      });
    });
  }

  function renderMessages(messages) {
    if (!Array.isArray(messages) || !messages.length) {
      messagesEl.innerHTML = '<div class="conversation-empty">Chưa có tin nhắn.</div>';
      return;
    }

    messagesEl.innerHTML = messages
      .map((message) => {
        const staffSide = message.senderRole === "STAFF" || message.senderRole === "ADMIN" || message.senderRole === "DENTIST";
        const senderLabel = message.senderName || (staffSide ? "Lễ tân" : "Khách hàng");
        return `
          <div class="message-row ${staffSide ? "staff" : "customer"}">
            <div class="message-bubble">
              <div class="message-author">${escapeHtml(senderLabel)}</div>
              ${escapeHtml(message.content)}
              <span class="message-meta">${formatDateTime(message.createdAt)}</span>
            </div>
          </div>
        `;
      })
      .join("");

    messagesEl.scrollTop = messagesEl.scrollHeight;
  }

  async function loadInbox() {
    const response = await fetch("/staff/chat/inbox/data", { credentials: "same-origin" });
    if (!response.ok) return;
    const data = await response.json();
    threads = Array.isArray(data) ? data : [];

    if (currentThreadId && !threads.some((thread) => thread.id === currentThreadId)) {
      currentThreadId = null;
    }
    if (!currentThreadId && threads.length) {
      currentThreadId = threads[0].id;
    }

    renderThreadList();
  }

  async function loadMessages() {
    if (!currentThreadId || loadingMessages) return;
    loadingMessages = true;
    try {
      const response = await fetch(`/staff/chat/${currentThreadId}`, { credentials: "same-origin" });
      if (!response.ok) return;
      const data = await response.json();
      renderMessages(data);
    } finally {
      loadingMessages = false;
    }
  }

  async function selectThread(threadId) {
    currentThreadId = threadId;
    const thread = threads.find((item) => item.id === threadId);
    const customerLabel = thread?.customerName || thread?.customerEmail || "Khách hàng";
    titleEl.textContent = `Đang chat với: ${customerLabel}`;
    setConversationEnabled(true);
    renderThreadList();
    await loadMessages();
  }

  async function sendMessage() {
    const content = inputEl.value.trim();
    if (!currentThreadId || !content) return;
    sendBtn.disabled = true;
    try {
      const response = await fetch(`/staff/chat/${currentThreadId}/reply`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "same-origin",
        body: JSON.stringify({ content })
      });
      if (!response.ok) return;
      inputEl.value = "";
      await Promise.all([loadInbox(), loadMessages()]);
      inputEl.focus();
    } finally {
      sendBtn.disabled = false;
    }
  }

  function startPolling() {
    if (pollTimer) return;
    pollTimer = window.setInterval(async () => {
      try {
        await loadInbox();
        if (currentThreadId) {
          await loadMessages();
        }
      } catch (e) {
        // Ignore polling errors.
      }
    }, 3000);
  }

  sendBtn.addEventListener("click", () => {
    sendMessage().catch(() => {});
  });

  inputEl.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      sendMessage().catch(() => {});
    }
  });

  (async function init() {
    setConversationEnabled(false);
    try {
      await loadInbox();
      if (currentThreadId) {
        await selectThread(currentThreadId);
      } else {
        titleEl.textContent = "Chọn cuộc hội thoại để bắt đầu";
      }
      startPolling();
    } catch (e) {
      listEl.innerHTML =
        '<div class="conversation-empty" style="padding:16px;">Không thể tải hộp thư chat.</div>';
    }
  })();
})();


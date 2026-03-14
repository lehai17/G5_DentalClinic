(function () {
  const listEl = document.getElementById("chat-thread-list");
  const messagesEl = document.getElementById("staff-chat-messages");
  const inputEl = document.getElementById("staff-chat-input");
  const sendBtn = document.getElementById("staff-chat-send");
  const titleEl = document.getElementById("staff-chat-title");
  const attachBtn = document.getElementById("staff-chat-attach");
  const fileInputEl = document.getElementById("staff-chat-attachment");
  const attachmentBarEl = document.getElementById("staff-chat-attachment-bar");
  const attachmentNameEl = document.getElementById("staff-chat-attachment-name");
  const attachmentClearEl = document.getElementById("staff-chat-attachment-clear");
  const maxAttachmentSize = 5 * 1024 * 1024;

  if (!listEl || !messagesEl || !inputEl || !sendBtn || !titleEl || !attachBtn || !fileInputEl || !attachmentBarEl || !attachmentNameEl || !attachmentClearEl) {
    return;
  }

  let threads = [];
  let currentThreadId = null;
  let pollTimer = null;
  let loadingMessages = false;
  let selectedAttachment = null;

  function escapeHtml(value) {
    return String(value || "")
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

  function formatFileSize(value) {
    const size = Number(value || 0);
    if (!size) return "";
    if (size >= 1024 * 1024) {
      return `${(size / (1024 * 1024)).toFixed(1)} MB`;
    }
    return `${Math.max(1, Math.round(size / 1024))} KB`;
  }

  function setConversationEnabled(enabled) {
    inputEl.disabled = !enabled;
    sendBtn.disabled = !enabled || (inputEl.value.trim().length === 0 && !selectedAttachment);
    attachBtn.disabled = !enabled;
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
    setConversationEnabled(Boolean(currentThreadId));
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
        const textHtml = message.content
          ? `<div class="message-text">${escapeHtml(message.content)}</div>`
          : "";
        const attachmentHtml = message.hasAttachment && message.attachmentDownloadUrl
          ? `
            <a class="message-attachment" href="${message.attachmentDownloadUrl}" target="_blank" rel="noopener">
              <span class="message-attachment-icon">📎</span>
              <span>${escapeHtml(message.attachmentOriginalName || "Tệp đính kèm")}</span>
              ${message.attachmentSize ? `<small>${escapeHtml(formatFileSize(message.attachmentSize))}</small>` : ""}
            </a>
          `
          : "";
        return `
          <div class="message-row ${staffSide ? "staff" : "customer"}">
            <div class="message-bubble">
              <div class="message-author">${escapeHtml(senderLabel)}</div>
              ${textHtml}
              ${attachmentHtml}
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
      clearSelectedAttachment();
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
    clearSelectedAttachment();
    const thread = threads.find((item) => item.id === threadId);
    const customerLabel = thread?.customerName || thread?.customerEmail || "Khách hàng";
    titleEl.textContent = `Đang chat với: ${customerLabel}`;
    setConversationEnabled(true);
    renderThreadList();
    await loadMessages();
  }

  async function sendMessage() {
    const content = inputEl.value.trim();
    if (!currentThreadId || (!content && !selectedAttachment)) return;
    sendBtn.disabled = true;
    try {
      const formData = new FormData();
      if (content) {
        formData.append("content", content);
      }
      if (selectedAttachment) {
        formData.append("attachment", selectedAttachment);
      }
      const response = await fetch(`/staff/chat/${currentThreadId}/reply`, {
        method: "POST",
        credentials: "same-origin",
        body: formData
      });
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.error || "Không thể gửi phản hồi.");
      }
      inputEl.value = "";
      clearSelectedAttachment();
      await Promise.all([loadInbox(), loadMessages()]);
      inputEl.focus();
    } catch (error) {
      window.alert(error.message || "Không thể gửi phản hồi.");
    } finally {
      setConversationEnabled(Boolean(currentThreadId));
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

  attachBtn.addEventListener("click", () => {
    if (!attachBtn.disabled) {
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
      window.alert("Tệp đính kèm không được vượt quá 5MB.");
      return;
    }
    selectedAttachment = file;
    renderSelectedAttachment();
    setConversationEnabled(Boolean(currentThreadId));
  });

  attachmentClearEl.addEventListener("click", clearSelectedAttachment);

  inputEl.addEventListener("input", () => {
    setConversationEnabled(Boolean(currentThreadId));
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

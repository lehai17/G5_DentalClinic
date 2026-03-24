(function () {
  const shell = document.querySelector('.notif-shell');
  const form = document.querySelector('.notif-filters');
  const select = form?.querySelector('select[name="category"]');
  const notifList = document.querySelector('.notif-list');
  const activeNavItem = document.querySelector('.nav-item.active');

  if (select && form) {
    select.addEventListener('change', () => {
      const pageInput = form.querySelector('input[name="page"]');
      if (pageInput) pageInput.value = '0';
      form.submit();
    });
  }

  if (!shell || !notifList) {
    return;
  }

  function getFilter() {
    return (shell.dataset.filter || 'all').trim().toLowerCase();
  }

  function getSidebarBadge() {
    return activeNavItem?.querySelector('.nav-badge') || null;
  }

  function syncSidebarBadge(value) {
    if (!activeNavItem) return;

    const count = Number.parseInt(String(value ?? '').trim(), 10);
    const shouldShow = Number.isFinite(count) && count > 0;

    let badge = getSidebarBadge();
    if (!shouldShow) {
      badge?.remove();
      return;
    }

    if (!badge) {
      badge = document.createElement('span');
      badge.className = 'nav-badge';
      activeNavItem.appendChild(badge);
    }
    badge.textContent = String(count);
  }

  function changeSidebarBadge(delta) {
    const badge = getSidebarBadge();
    const current = badge ? Number.parseInt(badge.textContent || '0', 10) : 0;
    syncSidebarBadge(Math.max(0, current + delta));
  }

  function ensureEmptyState() {
    const cards = notifList.querySelectorAll('.notif-item');
    let empty = notifList.querySelector('.empty');

    if (cards.length > 0) {
      empty?.remove();
      return;
    }

    if (!empty) {
      empty = document.createElement('div');
      empty.className = 'empty';
      empty.textContent = 'Chưa có thông báo nào.';
      notifList.appendChild(empty);
    }
  }

  function setCardReadState(card, isRead) {
    card.dataset.read = isRead ? 'true' : 'false';

    const unreadDot = card.querySelector('.unread-dot');
    unreadDot?.classList.toggle('hidden', isRead);

    const readForm = card.querySelector('.action-read');
    const unreadForm = card.querySelector('.action-unread');

    readForm?.classList.toggle('hidden', isRead);
    unreadForm?.classList.toggle('hidden', !isRead);
  }

  function shouldRemoveCardForFilter(isRead) {
    const filter = getFilter();
    if (filter === 'unread' && isRead) return true;
    if (filter === 'read' && !isRead) return true;
    return false;
  }

  async function submitAction(formElement) {
    const scrollTop = notifList.scrollTop;
    const formData = new FormData(formElement);

    try {
      const response = await fetch(formElement.action, {
        method: 'POST',
        body: formData,
        headers: {
          'X-Requested-With': 'XMLHttpRequest'
        }
      });

      if (!response.ok) {
        formElement.submit();
        return;
      }

      if (formElement.classList.contains('toolbar-form')) {
        const filter = getFilter();
        const unreadCards = Array.from(notifList.querySelectorAll('.notif-item'))
          .filter(item => item.dataset.read !== 'true');

        unreadCards.forEach(cardItem => {
          if (filter === 'unread') {
            cardItem.remove();
          } else {
            setCardReadState(cardItem, true);
          }
        });

        syncSidebarBadge(0);
        ensureEmptyState();
        notifList.scrollTop = scrollTop;
        return;
      }

      const card = formElement.closest('.notif-item');
      if (!card) {
        notifList.scrollTop = scrollTop;
        return;
      }

      const wasRead = card.dataset.read === 'true';

      if (formElement.classList.contains('action-read')) {
        if (!wasRead) {
          if (shouldRemoveCardForFilter(true)) {
            card.remove();
          } else {
            setCardReadState(card, true);
          }
          changeSidebarBadge(-1);
        }
      } else if (formElement.classList.contains('action-unread')) {
        if (wasRead) {
          if (shouldRemoveCardForFilter(false)) {
            card.remove();
          } else {
            setCardReadState(card, false);
          }
          changeSidebarBadge(1);
        }
      } else if (formElement.classList.contains('action-delete')) {
        if (!wasRead) {
          changeSidebarBadge(-1);
        }
        card.remove();
      }

      ensureEmptyState();
      notifList.scrollTop = scrollTop;
    } catch (error) {
      formElement.submit();
    }
  }

  document.addEventListener('submit', (event) => {
    const formElement = event.target;
    if (!(formElement instanceof HTMLFormElement)) {
      return;
    }

    if (!formElement.matches('.notif-action-form, .toolbar-form')) {
      return;
    }

    event.preventDefault();
    submitAction(formElement);
  });

  document.addEventListener('click', async (event) => {
    const link = event.target instanceof Element
      ? event.target.closest('.open-related-link')
      : null;

    if (!(link instanceof HTMLAnchorElement)) {
      return;
    }

    const card = link.closest('.notif-item');
    if (!(card instanceof HTMLElement)) {
      return;
    }

    if (card.dataset.read === 'true') {
      return;
    }

    const readForm = card.querySelector('.action-read');
    if (!(readForm instanceof HTMLFormElement)) {
      return;
    }

    event.preventDefault();

    const href = link.href;
    const formData = new FormData(readForm);

    try {
      const response = await fetch(readForm.action, {
        method: 'POST',
        body: formData,
        headers: {
          'X-Requested-With': 'XMLHttpRequest'
        }
      });

      if (response.ok) {
        setCardReadState(card, true);
        changeSidebarBadge(-1);
      }
    } catch (error) {
      // Ignore and continue navigating to the related page.
    }

    window.location.href = href;
  });
})();

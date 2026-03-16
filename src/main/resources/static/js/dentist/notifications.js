(function () {
  // Small UX: submit filter form when category changes.
  const form = document.querySelector('.notif-filters');
  if (!form) return;

  const select = form.querySelector('select[name="category"]');
  if (!select) return;

  select.addEventListener('change', () => {
    // Ensure we reset paging when changing type filter.
    const pageInput = form.querySelector('input[name="page"]');
    if (pageInput) pageInput.value = '0';
    form.submit();
  });
})();


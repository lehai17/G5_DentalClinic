document.addEventListener('DOMContentLoaded', function () {
  const filterForm = document.getElementById('filterForm');
  const fromInput = document.getElementById('fromDate');
  const toInput = document.getElementById('toDate');
  const resetBtn = document.getElementById('resetFilters');

  function syncDateBounds() {
    if (!fromInput || !toInput) return;
    if (fromInput.value) {
      toInput.setAttribute('min', fromInput.value);
      if (toInput.value && toInput.value < fromInput.value) {
        toInput.value = fromInput.value;
      }
    } else {
      toInput.removeAttribute('min');
    }
  }

  fromInput?.addEventListener('change', syncDateBounds);
  toInput?.addEventListener('change', syncDateBounds);
  syncDateBounds();

  filterForm?.addEventListener('submit', function (e) {
    if (!fromInput || !toInput) return;
    if (fromInput.value && toInput.value && fromInput.value > toInput.value) {
      e.preventDefault();
      alert('Từ ngày không được lớn hơn đến ngày.');
    }
  });

  resetBtn?.addEventListener('click', function () {
    window.location.href = '/dentist/examined-patients';
  });

  const rows = document.querySelectorAll('.examined-row');

  function setActiveStep(chainId, stepId) {
    const container = document.querySelector(`.chain-detail[data-chain-id="${chainId}"]`);
    if (!container) return;

    container.querySelectorAll('.chain-step-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.stepId === stepId);
    });

    container.querySelectorAll('.chain-step-panel').forEach(panel => {
      panel.classList.toggle('active', panel.dataset.stepId === stepId);
    });
  }

  function openChain(chainId) {
    document.querySelectorAll('.chain-detail-row:not([hidden])').forEach(row => {
      if (row.dataset.chainId !== chainId) {
        row.setAttribute('hidden', '');
        const openRow = document.querySelector(`.examined-row[data-chain-id="${row.dataset.chainId}"]`);
        openRow?.classList.remove('open');
      }
    });

    const row = document.querySelector(`.examined-row[data-chain-id="${chainId}"]`);
    const detail = document.querySelector(`.chain-detail-row[data-chain-id="${chainId}"]`);
    if (!detail) return;

    const isOpen = !detail.hasAttribute('hidden');
    if (isOpen) {
      detail.setAttribute('hidden', '');
      row?.classList.remove('open');
      return;
    }

    detail.removeAttribute('hidden');
    row?.classList.add('open');

    const defaultPanel = detail.querySelector('.chain-step-panel');
    if (defaultPanel) {
      setActiveStep(chainId, defaultPanel.dataset.stepId);
    }
  }

  rows.forEach(row => {
    row.addEventListener('click', () => openChain(row.dataset.chainId));
  });

  document.querySelectorAll('.chain-detail-row').forEach(detailRow => {
    detailRow.addEventListener('click', e => e.stopPropagation());
  });

  document.querySelectorAll('.chain-step-btn, .chain-pill, .chain-step').forEach(btn => {
    btn.addEventListener('click', e => {
      e.stopPropagation();
      const chainId = btn.dataset.chainId;
      const stepId = btn.dataset.stepId;
      if (!chainId || !stepId) return;
      const detailRow = document.querySelector(`.chain-detail-row[data-chain-id="${chainId}"]`);
      if (detailRow && detailRow.hasAttribute('hidden')) {
        detailRow.removeAttribute('hidden');
        const row = document.querySelector(`.examined-row[data-chain-id="${chainId}"]`);
        row?.classList.add('open');
      }
      setActiveStep(chainId, stepId);
    });
  });
});

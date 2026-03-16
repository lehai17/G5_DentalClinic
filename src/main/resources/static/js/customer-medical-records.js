(function () {
  "use strict";

  var resetBtn = document.getElementById("medical-reset");
  var fromInput = document.getElementById("medical-from");
  var toInput = document.getElementById("medical-to");
  var filterForm = document.getElementById("medicalRecordFilter");

  function syncDateBounds() {
    if (!fromInput || !toInput) return;
    if (fromInput.value) {
      toInput.setAttribute("min", fromInput.value);
      if (toInput.value && toInput.value < fromInput.value) {
        toInput.value = fromInput.value;
      }
    } else {
      toInput.removeAttribute("min");
    }
  }

  fromInput?.addEventListener("change", syncDateBounds);
  toInput?.addEventListener("change", syncDateBounds);
  syncDateBounds();

  resetBtn?.addEventListener("click", function () {
    if (filterForm && filterForm.action) {
      window.location.href = filterForm.action;
    } else {
      window.location.href = "/patient/medical-records";
    }
  });

  var listItems = document.querySelectorAll(".medical-record-item");
  if (!listItems.length) return;

  function closeAll() {
    document.querySelectorAll(".medical-record-item").forEach(function (item) {
      item.classList.remove("cap-item-active");
      var detail = item.querySelector(".cap-inline-detail");
      if (detail) detail.hidden = true;
    });
  }

  function setActiveStep(panel, stepId) {
    panel.querySelectorAll(".mr-step-panel").forEach(function (p) {
      p.classList.toggle("active", p.dataset.stepId === stepId);
    });
    panel.querySelectorAll(".mr-step-meta").forEach(function (m) {
      m.classList.toggle("active", m.dataset.stepId === stepId);
    });
    panel.querySelectorAll(".mr-step-btn").forEach(function (btn) {
      btn.classList.toggle("active", btn.dataset.stepId === stepId);
    });
  }

  listItems.forEach(function (item) {
    var row = item.querySelector(".cap-item-row");
    var detail = item.querySelector(".cap-inline-detail");
    var closeBtn = item.querySelector(".cap-inline-close");

    row?.addEventListener("click", function () {
      var isOpen = detail && !detail.hidden;
      if (isOpen) {
        detail.hidden = true;
        item.classList.remove("cap-item-active");
        return;
      }
      closeAll();
      item.classList.add("cap-item-active");
      if (detail) detail.hidden = false;

      var firstPanel = item.querySelector(".mr-step-panel");
      if (firstPanel) {
        setActiveStep(item, firstPanel.dataset.stepId);
      }
    });

    closeBtn?.addEventListener("click", function (e) {
      e.stopPropagation();
      if (detail) detail.hidden = true;
      item.classList.remove("cap-item-active");
    });

    item.querySelectorAll(".mr-step-btn").forEach(function (btn) {
      btn.addEventListener("click", function (e) {
        e.stopPropagation();
        setActiveStep(item, btn.dataset.stepId);
      });
    });
  });
})();

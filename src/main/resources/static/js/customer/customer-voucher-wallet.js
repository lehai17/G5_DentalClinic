(function () {
  "use strict";

  var searchEl = document.getElementById("voucher-wallet-search");
  var sortEl = document.getElementById("voucher-wallet-sort");
  var gridEl = document.querySelector(".voucher-wallet-grid");
  var emptyEl = document.getElementById("voucher-wallet-empty");

  if (!gridEl) return;

  function toTime(value) {
    var timestamp = Date.parse(value || "");
    return isNaN(timestamp) ? 0 : timestamp;
  }

  function toNumber(value) {
    var parsed = Number(value);
    return isNaN(parsed) ? 0 : parsed;
  }

  function applyWalletFilters() {
    var items = Array.prototype.slice.call(
      gridEl.querySelectorAll(".voucher-wallet-item"),
    );
    var keyword = (searchEl && searchEl.value ? searchEl.value : "")
      .trim()
      .toLowerCase();
    var sort = sortEl && sortEl.value ? sortEl.value : "expiring";

    items.forEach(function (item) {
      var haystack = (item.getAttribute("data-search") || "").toLowerCase();
      item.hidden = keyword.length > 0 && haystack.indexOf(keyword) === -1;
    });

    items.sort(function (left, right) {
      if (sort === "newest") {
        return (
          toTime(right.getAttribute("data-created")) -
          toTime(left.getAttribute("data-created"))
        );
      }
      if (sort === "discount_desc") {
        return (
          toNumber(right.getAttribute("data-discount")) -
          toNumber(left.getAttribute("data-discount"))
        );
      }
      if (sort === "code_asc") {
        return (left.getAttribute("data-code") || "").localeCompare(
          right.getAttribute("data-code") || "",
          "vi",
        );
      }
      return (
        toTime(left.getAttribute("data-end")) -
        toTime(right.getAttribute("data-end"))
      );
    });

    items.forEach(function (item) {
      gridEl.appendChild(item);
    });

    var visibleCount = items.filter(function (item) {
      return !item.hidden;
    }).length;

    if (emptyEl) {
      emptyEl.hidden = visibleCount > 0;
      if (visibleCount === 0) {
        emptyEl.innerHTML =
          '<i class="bi bi-search"></i><div>Không tìm thấy voucher phù hợp với bộ lọc hiện tại.</div>';
      }
    }
  }

  if (searchEl) {
    searchEl.addEventListener("input", applyWalletFilters);
  }
  if (sortEl) {
    sortEl.addEventListener("change", applyWalletFilters);
  }

  applyWalletFilters();
})();

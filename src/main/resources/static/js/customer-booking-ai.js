(function () {
    'use strict';

    function qs(id) {
        return document.getElementById(id);
    }

    function uncheckAllServices() {
        document.querySelectorAll('.customer-service-checkbox').forEach(function (cb) {
            cb.checked = false;
        });
    }

    function applySuggestedServices(services) {
        if (!Array.isArray(services)) return;

        uncheckAllServices();

        services.forEach(function (service) {
            var selector = '.customer-service-checkbox[value="' + service.id + '"]';
            var checkbox = document.querySelector(selector);
            if (checkbox) {
                checkbox.checked = true;
                checkbox.dispatchEvent(new Event('change', { bubbles: true }));
            }
        });
    }

    function renderResult(data) {
        var container = qs('ai-booking-result');
        if (!container) return;

        var html = '';
        html += '<div style="padding:12px;border:1px solid #dbeafe;background:#eff6ff;border-radius:10px;">';
        html += '<div style="font-weight:700;margin-bottom:8px;">Gợi ý từ AI</div>';
        html += '<div style="margin-bottom:8px;">' + escapeHtml(data.assistantMessage || '') + '</div>';

        if (Array.isArray(data.services) && data.services.length > 0) {
            html += '<div style="margin-bottom:10px;"><strong>Dịch vụ phù hợp:</strong><ul style="margin:6px 0 0 18px;">';
            data.services.forEach(function (s) {
                html += '<li>' + escapeHtml(s.name) + '</li>';
            });
            html += '</ul></div>';
        }

        if (Array.isArray(data.slotOptions) && data.slotOptions.length > 0) {
            html += '<div><strong>Khung giờ gợi ý:</strong></div>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px;">';

            data.slotOptions.forEach(function (slot) {
                html += '<button type="button" class="ai-slot-option" ' +
                    'data-date="' + escapeAttr(slot.date) + '" ' +
                    'data-start="' + escapeAttr(slot.startTime) + '" ' +
                    'data-end="' + escapeAttr(slot.endTime || '') + '" ' +
                    'style="padding:10px 12px;border:1px solid #bfdbfe;border-radius:10px;background:white;cursor:pointer;">' +
                    escapeHtml(slot.displayText || (slot.date + ' ' + slot.startTime)) +
                    '</button>';
            });

            html += '</div>';
        }

        html += '</div>';

        container.innerHTML = html;
        container.style.display = 'block';

        bindApplySuggestion(data);
    }

    function bindApplySuggestion(data) {
        document.querySelectorAll('.ai-slot-option').forEach(function (btn) {
            btn.addEventListener('click', function () {
                applySuggestedServices(data.services || []);

                var date = btn.getAttribute('data-date');
                var start = btn.getAttribute('data-start');
                var end = btn.getAttribute('data-end');

                if (window.customerBookingAI && typeof window.customerBookingAI.applySuggestion === 'function') {
                    window.customerBookingAI.applySuggestion({
                        date: date,
                        startTime: start,
                        endTime: end
                    });
                } else {
                    alert('Không thể áp dụng gợi ý AI vào form đặt lịch.');
                    return;
                }
            });
        });
    }

    function requestSuggestion() {
        var messageEl = qs('ai-booking-message');
        var loadingEl = qs('ai-booking-loading');
        var resultEl = qs('ai-booking-result');

        if (!messageEl || !loadingEl || !resultEl) return;

        var message = (messageEl.value || '').trim();
        if (!message) {
            alert('Vui lòng nhập nhu cầu khám để AI gợi ý.');
            return;
        }

        loadingEl.style.display = 'inline';
        resultEl.style.display = 'none';
        resultEl.innerHTML = '';

        fetch('/customer/ai-booking/suggest', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ message: message })
        })
            .then(function (res) {
                if (!res.ok) throw new Error('AI request failed');
                return res.json();
            })
            .then(function (data) {
                renderResult(data);
            })
            .catch(function (err) {
                console.error(err);
                alert('Không thể lấy gợi ý AI lúc này. Vui lòng thử lại.');
            })
            .finally(function () {
                loadingEl.style.display = 'none';
            });
    }

    function escapeHtml(str) {
        return String(str || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function escapeAttr(str) {
        return escapeHtml(str);
    }

    function bindEvents() {
        var btn = qs('btn-ai-suggest');
        if (btn) {
            btn.addEventListener('click', requestSuggestion);
        }
    }

    document.addEventListener('DOMContentLoaded', bindEvents);
})();
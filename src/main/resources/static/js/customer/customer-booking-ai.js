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

    function checkOnlyOneService(serviceId) {
        uncheckAllServices();

        var selector = '.customer-service-checkbox[value="' + serviceId + '"]';
        var checkbox = document.querySelector(selector);
        if (checkbox) {
            checkbox.checked = true;
            checkbox.dispatchEvent(new Event('change', { bubbles: true }));
        }
    }

    function getSelectedAIServiceId() {
        var checked = document.querySelector('input[name="ai-service-choice"]:checked');
        if (!checked) return null;
        var value = parseInt(checked.value, 10);
        return Number.isNaN(value) ? null : value;
    }

    function renderResult(data) {
        var container = qs('ai-booking-result');
        if (!container) return;

        var html = '';
        html += '<div style="padding:12px;border:1px solid #dbeafe;background:#eff6ff;border-radius:10px;">';
        html += '<div style="font-weight:700;margin-bottom:8px;">Gợi ý từ AI</div>';
        html += '<div style="margin-bottom:12px;">' + escapeHtml(data.assistantMessage || '') + '</div>';

        if (Array.isArray(data.services) && data.services.length > 0) {
            html += '<div style="margin-bottom:14px;">';
            html += '<strong>Dịch vụ phù hợp:</strong>';

            if (data.services.length === 1) {
                html += '<ul style="margin:6px 0 0 18px;">';
                html += '<li>' + escapeHtml(data.services[0].name) + '</li>';
                html += '</ul>';
            } else {
                html += '<div style="margin-top:10px;display:flex;flex-direction:column;gap:8px;">';
                data.services.forEach(function (s, index) {
                    html += '<label style="display:flex;align-items:flex-start;gap:8px;padding:10px;border:1px solid #bfdbfe;border-radius:10px;background:#fff;cursor:pointer;">';
                    html += '<input type="radio" name="ai-service-choice" value="' + escapeAttr(s.id) + '" ' + (index === 0 ? 'checked' : '') + ' style="margin-top:3px;">';
                    html += '<span>';
                    html += '<strong>' + escapeHtml(s.name) + '</strong>';
                    if (s.durationMinutes) {
                        html += '<div style="font-size:13px;color:#6b7280;margin-top:2px;">Thời lượng: ' + escapeHtml(String(s.durationMinutes)) + ' phút</div>';
                    }
                    if (s.price) {
                        html += '<div style="font-size:13px;color:#6b7280;margin-top:2px;">Giá tham khảo: ' + escapeHtml(Number(s.price).toLocaleString("vi-VN")) + ' VNĐ</div>';
                    }
                    html += '</span>';
                    html += '</label>';
                });
                html += '</div>';
                html += '<div style="margin-top:8px;font-size:13px;color:#6b7280;">Bạn hãy chọn 1 dịch vụ trước khi chọn giờ.</div>';
            }

            html += '</div>';
        }

        if (Array.isArray(data.slotOptions) && data.slotOptions.length > 0) {
            html += '<div><strong>Khung giờ gợi ý:</strong></div>';
            html += '<div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px;">';

            data.slotOptions.forEach(function (slot) {
                html += '<button type="button" class="ai-slot-option" ' +
                    'data-slot-id="' + escapeAttr(slot.slotId) + '" ' +
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
                var chosenServiceId = null;

                if (Array.isArray(data.services) && data.services.length === 1) {
                    chosenServiceId = data.services[0].id;
                } else {
                    chosenServiceId = getSelectedAIServiceId();
                }

                if (!chosenServiceId) {
                    alert('Vui lòng chọn 1 dịch vụ trước.');
                    return;
                }

                checkOnlyOneService(chosenServiceId);

                var slotId = btn.getAttribute('data-slot-id');
                var date = btn.getAttribute('data-date');
                var start = btn.getAttribute('data-start');
                var end = btn.getAttribute('data-end');

                if (window.customerBookingAI && typeof window.customerBookingAI.applySuggestion === 'function') {
                    window.customerBookingAI.applySuggestion({
                        serviceId: chosenServiceId,
                        slotId: slotId,
                        date: date,
                        startTime: start,
                        endTime: end
                    });
                } else {
                    alert('Không thể áp dụng gợi ý AI vào form đặt lịch.');
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
(function () {
    'use strict';

    function qs(id) {
        return document.getElementById(id);
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

    function formatTime(value) {
        if (!value) return '';
        return String(value).slice(0, 5);
    }

    function toDateDisplay(value) {
        if (!value) return '';
        var parts = String(value).split('-');
        if (parts.length !== 3) return value;
        return parts[2] + '/' + parts[1] + '/' + parts[0];
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

    function getSelectedAIServiceId(data) {
        var checked = document.querySelector('input[name="ai-service-choice"]:checked');
        if (checked) {
            var checkedValue = parseInt(checked.value, 10);
            return Number.isNaN(checkedValue) ? null : checkedValue;
        }

        if (data && Array.isArray(data.services) && data.services.length === 1) {
            var onlyId = parseInt(data.services[0].id, 10);
            return Number.isNaN(onlyId) ? null : onlyId;
        }

        return null;
    }

    function getSelectedServiceMeta(data) {
        var selectedId = getSelectedAIServiceId(data);
        if (!selectedId || !Array.isArray(data.services)) return null;
        return data.services.find(function (service) {
            return String(service.id) === String(selectedId);
        }) || null;
    }

    function buildBaseResult(data) {
        var html = '';
        html += '<div style="padding:12px;border:1px solid #dbeafe;background:#eff6ff;border-radius:10px;">';
        html += '<div style="font-weight:700;margin-bottom:8px;">Gợi ý từ AI</div>';
        html += '<div style="margin-bottom:12px;">' + escapeHtml(data.assistantMessage || '') + '</div>';

        if (Array.isArray(data.services) && data.services.length > 0) {
            html += '<div style="margin-bottom:14px;">';
            html += '<strong>Dịch vụ phù hợp:</strong>';

            if (data.services.length === 1) {
                var one = data.services[0];
                html += '<div style="margin-top:10px;padding:10px;border:1px solid #bfdbfe;border-radius:10px;background:#fff;">';
                html += '<strong>' + escapeHtml(one.name) + '</strong>';
                if (one.durationMinutes) {
                    html += '<div style="font-size:13px;color:#6b7280;margin-top:2px;">Thời lượng: ' + escapeHtml(String(one.durationMinutes)) + ' phút</div>';
                }
                if (one.price) {
                    html += '<div style="font-size:13px;color:#6b7280;margin-top:2px;">Giá tham khảo: ' + escapeHtml(Number(one.price).toLocaleString('vi-VN')) + ' VNĐ</div>';
                }
                html += '</div>';
            } else {
                html += '<div style="margin-top:10px;display:flex;flex-direction:column;gap:8px;" id="ai-service-choice-list">';
                data.services.forEach(function (service, index) {
                    html += '<label style="display:flex;align-items:flex-start;gap:8px;padding:10px;border:1px solid #bfdbfe;border-radius:10px;background:#fff;cursor:pointer;">';
                    html += '<input type="radio" name="ai-service-choice" value="' + escapeAttr(service.id) + '" ' + (index === 0 ? 'checked' : '') + ' style="margin-top:3px;">';
                    html += '<span>';
                    html += '<strong>' + escapeHtml(service.name) + '</strong>';
                    if (service.durationMinutes) {
                        html += '<div style="font-size:13px;color:#6b7280;margin-top:2px;">Thời lượng: ' + escapeHtml(String(service.durationMinutes)) + ' phút</div>';
                    }
                    if (service.price) {
                        html += '<div style="font-size:13px;color:#6b7280;margin-top:2px;">Giá tham khảo: ' + escapeHtml(Number(service.price).toLocaleString('vi-VN')) + ' VNĐ</div>';
                    }
                    html += '</span>';
                    html += '</label>';
                });
                html += '</div>';
                html += '<div style="margin-top:8px;font-size:13px;color:#6b7280;">Khi bạn đổi dịch vụ, AI sẽ tải lại khung giờ đúng với dịch vụ đó.</div>';
            }
            html += '</div>';
        }

        html += '<div>';
        html += '<strong>Khung giờ gợi ý:</strong>';
        html += '<div id="ai-slot-loading" style="display:none;margin-top:8px;color:#6b7280;">Đang tải khung giờ phù hợp...</div>';
        html += '<div id="ai-slot-error" style="display:none;margin-top:8px;color:#dc2626;"></div>';
        html += '<div id="ai-slot-options" style="display:flex;flex-wrap:wrap;gap:8px;margin-top:8px;"></div>';
        html += '</div>';
        html += '</div>';
        return html;
    }

    function renderResult(data) {
        var container = qs('ai-booking-result');
        if (!container) return;

        container.innerHTML = buildBaseResult(data);
        container.style.display = 'block';

        bindServiceChange(data);
        refreshSlotsForSelectedService(data);
    }

    function bindServiceChange(data) {
        document.querySelectorAll('input[name="ai-service-choice"]').forEach(function (radio) {
            radio.addEventListener('change', function () {
                refreshSlotsForSelectedService(data);
            });
        });
    }

    function refreshSlotsForSelectedService(data) {
        var serviceId = getSelectedAIServiceId(data);
        var loadingEl = qs('ai-slot-loading');
        var errorEl = qs('ai-slot-error');
        var optionsEl = qs('ai-slot-options');
        var preferredDate = data.preferredDate;
        var timePreference = data.timePreference || 'any';
        var preferredTime = data.preferredTime || '';

        if (!optionsEl) return;
        optionsEl.innerHTML = '';
        if (errorEl) {
            errorEl.style.display = 'none';
            errorEl.textContent = '';
        }

        if (!serviceId || !preferredDate) {
            optionsEl.innerHTML = '<div style="font-size:13px;color:#6b7280;">Hãy chọn dịch vụ để xem khung giờ phù hợp.</div>';
            return;
        }

        if (loadingEl) loadingEl.style.display = 'block';

        var params = new URLSearchParams({
            serviceId: String(serviceId),
            date: preferredDate
        });

        fetch('/customer/slots?' + params.toString(), { credentials: 'same-origin' })
            .then(function (res) {
                if (!res.ok) throw new Error('Không thể tải khung giờ phù hợp.');
                return res.json();
            })
            .then(function (slots) {
                var serviceMeta = getSelectedServiceMeta(data);
                var durationMinutes = serviceMeta && serviceMeta.durationMinutes ? Number(serviceMeta.durationMinutes) : 30;
                var slotOptions = buildAiSlotOptions(Array.isArray(slots) ? slots : [], preferredDate, durationMinutes, timePreference, preferredTime);
                renderSlotButtons(data, slotOptions, optionsEl);
            })
            .catch(function (err) {
                console.error(err);
                if (errorEl) {
                    errorEl.textContent = err.message || 'Không thể tải khung giờ phù hợp.';
                    errorEl.style.display = 'block';
                }
            })
            .finally(function () {
                if (loadingEl) loadingEl.style.display = 'none';
            });
    }

    function buildAiSlotOptions(slots, date, durationMinutes, timePreference, preferredTime) {
        var selectable = slots.filter(function (slot) {
            if (!slot || !slot.startTime) return false;
            if (slot.disabled === true) return false;
            if (slot.available === false && Number(slot.availableSpots || 0) <= 0) return false;
            return true;
        });

        var filtered = selectable.filter(function (slot) {
            var hour = parseInt(String(slot.startTime).slice(0, 2), 10);
            if (Number.isNaN(hour) || !timePreference || timePreference === 'any') return true;
            if (timePreference === 'morning') return hour < 12;
            if (timePreference === 'afternoon') return hour >= 12 && hour < 17;
            if (timePreference === 'evening') return hour >= 17;
            return true;
        });

        if (!filtered.length) {
            filtered = selectable;
        }

        var preferredMinutes = parseTimeToMinutes(preferredTime);

        return filtered
            .map(function (slot) {
                var startMinutes = parseTimeToMinutes(slot.startTime);
                var endTime = addMinutes(slot.startTime, durationMinutes || 30);
                var distance = preferredMinutes === null ? 0 : Math.abs(startMinutes - preferredMinutes);
                return {
                    slotId: slot.id,
                    date: date,
                    startTime: slot.startTime,
                    endTime: endTime,
                    displayText: toDateDisplay(date) + ' • ' + formatTime(slot.startTime) + ' - ' + formatTime(endTime),
                    sortDistance: distance,
                    sortTime: startMinutes
                };
            })
            .sort(function (a, b) {
                if (a.sortDistance !== b.sortDistance) {
                    return a.sortDistance - b.sortDistance;
                }
                return a.sortTime - b.sortTime;
            })
            .slice(0, 5);
    }

    function renderSlotButtons(data, slotOptions, optionsEl) {
        if (!slotOptions.length) {
            optionsEl.innerHTML = '<div style="font-size:13px;color:#6b7280;">Ngày này chưa còn khung giờ phù hợp cho dịch vụ bạn vừa chọn.</div>';
            return;
        }

        var html = '';
        slotOptions.forEach(function (slot) {
            html += '<button type="button" class="ai-slot-option" '
                + 'data-slot-id="' + escapeAttr(slot.slotId) + '" '
                + 'data-date="' + escapeAttr(slot.date) + '" '
                + 'data-start="' + escapeAttr(slot.startTime) + '" '
                + 'data-end="' + escapeAttr(slot.endTime || '') + '" '
                + 'style="padding:10px 12px;border:1px solid #bfdbfe;border-radius:10px;background:white;cursor:pointer;">'
                + escapeHtml(slot.displayText) + '</button>';
        });
        optionsEl.innerHTML = html;
        bindApplySuggestion(data);
    }

    function bindApplySuggestion(data) {
        document.querySelectorAll('.ai-slot-option').forEach(function (btn) {
            btn.addEventListener('click', function () {
                var chosenServiceId = null;

                chosenServiceId = getSelectedAIServiceId(data);

                if (!chosenServiceId) {
                    alert('Vui lòng chọn 1 dịch vụ trước.');
                    return;
                }

                checkOnlyOneService(chosenServiceId);

                if (window.customerBookingAI && typeof window.customerBookingAI.applySuggestion === 'function') {
                    window.customerBookingAI.applySuggestion({
                        serviceId: chosenServiceId,
                        slotId: btn.getAttribute('data-slot-id'),
                        date: btn.getAttribute('data-date'),
                        startTime: btn.getAttribute('data-start'),
                        endTime: btn.getAttribute('data-end')
                    });
                } else {
                    alert('Không thể áp dụng gợi ý AI vào form đặt lịch.');
                }
            });
        });
    }

    function parseTimeToMinutes(value) {
        if (!value) return null;
        var parts = String(value).split(':');
        if (parts.length < 2) return null;
        var hour = parseInt(parts[0], 10);
        var minute = parseInt(parts[1], 10);
        if (Number.isNaN(hour) || Number.isNaN(minute)) return null;
        return (hour * 60) + minute;
    }

    function addMinutes(timeValue, minutesToAdd) {
        var total = parseTimeToMinutes(timeValue);
        if (total === null) return timeValue;
        total += Number(minutesToAdd || 0);
        var hour = Math.floor(total / 60) % 24;
        var minute = total % 60;
        return String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0');
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

    function bindEvents() {
        var btn = qs('btn-ai-suggest');
        if (btn) {
            btn.addEventListener('click', requestSuggestion);
        }
    }

    document.addEventListener('DOMContentLoaded', bindEvents);
})();

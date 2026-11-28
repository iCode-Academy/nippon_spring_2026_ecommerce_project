"use strict";

(function () {
    const panel = document.querySelector('#orderStatusPanel');
    if (!panel) {
        return;
    }

    const orderId = panel.dataset.orderId;
    const currentStatusElement = document.querySelector('#currentStatus');
    const feedbackRegion = document.querySelector('#statusFeedback');
    const csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    const csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    const csrfToken = csrfTokenMeta ? csrfTokenMeta.content : '';
    const csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.content : 'X-CSRF-TOKEN';
    const actionButtons = Array.from(panel.querySelectorAll('button[data-status]'));

    let currentStatus = panel.dataset.currentStatus;

    function clearFeedback() {
        feedbackRegion.replaceChildren();
    }

    function showFeedback(kind, title, detail, options) {
        clearFeedback();

        const box = document.createElement('div');
        box.className = `flash ${kind === 'success' ? 'flash-success' : 'flash-error'}`;
        box.setAttribute('role', kind === 'success' ? 'status' : 'alert');

        const heading = document.createElement('strong');
        heading.textContent = title;
        box.appendChild(heading);

        if (detail) {
            const text = document.createElement('p');
            text.className = 'status-feedback-detail';
            text.textContent = detail;
            box.appendChild(text);
        }

        if (options && options.reload) {
            const reload = document.createElement('button');
            reload.type = 'button';
            reload.className = 'btn btn-secondary btn-sm';
            reload.textContent = 'Reload order';
            reload.addEventListener('click', () => window.location.reload());
            box.appendChild(reload);
        }

        feedbackRegion.appendChild(box);
    }

    function describeProblem(payload, statusCode) {
        if (!payload || typeof payload !== 'object') {
            return {
                title: 'Status update failed',
                detail: `The server returned HTTP ${statusCode}.`,
                code: null
            };
        }

        let detail = payload.detail || `The server returned HTTP ${statusCode}.`;
        if (payload.errors && typeof payload.errors === 'object') {
            const fieldMessages = Object.values(payload.errors).filter(Boolean);
            if (fieldMessages.length > 0) {
                detail = `${detail} ${fieldMessages.join(' ')}`;
            }
        }

        return {
            title: payload.title || 'Status update failed',
            detail: detail,
            code: payload.code || null
        };
    }

    async function submitStatus(targetStatus, button) {
        if (!targetStatus) {
            return;
        }

        let succeeded = false;
        actionButtons.forEach((action) => {
            action.disabled = true;
        });
        if (button) {
            button.setAttribute('aria-busy', 'true');
        }

        try {
            const response = await fetch(
                `/api/admin/orders/${encodeURIComponent(orderId)}/status`,
                {
                    method: 'PATCH',
                    headers: {
                        'Content-Type': 'application/json',
                        'Accept': 'application/json',
                        [csrfHeader]: csrfToken
                    },
                    body: JSON.stringify({
                        status: targetStatus,
                        expectedStatus: currentStatus
                    })
                }
            );

            let payload = null;
            try {
                payload = await response.json();
            } catch (ignored) {
                payload = null;
            }

            if (!response.ok) {
                const problem = describeProblem(payload, response.status);
                const suffix = problem.code ? ` (${problem.code})` : '';
                showFeedback('error', `${problem.title}${suffix}`, problem.detail, {
                    reload: problem.code === 'STALE_UPDATE'
                });
                return;
            }

            succeeded = true;
            currentStatus = payload && payload.status ? payload.status : targetStatus;
            panel.dataset.currentStatus = currentStatus;

            if (currentStatusElement) {
                currentStatusElement.textContent = currentStatus;
                currentStatusElement.className = 'badge';
                currentStatusElement.classList.add(
                    currentStatus === 'CANCELLED' ? 'badge-warn' : 'badge-ok'
                );
            }

            showFeedback(
                'success',
                'Status updated',
                `Order #${orderId} is now ${currentStatus}.`,
                { reload: true }
            );
        } catch (error) {
            showFeedback(
                'error',
                'Network error',
                'The order status could not be updated. Check your connection and try again.'
            );
        } finally {
            actionButtons.forEach((action) => {
                action.disabled = succeeded;
            });
            if (button) {
                button.removeAttribute('aria-busy');
            }
        }
    }

    actionButtons.forEach((button) => {
        button.addEventListener('click', () => {
            submitStatus(button.dataset.status, button);
        });
    });
})();

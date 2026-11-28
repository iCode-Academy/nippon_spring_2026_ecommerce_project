"use strict";

(() => {
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    const feedback = document.querySelector("#review-feedback");

    function showFeedback(message, error = false) {
        if (!feedback) return;
        const notice = document.createElement("div");
        notice.className = error ? "flash flash-error" : "flash flash-success";
        notice.setAttribute("role", error ? "alert" : "status");
        notice.textContent = message;
        feedback.replaceChildren(notice);
    }

    function renderProblem(problem, fallback) {
        const parts = [];
        if (problem && problem.title) parts.push(problem.title);
        if (problem && problem.detail) parts.push(problem.detail);
        if (problem && problem.errors) {
            Object.entries(problem.errors).forEach(([field, message]) => {
                parts.push(`${field}: ${message}`);
            });
        }
        showFeedback(parts.length > 0 ? parts.join(" ") : fallback, true);
    }

    async function readProblem(response) {
        try {
            return await response.json();
        } catch {
            return null;
        }
    }

    function setFormBusy(form, busy) {
        form.setAttribute("aria-busy", String(busy));
        form.querySelectorAll("button, select, textarea, input").forEach(control => {
            control.disabled = busy;
        });
    }

    document.addEventListener("submit", async (event) => {
        const form = event.target.closest(".js-review-form");
        if (!form) return;

        event.preventDefault();

        if (!csrfToken || !csrfHeader) {
            showFeedback("Your session token is missing. Please refresh the page and try again.", true);
            return;
        }

        if (!form.reportValidity()) return;

        const action = form.dataset.action;
        const method = form.dataset.method || "POST";
        const rating = Number(form.elements.rating.value);
        const comment = form.elements.comment.value.trim();

        setFormBusy(form, true);

        try {
            const response = await fetch(action, {
                method,
                credentials: "same-origin",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                    [csrfHeader.content]: csrfToken.content
                },
                body: JSON.stringify({ rating, comment })
            });

            if (response.redirected) {
                showFeedback("Please sign in and try again.", true);
                return;
            }

            if (response.ok) {
                showFeedback(method === "POST"
                    ? "Review submitted and awaiting moderation."
                    : "Review updated and awaiting moderation.");
                window.location.reload();
                return;
            }

            const problem = await readProblem(response);

            if (response.status === 400) {
                renderProblem(problem, "Check the rating and review text and try again.");
            } else if (response.status === 403) {
                renderProblem(problem, "You do not have permission to change this review.");
            } else if (response.status === 404) {
                renderProblem(problem, "This review no longer exists.");
            } else if (response.status === 409) {
                renderProblem(problem, "This review could not be saved because of a conflict.");
            } else {
                renderProblem(problem, "Unable to save your review right now. Please try again.");
            }
        } catch {
            showFeedback("Unable to connect. Please check your connection and try again.", true);
        } finally {
            setFormBusy(form, false);
        }
    });

    document.addEventListener("click", async (event) => {
        const button = event.target.closest(".js-delete-review");
        if (!button) return;

        if (!csrfToken || !csrfHeader) {
            showFeedback("Your session token is missing. Please refresh the page and try again.", true);
            return;
        }

        if (!window.confirm("Delete your review? This cannot be undone.")) return;

        const originalText = button.textContent;
        button.disabled = true;
        button.textContent = "Deleting…";

        try {
            const response = await fetch(`/api/reviews/${button.dataset.reviewId}`, {
                method: "DELETE",
                credentials: "same-origin",
                headers: {
                    "Accept": "application/json",
                    [csrfHeader.content]: csrfToken.content
                }
            });

            if (response.redirected) {
                showFeedback("Please sign in and try again.", true);
                return;
            }

            if (response.ok || response.status === 204) {
                showFeedback("Review deleted.");
                window.location.reload();
                return;
            }

            const problem = await readProblem(response);

            if (response.status === 403) {
                renderProblem(problem, "You do not have permission to delete this review.");
            } else if (response.status === 404) {
                renderProblem(problem, "This review no longer exists.");
            } else {
                renderProblem(problem, "Unable to delete your review right now. Please try again.");
            }
        } catch {
            showFeedback("Unable to connect. Please check your connection and try again.", true);
        } finally {
            if (button.isConnected) {
                button.disabled = false;
                button.textContent = originalText;
            }
        }
    });
})();

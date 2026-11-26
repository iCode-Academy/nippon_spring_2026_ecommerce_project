"use strict";

const cartFeedback = document.querySelector("#cart-feedback");

function showCartFeedback(message, error = false) {
    const notice = document.createElement("div");
    notice.className = error ? "flash flash-error" : "flash flash-success";
    notice.setAttribute("role", error ? "alert" : "status");
    notice.textContent = message;
    cartFeedback.replaceChildren(notice);
}

document.addEventListener("click", async (event) => {
    const button = event.target.closest(".js-add-to-cart");
    if (!button || button.disabled) return;

    const quantityInput = document.querySelector("#cart-quantity");
    if (quantityInput && !quantityInput.reportValidity()) return;

    const quantity = quantityInput ? Number(quantityInput.value) : 1;
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (!csrfToken || !csrfHeader) {
        showCartFeedback("Unable to add this item right now. Please refresh and try again.", true);
        return;
    }

    button.disabled = true;
    const originalText = button.textContent;
    button.textContent = "Adding…";
    try {
        const response = await fetch("/api/cart/items", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                [csrfHeader.content]: csrfToken.content
            },
            body: JSON.stringify({ productId: Number(button.dataset.productId), quantity })
        });

        if (response.redirected) {
            showCartFeedback("Please sign in and try again.", true);
        } else if (response.ok) {
            const cart = await response.json();
            const item = cart.items.find(entry => entry.productId === Number(button.dataset.productId));
            showCartFeedback(`${button.dataset.productName} added to cart. ${item.quantity} in cart.`);
        } else if (response.status === 409) {
            showCartFeedback("This quantity is no longer available. Please check the stock and try again.", true);
        } else if (response.status === 403) {
            showCartFeedback("Your session has expired or your account cannot add items. Please sign in again.", true);
        } else if (response.status === 400) {
            showCartFeedback("Enter a valid quantity and try again.", true);
        } else if (response.status === 404) {
            showCartFeedback("This product is no longer available.", true);
        } else {
            showCartFeedback("Unable to add this item right now. Please try again.", true);
        }
    } catch {
        showCartFeedback("Unable to connect. Please check your connection and try again.", true);
    } finally {
        button.disabled = false;
        button.textContent = originalText;
    }
});

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

// Helper: update a cart item quantity inline via PATCH
async function updateCartItemQuantity(itemId, newQuantity) {
    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    if (!csrfToken || !csrfHeader) {
        showCartFeedback("CSRF token missing. Please refresh and try again.", true);
        return false;
    }

    try {
        const response = await fetch(`/api/cart/items/${itemId}`, {
            method: "PATCH",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                [csrfHeader.content]: csrfToken.content
            },
            body: JSON.stringify({ quantity: newQuantity })
        });

        if (response.ok) {
            const cart = await response.json();
            // Find the updated item and return it
            const item = cart.items.find(entry => entry.id === itemId);
            if (item) {
                showCartFeedback(`Quantity updated to ${item.quantity}.`, false);
            } else {
                showCartFeedback("Item updated but not found in cart display.", true);
            }
            return true;
        } else if (response.status === 409) {
            showCartFeedback("Requested quantity exceeds available stock.", true);
        } else if (response.status === 403) {
            showCartFeedback("You do not own this cart item or session expired.", true);
        } else if (response.status === 400) {
            showCartFeedback("Invalid quantity. Must be at least 1.", true);
        } else if (response.status === 404) {
            showCartFeedback("Cart item not found.", true);
        } else {
            showCartFeedback("Unable to update quantity. Please try again.", true);
        }
        return false;
    } catch {
        showCartFeedback("Unable to connect. Please check your connection and try again.", true);
        return false;
    }
}

// Handle quantity changes in the cart UI
document.addEventListener("change", async (event) => {
    const input = event.target.closest(".quantity-input");
    if (!input) return;

    const itemId = Number(input.dataset.itemId);
    const newQuantity = Number(input.value);

    if (newQuantity < 1) {
        input.value = input.dataset.oldQuantity || 1;
        showCartFeedback("Quantity must be at least 1.", true);
        return;
    }

    // Store old quantity for revert
    input.dataset.oldQuantity = input.value;

    const success = await updateCartItemQuantity(itemId, newQuantity);
    if (!success) {
        // Revert on failure
        input.value = input.dataset.oldQuantity || 1;
    }
});

// Handle +/- buttons in the cart
document.addEventListener("click", async (event) => {
    const button = event.target.closest(".quantity-btn");
    if (!button) return;

    const itemId = Number(button.dataset.itemId);
    const input = document.querySelector(`.quantity-input[data-item-id="${itemId}"]`);
    if (!input) return;

    let newQuantity = Number(input.value);

    if (button.classList.contains("plus")) {
        newQuantity += 1;
    } else if (button.classList.contains("minus")) {
        newQuantity -= 1;
        if (newQuantity < 1) newQuantity = 1;
    }

    input.value = newQuantity;
    input.dataset.oldQuantity = String(newQuantity);

    await updateCartItemQuantity(itemId, newQuantity);
});

"use strict";

const csrfToken = document.querySelector('meta[name="_csrf"]');
const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
const cartFeedback = document.querySelector("#cart-feedback");
let cartOperationInProgress = false;

function showCartFeedback(message, error = false) {
    const notice = document.createElement("div");
    notice.className = error ? "flash flash-error" : "flash flash-success";
    notice.setAttribute("role", error ? "alert" : "status");
    notice.textContent = message;
    cartFeedback.replaceChildren(notice);
}

function setCartLoading(loading) {
    const summary = document.querySelector("#cart-summary");
    if (!summary) return;
    summary.setAttribute("aria-busy", String(loading));
    document.querySelector("#cart-loading").hidden = !loading;
}

async function refreshCart() {
    try {
        const response = await fetch("/api/cart", {
            credentials: "same-origin",
            headers: { "Accept": "application/json" }
        });
        if (!response.ok || response.redirected) return;
        renderCart(await response.json());
    } catch {
        // Keep the previously displayed cart when the refresh fails.
    }
}

document.addEventListener("click", async (event) => {
    const button = event.target.closest(".js-add-to-cart");
    if (!button || button.disabled) return;

    const quantityInput = document.querySelector("#cart-quantity");
    if (quantityInput && !quantityInput.reportValidity()) return;

    const quantity = quantityInput ? Number(quantityInput.value) : 1;

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

        if (response.redirected) {
            showCartFeedback("Please sign in and try again.", true);
            return false;
        }

        if (response.ok) {
            const cart = await response.json();

            renderCart(cart);

            const item = cart.items.find(entry => entry.id === itemId);

            if (item) {
                showCartFeedback(`Quantity updated to ${item.quantity}.`);
            } else {
                showCartFeedback("Cart item is no longer available.", true);
            }

            return true;
        } else if (response.status === 409) {
            await refreshCart();
            showCartFeedback("Stock or product availability has changed. Review this item or remove it.", true);
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

// Handle +/- buttons in the cart
document.addEventListener("click", async (event) => {
    const button = event.target.closest(".quantity-btn");

    if (!button || button.disabled || cartOperationInProgress) return;

    const itemId = Number(button.dataset.itemId);
    const input = document.querySelector(
        `.quantity-input[data-item-id="${itemId}"]`
    );

    if (!input) return;

    const oldQuantity = Number(input.value);
    let newQuantity = oldQuantity;

    if (button.classList.contains("plus")) {
        newQuantity += 1;
    } else if (button.classList.contains("minus")) {
        newQuantity -= 1;
    }

    if (newQuantity < 1) return;

    cartOperationInProgress = true;
    setCartLoading(true);
    button.disabled = true;
    const originalText = button.textContent;
    button.textContent = "Updating…";

    try {
        await updateCartItemQuantity(itemId, newQuantity);
    } finally {
        cartOperationInProgress = false;
        setCartLoading(false);
        button.textContent = originalText;
        const row = button.closest("tr");
        if (row.isConnected) {
            button.disabled = row.dataset.available === "false"
                || (button.classList.contains("minus") && Number(input.value) <= 1);
            if (!button.disabled) button.focus();
        }
    }
}); 
function renderCart(cart) {
    const tableBody = document.querySelector("#cart-table-body");
    const cartItems = document.querySelector("#cart-items");
    const cartTotals = document.querySelector("#cart-totals");
    const emptyState = document.querySelector("#cart-empty-state");
    const subtotal = document.querySelector("#cart-subtotal");

    const itemsById = new Map(
        cart.items.map(item => [String(item.id), item])
    );

    tableBody.querySelectorAll("tr").forEach(row => {
        const button = row.querySelector(".remove-item");
        const item = itemsById.get(button.dataset.itemId);

        if (!item) {
            row.remove();
            return;
        }

        row.querySelector(".quantity-input").value = item.quantity;
        row.dataset.available = String(item.available);
        row.querySelector(".cart-warning").hidden = item.available;

        row.querySelector(".product-price").textContent =
            Number(item.unitPrice).toFixed(2);

        row.querySelector(".product-subtotal").textContent =
            Number(item.lineTotal).toFixed(2);

        row.querySelector(".minus").disabled = item.quantity <= 1 || !item.available;
        row.querySelector(".plus").disabled = !item.available;
    });

    subtotal.textContent = Number(cart.subtotal).toFixed(2);

    const isEmpty = cart.items.length === 0;

    cartItems.hidden = isEmpty;
    cartTotals.hidden = isEmpty;
    emptyState.hidden = !isEmpty;
}

async function deleteCartItem(itemId) {

    if (!csrfToken || !csrfHeader) {
        showCartFeedback("CSRF token missing. Please refresh and try again.",
            true
        );
        return false;
    }

    try {
        const response = await fetch(`/api/cart/items/${itemId}`, {
            method: "DELETE",
            credentials: "same-origin",
            headers: {
                "Accept": "application/json",
                [csrfHeader.content]: csrfToken.content
            }
        });

        if (response.redirected) {
            showCartFeedback("Please sign in and try again.", true);
            return false;
        }

        if (response.ok) {
            const cart = await response.json();

            renderCart(cart);
            showCartFeedback("Item removed from cart.");

            return true;
        }

        if (response.status === 404) {
            showCartFeedback("Cart item not found or already removed. Please refresh the page.", true);
        }
        else if (response.status === 403) {
            showCartFeedback("Your session has expired or you do not have permission.", true);
        }
        else {
            showCartFeedback("Unable to remove item. Please try again.", true);
        }
        return false;
    } catch {
        showCartFeedback("Unable to connect. Please check your connection and try again.", true);
        return false;
    }

}

document.addEventListener("click", async (event) => {
    const button = event.target.closest(".remove-item");

    if (!button || button.disabled || cartOperationInProgress) return;

    const itemId = button.dataset.itemId;

    const removeButtons = Array.from(
        document.querySelectorAll(".remove-item")
    );

    const currentIndex = removeButtons.indexOf(button);
    const originalText = button.textContent;

    cartOperationInProgress = true;
    setCartLoading(true);
    button.disabled = true;
    button.textContent = "Removing…";

    try {
        const success = await deleteCartItem(itemId);

        if (success) {
            const remainingButtons =
                document.querySelectorAll(".remove-item");

            if (remainingButtons.length === 0) {
                document.querySelector("#continue-shopping").focus();
            } else {
                const nextIndex = Math.min(
                    currentIndex,
                    remainingButtons.length - 1
                );

                remainingButtons[nextIndex].focus();
            }
        }
    } finally {
        cartOperationInProgress = false;
        setCartLoading(false);

        if (button.isConnected) {
            button.disabled = false;
            button.textContent = originalText;
            button.focus();
        }
    }
});


document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("product-form");
    const productId = document.getElementById("product-id");
    const nameInput = document.getElementById("product-name");
    const descriptionInput = document.getElementById("product-description");
    const priceInput = document.getElementById("product-price");
    const stockInput = document.getElementById("product-stock");
    const categoryInput = document.getElementById("product-category");
    const activeInput = document.getElementById("product-active");

    const tableBody = document.getElementById("products-table");
    const emptyMessage = document.getElementById("products-empty");
    const errorMessage = document.getElementById("form-error");
    const formTitle = document.getElementById("form-title");
    const cancelButton = document.getElementById("cancel-edit");
    const saveButton = form.querySelector('button[type="submit"]');

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;

    let products = [];

    const imageSection = document.getElementById("image-management");
    const imageProductName = document.getElementById("image-product-name");
    const imagesList = document.getElementById("images-list");
    const imagesEmpty = document.getElementById("images-empty");
    const imageError = document.getElementById("image-error");
    const closeImageButton = document.getElementById("close-image-management");
    const imageForm = document.getElementById("image-form");
    const imageUrlInput = document.getElementById("image-url");
    const addImageButton = imageForm.querySelector('button[type="submit"]');

    let selectedProductId = null;


    function showError(message) {
        errorMessage.textContent = message;
        errorMessage.hidden = false;
    }

    function clearError() {
        errorMessage.textContent = "";
        errorMessage.hidden = true;
    }

    async function apiRequest(url, options = {}) {
        const headers = {
            "Content-Type": "application/json",
            ...options.headers
        };

        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        const response = await fetch(url, {
            ...options,
            headers,
            credentials: "same-origin"
        });

        if (!response.ok) {
            let message = `Request failed (${response.status})`;

            try {
                const error = await response.json();

                if (error.errors) {
                    message = Object.entries(error.errors)
                        .map(([field, detail]) => `${field}: ${detail}`)
                        .join(", ");
                } else {
                    message = error.detail || error.message || message;
                }
            } catch {
                // Keep the default error message.
            }

            throw new Error(message);
        }

        return response.status === 204 ? null : response.json();
    }

    async function loadCategories() {
        const categories = await apiRequest("/api/admin/categories");

        categoryInput.replaceChildren();

        const placeholder = document.createElement("option");
        placeholder.value = "";
        placeholder.textContent = "Select category";
        categoryInput.appendChild(placeholder);

        categories.forEach(category => {
            const option = document.createElement("option");
            option.value = category.id;
            option.textContent = category.name;
            categoryInput.appendChild(option);
        });
    }

    // Image loading
    async function loadImages() {
        if (selectedProductId === null) {
            return;
        }

        const productId = selectedProductId;

        const images = await apiRequest(
            `/api/admin/products/${productId}/images`
        );

        // Ignore an outdated response if another product was selected.
        if (selectedProductId !== productId) {
            return;
        }

        imagesList.replaceChildren();
        imagesEmpty.hidden = images.length > 0;

        images.forEach((image, index) => {
            const item = document.createElement("div");
            item.className = "card";


            const preview = document.createElement("img");

            preview.src = image.imageUrl;
            preview.alt = `Product image ${index + 1}`;
            preview.className = "product-image-preview";
            preview.loading = "lazy";

            preview.addEventListener("error", () => {
                preview.hidden = true;

                const warning = document.createElement("p");
                warning.textContent = "Image preview unavailable.";
                item.prepend(warning);
            });

            item.appendChild(preview);


            const imageUrl = document.createElement("p");
            imageUrl.textContent = image.imageUrl;

            const position = document.createElement("p");
            position.textContent = image.position === 0
                ? "Position: 0 (Primary image)"
                : `Position: ${image.position}`;


            item.appendChild(imageUrl);
            item.appendChild(position);

            // Create Delete button
            const deleteButton = document.createElement("button");

            deleteButton.type = "button";
            deleteButton.className = "btn";
            deleteButton.textContent = "Delete";

            // Delete image when clicked
            deleteButton.addEventListener("click", async () => {

                const confirmed = confirm(
                    "Are you sure you want to delete this image?"
                );

                if (!confirmed) {
                    return;
                }

                const productId = selectedProductId;

                if (productId === null) {
                    return;
                }

                deleteButton.disabled = true;
                imageError.hidden = true;

                try {
                    await apiRequest(
                        `/api/admin/products/${productId}/images/${image.id}`,
                        {
                            method: "DELETE"
                        }
                    );

                    // Refresh the image list
                    if (selectedProductId === productId) {
                        await loadImages();
                    }

                } catch (error) {
                    if (selectedProductId === productId) {
                        imageError.textContent = error.message;
                        imageError.hidden = false;
                    }
                } finally {
                    deleteButton.disabled = false;
                }
            });

            item.appendChild(deleteButton);

            const moveUpButton = document.createElement("button");
            moveUpButton.type = "button";
            moveUpButton.className = "btn";
            moveUpButton.textContent = "Move Up";
            moveUpButton.disabled = index === 0;

            const moveDownButton = document.createElement("button");
            moveDownButton.type = "button";
            moveDownButton.className = "btn";
            moveDownButton.textContent = "Move Down";
            moveDownButton.disabled = index === images.length - 1;

            async function moveImage(direction) {
                const productId = selectedProductId;

                if (productId === null) {
                    return;
                }

                const newIndex = index + direction;
                const imageIds = images.map(img => img.id);

                // Swap the image IDs.
                [imageIds[index], imageIds[newIndex]] =
                    [imageIds[newIndex], imageIds[index]];

                moveUpButton.disabled = true;
                moveDownButton.disabled = true;
                imageError.hidden = true;

                try {
                    await apiRequest(
                        `/api/admin/products/${productId}/images/order`,
                        {
                            method: "PUT",
                            body: JSON.stringify({ imageIds })
                        }
                    );

                    if (selectedProductId === productId) {
                        await loadImages();
                    }
                } catch (error) {
                    if (selectedProductId === productId) {
                        imageError.textContent = error.message;
                        imageError.hidden = false;
                    }
                } finally {
                    moveUpButton.disabled = false;
                    moveDownButton.disabled = false;
                }
            }

            moveUpButton.addEventListener("click", () => moveImage(-1));
            moveDownButton.addEventListener("click", () => moveImage(1));

            item.appendChild(moveUpButton);
            item.appendChild(moveDownButton);

            imagesList.appendChild(item);

        });
    }


    async function openImageManagement(product) {
        selectedProductId = product.id;
        imageProductName.textContent = product.name;

        imageSection.hidden = false;
        imageError.hidden = true;

        imagesList.replaceChildren();
        imagesEmpty.hidden = true;

        imageSection.scrollIntoView({ behavior: "smooth" });

        try {
            await loadImages();
        } catch (error) {
            if (selectedProductId === product.id) {
                imageError.textContent = error.message;
                imageError.hidden = false;
            }
        }
    }


    async function loadProducts() {
        products = await apiRequest("/api/admin/products");
        tableBody.replaceChildren();

        emptyMessage.hidden = products.length > 0;

        products.forEach(product => {
            const row = document.createElement("tr");

            const values = [
                product.name,
                product.categoryName,
                product.price,
                product.stockQuantity,
                product.active ? "Active" : "Inactive"
            ];

            values.forEach(value => {
                const cell = document.createElement("td");
                cell.textContent = value;
                row.appendChild(cell);
            });

            const actionCell = document.createElement("td");
            const editButton = document.createElement("button");

            editButton.type = "button";
            editButton.className = "btn";
            editButton.textContent = "Edit";

            editButton.addEventListener("click", () => {
                editProduct(product);
            });

            actionCell.appendChild(editButton);

            const imagesButton = document.createElement("button");

            imagesButton.type = "button";
            imagesButton.className = "btn";
            imagesButton.textContent = "Manage Images";

            imagesButton.addEventListener("click", () => {
                openImageManagement(product);
            });

            actionCell.appendChild(imagesButton);

            row.appendChild(actionCell);
            tableBody.appendChild(row);
        });
    }

    function editProduct(product) {
        productId.value = product.id;
        nameInput.value = product.name;
        descriptionInput.value = product.description || "";
        priceInput.value = product.price;
        stockInput.value = product.stockQuantity;
        categoryInput.value = product.categoryId;
        activeInput.checked = product.active;

        formTitle.textContent = "Edit Product";
        cancelButton.hidden = false;

        clearError();
        form.scrollIntoView({ behavior: "smooth" });
        nameInput.focus();
    }

    function resetForm() {
        form.reset();
        productId.value = "";
        activeInput.checked = true;

        formTitle.textContent = "Add Product";
        cancelButton.hidden = true;

        clearError();
    }

    cancelButton.addEventListener("click", resetForm);

    closeImageButton.addEventListener("click", () => {
        selectedProductId = null;
        imageSection.hidden = true;
        imagesList.replaceChildren();
        imageError.hidden = true;
    });


    imageForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        if (selectedProductId === null) {
            return;
        }

        if (!imageForm.reportValidity()) {
            return;
        }

        const productId = selectedProductId;
        const imageUrl = imageUrlInput.value.trim();

        imageError.hidden = true;
        addImageButton.disabled = true;

        try {
            await apiRequest(
                `/api/admin/products/${productId}/images`,
                {
                    method: "POST",
                    body: JSON.stringify({
                        imageUrl: imageUrl
                    })
                }
            );

            // Clear the form after successful creation.
            imageForm.reset();

            // Refresh images for the selected product.
            if (selectedProductId === productId) {
                await loadImages();
            }

        } catch (error) {
            if (selectedProductId === productId) {
                imageError.textContent = error.message;
                imageError.hidden = false;
            }
        } finally {
            addImageButton.disabled = false;
        }
    });

    form.addEventListener("submit", async event => {
        event.preventDefault();
        clearError();

        if (!form.reportValidity()) {
            return;
        }

        const data = {
            name: nameInput.value.trim(),
            description: descriptionInput.value.trim(),
            price: Number(priceInput.value),
            stockQuantity: Number(stockInput.value),
            categoryId: Number(categoryInput.value),
            active: activeInput.checked
        };

        const editing = Boolean(productId.value);
        const url = editing
            ? `/api/admin/products/${productId.value}`
            : "/api/admin/products";

        saveButton.disabled = true;

        try {
            await apiRequest(url, {
                method: editing ? "PUT" : "POST",
                body: JSON.stringify(data)
            });

            await loadProducts();
            resetForm();
        } catch (error) {
            showError(error.message);
        } finally {
            saveButton.disabled = false;
        }
    });

    async function initialize() {
        try {
            await loadCategories();
            await loadProducts();
        } catch (error) {
            showError(error.message);
        }
    }

    initialize();
});

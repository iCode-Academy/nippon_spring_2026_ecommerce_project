"use strict";

const API_URL = '/api/admin/categories';

const formSection = document.querySelector('#formSection');
const formTitle = document.querySelector('#formTitle');
const form = document.querySelector('#categoryForm');
const editIdInput = document.querySelector('#editId');
const nameInput = document.querySelector('#fName');
const tableBody = document.querySelector('#categoriesTableBody');
const emptyTable = document.querySelector('#emptyTable');

const csrfToken = document.querySelector('meta[name="_csrf"]').content;
const csrfHeader = document.querySelector('meta[name="_csrf_header"]').content;

async function loadCategories() {
    try {
        const response = await fetch(API_URL);
        if (!response.ok) {
            throw new Error("Categories could not be loaded");
        }
        const data = await response.json();

        renderCategories(data);

    } catch (error) {
        console.error(error);
        showMessage("Categories could not be loaded", true);
    }
}

function renderCategories(categories) {
    tableBody.innerHTML = "";

    if (categories.length === 0) {
        emptyTable.classList.remove('hidden');
        return;
    }
    emptyTable.classList.add('hidden');

    for (let category of categories) {
        const row = document.createElement('tr');

        const idCell = document.createElement('td');
        idCell.textContent = category.id;

        const nameCell = document.createElement('td');
        nameCell.textContent = category.name;

        const actionCell = document.createElement('td');
        actionCell.style.textAlign = 'right';

        const editButton = document.createElement('button');
        editButton.type = 'button';
        editButton.textContent = 'Edit';
        editButton.className = 'btn btn-secondary btn-sm';
        editButton.style.marginRight = '8px';
        editButton.addEventListener('click', () => {
            startEdit(category);
        });

        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.textContent = 'Delete';
        deleteButton.className = 'btn btn-danger btn-sm';
        deleteButton.addEventListener('click', () => {
            deleteCategory(category.id);
        });

        actionCell.appendChild(editButton);
        actionCell.appendChild(deleteButton);

        row.appendChild(idCell);
        row.appendChild(nameCell);
        row.appendChild(actionCell);

        tableBody.appendChild(row);
    }
}

async function deleteCategory(id) {
    const confirmed = confirm("Are you sure you want to delete this category?");
    if (!confirmed) {
        return;
    }

    try {
        const response = await fetch(`${API_URL}/${id}`, {
            method: "DELETE",
            headers: {
                [csrfHeader]: csrfToken
            }
        });

        if (!response.ok) {
            throw new Error("Delete failed");
        }

        showMessage("Category deleted successfully");
        hideForm();
        await loadCategories();

    } catch (error) {
        console.error(error);
        showMessage("Category could not be deleted", true);
    }
}

async function handleSubmit(e) {
    e.preventDefault();

    const id = editIdInput.value;
    const category = {
        name: nameInput.value.trim()
    };

    if (category.name === "") {
        showMessage("Category name is required", true);
        return;
    }

    const isEditing = id !== "";
    const url = isEditing ? `${API_URL}/${id}` : API_URL;
    const method = isEditing ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method: method,
            headers: {
                "Content-Type": "application/json",
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(category)
        });

        if (!response.ok) {
            throw new Error("Request failed");
        }

        showMessage(isEditing ? "Category updated successfully" : "Category created successfully");

        hideForm();
        await loadCategories();

    } catch (error) {
        console.error(error);
        showMessage("Category name already exists", true);
    }
}

function showForm() {
    form.reset();
    editIdInput.value = "";
    formTitle.textContent = "New Category";
    formSection.classList.remove('hidden');
    nameInput.focus();
}

function hideForm() {
    form.reset();
    editIdInput.value = "";
    formSection.classList.add('hidden');
}

function startEdit(category) {
    editIdInput.value = category.id;
    nameInput.value = category.name;
    formTitle.textContent = "Edit Category";
    formSection.classList.remove('hidden');
    nameInput.focus();
}

function showMessage(text, isError = false) {
    let message = document.querySelector('#message');

    if (!message) {
        message = document.createElement('div');
        message.id = 'message';
        message.style.position = 'fixed';
        message.style.top = '20px';
        message.style.right = '20px';
        message.style.padding = '12px 18px';
        message.style.borderRadius = '8px';
        message.style.color = '#fff';
        message.style.zIndex = '1000';
        document.body.appendChild(message);
    }

    message.style.background = isError ? '#dc2626' : '#16a34a';
    message.textContent = text;
    message.hidden = false;

    setTimeout(() => {
        message.hidden = true;
    }, 3000);
}

form.addEventListener("submit", handleSubmit);

document.querySelector('#newCategoryBtn').addEventListener('click', showForm);

loadCategories();

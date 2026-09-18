const deleteForm = document.querySelector("[data-contact-delete-form]");

if (deleteForm) {
    deleteForm.addEventListener("submit", (event) => {
        const contactName = document.querySelector("#contact-name")?.textContent?.trim() || "this contact";
        if (!window.confirm(`Delete “${contactName}”? This cannot be undone.`)) event.preventDefault();
    });
}

document.querySelectorAll("[data-follow-up-cancel]").forEach((form) => {
    form.addEventListener("submit", (event) => {
        const message = form.dataset.followUpCancel || "Cancel this follow-up?";
        if (!window.confirm(message)) event.preventDefault();
    });
});

document.querySelectorAll("[data-follow-up-create-cancel]").forEach((button) => {
    button.addEventListener("click", () => {
        const details = button.closest("details");
        if (details) details.open = false;
    });
});

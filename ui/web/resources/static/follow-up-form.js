document.querySelectorAll("[data-follow-up-cancel]").forEach((form) => {
    form.addEventListener("submit", (event) => {
        const message = form.dataset.followUpCancel || "Cancel this follow-up?";
        if (!window.confirm(message)) event.preventDefault();
    });
});

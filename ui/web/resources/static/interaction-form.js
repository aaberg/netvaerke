const interactionTimeInputs = document.querySelectorAll("[data-interaction-time]");

setLocalInteractionTimes(interactionTimeInputs);

document.querySelectorAll("[data-interaction-local-time]").forEach((time) => {
    const date = new Date(time.dateTime);
    if (!Number.isNaN(date.valueOf())) time.textContent = localDateTimeLabel(date);
});

document.querySelectorAll("[data-interaction-form]").forEach((form) => {
    form.addEventListener("submit", (event) => {
        if (event.submitter?.matches("[data-interaction-remove]") && !window.confirm("Remove this interaction? This cannot be undone.")) {
            event.preventDefault();
            return;
        }

        const input = form.querySelector("[data-interaction-time]");
        const date = new Date(input.value);
        if (!Number.isNaN(date.valueOf())) {
            input.type = "hidden";
            input.value = date.toISOString();
        }
    });
});

document.querySelectorAll("[data-interaction-cancel]").forEach((button) => {
    button.addEventListener("click", () => {
        const form = button.closest("form");
        form.reset();
        setLocalInteractionTimes(form.querySelectorAll("[data-interaction-time]"));
        button.closest("details").open = false;
    });
});

function setLocalInteractionTimes(inputs) {
    inputs.forEach((input) => {
        const utcValue = input.dataset.utc;
        if (utcValue) {
            const date = new Date(utcValue);
            if (!Number.isNaN(date.valueOf())) input.value = localDateTimeValue(date);
        }
    });
}

function localDateTimeValue(date) {
    const offsetDate = new Date(date.valueOf() - date.getTimezoneOffset() * 60_000);
    return offsetDate.toISOString().slice(0, 16);
}

function localDateTimeLabel(date) {
    return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(date);
}

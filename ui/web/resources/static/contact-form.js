const form = document.querySelector("[data-contact-form]");

if (form) {
    form.addEventListener("click", (event) => {
        const addButton = event.target.closest("[data-add-detail]");
        if (addButton) {
            const type = addButton.dataset.addDetail;
            const group = addButton.closest(`[data-detail-group="${type}"]`);
            const list = group.querySelector("[data-detail-list]");
            const template = group.querySelector(`[data-detail-template="${type}"]`);
            const index = nextIndex(list, type);
            const content = template.content.cloneNode(true);

            content.querySelectorAll("[name], [value]").forEach((element) => {
                if (element.name) element.name = element.name.replace("__index__", index);
                if (element.value === "__index__") element.value = index;
            });
            list.append(content);
            return;
        }

        const removeButton = event.target.closest("[data-remove-detail]");
        if (removeButton) removeButton.closest(".detail-row").remove();
    });
}

function nextIndex(list, type) {
    const prefix = type === "email" ? "emailValue-" : "phoneValue-";
    return Array.from(list.querySelectorAll(`[name^="${prefix}"]`))
        .map((input) => Number.parseInt(input.name.slice(prefix.length), 10))
        .filter(Number.isFinite)
        .reduce((maximum, index) => Math.max(maximum, index), -1) + 1;
}

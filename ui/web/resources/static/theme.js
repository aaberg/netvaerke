const themeStorageKey = "netvaerke-theme";
const root = document.documentElement;

try {
    const savedTheme = localStorage.getItem(themeStorageKey);
    if (savedTheme === "light" || savedTheme === "dark") root.dataset.theme = savedTheme;
} catch {
    // Theme preference is optional when browser storage is unavailable.
}

document.addEventListener("DOMContentLoaded", () => {
    const toggle = document.querySelector("[data-theme-toggle]");
    if (!toggle) return;

    const systemPrefersDark = window.matchMedia("(prefers-color-scheme: dark)");
    const isDark = () => root.dataset.theme === "dark" || (!root.dataset.theme && systemPrefersDark.matches);
    const renderToggle = () => {
        const dark = isDark();
        const label = dark ? "Switch to light mode" : "Switch to dark mode";
        toggle.setAttribute("aria-label", label);
        toggle.setAttribute("aria-pressed", String(dark));
        toggle.title = label;
    };

    toggle.addEventListener("click", () => {
        const theme = isDark() ? "light" : "dark";
        root.dataset.theme = theme;
        try {
            localStorage.setItem(themeStorageKey, theme);
        } catch {
            // The selected theme still applies for the current page.
        }
        renderToggle();
    });

    systemPrefersDark.addEventListener("change", () => {
        if (!root.dataset.theme) renderToggle();
    });
    renderToggle();
});

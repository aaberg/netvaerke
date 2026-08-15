import { register } from "https://cdn.jsdelivr.net/npm/@teamhanko/hanko-elements/dist/elements.js";

const apiUrl = document.body.dataset.hankoApiUrl;

if (apiUrl) {
    const options = { sessionTokenLocation: "cookie" };
    const cookieDomain = document.body.dataset.hankoCookieDomain;
    if (cookieDomain) {
        options.cookieDomain = cookieDomain;
    }

    try {
        const { hanko } = await register(apiUrl, options);
        const auth = document.getElementById("hanko-auth");
        if (auth) {
            auth.addEventListener("onSessionCreated", () => window.location.assign("/dashboard"));
        }

        const logout = document.getElementById("logout");
        if (logout) {
            logout.addEventListener("click", async () => {
                logout.disabled = true;
                try {
                    await hanko.logout();
                    window.location.assign("/");
                } catch (_) {
                    logout.disabled = false;
                }
            });
        }
    } catch (_) {
        const error = document.getElementById("hanko-error");
        if (error) {
            error.hidden = false;
        }
    }
}

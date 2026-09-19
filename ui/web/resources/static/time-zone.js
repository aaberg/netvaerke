const detectedTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;

if (detectedTimeZone) {
    const currentTimeZone = document.cookie
        .split("; ")
        .find((cookie) => cookie.startsWith("netvaerke_timezone="))
        ?.slice("netvaerke_timezone=".length);

    if (currentTimeZone !== detectedTimeZone) {
        document.cookie = `netvaerke_timezone=${detectedTimeZone}; path=/; SameSite=Lax`;
        window.location.reload();
    }

    document.querySelectorAll("[data-time-zone]").forEach((input) => {
        input.value = detectedTimeZone;
    });
}

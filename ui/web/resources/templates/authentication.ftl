<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title?html} · netværke</title>
    <link rel="stylesheet" href="/assets/site.css">
</head>
<body class="account-page" data-hanko-api-url="${hankoApiUrl?html}" data-hanko-cookie-domain="<#if hankoCookieDomain??>${hankoCookieDomain?html}</#if>">
    <header class="site-header compact-header">
        <a class="brand" href="/" aria-label="netværke home">
            <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
                <path d="M7 24V8l18 16V8" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="4"/>
            </svg>
            <span>netværke</span>
        </a>
    </header>

    <main class="account-main">
        <section class="account-intro">
            <p class="eyebrow">WELCOME</p>
            <h1>${title?html}</h1>
            <p>Bring the important people in your life into clearer focus.</p>
        </section>
        <section class="auth-card" aria-label="Authentication">
            <hanko-auth id="hanko-auth" mode="${mode?html}"></hanko-auth>
            <p id="hanko-error" class="form-error" hidden>Authentication is temporarily unavailable. Please try again.</p>
        </section>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
</body>
</html>

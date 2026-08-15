<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dashboard · netværke</title>
    <link rel="stylesheet" href="/assets/site.css">
</head>
<body class="dashboard-page" data-hanko-api-url="${hankoApiUrl?html}" data-hanko-cookie-domain="<#if hankoCookieDomain??>${hankoCookieDomain?html}</#if>" data-hanko-logout>
    <header class="site-header dashboard-header">
        <a class="brand" href="/dashboard" aria-label="netværke dashboard">
            <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
                <path d="M7 24V8l18 16V8" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="4"/>
            </svg>
            <span>netværke</span>
        </a>
        <div class="account-summary">
            <span>${profile.name?html}</span>
            <button id="logout" class="button button-quiet" type="button">Sign out</button>
        </div>
    </header>

    <main class="dashboard-main">
        <p class="eyebrow">YOUR SPACE</p>
        <h1>Welcome, ${profile.name?html}.</h1>
        <section class="empty-state">
            <div class="empty-state-icon" aria-hidden="true">+</div>
            <h2>Your network is ready for its first connection.</h2>
            <p>This is the beginning. Contact notes, follow-up rhythms, and communication support will live here.</p>
        </section>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
</body>
</html>

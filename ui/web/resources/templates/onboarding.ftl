<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Set up your profile · netværke</title>
    <link rel="stylesheet" href="/assets/site.css">
</head>
<body class="account-page">
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
            <p class="eyebrow">ONE LAST STEP</p>
            <h1>Tell us how to introduce you.</h1>
            <p>We will use this for your profile and your personal network.</p>
        </section>
        <section class="profile-card">
            <#if error??>
                <p class="form-error" role="alert">${error?html}</p>
            </#if>
            <form method="post" action="/onboarding">
                <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                <label for="name">Name</label>
                <input id="name" name="name" type="text" autocomplete="name" maxlength="255" value="${name?html}" required>
                <label for="email">Email</label>
                <input id="email" name="email" type="email" autocomplete="email" maxlength="255" value="${email?html}" required>
                <button class="button button-primary button-full" type="submit">Create my profile</button>
            </form>
        </section>
    </main>
</body>
</html>

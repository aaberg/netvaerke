<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dashboard · netværke</title>
    <script src="/assets/theme.js"></script>
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
            <button class="button button-quiet theme-toggle" type="button" data-theme-toggle aria-label="Switch to dark mode" aria-pressed="false" title="Switch to dark mode">
                <span class="theme-icon theme-moon" aria-hidden="true"></span>
                <span class="theme-icon theme-sun" aria-hidden="true"></span>
            </button>
            <button id="logout" class="button button-quiet" type="button">Sign out</button>
        </div>
    </header>

    <main class="dashboard-main">
        <div class="dashboard-title">
            <div>
                <p class="eyebrow">YOUR SPACE</p>
                <h1>Contacts</h1>
            </div>
            <a class="button button-primary" href="/contacts/new">Add contact</a>
        </div>

        <#if contacts?size == 0>
            <section class="empty-state">
                <div class="empty-state-icon" aria-hidden="true">+</div>
                <h2>Your network is ready for its first connection.</h2>
                <p>Add the people you want to keep close. Notes, follow-up rhythms, and communication support will grow from here.</p>
                <a class="button button-primary" href="/contacts/new">Add your first contact</a>
            </section>
        <#else>
            <section class="contact-list" aria-label="Contacts">
                <#list contacts as contact>
                    <a class="contact-row" href="/contacts/${contact.contactId?html}/edit">
                        <#if contact.imageUrl??>
                            <img class="contact-avatar contact-photo" src="${contact.imageUrl?html}" alt="">
                        <#else>
                            <span class="contact-avatar" aria-hidden="true"><#if contact.name?has_content>${contact.name?substring(0, 1)?upper_case?html}<#else>?</#if></span>
                        </#if>
                        <span class="contact-summary">
                            <strong>${contact.name?html}</strong>
                            <#if contact.primaryEmailAddress??>
                                <span>${contact.primaryEmailAddress?html}</span>
                            <#else>
                                <span>No email address</span>
                            </#if>
                        </span>
                        <span class="contact-edit">Edit</span>
                    </a>
                </#list>
            </section>
        </#if>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
</body>
</html>

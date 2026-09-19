<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Schedule follow-up · netværke</title>
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
            <a class="button button-quiet" href="/contacts/${contact.contactId?html}">Back to contact</a>
            <button class="button button-quiet theme-toggle" type="button" data-theme-toggle aria-label="Switch to dark mode" aria-pressed="false" title="Switch to dark mode">
                <span class="theme-icon theme-moon" aria-hidden="true"></span>
                <span class="theme-icon theme-sun" aria-hidden="true"></span>
            </button>
            <button id="logout" class="button button-quiet" type="button">Sign out</button>
        </div>
    </header>

    <main class="follow-up-form-main" data-time-zone-container>
        <a class="back-link" href="/contacts/${contact.contactId?html}">Back to ${contact.name?html}</a>
        <div class="follow-up-page-intro">
            <p class="eyebrow">REMINDERS</p>
            <h1>Schedule a follow-up</h1>
            <p>Choose how you want to stay in touch with <strong>${contact.name?html}</strong>.</p>
        </div>

        <section class="follow-up-form-card">
            <div class="follow-up-type-switcher" aria-label="Follow-up type">
                <#if newFollowUp.recurrence == "NONE">
                    <span class="button button-primary">One-time</span>
                    <a class="button button-quiet" href="/contacts/${contact.contactId?html}/follow-ups/new?recurrence=RECURRING">Repeating</a>
                <#else>
                    <a class="button button-quiet" href="/contacts/${contact.contactId?html}/follow-ups/new?recurrence=NONE">One-time</a>
                    <span class="button button-primary">Repeating</span>
                </#if>
            </div>
            <#if error??><p class="form-error">${error?html}</p></#if>

            <form method="post" action="/contacts/${contact.contactId?html}/follow-ups">
                <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                <input type="hidden" name="recurrence" value="${newFollowUp.recurrence?html}">
                <#if newFollowUp.recurrence == "NONE">
                    <div class="follow-up-form-fields">
                        <label>Due date
                            <input type="date" name="dueOn" value="${newFollowUp.dueOn?html}" required>
                        </label>
                    </div>
                    <p class="section-hint">Set the date when you want to follow up.</p>
                <#else>
                    <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
                    <div class="follow-up-recurrence-fields">
                        <label>How often do you want to follow up with this contact?
                            <select name="frequency" required>
                                <option value=""<#if newFollowUp.frequency == ""> selected</#if>>Choose a frequency</option>
                                <#list followUpFrequencies as frequency>
                                    <option value="${frequency.value?html}"<#if newFollowUp.frequency == frequency.value> selected</#if>>${frequency.label?html}</option>
                                </#list>
                            </select>
                        </label>
                        <p class="section-hint">The first follow-up is one interval from today. After each completion, the next follow-up is one interval from the completion date.</p>
                    </div>
                </#if>
                <div class="follow-up-form-actions">
                    <a class="button" href="/contacts/${contact.contactId?html}">Cancel</a>
                    <button class="button button-primary" type="submit">Schedule follow-up</button>
                </div>
            </form>
        </section>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
    <script src="/assets/time-zone.js"></script>
</body>
</html>

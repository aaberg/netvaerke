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

    <main class="dashboard-main" data-time-zone-container>
        <div class="dashboard-title">
            <div>
                <p class="eyebrow">YOUR SPACE</p>
                <h1>Contacts</h1>
            </div>
            <a class="button button-primary" href="/contacts/new">Add contact</a>
        </div>

        <#if followUpMessage??>
            <p class="flash-message">${followUpMessage?html}</p>
        </#if>

        <section class="dashboard-follow-ups" aria-labelledby="dashboard-follow-ups-heading">
            <div class="section-heading">
                <div>
                    <p class="eyebrow">STAY CLOSE</p>
                    <h2 id="dashboard-follow-ups-heading">Follow-ups</h2>
                </div>
                <span class="section-hint">Due through the next 7 days</span>
            </div>
            <#if followUpSections?size == 0>
                <p class="follow-up-empty">No follow-ups due this week. Schedule one from a contact.</p>
            <#else>
                <div class="dashboard-follow-up-groups">
                    <#list followUpSections as section>
                        <section class="follow-up-group" aria-labelledby="follow-up-group-${section.label?lower_case?replace(" ", "-")}">
                            <h3 id="follow-up-group-${section.label?lower_case?replace(" ", "-")}">${section.label?html}</h3>
                            <ol class="dashboard-follow-up-list">
                                <#list section.items as followUp>
                                    <li class="dashboard-follow-up-row">
                                        <div class="dashboard-follow-up-summary">
                                            <a href="/contacts/${followUp.contactId?html}">${followUp.contactName?html}</a>
                                            <span class="dashboard-follow-up-meta">
                                                <span class="follow-up-type-label">${followUp.typeLabel?html}</span>
                                                <span>${followUp.dueLabel?html}</span>
                                                <#if followUp.frequencyLabel??><span>${followUp.frequencyLabel?html}</span></#if>
                                            </span>
                                        </div>
                                        <div class="follow-up-actions">
                                            <form method="post" action="/contacts/${followUp.contactId?html}/follow-ups/${followUp.followUpId?html}/complete">
                                                <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                                <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
                                                <input type="hidden" name="returnTo" value="dashboard">
                                                <button class="button button-primary" type="submit">Mark done</button>
                                            </form>
                                            <a class="button" href="/contacts/${followUp.contactId?html}?completeFollowUp=${followUp.followUpId?html}#follow-up-${followUp.followUpId?html}">Record interaction &amp; complete</a>
                                            <a class="button button-quiet" href="/contacts/${followUp.contactId?html}?rescheduleFollowUp=${followUp.followUpId?html}#follow-up-${followUp.followUpId?html}">Reschedule</a>
                                        </div>
                                    </li>
                                </#list>
                            </ol>
                        </section>
                    </#list>
                </div>
            </#if>
        </section>

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
                    <article class="contact-row">
                        <a class="contact-row-link" href="/contacts/${contact.contactId?html}">
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
                        </a>
                        <a class="button button-quiet contact-edit" href="/contacts/${contact.contactId?html}/edit">Edit</a>
                    </article>
                </#list>
            </section>
        </#if>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
    <script src="/assets/time-zone.js"></script>
    <script src="/assets/follow-up-form.js"></script>
</body>
</html>

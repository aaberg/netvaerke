<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${contact.name?html} · netværke</title>
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
            <a class="button button-quiet" href="/dashboard">Contacts</a>
            <button class="button button-quiet theme-toggle" type="button" data-theme-toggle aria-label="Switch to dark mode" aria-pressed="false" title="Switch to dark mode">
                <span class="theme-icon theme-moon" aria-hidden="true"></span>
                <span class="theme-icon theme-sun" aria-hidden="true"></span>
            </button>
            <button id="logout" class="button button-quiet" type="button">Sign out</button>
        </div>
    </header>

    <main class="contact-overview-main">
        <a class="back-link" href="/dashboard">Back to contacts</a>
        <section class="contact-overview-card" aria-labelledby="contact-name">
            <div class="contact-overview-heading">
                <#if contact.imageUrl??>
                    <img class="contact-overview-avatar contact-photo" src="${contact.imageUrl?html}" alt="">
                <#else>
                    <span class="contact-overview-avatar" aria-hidden="true"><#if contact.name?has_content>${contact.name?substring(0, 1)?upper_case?html}<#else>?</#if></span>
                </#if>
                <div>
                    <p class="eyebrow">CONTACT</p>
                    <h1 id="contact-name">${contact.name?html}</h1>
                    <#if contact.workTitle?? || contact.workOrganization??>
                        <p class="contact-work"><#if contact.workTitle??>${contact.workTitle?html}</#if><#if contact.workTitle?? && contact.workOrganization??> · </#if><#if contact.workOrganization??>${contact.workOrganization?html}</#if></p>
                    </#if>
                </div>
                <div class="contact-overview-actions">
                    <a class="button contact-action-button" href="/contacts/${contact.contactId?html}/edit" aria-label="Edit contact" title="Edit contact">
                        <span class="contact-action-icon contact-edit-icon" aria-hidden="true"></span>
                    </a>
                    <form method="post" action="/contacts/${contact.contactId?html}/delete" data-contact-delete-form>
                        <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                        <button class="button button-danger contact-action-button" type="submit" aria-label="Delete contact" title="Delete contact">
                            <span class="contact-action-icon contact-delete-icon" aria-hidden="true"></span>
                        </button>
                    </form>
                </div>
            </div>

            <div class="contact-details">
                <#if contact.emails?size != 0>
                    <section>
                        <h2>Email</h2>
                        <ul>
                            <#list contact.emails as email>
                                <li><a href="mailto:${email.value?html}">${email.value?html}</a><#if email.label??> <span>${email.label?html}</span></#if><#if email.isPrimary()> <span>Primary</span></#if></li>
                            </#list>
                        </ul>
                    </section>
                </#if>
                <#if contact.phoneNumbers?size != 0>
                    <section>
                        <h2>Phone</h2>
                        <ul>
                            <#list contact.phoneNumbers as phone>
                                <li><a href="tel:${phone.value?html}">${phone.value?html}</a><#if phone.label??> <span>${phone.label?html}</span></#if></li>
                            </#list>
                        </ul>
                    </section>
                </#if>
                <#if contact.note??>
                    <section class="contact-note">
                        <h2>Note</h2>
                        <p>${contact.note?html}</p>
                    </section>
                </#if>
            </div>
        </section>

        <section class="follow-ups-section" aria-labelledby="follow-ups-heading">
            <div class="follow-ups-heading">
                <span>
                    <p class="eyebrow">REMINDERS</p>
                    <h2 id="follow-ups-heading">Follow-ups</h2>
                </span>
            </div>

            <#if followUpMessage??>
                <p class="flash-message">${followUpMessage?html}</p>
            </#if>

            <div class="follow-up-groups">
                <#list followUpGroups as group>
                    <section class="contact-follow-up-group" aria-labelledby="follow-up-group-${group?index}">
                        <h3 id="follow-up-group-${group?index}">${group.label?html}</h3>

                        <#if group.items?size == 0>
                            <p class="follow-up-empty">
                                <#if group.isRecurring()>
                                    No repeating follow-up scheduled.
                                    <a class="text-link" href="/contacts/${contact.contactId?html}/follow-ups/new?recurrence=RECURRING">Set a recurring follow-up</a>
                                <#else>
                                    No one-time follow-ups scheduled.
                                    <a class="text-link" href="/contacts/${contact.contactId?html}/follow-ups/new?recurrence=NONE">Schedule one</a>
                                </#if>
                            </p>
                        <#else>
                            <ol class="follow-up-list">
                                <#list group.items as followUp>
                                    <li class="follow-up-card">
                                        <div class="follow-up-card-heading">
                                            <div>
                                                <strong>Due ${followUp.dueLabel?html}</strong>
                                                <span>${followUp.recurrenceLabel?html}</span>
                                            </div>
                                            <span class="follow-up-status follow-up-status-open">${followUp.statusLabel?html}</span>
                                        </div>
                                        <div class="follow-up-actions">
                                            <a class="button button-primary" href="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}">Follow up</a>
                                            <a class="button button-quiet" href="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}?manage=reschedule">Manage</a>
                                        </div>
                                    </li>
                                </#list>
                            </ol>
                        </#if>
                    </section>
                </#list>
            </div>
        </section>

        <section class="interactions-section" aria-labelledby="interactions-heading">
            <details class="new-interaction"<#if error??> open</#if>>
                <summary class="interactions-heading">
                    <span>
                        <p class="eyebrow">HISTORY</p>
                        <h2 id="interactions-heading">Interactions</h2>
                    </span>
                    <span class="button button-primary new-interaction-trigger">+ New interaction</span>
                </summary>
                <section class="interaction-create-card" aria-labelledby="record-interaction-heading">
                    <h3 id="record-interaction-heading">Record an interaction</h3>
                    <#if error??><p class="form-error">${error?html}</p></#if>
                    <form method="post" action="/contacts/${contact.contactId?html}/interactions" data-interaction-form>
                        <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                        <div class="interaction-form-fields">
                            <label>Type
                                <select name="channel">
                                    <#list channels as channel>
                                        <option value="${channel.value?html}" <#if newInteraction.channel == channel.value>selected</#if>>${channel.label?html}</option>
                                    </#list>
                                </select>
                            </label>
                            <label>When
                                <input type="datetime-local" name="occurredAt" value="${newInteraction.occurredAtInput?html}" data-interaction-time data-utc="${newInteraction.occurredAt?html}" required>
                            </label>
                        </div>
                        <label class="interaction-notes">Notes
                            <textarea name="notes" rows="3" placeholder="What happened?">${newInteraction.notes?html}</textarea>
                        </label>
                        <div class="interaction-actions">
                            <button class="button" type="button" data-interaction-cancel>Cancel</button>
                            <button class="button button-primary" type="submit">Record interaction</button>
                        </div>
                    </form>
                </section>
            </details>

            <#if interactions?size == 0>
                <p class="interaction-empty">No interactions recorded yet.</p>
            <#else>
                <ol class="interaction-list">
                    <#list interactions as interaction>
                        <li class="interaction-card">
                            <div class="interaction-summary">
                                <div class="interaction-summary-heading">
                                    <span class="interaction-channel">
                                        <span class="interaction-icon interaction-icon-${interaction.channel?lower_case?replace("_", "-")}" aria-hidden="true"></span>
                                        <strong>${interaction.channelLabel?html}</strong>
                                    </span>
                                    <time datetime="${interaction.occurredAt?html}" data-interaction-local-time>${interaction.occurredAtDisplay?html}</time>
                                </div>
                                <#if interaction.notes?has_content><p>${interaction.notes?html}</p></#if>
                            </div>
                            <details class="interaction-edit">
                                <summary>Edit</summary>
                                <form method="post" action="/contacts/${contact.contactId?html}/interactions/${interaction.interactionId?html}" data-interaction-form>
                                    <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                    <div class="interaction-form-fields">
                                        <label>Type
                                            <select name="channel">
                                                <#list channels as channel>
                                                    <option value="${channel.value?html}" <#if interaction.channel == channel.value>selected</#if>>${channel.label?html}</option>
                                                </#list>
                                            </select>
                                        </label>
                                        <label>When
                                            <input type="datetime-local" name="occurredAt" value="${interaction.occurredAtInput?html}" data-interaction-time data-utc="${interaction.occurredAt?html}" required>
                                        </label>
                                    </div>
                                    <label class="interaction-notes">Notes
                                        <textarea name="notes" rows="3" placeholder="What happened?">${interaction.notes?html}</textarea>
                                    </label>
                                    <div class="interaction-actions">
                                        <button class="button button-primary" type="submit">Save interaction</button>
                                        <button class="button" type="button" data-interaction-cancel>Cancel</button>
                                        <button class="button button-danger interaction-remove-button" type="submit" formaction="/contacts/${contact.contactId?html}/interactions/${interaction.interactionId?html}/remove" data-interaction-remove aria-label="Remove interaction" title="Remove interaction">
                                            <span class="interaction-remove-icon" aria-hidden="true"></span>
                                        </button>
                                    </div>
                                </form>
                            </details>
                        </li>
                    </#list>
                </ol>
            </#if>
        </section>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
    <script src="/assets/time-zone.js"></script>
    <script src="/assets/follow-up-form.js"></script>
    <script src="/assets/interaction-form.js"></script>
    <script src="/assets/contact-delete.js"></script>
</body>
</html>

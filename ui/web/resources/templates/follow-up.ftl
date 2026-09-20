<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Follow up with ${contact.name?html} · netværke</title>
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
            <a class="button button-quiet" href="/contacts/${contact.contactId?html}">Contact</a>
            <button class="button button-quiet theme-toggle" type="button" data-theme-toggle aria-label="Switch to dark mode" aria-pressed="false" title="Switch to dark mode">
                <span class="theme-icon theme-moon" aria-hidden="true"></span>
                <span class="theme-icon theme-sun" aria-hidden="true"></span>
            </button>
            <button id="logout" class="button button-quiet" type="button">Sign out</button>
        </div>
    </header>

    <main class="follow-up-detail-main" data-time-zone-container>
        <a class="back-link" href="/contacts/${contact.contactId?html}">Back to ${contact.name?html}</a>
        <section class="follow-up-detail-card">
            <div class="follow-up-contact-heading">
                <#if contact.imageUrl??>
                    <img class="contact-overview-avatar contact-photo" src="${contact.imageUrl?html}" alt="">
                <#else>
                    <span class="contact-overview-avatar" aria-hidden="true"><#if contact.name?has_content>${contact.name?substring(0, 1)?upper_case?html}<#else>?</#if></span>
                </#if>
                <div>
                    <p class="eyebrow">FOLLOW-UP</p>
                    <h1>Follow up with ${contact.name?html}</h1>
                    <#if contact.emails?size != 0 || contact.phoneNumbers?size != 0>
                        <p class="follow-up-contact-links">
                            <#if contact.emails?size != 0><a href="mailto:${contact.emails[0].value?html}">${contact.emails[0].value?html}</a></#if>
                            <#if contact.emails?size != 0 && contact.phoneNumbers?size != 0> · </#if>
                            <#if contact.phoneNumbers?size != 0><a href="tel:${contact.phoneNumbers[0].value?html}">${contact.phoneNumbers[0].value?html}</a></#if>
                        </p>
                    </#if>
                </div>
            </div>

            <div class="follow-up-detail-meta">
                <div>
                    <span class="follow-up-meta-label">Due</span>
                    <strong>${followUp.dueLabel?html}</strong>
                </div>
                <div>
                    <span class="follow-up-meta-label">Schedule</span>
                    <strong>${followUp.recurrenceLabel?html}</strong>
                </div>
                <div>
                    <span class="follow-up-meta-label">Status</span>
                    <strong class="follow-up-status<#if followUp.isOpen()> follow-up-status-open</#if>">${followUp.statusLabel?html}</strong>
                </div>
            </div>

            <#if followUp.isOpen()>
                <section class="follow-up-primary-card" aria-labelledby="record-follow-up-heading">
                    <p class="eyebrow">PRIMARY ACTION</p>
                    <h2 id="record-follow-up-heading">Record the interaction</h2>
                    <p class="follow-up-card-intro">Capture what happened and complete this follow-up at the same time.</p>
                    <#if followUpMode == "COMPLETE_WITH_INTERACTION" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                    <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/complete-with-interaction" data-interaction-form>
                        <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                        <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
                        <#if returnTo??><input type="hidden" name="returnTo" value="${returnTo?html}"></#if>
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
                            <textarea name="notes" rows="4" placeholder="What happened?">${newInteraction.notes?html}</textarea>
                        </label>
                        <div class="interaction-actions">
                            <button class="button button-primary" type="submit">Save interaction &amp; complete</button>
                        </div>
                    </form>
                </section>

                <section class="follow-up-secondary-actions" aria-label="Other follow-up actions">
                    <#if followUpMode == "COMPLETE" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                    <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/complete">
                        <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                        <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
                        <#if returnTo??><input type="hidden" name="returnTo" value="${returnTo?html}"></#if>
                        <button class="button button-quiet" type="submit">Complete without interaction</button>
                    </form>

                    <details class="follow-up-management"<#if followUpMode?has_content && followUpMode != "COMPLETE_WITH_INTERACTION" && followUpMode != "COMPLETE"> open</#if>>
                        <div class="follow-up-management-content">
                            <#if followUpMode == "MANAGE" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                            <section class="follow-up-edit-card">
                                <h3>Reschedule follow-up</h3>
                                <#if followUpMode == "RESCHEDULE" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                                <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/reschedule">
                                    <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                    <#if returnTo??><input type="hidden" name="returnTo" value="${returnTo?html}"></#if>
                                    <label>New due date
                                        <input type="date" name="dueOn" value="${rescheduleDueOn?html}" required>
                                    </label>
                                    <div class="interaction-actions">
                                        <button class="button button-primary" type="submit">Save date</button>
                                    </div>
                                </form>
                            </section>

                            <#if followUp.isRecurring()>
                                <section class="follow-up-edit-card">
                                    <h3>Change frequency</h3>
                                    <#if followUpMode == "FREQUENCY" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                                    <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/frequency">
                                        <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                        <#if returnTo??><input type="hidden" name="returnTo" value="${returnTo?html}"></#if>
                                        <div class="follow-up-recurrence-fields">
                                            <label>How often do you want to follow up with this contact?
                                                <select name="frequency" required>
                                                    <option value=""<#if frequencyForm.frequency == ""> selected</#if>>Choose a frequency</option>
                                                    <#list followUpFrequencies as frequency>
                                                        <option value="${frequency.value?html}"<#if frequencyForm.frequency == frequency.value> selected</#if>>${frequency.label?html}</option>
                                                    </#list>
                                                </select>
                                            </label>
                                        </div>
                                        <div class="interaction-actions">
                                            <button class="button button-primary" type="submit">Save frequency</button>
                                        </div>
                                    </form>
                                </section>
                            </#if>

                            <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/cancel" data-follow-up-cancel="Cancel this follow-up?<#if followUp.isRecurring()> This will stop future repeats.</#if>">
                                <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                <#if returnTo??><input type="hidden" name="returnTo" value="${returnTo?html}"></#if>
                                <button class="button button-danger" type="submit"><#if followUp.isRecurring()>Stop repeating<#else>Cancel follow-up</#if></button>
                            </form>
                        </div>
                    </details>
                </section>
            <#else>
                <section class="follow-up-closed-card">
                    <h2>Follow-up ${followUp.statusLabel?lower_case}</h2>
                    <#if followUp.completedOn??>
                        <p>Completed on ${followUp.completedOn?html}.</p>
                    <#else>
                        <p>This follow-up is no longer active.</p>
                    </#if>
                    <a class="button" href="/contacts/${contact.contactId?html}">View contact</a>
                </section>
            </#if>
        </section>

        <section class="follow-up-history" aria-labelledby="follow-up-history-heading">
            <div class="section-heading">
                <div>
                    <p class="eyebrow">HISTORY</p>
                    <h2 id="follow-up-history-heading">Recent interactions</h2>
                </div>
                <a class="button button-quiet" href="/contacts/${contact.contactId?html}">View full history</a>
            </div>
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
</body>
</html>

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
            <#if followUpError?? && followUpMode != "CREATE" && followUpMode != "COMPLETE_WITH_INTERACTION" && followUpMode != "COMPLETE">
                <p class="form-error follow-up-section-error">${followUpError?html}</p>
            </#if>

            <div class="follow-up-groups">
                <#list followUpGroups as group>
                    <section class="contact-follow-up-group" aria-labelledby="follow-up-group-${group?index}">
                        <h3 id="follow-up-group-${group?index}">${group.label?html}</h3>

                        <#if group.isRecurring() && (followUpMode!"") == "CREATE" && newFollowUp.recurrence == "RECURRING" && followUpError??>
                            <p class="form-error">${followUpError?html}</p>
                        </#if>

                        <#if group.isRecurring() && group.items?size == 0>
                            <section class="follow-up-create-card recurring-follow-up-create-card">
                                <form method="post" action="/contacts/${contact.contactId?html}/follow-ups">
                                    <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                    <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
                                    <input type="hidden" name="recurrence" value="RECURRING">
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
                                    <div class="interaction-actions">
                                        <button class="button button-primary" type="submit">Schedule follow-up</button>
                                    </div>
                                </form>
                            </section>
                        <#elseif group.items?size == 0>
                            <p class="follow-up-empty">No one-time follow-ups scheduled.</p>
                        <#else>
                            <ol class="follow-up-list">
                                <#list group.items as followUp>
                                    <li class="follow-up-card"<#if (selectedCompleteFollowUpId!"") == followUp.followUpId || (selectedRescheduleFollowUpId!"") == followUp.followUpId || (selectedFrequencyFollowUpId!"") == followUp.followUpId> id="follow-up-${followUp.followUpId?html}"</#if>>
                                        <div class="follow-up-card-heading">
                                            <div>
                                                <strong>Due ${followUp.dueLabel?html}</strong>
                                                <span>${followUp.recurrenceLabel?html}</span>
                                            </div>
                                            <span class="follow-up-status follow-up-status-open">${followUp.statusLabel?html}</span>
                                        </div>
                                        <div class="follow-up-actions">
                                            <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/complete">
                                                <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                                <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
                                                <button class="button button-primary" type="submit">Mark done</button>
                                            </form>
                                            <a class="button" href="?completeFollowUp=${followUp.followUpId?html}#follow-up-${followUp.followUpId?html}">Record interaction &amp; complete</a>
                                            <a class="button button-quiet" href="?rescheduleFollowUp=${followUp.followUpId?html}#follow-up-${followUp.followUpId?html}">Reschedule</a>
                                            <#if followUp.isRecurring()>
                                                <a class="button button-quiet" href="?editFrequencyFollowUp=${followUp.followUpId?html}#follow-up-${followUp.followUpId?html}">Change frequency</a>
                                            </#if>
                                            <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/cancel" data-follow-up-cancel="Cancel this follow-up?<#if followUp.isRecurring()> This will stop future repeats.</#if>">
                                                <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                                <button class="button button-danger" type="submit"><#if followUp.isRecurring()>Stop repeating<#else>Cancel</#if></button>
                                            </form>
                                        </div>

                                        <#if (selectedCompleteFollowUpId!"") == followUp.followUpId && (followUpMode!"") == "COMPLETE_WITH_INTERACTION">
                                            <section class="follow-up-interaction-card" aria-labelledby="complete-follow-up-heading-${followUp.followUpId?html}">
                                                <h3 id="complete-follow-up-heading-${followUp.followUpId?html}">Record the interaction</h3>
                                                <#if followUpError??><p class="form-error">${followUpError?html}</p></#if>
                                                <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/complete-with-interaction" data-interaction-form>
                                                    <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                                    <input type="hidden" name="timeZone" value="${timeZone?html}" data-time-zone>
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
                                                        <a class="button" href="/contacts/${contact.contactId?html}#follow-up-${followUp.followUpId?html}">Cancel</a>
                                                        <button class="button button-primary" type="submit">Save interaction &amp; complete</button>
                                                    </div>
                                                </form>
                                            </section>
                                        <#elseif (selectedCompleteFollowUpId!"") == followUp.followUpId && (followUpMode!"") == "COMPLETE" && followUpError??>
                                            <p class="form-error follow-up-card-error">${followUpError?html}</p>
                                        </#if>

                                        <#if (selectedRescheduleFollowUpId!"") == followUp.followUpId>
                                            <section class="follow-up-edit-card">
                                                <h3>Reschedule follow-up</h3>
                                                <#if (followUpMode!"") == "RESCHEDULE" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                                                <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/reschedule">
                                                    <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                                    <label>New due date
                                                        <input type="date" name="dueOn" value="${rescheduleDueOn?html}" required>
                                                    </label>
                                                    <div class="interaction-actions">
                                                        <a class="button" href="/contacts/${contact.contactId?html}#follow-up-${followUp.followUpId?html}">Cancel</a>
                                                        <button class="button button-primary" type="submit">Save date</button>
                                                    </div>
                                                </form>
                                            </section>
                                        </#if>

                                        <#if (selectedFrequencyFollowUpId!"") == followUp.followUpId>
                                            <section class="follow-up-edit-card">
                                                <h3>Change frequency</h3>
                                                <#if (followUpMode!"") == "FREQUENCY" && followUpError??><p class="form-error">${followUpError?html}</p></#if>
                                                <form method="post" action="/contacts/${contact.contactId?html}/follow-ups/${followUp.followUpId?html}/frequency">
                                                    <input type="hidden" name="csrfToken" value="${csrfToken?html}">
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
                                                        <a class="button" href="/contacts/${contact.contactId?html}#follow-up-${followUp.followUpId?html}">Cancel</a>
                                                        <button class="button button-primary" type="submit">Save frequency</button>
                                                    </div>
                                                </form>
                                            </section>
                                        </#if>
                                    </li>
                                </#list>
                            </ol>
                        </#if>

                        <#if !group.isRecurring()>
                            <details class="new-one-time-follow-up"<#if (followUpMode!"") == "CREATE" && newFollowUp.recurrence == "NONE"> open</#if>>
                                <summary>
                                    <span class="button new-one-time-follow-up-trigger">+ Add one-time follow-up</span>
                                </summary>
                                <section class="follow-up-create-card" aria-labelledby="schedule-one-time-follow-up-heading">
                                    <h3 id="schedule-one-time-follow-up-heading">Schedule a one-time follow-up</h3>
                                    <#if (followUpMode!"") == "CREATE" && newFollowUp.recurrence == "NONE" && followUpError??>
                                        <p class="form-error">${followUpError?html}</p>
                                    </#if>
                                    <form method="post" action="/contacts/${contact.contactId?html}/follow-ups">
                                        <input type="hidden" name="csrfToken" value="${csrfToken?html}">
                                        <input type="hidden" name="recurrence" value="NONE">
                                        <div class="follow-up-form-fields">
                                            <label>Due date
                                                <input type="date" name="dueOn" value="${newFollowUp.dueOn?html}" required>
                                            </label>
                                        </div>
                                        <div class="interaction-actions">
                                            <button class="button" type="button" data-follow-up-create-cancel>Cancel</button>
                                            <button class="button button-primary" type="submit">Schedule follow-up</button>
                                        </div>
                                    </form>
                                </section>
                            </details>
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

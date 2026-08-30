<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${title?html} · netværke</title>
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

    <main class="contact-form-main">
        <a class="back-link" href="/dashboard">Back to contacts</a>
        <div class="contact-form-intro">
            <p class="eyebrow">YOUR NETWORK</p>
            <h1>${title?html}</h1>
            <p>Keep the details that will help you show up well.</p>
        </div>
        <section class="contact-form-card">
            <#if error??>
                <p class="form-error" role="alert">${error?html}</p>
            </#if>
            <form method="post" action="${formAction?html}" enctype="multipart/form-data" data-contact-form>
                <input type="hidden" name="csrfToken" value="${csrfToken?html}">

                <div class="form-field">
                    <label for="name">Name</label>
                    <input id="name" name="name" type="text" autocomplete="name" maxlength="255" value="${contact.name?html}" required autofocus>
                </div>

                <fieldset class="contact-detail-group" data-detail-group="email">
                    <legend>Email addresses</legend>
                    <p class="field-hint">Choose one primary address for the contact list.</p>
                    <div data-detail-list>
                        <#list contact.emails as email>
                            <div class="detail-row">
                                <input name="emailValue-${email?index}" type="email" autocomplete="email" maxlength="255" value="${email.value?html}" placeholder="name@example.com" aria-label="Email address">
                                <input name="emailLabel-${email?index}" type="text" value="${email.label?html}" placeholder="Label" aria-label="Email label">
                                <label class="primary-choice">
                                    <input name="emailPrimary" type="radio" value="${email?index}" <#if contact.primaryEmailIndex?? && contact.primaryEmailIndex == email?index>checked</#if>>
                                    Primary
                                </label>
                                <button class="detail-remove" type="button" data-remove-detail aria-label="Remove email address">Remove</button>
                            </div>
                        </#list>
                    </div>
                    <button class="button button-quiet detail-add" type="button" data-add-detail="email">Add email</button>
                    <template data-detail-template="email">
                        <div class="detail-row">
                            <input name="emailValue-__index__" type="email" autocomplete="email" maxlength="255" placeholder="name@example.com" aria-label="Email address">
                            <input name="emailLabel-__index__" type="text" placeholder="Label" aria-label="Email label">
                            <label class="primary-choice"><input name="emailPrimary" type="radio" value="__index__">Primary</label>
                            <button class="detail-remove" type="button" data-remove-detail aria-label="Remove email address">Remove</button>
                        </div>
                    </template>
                </fieldset>

                <fieldset class="contact-detail-group" data-detail-group="phone">
                    <legend>Phone numbers</legend>
                    <div data-detail-list>
                        <#list contact.phoneNumbers as phone>
                            <div class="detail-row">
                                <input name="phoneValue-${phone?index}" type="tel" autocomplete="tel" value="${phone.value?html}" placeholder="Phone number" aria-label="Phone number">
                                <input name="phoneLabel-${phone?index}" type="text" value="${phone.label?html}" placeholder="Label" aria-label="Phone label">
                                <button class="detail-remove" type="button" data-remove-detail aria-label="Remove phone number">Remove</button>
                            </div>
                        </#list>
                    </div>
                    <button class="button button-quiet detail-add" type="button" data-add-detail="phone">Add phone</button>
                    <template data-detail-template="phone">
                        <div class="detail-row">
                            <input name="phoneValue-__index__" type="tel" autocomplete="tel" placeholder="Phone number" aria-label="Phone number">
                            <input name="phoneLabel-__index__" type="text" placeholder="Label" aria-label="Phone label">
                            <button class="detail-remove" type="button" data-remove-detail aria-label="Remove phone number">Remove</button>
                        </div>
                    </template>
                </fieldset>

                <fieldset class="contact-detail-group work-fields">
                    <legend>Work</legend>
                    <div class="detail-row">
                        <input id="work-title" name="workTitle" type="text" autocomplete="organization-title" value="${contact.workTitle?html}" placeholder="Title" aria-label="Work title">
                        <input id="work-organization" name="workOrganization" type="text" autocomplete="organization" value="${contact.workOrganization?html}" placeholder="Organization" aria-label="Organization">
                    </div>
                </fieldset>

                <fieldset class="contact-detail-group image-field">
                    <legend>Photo</legend>
                    <p class="field-hint">JPEG, PNG, or WebP, up to 5 MB.</p>
                    <#if contact.imageUrl??>
                        <div class="current-photo">
                            <img src="${contact.imageUrl?html}" alt="Current contact photo">
                            <label class="remove-photo"><input name="removeImage" type="checkbox" value="true"> Remove current photo</label>
                        </div>
                    </#if>
                    <input id="image" name="image" type="file" accept="image/jpeg,image/png,image/webp">
                    <p class="field-hint"><#if contact.imageUrl??>Choosing a new photo replaces the current one.<#else>Add a photo when you have one.</#if></p>
                </fieldset>

                <div class="form-field note-field">
                    <label for="note">Note</label>
                    <textarea id="note" name="note" rows="5" placeholder="What would you like to remember?">${contact.note?html}</textarea>
                </div>

                <div class="contact-form-actions">
                    <a class="button" href="/dashboard">Cancel</a>
                    <button class="button button-primary" type="submit">${submitLabel?html}</button>
                </div>
            </form>
        </section>
    </main>
    <script type="module" src="/assets/hanko.js"></script>
    <script src="/assets/contact-form.js"></script>
</body>
</html>

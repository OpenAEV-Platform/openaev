# Landing pages

A Landing Page is the page a recipient sees after clicking the link in a lure email. It is a reusable Component: you author it once and use it in as many Injects as you need. Open **Components > Phishing > Pages** to manage Landing Pages.

Creating a Landing Page also creates the Threat Arsenal action you will use in your Injects, so a Landing Page is both the content *and* the entry point of a phishing exercise.

## Create a landing page

1. Go to **Components > Phishing > Pages**.
2. Click **Create**.
3. Fill in the **Details** section:

    | Field | Description |
    |---|---|
    | Name | The Landing Page name. It is also used as the label of the generated Threat Arsenal action (*Phishing: \<name\>*). Required. |
    | Description | Free-text description, shown in the list. |
    | Redirect URL after submit | Where the recipient is sent after submitting the form. Leave empty to keep them on the page. Only a relative path or an `http(s)` URL is accepted. |
    | Serve on domain | The hostname the lure links are built on: *Platform default domain*, or one of your verified [custom domains](custom-domains.md). |
    | Capture submitted data | When enabled, what the recipient submits is stored as a Credentials Finding. When disabled, the submission is only counted as an event. |
    | Capture passwords | When enabled, the submitted password is stored alongside the username. Disable it to record who submitted without keeping their password. |

4. Fill in the **Landing page content** section:

    | Field | Description |
    |---|---|
    | HTML content | The page body. Add `data-phishing-form` to the form element you want the platform to intercept. |
    | CSS content | Optional styling applied to the page. |

5. Click **Create**.

The **Preview** pane on the right renders the page exactly as recipients see it, on a light canvas and inside a sandboxed frame. Use the fullscreen button to inspect it at full size.

!!! note

    Only the *Capture submitted data* setting is reflected in the Threat Arsenal action: a capture-enabled Landing Page declares a `Credentials` output, so it can feed findings-based [Inject chaining](../../../inject-chaining.md). A page that captures nothing declares no output.

## Write the form

The platform intercepts the submit event of the element carrying the `data-phishing-form` attribute, collects its named inputs, and posts them to the tracking endpoint. Any field name works, but a recognized username and password field name is stored as a clean `username / password` pair rather than a raw field dump.

- Recognized username fields: `username`, `email`, `user`, `login`, `loginfmt`, `user_name`, `userid`, `user_id`, `identifier`, `emailaddress`, `e-mail`, `account`.
- Recognized password fields: `password`, `passwd`, `pass`, `pwd`, `passwordinput`, `userpassword`.

When none of the submitted field names is recognized (a cloned real-world form with unusual names), every non-empty field is captured as `name=value` pairs instead, so a genuine submission is never silently dropped.

!!! example

    A minimal credential-capture form:

    ```html
    <div class="phishing-card">
      <h1>Sign in</h1>
      <form data-phishing-form>
        <label>Email<input type="email" name="username" autocomplete="username" required /></label>
        <label>Password<input type="password" name="password" autocomplete="current-password" required /></label>
        <button type="submit">Sign in</button>
      </form>
    </div>
    ```

## Generate content with AI

Both the HTML and the CSS field offer a **Generate with AI** button. It opens a dialog where you can pick an agent, start from a one-click preset, and refine with your own instructions before accepting the result. Presets include *Microsoft 365 sign-in*, *Google Workspace login*, *Corporate VPN portal*, *Minimal and modern*, and *Match a specific brand*.

If the field already has content, the generation refines it instead of starting over. The streamed result is shown as a live preview next to its code, and nothing is written to the form until you accept it.

!!! tip "Enterprise Edition"

    AI generation requires the Enterprise Edition with XTM One configured. See [XTM Suite connector](../../../evaluate/xtm-suite-connector.md).

## What's next?

- [Email templates](email-templates.md) -- Author the lure email that links to this page
- [Run a phishing exercise](run-a-campaign.md) -- Use the generated action in an Inject
- [Custom domains](custom-domains.md) -- Serve this page from a hostname you own

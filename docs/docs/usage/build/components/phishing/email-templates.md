# Email templates

An Email Template is the lure email sent to recipients. Like Landing Pages, it is a reusable Component: every Email Template is offered as a choice on every Landing Page action, so you can pair any lure with any page. Open **Components > Phishing > Emails** to manage Email Templates.

## Create an email template

1. Go to **Components > Phishing > Emails**.
2. Click **Create**.
3. Fill in the **Details** section:

    | Field | Description |
    |---|---|
    | Name | The Email Template name, shown in the list and in the Inject's template chooser. Required. |
    | Description | Free-text description, shown in the list. |
    | Subject | The email subject line. Required. |
    | Sender name override | Display name of the sender. Leave empty to use the Simulation sender, then the platform default. |
    | Sender email override | Address the email is sent from. Same fallback chain as the sender name. |
    | Add tracking pixel | When enabled, an invisible 1x1 image is appended to the body so *email opened* can be detected. |

4. Fill in the **Email content** section:

    | Field | Description |
    |---|---|
    | HTML body | The email content. Use the `{{phishing_url}}` placeholder where you want the per-recipient link. |
    | Text body | Optional plain-text alternative. |

5. Click **Create**.

The **Preview** pane on the right shows the rendered email with its sender and subject line, as the recipient sees it.

## The link placeholder

At execution time, the platform replaces `{{phishing_url}}` with the unique link of the recipient being sent to. Put it in an `href`, in visible text, or both:

```html
<p>Hello,</p>
<p>We detected unusual activity on your account. Please verify your identity to keep your access active.</p>
<p><a href="{{phishing_url}}">Verify my account</a></p>
<p>If you did not expect this message, you can ignore it.</p>
```

If the body contains no `{{phishing_url}}` placeholder, the platform appends the raw link as a paragraph at the end of the email, so a template can never be sent without a way to reach the Landing Page.

!!! note

    The body is inserted as-is: it is never evaluated as a template language beyond this placeholder. Custom [variables](../variables.md) are not interpolated in a phishing lure.

## Sender identity

The address a lure is sent from is resolved in this order, first non-empty value winning:

1. The **Sender email override** set on the Inject.
2. The **Sender email override** set on the Email Template.
3. The Simulation's sender address.
4. The platform default mailer address.

The sender display name follows the same chain, and the reply-to address always comes from the Simulation (or the platform default outside a Simulation).

!!! tip

    A convincing lure needs a sender domain that your mail infrastructure accepts. Set the sender on the Email Template when the lure and the identity belong together (an "IT helpdesk" template), and use the Inject overrides for one-off variations.

## Tracking pixel

*Add tracking pixel* appends an invisible image pointing at the recipient's open-tracking endpoint. Loading it is what marks the email as opened.

Keep in mind what an invisible image can and cannot tell you:

- A recipient reading the email in a client that blocks remote images never triggers the pixel, so the *Email not opened* step stays green even though the email was read.
- Mail gateways and image proxies fetch remote images on delivery. The platform ignores hits that arrive within seconds of delivery or that come from known scanning infrastructure, so those do not turn the step red.

Disable the pixel if you only want to measure clicks and submissions.

## Generate content with AI

The HTML body field offers a **Generate with AI** button, with one-click presets: *Password reset lure*, *IT security notice*, *Shared document invite*, and *Match a specific brand*. Generated bodies include the `{{phishing_url}}` placeholder. As with Landing Pages, existing content is refined rather than replaced, and nothing is written to the form until you accept the result.

!!! tip "Enterprise Edition"

    AI generation requires the Enterprise Edition with XTM One configured. See [XTM Suite connector](../../../evaluate/xtm-suite-connector.md).

## What's next?

- [Landing pages](landing-pages.md) -- Author the page this email links to
- [Run a phishing exercise](run-a-campaign.md) -- Pair a template and a page in an Inject
- [Results and scoring](results.md) -- Understand what open, click, and submit mean for the score

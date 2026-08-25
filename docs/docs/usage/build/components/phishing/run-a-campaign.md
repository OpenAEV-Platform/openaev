# Run a phishing exercise

A phishing exercise is a regular Inject built on the Threat Arsenal action that your Landing Page generated. This page walks through building it, what happens at execution, and what the recipient receives.

## Create the Inject

In the Injects list of your Scenario or Simulation, search the Threat Arsenal for **Phishing: \<your landing page name\>**. Every Landing Page generates one action under that name. Select it, then fill in the Inject content:

| Field | Required | Description |
|---|---|---|
| Teams | Yes | The Teams whose Players receive the lure. Every Player of every selected Team gets one email. |
| Email template | Yes | The lure email to send. Defaults to the first available template. |
| Subject override | No | Replaces the Email Template subject for this Inject only. |
| Sender name override | No | Replaces the sender display name for this Inject only. |
| Sender email override | No | Replaces the sender address for this Inject only. |

The three phishing expectations are predefined and enabled by default. See [Results and scoring](results.md). Saving and scheduling work like any other Inject.

!!! note

    Phishing Injects target Teams, never Assets: no Agent or Executor is involved, and the platform sends the mail itself. This is why a phishing action never asks for endpoints and never fails a target health check.

## Test before you launch

The **Test** action of the Injects list covers email and SMS Injects only, so a phishing Inject cannot be dry-run that way. To validate a lure before a real exercise:

1. Create a Team containing only yourself.
2. Run the phishing Inject against that Team in a throwaway Simulation.
3. Check the email as it arrives, click through the Landing Page, and submit the form.
4. Review the results, then delete the throwaway Simulation.

This exercises the whole chain (delivery, link, page rendering, tracking, and scoring), which a content-only preview cannot do.

## What happens at execution

A recipient whose email fails to send is recorded as an error in the Inject's execution traces, and the remaining recipients are still sent. The execution ends with an information trace: *Phishing emails sent to N target(s)*.

Each recipient's tracking record is committed before their email leaves the platform, so an early recipient can already open and click while the rest of the batch is still being sent, and those events are tracked correctly.

## What the recipient receives

The lure email carries a unique link of the form:

```
https://<host>/auth/<token>
```

`<host>` is the [custom domain](custom-domains.md) linked to the Landing Page, or the platform host when none is set. The `<token>` is an unguessable, per-recipient identifier: the platform recovers the owning Tenant from the token alone, so the URL exposes no Tenant identifier and reads like a generic authentication link.

When the recipient opens the link, the platform serves the sanitized Landing Page in a sandboxed frame, which is also what marks the *link clicked* step. Submitting the form records the submission and, if the Landing Page defines a redirect URL, sends the recipient there.

!!! note

    Links in emails that were already sent keep working after an upgrade: the previous tracking endpoints, which carried the Tenant identifier in the URL, remain available for backward compatibility.

## Execution failures

| Message | Cause |
|---|---|
| *Phishing inject requires an email template* | No Email Template selected on the Inject. |
| *Phishing email template not found* | The selected Email Template was deleted after the Inject was created. |
| *Phishing landing page not found* | The Landing Page behind the action no longer exists. |
| *Phishing needs at least one target user* | The selected Teams contain no Player. |

## What's next?

- [Results and scoring](results.md) -- Read the outcome of the exercise
- [Inject status](../../../evaluate/injects/inject-status.md) -- Interpret execution status and traces
- [Findings](../../../evaluate/findings/findings.md) -- Work with the captured credentials

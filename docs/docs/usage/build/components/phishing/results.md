# Results and scoring

A phishing exercise measures how each recipient behaved at every step of the lure. This page explains the three expectations a phishing Inject carries, why their colors are the opposite of what you may expect, and how to read the evidence attached to each step.

## The three expectations

Every phishing action declares three predefined expectations, all enabled by default.

| Expectation | Family | Scored by |
|---|---|---|
| `Email not opened` | Human response | The recipient's tracking pixel loading. |
| `Link not clicked` | Human response | The recipient's Landing Page loading. |
| `Credentials not submitted` | Human response | The recipient submitting the form. |

The three human-response steps are ordered 1, 2, 3 in the results, following the kill chain: email, then link, then submission.

## Green means resisted

Phishing expectations are **inverted** compared to the rest of the platform. They measure whether the recipient *resisted* a step, and resisting is the desired outcome:

- **Green** -- The recipient has not performed the step. The row reads *Email not opened*, *Link not clicked*, or *Credentials not submitted*, and that statement is true.
- **Red** -- The recipient performed the step: they fell for that part of the lure.

Every step is pre-scored green the moment the lure is sent, before any link is published. The matching event then flips it to red. Two consequences worth knowing:

- A recipient who never interacts keeps the green verdict permanently. Phishing steps are never left pending and never expire, so you do not have to wait for an expiration window to read the result of an exercise.
- Steps cascade backwards. A click flips both *Email not opened* and *Link not clicked*; a submission flips all three. This is deliberate: a recipient who submits data necessarily opened the email and followed the link, even if a blocked remote image meant the pixel never loaded.

## The evidence on a red step

Every flip records the forensic origin of the request that triggered it, shown on the expectation card in the target results:

| Field | What it tells you |
|---|---|
| IP address | Where the request came from. A cloud provider range is a strong hint that no human was involved. |
| User agent | Which client made the request: a real browser, or a mail client image proxy. |
| Delay | How long after delivery the event happened, for example *3 minutes after delivery*. |
| Automation hint | A **Possibly automated** chip when the request came from a mail client image proxy, which carries both genuine recipient opens and provider-side pre-fetches. The hint is advisory: it never changes the score, it only helps you weigh the row. |

Each step keeps the origin of *its own* triggering request, so a red *Email not opened* from an image proxy and a red *Link not clicked* from a real browser stay distinguishable.

## Captured credentials

When the Landing Page has *Capture submitted data* enabled, a submission creates a Finding:

- **Type**: `Credentials`
- **Name**: the Landing Page name
- **Value**: the recognized username, plus the password when *Capture passwords* is enabled, as `username / password`
- **Linked to**: the recipient and their Team, and the Inject

Findings are visible on the Inject, on the Simulation, and in the global Findings list, and can be reused as input for [Inject chaining](../../../inject-chaining.md). See [Findings](../../../evaluate/findings/findings.md).

A Landing Page with capture disabled records the submission as an event and scores the step, but stores nothing.

## What's next?

- [Expectations](../../../evaluate/expectations/expectations.md) -- How expectations and validation rules work in general
- [Inject result](../../../evaluate/injects/inject-result.md) -- Read per-target Inject outcomes
- [Findings](../../../evaluate/findings/findings.md) -- Work with the captured credentials

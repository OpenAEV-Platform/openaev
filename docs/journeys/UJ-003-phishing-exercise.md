---
id: UJ-003
title: Run a phishing exercise with a landing page and read the results
status: draft
owner:
persona: [SOC manager, Crisis manager]
priority: P1
edition: [CE, EE]
doc_pages:
  - usage/build/components/phishing/overview.md
  - usage/build/components/phishing/email-templates.md
  - usage/build/components/phishing/landing-pages.md
  - usage/build/components/phishing/run-a-campaign.md
  - usage/build/components/phishing/results.md
  - usage/build/people.md
last_checked:
---

## Overview

- **What**: send a phishing email that leads to a landing page, then read who opened, clicked and submitted.
- **Who**: SOC manager or awareness lead.
- **Goal**: per-recipient results to measure user resilience.

## Prerequisites

- A Team with Players using test mailboxes only.
- `openaev.base-url` reachable over HTTPS (tracking pixel and landing links use it).
- Optional: a verified custom domain for links.

## Steps

1. In **Components > Phishing**, create an email template: subject, HTML body, sender, tracking pixel.
2. Create a landing page: HTML, CSS, capture of submitted data and passwords, redirect URL.
3. Create an Inject or atomic test with the phishing injector, using the template and the landing page, targeting the Team.
4. Launch it, open the email in the test mailbox, click the link and submit dummy data.
5. Read the results: opened, clicked, submitted, per recipient.

## Expected result

Tracking and capture events show in the results and match what was done in the mailbox.

## Doc gaps

- Not checked yet.

## Completion criteria

A new user completes the steps from the docs only.

# Notifications

Notifications alert users when important events occur on the platform. OpenAEV currently supports one notification type: Scenario score degradation.

## Scenario score degradation

This notification sends an email when a Simulation's score drops below the score of the previous run for the same Scenario. It helps track regressions in your security posture over time.

### How to enable

1. Open the Scenario page.
2. Click the notification icon in the Scenario header.
3. Edit the email subject in the popup. The **Trigger** (score degradation) and **Notifier** (email) are fixed.
4. Confirm. The alert is sent to the email address of the user who activates it.

### How to disable

1. Open the Scenario page.
2. Click the notification icon.
3. Select **Delete** in the popup.

## In-app notifications

OpenAEV also provides an in-app notification center accessible from the top navigation bar. Notifications appear as unread items and can be:

- Marked as read individually or in bulk
- Deleted in bulk
- Searched and filtered

## What's next?

- [Scenario](build/scenario/scenario.md) -- Create and manage Scenarios
- [Simulation](evaluate/simulation/simulation.md) -- Run Simulations and track results

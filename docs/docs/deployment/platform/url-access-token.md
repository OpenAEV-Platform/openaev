# URL access token

This page explains how OpenAEV secures player email links with short-lived URL access tokens.

## What is this?

OpenAEV uses URL access tokens to protect links sent in player-facing emails.

Instead of exposing persistent identifiers in query parameters, OpenAEV now generates an opaque token and sends links in this format:

```text
/api/url/access?token=<raw-token>
```

When a player opens the link, OpenAEV validates the token, sets a secure cookie, and redirects to the target page.

## Why use it?

This mechanism reduces the risk of data leakage in browser history, logs, and referrer headers.

Main security benefits:

- Token is short-lived and revocable.
- Token is scoped to a user and an exercise.
- OpenAEV stores only a SHA-256 hash in the database.
- OpenAEV removes the token from the URL after first access.

!!! tip
    This change hardens security without changing the player experience.

## How do I do it?

### Enable and configure token behavior

1. Set the token validity margin after the exercise end date.
2. Set the retention window for purge operations.

| Parameter                                     | Environment variable                          | Default value | Description                                                                |
|:----------------------------------------------|:----------------------------------------------|:--------------|:---------------------------------------------------------------------------|
| `openaev.url.access.token.expiry-margin-days` | `OPENAEV_URL_ACCESS_TOKEN_EXPIRY-MARGIN-DAYS` | `7`           | Adds a grace period after `exercise.end_date` to compute token expiration. |
| `openaev.url.access.token.retention-days`     | `OPENAEV_URL_ACCESS_TOKEN_RETENTION-DAYS`     | `30`          | Keeps revoked or expired tokens for audit before purge.                    |

### Verify email link flow

1. Send a Media Pressure, Challenge, or Lessons Learned email.
2. Confirm the email contains `/url/access?token=...`.
3. Open the link and verify OpenAEV returns a redirect to the target page.
4. Verify the browser receives a cookie with `HttpOnly`, `Secure`, and `SameSite=Strict`.

## Example

A player receives this link:

```text
https://<openaev-base-url>/url/access?token=<opaque-token>
```

OpenAEV validates the token and redirects the player to the initial exercise resource.
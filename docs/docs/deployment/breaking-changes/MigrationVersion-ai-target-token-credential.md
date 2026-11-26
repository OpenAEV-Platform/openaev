# AI target API token moved to credentials

!!! info ""

    * **Introduced in**: `OpenAEV [MigrationVersion]`

## Description of changes

An AI target no longer keeps its API token in its own settings. It now points to an **API token credential**, created
once and picked from the AI target form.

## Impact

* The upgrade deletes the API tokens already saved on AI targets. They were stored in clear text and may have leaked.
* Until a credential is set, an AI target that needs a token fails to authenticate.

## Migration guide

1. Create a new API token at your AI provider, and revoke the old one.
2. In OpenAEV, create an **API token** credential holding the new token.
3. Edit each AI target that needs a token and choose this credential.

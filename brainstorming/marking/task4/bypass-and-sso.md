# Task 4 — Who ignores markings, and SSO admins

**Issue**: https://github.com/OpenAEV-Platform/openaev/issues/8562 (chunk 2) · **Depends on**: #8739 (#6370)

## Problem

Today, a user with the `BYPASS` role and only `TLP:GREEN` can see `TLP:RED` assets.

Why: the agent and OpenCTI accounts must see every asset. So the code skips markings for any user with a hidden right
that only these accounts should have. But `BYPASS` gives that right too, so `BYPASS` users skip markings as well.

Also, this check is written in five places that do not agree: the same user can be allowed to see an asset but not to
launch on it.

## Rule

Markings come first, capabilities second (Task 5).

| Who | Sees marked assets |
|---|---|
| First admin (created at install, `openaev.admin.email`) | all, in every tenant |
| Everyone else, `user_admin` and `BYPASS` (tenant or platform) included | only what their groups' markings allow |
| Agent and OpenCTI accounts | all of their tenant, because their group holds every marking |

One place in the code decides it, used by reads, launches and marking assignment.

## Examples

- **Bob**, tenant admin (`BYPASS`) with `TLP:GREEN`: sees GREEN and CLEAR assets, not RED. A launch on a group holding a
  RED asset skips it.
- **Carole**, ops in a platform group with `BYPASS`: markings belong to a tenant, so she sees no marked asset until a
  tenant group gives her some.
- **The agent** of tenant A: its group gets every new marking of tenant A, at creation, so it keeps reporting on its
  host even once the host is marked. Nobody can change this group's markings through the API (already the case for
  system groups).

## SSO admins

Today `roles_admin` / `all_admin` set `user_admin`, which administers the whole platform. From now on, for new admins:

| Provider | The admin joins | `user_admin` |
|---|---|---|
| with `tenant_id` = A | "Administrators" of tenant A (`TLP:RED` by default, #7639) | not set |
| without `tenant_id` | platform "Administrators" | not set |

This reuses the scoped group mapping of #8739: the admin role is an implicit group rule. The group is left when the IdP
role is removed.

Existing `user_admin` users are left as they are. Only today's rule stays: role removed in the IdP → `user_admin` set to
`false`, except for the first admin, who is never demoted.

## Breaking changes

- SSO, new admins only: an admin from a provider bound to a tenant administers only that tenant.
- Markings (`MARKING` preview flag): `user_admin` users other than the first admin, and `BYPASS` users, no longer see
  assets above their markings.

## Out of scope

OpenCTI scenario launcher (chunk 6), one account per agent, the wider `BYPASS` cleanup (several ways to check it,
hidden capabilities given by `BYPASS`).

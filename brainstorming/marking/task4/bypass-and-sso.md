# Task 4 — Who ignores markings, and SSO admins

**Issue**: https://github.com/OpenAEV-Platform/openaev/issues/8562 (chunk 2) · **Depends on**: #8739 (#6370)

## Problem

Today, a user with the `BYPASS` role and only `TLP:GREEN` can see `TLP:RED` assets.

Why: the agent and OpenCTI accounts must see every asset. So the code skips markings for any user with a hidden right
that only these accounts should have. But `BYPASS` gives that right too, so `BYPASS` users skip markings as well.

Also, the rule "who skips markings" is copied in five places, and the copies differ: the OpenCTI account skips markings
when it reads assets, but not when it launches. We want one rule, in one place.

## Rule

Markings come first, capabilities second (Task 5).

| Who | Sees marked assets |
|---|---|
| First admin (created at install, `openaev.admin.email`) | all, in every tenant |
| Everyone else, `user_admin` and `BYPASS` (tenant or platform) included | only what their groups' markings allow |
| Agent and OpenCTI accounts | all assets of their own tenant: their group is given every marking of that tenant, like a normal user who holds them all |

One place in the code decides it, used by reads, launches and marking assignment.

## Examples

Tenant A has four assets: `web-01` (no marking), `db-01` (`TLP:GREEN`), `hr-01` (`TLP:AMBER`), `ceo-laptop` (`TLP:RED`).

| Who | Their markings | `web-01` | `db-01` | `hr-01` | `ceo-laptop` |
|---|---|:-:|:-:|:-:|:-:|
| First admin | none needed | ✅ | ✅ | ✅ | ✅ |
| Bob, tenant admin (`BYPASS`) | `TLP:GREEN` | ✅ | ✅ | ❌ | ❌ |
| Carole, ops in a platform group with `BYPASS` | none (markings belong to a tenant) | ✅ | ❌ | ❌ | ❌ |
| Alice, SSO admin in "Administrators" of tenant A | `TLP:RED` (group default) | ✅ | ✅ | ✅ | ✅ |
| Agent account of tenant A | every marking of tenant A | ✅ | ✅ | ✅ | ✅ |

Today, Bob and Carole see all four. When Bob launches an inject on the four assets, it runs on `web-01` and `db-01` only.

When a new marking is created in tenant A, the agent's group gets it at once, so the agent keeps reporting on its host
even if the host is marked with it. Nobody can change this group's markings through the API (already the case for
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

# Task 4 — Who ignores markings, and SSO admins

**Issue**: https://github.com/OpenAEV-Platform/openaev/issues/8562 (chunk 2) · **Depends on**: #8739 (#6370)

## Problem

Today, a user with `BYPASS` in a tenant group and only `TLP:GREEN` can see `TLP:RED` assets.

Why: the agent and OpenCTI accounts must see every asset. So the code skips markings for any user holding
`AGENT_RUNTIME_ACCESS` or `MANAGE_STIX_BUNDLE`, two hidden rights meant only for these accounts. But a tenant `BYPASS`
gives every tenant right, these two included, so `BYPASS` users skip markings as well.

Also, the rule "who skips markings" is copied in five places, and the copies differ:

| Place | Who skips markings today |
|---|---|
| Reading data (`HttpMarkingScopeSupplier`) | `user_admin`, tenant `BYPASS`, agent, OpenCTI |
| Launching an inject (`InjectService`) | `user_admin` only |
| Marking an asset (`AssetMarkingsService`) | `user_admin` only |
| Giving markings to a group (`TenantGroupService`) | `user_admin` only |
| List of markings you can give (`MarkingDefinitionApi`) | `user_admin` only |

So a tenant `BYPASS` user reads every asset but launches only on the ones their markings allow. We want one rule, in one
place.

## Rule

Markings decide which assets you see; capabilities decide what you can do with them.

| Who | Sees marked assets |
|---|---|
| First admin (created at install from `openaev.admin.email`, fixed id) | all, in every tenant |
| Everyone else, `user_admin` and `BYPASS` (tenant or platform) included | only what their groups' markings allow |
| Agent and OpenCTI accounts | all assets of their own tenant: their group is given every marking of that tenant, like a normal user who holds them all |

Background jobs run without a user and still see everything, as today.

One place in the code decides it, used by the five places above.

**New check, or the rule can be bypassed:** today you can add a user to a group if you hold the group's capabilities;
markings are not checked, and a `BYPASS` user passes. So Bob (`TLP:GREEN`) could add himself to the "Admin" group
(`TLP:RED`) and see everything. From now on, adding someone to a group also needs every marking of that group, like
giving the markings directly.

## Examples

Tenant A has four assets: `web-01` (no marking), `db-01` (`TLP:GREEN`), `hr-01` (`TLP:AMBER`), `ceo-laptop` (`TLP:RED`).

| Who | Their markings | `web-01` | `db-01` | `hr-01` | `ceo-laptop` |
|---|---|:-:|:-:|:-:|:-:|
| First admin | none needed | ✅ | ✅ | ✅ | ✅ |
| Bob, tenant admin (`BYPASS` in tenant A) | `TLP:GREEN` | ✅ | ✅ | ❌ | ❌ |
| Carole, ops in a platform group with `BYPASS` | none (markings belong to a tenant) | ✅ | ❌ | ❌ | ❌ |
| Alice, SSO admin in the "Admin" group of tenant A | `TLP:RED` (group default) | ✅ | ✅ | ✅ | ✅ |
| Dave, `user_admin` from an old SSO login, in no group | none | ✅ | ❌ | ❌ | ❌ |
| Agent account of tenant A | every marking of tenant A | ✅ | ✅ | ✅ | ✅ |

What changes for them:

- Bob sees all four today (his tenant `BYPASS` gives him the agent's hidden rights). Afterwards, only `web-01` and
  `db-01`, for reading and launching alike.
- Carole already sees only `web-01` today: a platform `BYPASS` does not give the agent's hidden rights.
- Dave sees all four today. Afterwards, only `web-01`, until someone who holds the markings adds him to "Admin".
- "Admin" holds `TLP:RED` only: an asset marked with another type (PAP, a custom type) stays hidden from Alice unless the
  group is given that marking too.

The agent and OpenCTI groups always hold every marking of their tenant: the ones that already exist (at upgrade), the
ones created later (through the API or a data pack), and all of them when the group is created (the OpenCTI group is
created when the first connector registers; all connectors of a tenant share it). Nobody can change these groups'
markings through the API (already refused for system groups; to be tested on both group routes).

The task 3 design had set this option aside as fragile, because a missed marking breaks the agent. It is fine now: one
operation keeps the list complete, run at each of the moments above, and a miss only hides an asset from the agent, it
never shows an asset to someone who should not see it. For chunk 6 (#8554): the agent reads exactly what it reads today
and never launches, so it gains nothing.

## SSO admins

Today `roles_admin` / `all_admin` set `user_admin`, which administers the whole platform and, today, skips markings.
From now on, a user who signs in with the admin role:

| Provider (`openaev.provider.<id>.tenant_id`) | The admin joins | `user_admin` |
|---|---|---|
| set to tenant A | the "Admin" group of tenant A (`TLP:RED` by default, #7639) | not set |
| not set | the "Admin" group of the default tenant, where these users already land today | not set |

With `all_admin=true`, every user of the provider joins that group. The admin role comes from the IdP roles, as today.

It works like a #8739 group rule: IdP admin role → "Admin" group of the provider's tenant. The user leaves the group when
the IdP role is removed.

Why SSO is in this change: once `user_admin` no longer skips markings, a new SSO admin would see no marked asset at all.
Putting them in "Admin" gives them the tenant's default markings.

Existing `user_admin` users keep the flag (they keep all their rights except skipping markings, see Dave). When they sign
in again with the admin role, they also join "Admin". As today, losing the IdP role sets `user_admin` to `false`.

**New:** the first admin is never demoted by SSO. Today it can be, if its email also signs in through SSO without the
admin role. It is recognized by its fixed id, not by `user_admin`.

## Breaking changes

- SSO: an admin from a provider bound to a tenant now administers only that tenant, no longer the whole platform. This
  applies to users who sign in with the admin role after the upgrade and do not already have `user_admin`.
- Markings (`MARKING` preview flag): `user_admin` users other than the first admin, and `BYPASS` users, no longer see
  assets above their markings. Fix: add them to a group that holds those markings.
- Adding a user to a group now needs the group's markings.

## Out of scope

- Scenarios created from OpenCTI (chunk 6, #8554): this change gives the OpenCTI account every marking, including later
  ones. Chunk 6 only has to set that account as the scheduler of these scenarios.
- One account per agent: today all agents of a tenant share one account.
- The follow-ups below.

## Follow-up

- **`BYPASS` gives too much.** It is turned into "every capability", including the hidden ones meant only for the agent
  and OpenCTI accounts. So a tenant `BYPASS` user can, for example, call the agent API. It should not.
- **`BYPASS` is checked in several ways.** `User.isAdminOrBypass()` looks for `BYPASS` in the user's capabilities, but
  since #4864 `BYPASS` is replaced there by the capabilities it covers. So it never finds it and only means "is admin",
  in about 30 places. Other checks (`hasPlatformBypass`, `hasTenantBypass`, `PermissionService.isBypassGranted`) work.
  We should keep one way.
- **Two admin groups in the default tenant.** "Administrators" (created at startup for the first admin, `BYPASS`, no
  marking) and "Admin" (the default group every tenant gets since #8193, `TLP:RED`). They should be one group.

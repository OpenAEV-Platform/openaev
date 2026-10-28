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
giving the markings directly. Same for making a group a default group: otherwise Bob could make a `TLP:RED` group the
default one, and every new user of the tenant would get `TLP:RED`.

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

How the agent and OpenCTI groups get every marking: like in OpenCTI, a group can be set to **receive new markings**.
When a marking is created, every group of the tenant with this setting gets it. The agent and OpenCTI groups have it
on, and get every existing marking when they are created (the OpenCTI group is created when the first connector
registers; all connectors of a tenant share it). At upgrade, a data pack turns it on for the existing technical groups
and gives them the markings that already exist. Nobody can change these groups' markings through the API.

The setting is not in the API yet. Later, it can be turned on for other groups, for example "Admin".

The task 3 design had set this option aside as fragile, because a missed marking breaks the agent. It is fine now: the
groups get new markings automatically, and a miss only hides an asset from the agent, it never shows an asset to
someone who should not see it. For chunk 6 (#8554): the agent reads exactly what it reads today
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

## Implementation (pseudo code)

**1. One rule for the clearance.** The `bypass` parameter goes away; the cache decides from the user id.

```java
// MarkingClearanceCacheManager
MarkingCtx findClearance(userId, tenantId) {            // key: userId:tenantId (was userId:tenantId:bypass)
  boolean all = User.ADMIN_UUID.equals(userId);
  return resolver.resolve(markingsOfUserGroups(userId, tenantId), tenantMarkings(tenantId), all);
}
```

**2. The five places just pass the user.**

```java
// before: findClearance(user.getId(), tenantId, user.isAdminOrBypass() || hasAgentOrStixRight(user))
// after:
findClearance(user.getId(), tenantId);
```

The `AGENT_RUNTIME_ACCESS` / `MANAGE_STIX_BUNDLE` special case is deleted.

**3. Groups that receive new markings (as in OpenCTI).** A new flag on the group, `group_auto_new_marking`:

```java
// MarkingDefinitionService.create, after saving the marking
for (group : groupsOfTenant(tenantId).where(autoNewMarking)) {
  addIfMissing(group.markings, marking);            // never removes
  evictClearance(group.users());
}

// when the agent or OpenCTI group is created, and once at upgrade (data pack)
group.autoNewMarking = true;
addIfMissing(group.markings, tenantMarkings(tenantId));
```

**4. Joining a group, or making it a default group, needs its markings.**

```java
// TenantGroupService.updateGroupUsers, and updateGroup when "default group" is turned on
assertCanAssignCapabilities(currentUser, group.roles());           // today
assertCanAssignMarkings(findClearance(currentUser, tenantId), group.markings());  // new
```

**5. SSO admin (after #8739).**

```java
// at SSO login, provider has roles_admin or all_admin
tenantId = provider.tenantId() ?: DEFAULT_TENANT;
admin    = provider.allAdmin() || intersects(provider.adminRoles(), idpRoles);
group    = findGroupByName("Admin", tenantId);         // not found → warning, nothing granted
if (admin)  grantGroup(user, group);                   // user_admin is never set to true
else {
  revokeGroup(user, group);
  if (!user.isFirstAdmin()) user.setAdmin(false);      // as today, except the first admin
}
```

## Plan

| Step | What | Status |
|---|---|---|
| 1 | Team review of this doc and the ADR (this draft PR) | in progress |
| 2 | Agent and OpenCTI groups receive every marking (group flag) | done, in the code PR |
| 3 | One rule: only the first admin ignores markings; joining a group or making it a default group needs its markings | done, in the code PR |
| 4 | SSO admins join the "Admin" group | after #8739, separate PR |
| 5 | Check in the app with the `MARKING` flag on | to do |

## Breaking changes

- SSO: an admin from a provider bound to a tenant now administers only that tenant, no longer the whole platform. This
  applies to users who sign in with the admin role after the upgrade and do not already have `user_admin`.
- Markings (`MARKING` preview flag): `user_admin` users other than the first admin, and `BYPASS` users, no longer see
  assets above their markings. Fix: add them to a group that holds those markings.
- Adding a user to a group, or making it a default group, now needs the group's markings.

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

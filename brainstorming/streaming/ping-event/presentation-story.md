# Live screens, without pushing state: the story

Spoken companion of the [Figma Slides deck](https://www.figma.com/slides/3dkUNyYCtezUu0CqzzE1JA). The slides
carry only diagrams; this is what is said over them. Each slide's speaker notes in Figma hold the short version.
About 25 minutes, demo included.

## 1. Title

Our screens look live, and mostly they are not. This talk tells why, in three problems, then how TanStack Query
and change signals fix them, and ends on a live demo on the assets. Watch the amber band: it is the SSE stream.
What travels on it is the whole story.

## 2. The symptom: two users, one asset

An admin renames `web-01` to `web-01-prod`. An analyst has the assets list open. Nothing moves on their screen.
They will see the new name after a reload, a filter, a page change. Yet the stream delivered the event to their
browser. So where did it go?

## 3. Before: the stream pushes the whole entity

Follow one write, left to right.

- **Backend.** Every JPA write goes through `ModelBaseListener`, which builds a `BaseEvent` carrying the whole
  entity, serialized. `StreamService` checks each open connection: the tenant, READ, and since MARKING the
  marking. Then it strips the fields this reader may not see.
- **🆕 With MARKING, `StreamVisibilityService`.** A row can now be hidden from some readers, so before sending,
  the stream needs a per-reader decision. `StreamVisibilityService.visibilityOf(event, tenant)` builds a
  `StreamVisibility` from rules derived from the schema (`StreamMarkingRules`): the row rule (the row's own
  markings, its parent's, or its link rows'), and the rules for the marked rows a payload lists or embeds. For each
  reader, `visibleUnder(clearance)` says whether the row is within their clearance, and `payloadFor(clearance)`
  rebuilds a payload without the marked children they may not see. Notifications reuse the same decision (#9319).
  This is the layer the next slides question: it exists only because the event carries content.
- **The stream** carries the entity: `asset_id`, name, IPs, agents…
- **Frontend.** `useDataLoader` is the whole client side of the stream: it opens the SSE connection to
  `/api/stream`, re-runs every mounted loader on a reconnection or at the end of a bulk operation, and manages
  the Redux store. It normalizes each instance (normalizr) and deep-merges it into the Redux store. The
  store holds 60 entries, one map per type (`endpoints`, `tags`, `asset_groups`, `users`, `injects`…), each keyed
  by id. Normalized means one copy of each entity, pointing to the others by id: an endpoint carries
  `asset_tags: ["t1"]`, and the screen resolves `tags["t1"]` with `getTag(id)`. The stream event lands in
  `endpoints`, highlighted. The details page reads the store with `useHelper`: it is live. The assets list does
  not read the store.

## 4. Problem 1: we update the store, the screen reads its own fetch

The event arrives, the store is updated: that part works. It is the same store as on the previous slide, the
`endpoints` map holds the new name. But the list keeps its rows in local state, loaded by
its own REST search when it mounted. It refetches only on its own triggers: page, filter, sort, its own
mutations, a remount. A change made by someone else, another user, an agent, the backend, shows up later.

66 files use `PaginationComponentV2`: almost every list is in this case. The inventory of the implementation plan
found the same on details pages that copy a fetch into `useState`. The Endpoints list even had a workaround,
`patchRowsFromStore`, which patched the names of the visible rows from the store.


## 5. Problem 2: the page state lives in the browser

`PaginationComponentV2` fetches one page into local state. After its own mutations, each screen patches that state
by hand:

- a creation prepends the new row: a page of 20 shows 21, whatever the sort and the filters;
- a deletion leaves 19: the first row of the next page never moves up;
- an update stays in place, even when it moves the row out of the filter;
- the total is a counter: `incrementTotalElements`, `handleChangeTotalElements`.

34 files do this. The server already knows the right page: we re-implemented pagination in the browser, and
got it slightly wrong.

## 6. Problem 3: with marking, the payload must be cut for every reader

Since MARKING, a row can be hidden from some readers. The event carries the entity as the *publisher* loaded it, so
before sending, `StreamService` rebuilds it for each reader: `payloadFor`, `keepOnly`, the user trimming, the
notifier redaction. An inject targeting three assets of three markings becomes three different payloads.

Every new marked type means new stripping code, and a forgotten field is a leak. And a reader who *loses* access
receives nothing at all, so the row stays on their screen.

Pause. What if the event carried no content?

## 7. After: send a signal, re-read through REST

The stream says *what changed*: `{ type: "assets", id, action: "update" }`. It never says what it looks like. The
screen re-reads through REST, which already applies the tenant, RBAC and marking for this reader. The small dots
on the band: from now on, only signals.

## 8. After: the architecture

Same write, same listener. Two differences:

- **Backend.** The listener now also remembers the markings each row was loaded with. `StreamService` only decides
  *who* receives the signal: the tenant, READ, the marking now, and the marking before the change.
- **Frontend.** `streamClient` reduces a message to a change: `{type, id, action}`. Asset subtypes become `assets`
  (`TYPE_OF`); an agent's change is also a change of its asset (`PARENTS_OF`). The `LiveRouter` finds the queries
  that care, batches, throttles, and invalidates them. TanStack refetches through REST. The screen shows exactly
  what the API returns for this reader. Nothing ever writes server data into the cache.

Where we are: the frontend already uses only type, id and action. The backend still sends the entity, and stops
type by type, lot by lot.

## 9. How a change finds its queries

A query declares what it shows, in `meta: liveMeta({...})`:

- `refs`: the entities on screen, children included. A change on one refetches the query.
- `collections`: the types it lists. A create or a delete may enter the page.
- `watch`: types that change the result without being shown (a count by status). Never on a list's own type, or
  every update anywhere refetches every open page.
- `throttleMs`: agents send a heartbeat every 2 minutes, so the assets list waits at most 30 s between refetches.

Walk the four examples on the right. And your own mutation: `useLiveMutation` describes what it changed, and the
queries showing it refetch *before* the mutation resolves.

## 10. Fix 2: the server owns the page

The list owns its input (search, filters, sort, page, size), kept in the URI and localStorage. The query key carries
all of it: each page is its own query. The total comes from the data. While another page loads, the previous one
stays on screen. A change, ours or someone else's, refetches the same key: 20 rows, the right total. The three
helpers struck through are gone.

This fix holds even for types the stream never sends, like credentials: our own mutations still refetch.

## 11. Fix 3: for one reader, a marking change is a create or a delete

A list ignores an update for a row it does not show, so an update can neither bring a row in nor take it out.
Seen from one reader, a marking change crossing their clearance *is* a row entering or leaving their world.

`StreamService` asks two questions per reader: did they see it before, do they see it now? Saw it and still does:
update. Saw it, no longer: a delete with the id only. Did not, now does: a create. It is generic for every table
with its own markings (`MarkedEntity`), and the platform refuses to start if a marked table forgets it.

## 12. Fix 3: no row changed, the reader did

An admin adds a reader to a group granting TLP:RED. No row changed, so no entity event, yet what this reader may
read just changed. Every clearance change already goes through `MarkingClearanceCacheManager`: its evictions now
publish an event. After commit, `StreamService` sends `clearance-changed`, with an empty payload, only to the users
concerned. The browser resets every query, and everything re-reads with the new clearance. The marking chips too:
the definitions behind them are now a query. We found that one in testing: the rows came back with an empty chip.

## 13. Moving out of Redux: one entity at a time, the whole feature

A lot moves one entity's whole feature: list, details, create, update, delete, drawers. Why not screen by screen?
Because a details page on a query whose drawer still reads Redux shows two versions of the same entity.

Lot 1 is every asset type at once: one table, one id space, one marking column. List and details are done; the
drawers and the security platforms are left. Then asset groups, which are not live anywhere today because the
store says `asset_groups` and the stream says `assetgroups`. Then findings and expectations, where the throttles
of a running simulation are decided. Then injects, then people and components, then settings.

When nothing reads a type from Redux any more, the backend stops sending its content. The implementation plan holds
the matrix: every screen, its live status today, how to test it, what may regress.

## 14. The recipe

The skill `migrate-to-live-query`, in the repository, is the procedure for one lot: inventory, stream type, plain
action functions, query options with `liveMeta`, move the reads, move the writes, check that no Redux read is left,
tests, PR. The tests are the part not to skip: a unit test with a fake stream, and a two-actor e2e that proves the
page never reloads.

## 15. Live demo: assets, two browsers

See the runbook below. Say out loud, each time: no reload, one search request, and the screen ignores what the
event carries.

## 16. Questions

Open questions worth raising: should one signal cover every rights change, not only the clearance? What format for
the signal marker? Live e2e specs in their own CI shard, or nightly only? Next step: finish lot 1.

---

# Demo runbook

## Setup (before the talk)

1. Backend and frontend running from the branch, with the `MARKING` preview feature on (`dev` profile:
   `openaev.enabled-dev-features=*`).
2. **Browser A**: `admin@openaev.io`, group Admin, cleared for TLP:RED.
3. **Browser B**, another browser or a private window: `e2e-manager@openaev.io` / `e2e-manager`, group Manager,
   cleared for TLP:AMBER. The e2e setup creates this user; otherwise create it and add it to the Manager group.
4. In A, create an agentless endpoint `demo-web-01`, with no marking.
5. In B, open Assets, search `demo`. Open DevTools: Network filtered on `stream|search`, and the React Query
   devtools panel.
6. Wait 30 s after B's page loaded before the first change: the assets list refetches at most every 30 s (agent
   heartbeats), and the page load just used that slot.

## Beats

| # | In browser A | Expected in browser B | What to show |
|---|---|---|---|
| 1 | Rename `demo-web-01` to `demo-web-01-prod` (row menu, Update) | the row shows the new name, no reload | Network: one stream message, then one `POST …/assets/search`; the list re-read, it did not patch |
| 2 | Same drawer: Markings → TLP:RED, save | the row leaves B's list, the total drops | the stream message B receives is a *delete* with the id only |
| 3 | Settings → Security → Groups → Manager → markings: add TLP:RED | the asset comes back with its TLP:RED chip; its Update drawer offers TLP:RED | the named `clearance-changed` event, empty; the React Query devtools show every query reset |
| 4 | Delete the asset | gone from B, total included | the same, for a delete |

Use the single-asset drawer for beat 2, not the bulk "Edit markings": a bulk operation sends nothing per entity, and
B would see it only at its next refetch (a known limit).

## After the demo

Remove TLP:RED from the Manager group, or the next e2e run starts with a wider clearance.

## Fallback

If the live setup fails, run the e2e spec with the browser visible. It plays beats 2 and 3 on its own:

```bash
cd openaev-front
HEADLESS=false yarn playwright test --project=setup --project=chromium \
  tests_e2e/auth.setup.ts tests_e2e/tests/assets/assets-list-live-markings.spec.ts
```

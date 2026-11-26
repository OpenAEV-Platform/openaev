---
name: migrate-to-live-query
description: >-
  Migrates one entity's frontend feature (list, details page, creation, update, deletion,
  popovers and drawers) from Redux (useDataLoader + useHelper + dispatch thunks) or
  PaginationComponentV2 to live TanStack queries refreshed by the platform stream. Use when asked
  to migrate a screen or an entity to TanStack Query, to make a list or details page live, or to
  remove an entity from the Redux store. Covers the scope inventory, the stream type mapping,
  plain action functions, query options with liveMeta, paginated lists, mutations through
  useLiveMutation, tests (unit and two-actor e2e) and the final check that no Redux read or
  write of the entity is left in the feature.
---

# Migrate an Entity to Live Queries

Move one entity's feature from Redux to TanStack queries that the platform stream refreshes.

The rules live in [frontend.instructions.md](../../../.github/instructions/frontend.instructions.md),
section **Data Loading & State**. This skill is the procedure: it never restates a rule, it tells you
when to apply which. Read that section once before starting.

The reference implementation is the assets feature:

| What | Reference file |
|---|---|
| Plain action functions | `openaev-front/src/actions/assets/endpoint-actions.ts` (`searchAssets`, `getAssetOverview`) |
| Query options and live rules | `openaev-front/src/actions/assets/asset-queries.ts` |
| Details page | `openaev-front/src/admin/components/assets/asset/AssetDetail.tsx` |
| Paginated list | `openaev-front/src/admin/components/assets/endpoints/Endpoints.tsx` |
| List unit test (fake stream) | `openaev-front/src/__tests__/admin/components/assets/endpoints/Endpoints.test.tsx` |
| E2E, changes made outside the page | `openaev-front/tests_e2e/tests/assets/assets-list-live-updates.spec.ts`, `asset-detail-live-updates.spec.ts` |
| The live layer | `openaev-front/src/utils/query/` (`live.ts`, `liveRouter.ts`, `entityChange.ts`), `openaev-front/src/utils/stream/streamClient.ts` |

For why the stream only carries change signals and how marking visibility applies, read
`brainstorming/streaming/ping-event/technical-design.md`.

> **Scope rule: migrate the entity's whole feature in one go.** List, details page, creation,
> update, deletion, popovers and drawers all move together. When the migration is done, no
> component of the feature reads the entity with `useHelper` or writes it with `dispatch(thunk)`.
> Other entities the feature displays (users, tags, executors…) may stay on Redux until their own
> migration, but a component never shows one entity from both sources.
>
> Screens of *other* features that read the same Redux store entry (an inject form listing
> endpoints) are out of scope: they move with their own feature, and the stream keeps feeding the
> store entry until then.

---

## Step 1 — Inventory the feature

List every file of the feature and what it does with the entity. Replace `<feature>` with the
folder name (`assets`, `tags`…) and `<store>` with the Redux store name of the entity
(`endpoints`, `tags`; the keys of `entitiesInitializer` in `openaev-front/src/reducers/Referential.ts`).

```bash
# Redux reads and writes in the feature's screens
grep -rn "useHelper\|dispatch(\|useDataLoader" openaev-front/src/admin/components/<feature>
# Thunks of the entity
grep -n "Referential\|=> (dispatch" openaev-front/src/actions/<feature>/*.ts
# Redux helpers of the entity (getXxx, getXxxs, getXxxsMap)
grep -n "'<store>'" openaev-front/src/actions/Schema.js
# Screens outside the feature reading the same store entry (out of scope, list them in the PR)
grep -rln "getXxx\b\|getXxxs\b\|getXxxsMap" openaev-front/src --include=*.tsx --include=*.ts --include=*.js
```

Write the inventory as a table in the PR description:

| Component | Reads | Writes | Opened from |
|---|---|---|---|
| `XxxList` | `PaginationComponentV2` fetch | — | route |
| `XxxDetail` | `useHelper(getXxx)` + `fetchXxx` | — | route |
| `XxxCreation` | — | `dispatch(addXxx)` | list |
| `XxxPopover` / `XxxUpdate` drawer | `useHelper(getXxx)` | `dispatch(updateXxx)`, `dispatch(deleteXxx)` | list rows, details page |

Every row is in scope. If the feature is too large for one PR, split by **entity**, never by
screen: a details page on a query whose edit drawer still reads Redux shows two versions of the same
entity.

## Step 2 — Map the stream type

1. **Type name**: find the entity's `attribute_schema` (the rule is in the comment of `ENTITY_TYPES`,
   `openaev-front/src/utils/query/entityChange.ts`; the backend overrides it with `getStreamSchema()`).
   Add the type to `ENTITY_TYPES` if missing.
2. **Streamed at all?** The backend streams an entity only if `isListened()` is true
   (`Base.java`, overridden per entity). A type that is not listened gets no change: its queries rely on
   TanStack's defaults only. Say so in the query's comment.
3. **Subtypes sharing an id space** (endpoints, security platforms and AI targets are all `assets`):
   map each streamed schema to the query type in `TYPE_OF` (`streamClient.ts`).
4. **Children whose change alters the parent** (an agent changes its asset): add the parent in
   `PARENTS_OF` (`streamClient.ts`), and list it in `parents` in the mutations' changes.
5. Add a case to `openaev-front/src/__tests__/utils/stream/streamClient.test.ts` for each new mapping.

## Step 3 — Plain action functions

In `actions/<feature>/<feature>-action(s).ts`, add one plain function per call the feature makes,
named by its verb, resolving to the data, never dispatching. Follow `searchAssets` and
`getAssetOverview`:

- reads take `{ ..., signal }` and turn the toast off: `simplePostCall(uri, input, { signal }, false).then(r => r.data)`;
- writes keep the default toast: `simplePutCall(uri, input).then(r => r.data)`.

Keep the old thunks while other features still call them (Step 1's out-of-scope list). Delete a thunk
when its last caller is gone.

## Step 4 — Query options

Create `actions/<feature>/<feature>-queries.ts`, modeled on `asset-queries.ts`:

- a key factory `<feature>Keys` (`all`, `lists`, `list(input)`, `details`, `detail(id)`), whose keys
  start with the entity type and carry every parameter;
- `<feature>Queries.list(input)` and `.detail(id)` with `queryOptions`, and `meta: liveMeta<TData>({...})`.

Choose the live rules from what the data shows, and write the reason in a comment above each query:

| The query… | Declare |
|---|---|
| shows these entities (rows, the detail, embedded children) | `refs: data => [...]`, children included |
| lists or counts a type | `collections: [TYPE]` |
| depends on a type it does not show (membership owned by another entity, a count by status) | `watch: [TYPE]` |
| shows rows with high-frequency children (agents: a heartbeat every 2 min each) | `throttleMs` raised (assets list: 30 s) |

A list never uses `watch` for its own type: every update anywhere would refetch every open page.

## Step 5 — Move the reads

- **Details page**: `const { data, isPending, error } = useQuery(<feature>Queries.detail(id))`.
  Render the states as the conventions say (`isPending`, then the error only on no data or
  `isClientError(error)`, with `isNotFoundError` as the deleted state). Reference: `AssetDetail.tsx`.
- **Paginated list**: replace `PaginationComponentV2` with `usePaginatedQuery(useQueryableWithLocalStorage(...), <feature>Queries.list)`
  and `QueryableToolbar`. Remove `setContent`, `reloadContentCount`, `setLoading`, the local rows
  state and any store-sync code (`patchRowsFromStore`-like helpers). Loader on
  `query.isPending || query.isPlaceholderData`. Reference: `Endpoints.tsx`.
- **Drawers and popovers**: read the entity from the same query as the page that opens them
  (`<feature>Queries.detail(id)`), or receive it as a prop from that query. Never
  `useHelper(getXxx)` + `fetchXxx` inside the drawer.
- Remove the feature's `useDataLoader` calls that only loaded this entity. Keep the ones loading other
  entities, and say which in a comment.

## Step 6 — Move the writes

- Each create, update, delete, marking change, bulk action: `useLiveMutation({ mutationFn, changes })`
  with the Step 3 function, and `changes` describing the entity (and `parents` if any). Example in
  the conventions (`updateTag` / `deleteTag`).
- A component that only reports back through `onCreate` / `onUpdate` / `onDelete` and cannot own the
  mutation yet: the parent calls `useApplyChanges()(changes, { immediate: true })`. This is a
  transition only; within the migrated feature, prefer the mutation in the component itself.
- Never update local rows or totals by hand (`setRows`, `incrementTotalElements`): describe the change.
- **Bulk operations**: the backend sends no per-entity stream event during a bulk operation, and the
  bulk-end event reaches only the user who launched it, whose router then refetches every live query.
  Describe the bulk mutation's own changes from the ids the response returns (see `bulkDelete` in
  `Endpoints.tsx`); other users see the result at their next refetch.

## Step 7 — Check nothing is left

```bash
grep -rn "useHelper\|dispatch(\|<store>" openaev-front/src/admin/components/<feature>
```

Each remaining hit must be another entity (users, tags, executors…), with no component reading the
migrated entity from Redux. Then:

```bash
cd openaev-front && yarn lint && yarn check-ts && yarn depcruise && yarn knip --dependencies && yarn test
```

## Step 8 — Tests

**Unit test of the list (and of the details page if it has live logic)**, modeled on `Endpoints.test.tsx`:
render inside `AppQueryProvider` with `FakeEventSource` and `startStream`, mock only the action
functions, and cover:

- `live`: a create on the stream refetches the page; an update of a shown row refetches; an update of
  a row not shown does not; a child change (if `PARENTS_OF`) refetches; the throttle holds if raised;
- `changes made on the page`: each mutation refetches at once (create, update, delete, bulk).

**E2E, two actors**, modeled on `assets-list-live-updates.spec.ts`: the page is open in the browser,
the changes are made through the API (`request` fixture and an `*ApiHelpers` class), and a window
marker proves the page never reloaded. Cover create, rename and delete for the list, and update and
delete for the details page. Set the timeout above the query's `throttleMs`.

**Manual check with two browsers** when the entity is marked (an admin and a restricted reader,
`MARKING` flag on), without reloading the reader's page:

1. The admin marks an entity the reader sees with a marking they don't hold → the row leaves the
   reader's list (the stream sends them a deletion, id only).
2. The admin removes that marking → the row comes back (a creation, for the reader).
3. The admin adds the reader to a group granting that marking → the rows come back with their marking
   chips, and the marking picker offers it (`clearance-changed` resets every query).
4. The reverse of 3 → the rows leave the list.

Steps 1 and 2 hold for a table with its own markings (`MarkedEntity`); a row marked through another
one is not covered yet. Marking chips and pickers read `useMarkingDefinitions`, never their own fetch
of the definitions, or step 3 shows empty chips. See points 4 and 6 of 3.2 in the technical design.

## Step 9 — Pull request

- The PR description contains Step 1's inventory, with the out-of-scope readers of the store entry
  listed for a later migration.
- If the migration showed a new pattern or a rule worth keeping, update `frontend.instructions.md`,
  and `.github/agents/frontend-reviewer.agent.md` in the same PR (agent maintenance rule in `AGENTS.md`).
- Run the `review-frontend` skill on the diff before asking for review.

---

## Pitfalls seen on the assets migration

| Pitfall | What happens | Fix |
|---|---|---|
| Store name ≠ stream schema (`asset_groups` vs `assetgroups`, endpoints streamed as `endpoints` not `assets`) | the query never matches a change | map in `TYPE_OF`; use only `ENTITY_TYPES` |
| A drawer of the page still on Redux | the page and its drawer show two versions of the entity | Step 5, drawers read the page's query |
| `watch` on the list's own type | every update anywhere refetches every open page | `collections` + `refs` |
| Rows with agents and the default throttle | the list refetches on every agent heartbeat | raise `throttleMs` |
| `staleTime` raised or `refetchOnWindowFocus` off "because the query is live" | changes the stream does not send (rights lost through a group role, a row marked through another one) never show | keep TanStack's defaults |
| Expecting a refetch from a bulk operation in another browser | none comes: bulk events go only to the launcher | the next refetch (focus, filter, page) shows it |
| Testing with the page loaded once | a page load fetches twice (mount, then stream open) | wait for the stream's first fetch before acting |

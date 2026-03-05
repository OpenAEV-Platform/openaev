You are adding a new frontend feature page in OpenAEV.

> Follow conventions from `frontend.instructions.md`.

## File structure to create

Use `snake_case` for folder names. One folder per feature, one file per behavior:

```
src/actions/{feature}/
  {feature}-action.ts      # API calls (CRUD, search)
  {feature}-helper.d.ts    # Store selectors/helpers contract (interface)
  {feature}-schema.ts      # normalizr schema + idAttribute

src/admin/components/{section}/{feature}/
  {Feature}s.tsx            # List page
  {feature}s.queryable.ts   # Headers/filters/sorts
  {feature}/
    {Feature}Form.tsx       # react-hook-form + Zod form
    {Feature}Update.tsx     # Update Drawer
    {Feature}Creation.tsx   # Create Drawer/Dialog
  hooks/
    use{Feature}s.ts        # Custom hook for search/state
```

## Actions split

```typescript
// {feature}-action.ts — API calls
export const ENTITY_URI = '/api/{entities}';
export const searchEntities = (input: SearchPaginationInput) => simplePostCall(`${ENTITY_URI}/search`, input);
export const addEntity = (data: EntityInput) => (dispatch: Dispatch) => postReferential(entity, ENTITY_URI, data)(dispatch);

// {feature}-helper.d.ts — Store contract
export interface EntityHelper {
    getEntities: () => EntityOutput[];
    getEntityMaps: () => Record<string, EntityOutput>;
    getEntity: (id: string) => EntityOutput | undefined;
}

// {feature}-schema.ts — normalizr
export const entity = new schema.Entity('{entities}', {}, {idAttribute: '{entity}_id'});
export const arrayOfEntities = new schema.Array(entity);
```

## Queryable

```typescript
export const LOCAL_STORAGE_KEY = '{features}';
export const ENTITY_PREFIX = '{feature}';
const FIELD_NAME = '{feature}_name';
export const getHeaders: (t: (text: string) => string) => Header[] = (t) => [
    {field: FIELD_NAME, label: t('Name'), isSortable: true, value: (item) => item.{feature}_name
},
]
;
export const FILTERS = [FIELD_NAME];
export const SORTS: SortField[] = initSorting(FIELD_NAME);
```





export type EntityChangeAction = 'create' | 'update' | 'delete';

/**
 * Entity type names the live layer matches on: the platform stream's `attribute_schema`, i.e. the
 * `@Entity(name)` or simple class name of the backend class declaring the `@Id` (a
 * `@MappedSuperclass` included), lowercased with no separator, plus 's' ('es' after an 's'). So
 * Endpoint is 'assets' (its id is declared by Asset) and AssetGroup is 'assetgroups'. Feature code
 * only names types from here, so a typo does not compile: add a missing type before using it.
 */
export const ENTITY_TYPES = {
  AGENTS: 'agents',
  ASSET_GROUPS: 'assetgroups',
  ASSETS: 'assets',
  TAGS: 'tags',
} as const;

export type EntityType = typeof ENTITY_TYPES[keyof typeof ENTITY_TYPES];

export interface EntityRef<TType extends string = string> {
  type: TType;
  id: string;
}

/**
 * What the live layer knows about a server-side change: the entity type and id, the kind of change,
 * and the parents it also affects (e.g. an agent's asset). Whatever delivers changes (the platform
 * stream today) is reduced to this shape before it reaches the queries, so the delivery can change
 * without touching any query.
 */
export interface EntityChange<TType extends string = string> extends EntityRef<TType> {
  action: EntityChangeAction;
  /** Entities the change also updates (e.g. a new agent changes its asset's agent list). */
  parents?: EntityRef<TType>[];
}

export const entityRefKey = (ref: EntityRef) => `${ref.type}:${ref.id}`;

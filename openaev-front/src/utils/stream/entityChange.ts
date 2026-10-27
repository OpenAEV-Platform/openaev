/**
 * What the live layer knows about a server-side change: the entity type and id, the kind of change,
 * and optionally the permission root the change belongs to (e.g. an agent belongs to its asset).
 *
 * This is deliberately all the information a consumer may rely on. The stream payload carries more
 * today (the full entity), but nothing past the stream adapter reads it, so the backend can slim the
 * event down to this shape without touching any consumer.
 */
export type EntityChangeAction = 'create' | 'update' | 'delete';

export interface EntityRef {
  /** Stream schema name, e.g. 'assets', 'agents', 'tags' (the backend pluralized entity name). */
  type: string;
  id: string;
}

export interface EntityChange extends EntityRef {
  action: EntityChangeAction;
  /** Parent the change also affects, already covered by the server-side permission check. */
  root?: EntityRef;
}

export const entityRefKey = (ref: EntityRef) => `${ref.type}:${ref.id}`;

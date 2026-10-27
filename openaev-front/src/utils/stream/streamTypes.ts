/**
 * Stream schema names the live layer reacts to: the backend's pluralized, lowercased entity name
 * (`attribute_schema`). An entity class inheriting its id (e.g. Endpoint extends Asset) is emitted
 * under the class that declares the id ('assets'). Keep every name the frontend relies on here, so
 * a typo cannot silently match nothing.
 */
export const STREAM_TYPES = {
  AGENTS: 'agents',
  ASSETS: 'assets',
  EXECUTORS: 'executors',
  TAGS: 'tags',
} as const;

export type StreamType = typeof STREAM_TYPES[keyof typeof STREAM_TYPES];

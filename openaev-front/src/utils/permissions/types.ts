import { type CapabilityOutput, type RoleInput } from '../api-types';

/** Scope vocabulary owned by the back end (CapabilityScope), read from the generated types. */
export type CapabilityScope = CapabilityOutput['capability_scopes'][number];

export const ACTIONS = {
  ACCESS: 'ACCESS',
  MANAGE: 'MANAGE',
  LAUNCH: 'LAUNCH',
  DELETE: 'DELETE',
  SEARCH: 'SEARCH',
  CREATE: 'CREATE',
} as const;

export type Actions = typeof ACTIONS[keyof typeof ACTIONS];

type CapabilityName = NonNullable<RoleInput['role_capabilities']>[number];
type SuffixOf<T> = T extends `${Actions}_${infer S}` ? S : never;
type BackendSubject = SuffixOf<CapabilityName>;

export const SUBJECTS = {
  ASSESSMENT: 'ASSESSMENT', // Scenarios, Simulations and Atomic Testings
  TEAMS_AND_PLAYERS: 'TEAMS_AND_PLAYERS',
  ASSETS: 'ASSETS',
  CREDENTIALS: 'CREDENTIALS',
  THREAT_ARSENALS: 'THREAT_ARSENALS',
  DASHBOARDS: 'DASHBOARDS',
  REPORTINGS: 'REPORTINGS',
  FINDINGS: 'FINDINGS',
  DOCUMENTS: 'DOCUMENTS',
  CHANNELS: 'CHANNELS',
  PHISHING: 'PHISHING',
  CHALLENGES: 'CHALLENGES',
  LESSONS_LEARNED: 'LESSONS_LEARNED',
  SECURITY_PLATFORMS: 'SECURITY_PLATFORMS',
  PLATFORM_SETTINGS: 'PLATFORM_SETTINGS',
  TENANT_SETTINGS: 'TENANT_SETTINGS',
  TENANT_USERS_GROUPS_AND_ROLES: 'TENANT_USERS_GROUPS_AND_ROLES',
  RESOURCE: 'RESOURCE',
  TAGS: 'TAGS',
  TENANTS: 'TENANTS',
  PLATFORM_USERS_GROUPS_AND_ROLES: 'PLATFORM_USERS_GROUPS_AND_ROLES',
  SESSIONS: 'SESSIONS',
  PLATFORM_SESSIONS: 'PLATFORM_SESSIONS',
} as const satisfies Record<string, BackendSubject | 'RESOURCE'>;

export type Subjects = typeof SUBJECTS[keyof typeof SUBJECTS];

/**
 * Tooltip for an affordance kept visible but disabled for lack of permission. Reading rights hide
 * a screen; create, update and delete rights only grey out their action.
 */
export const PERMISSION_REQUIRED = 'Permission required';

export const INHERITED_CONTEXT = {
  SCENARIO: 'SCENARIO',
  SIMULATION: 'SIMULATION',
  NONE: 'NONE',
} as const;

export type InheritedContext = typeof INHERITED_CONTEXT[keyof typeof INHERITED_CONTEXT];

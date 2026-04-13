/**
 * TODO multi-tenancy:
 * API prefixes NOT YET migrated to /api/tenants/{tenantId}/… on the backend.
 *
 * As each BE controller is migrated, remove the corresponding prefix(es).
 * Once this list is empty, all tenant-scoped APIs are fully migrated.
 *
 * PR1 — Tags ✅ DONE
 * PR2 — Scenarios & Exercises core
 * PR3 — Injects & Inject lifecycle
 * PR4 — Teams & Players
 * PR5 — Assets
 * PR6 — Components (Channels, Challenges, Payloads, Documents)
 * PR7 — Findings, Expectations & Lessons
 * PR9 — Reference data & Misc
 */
const TENANT_MIGRATION_TODO: string[] = [
  // PR3 — Injects & Inject lifecycle
  '/api/injector_contracts',
  '/api/atomic-testings',
  '/api/inject-expectations-traces',
  // PR4 — Teams & Players
  '/api/teams',
  '/api/players',
  '/api/organizations',
  // PR6 — Components
  '/api/channels',
  '/api/challenges',
  '/api/payloads',
  '/api/documents',
  // PR7 — Findings, Expectations & Lessons
  '/api/findings',
  '/api/detection-remediations',
  '/api/notification-rules',
  '/api/vulnerabilities',
  '/api/lessons_templates',
  // PR9 — Reference data & Misc
  '/api/attack_patterns',
  '/api/kill_chain_phases',
  '/api/domains',
  '/api/mappers',
  '/api/tag-rules',
  '/api/dashboards',
  '/api/custom-dashboards',
  '/api/fulltextsearch',
  '/api/schemas',
  '/api/engine',
  '/api/roles',
  '/api/groups',
  '/api/users',
  '/api/capabilities',
  '/api/xtmhub',
  '/api/xtm-composer',
  '/api/variables',
  '/api/reports',
  '/api/stream',
];

export default TENANT_MIGRATION_TODO;

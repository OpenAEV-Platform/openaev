/**
 * TODO multi-tenancy:
 * API prefixes NOT YET migrated to /api/tenants/{tenantId}/… on the backend.
 *
 * As each BE controller is migrated, remove the corresponding prefix(es).
 * Once this list is empty, all tenant-scoped APIs are fully migrated.
 *
 * PR4 — Teams & Players
 * PR7 — Findings, Expectations & Lessons
 * PR9 — Reference data & Misc
 */
const TENANT_MIGRATION_TODO: string[] = [
  // PR4 — Teams & Players
  '/api/players',
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

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
  // PR9 — Reference data & Misc
  '/api/xtmhub',
];

export default TENANT_MIGRATION_TODO;

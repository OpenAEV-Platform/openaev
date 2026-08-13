import type { HealthCheck } from '../../../../utils/api-types';

/**
 * Scope-definition details that block launching a chained scenario/simulation:
 * an entirely empty scope, or a scope missing the entry kind (technical vs
 * audience) that the workflow's steps rely on. The INEFFECTIVE_* details are
 * advisory only (entries no step consumes) and never block the launch.
 */
const SCOPE_BLOCKING_DETAILS: HealthCheck['detail'][] = [
  'EMPTY',
  'MISSING_TECHNICAL_TARGETS',
  'MISSING_AUDIENCE_TARGETS',
];

/** True when a scope-definition health check blocks launching the chained workflow. */
const isScopeLaunchBlocked = (healthchecks: HealthCheck[]): boolean =>
  healthchecks.some(healthcheck => healthcheck.type === 'SCOPE_DEFINITION' && SCOPE_BLOCKING_DETAILS.includes(healthcheck.detail));

export default isScopeLaunchBlocked;

import { fetchCatalogScenarioInstallPreview, fetchScenarioTargets } from '../../../../actions/catalog/scenario-catalog-actions';
import { searchScenarioHealthcheks } from '../../../../actions/scenarios/scenario-actions';
import { searchScenarioInjectsSimple } from '../../../../actions/scenarios/scenario-inject-actions';
import { type HealthCheck, type InjectOutput, type Scenario } from '../../../../utils/api-types';
import { type MissingPrerequisite } from '../catalog/ScenarioTargetsDrawer';

// Everything that stops a scenario from launching, or makes the run fail, gathered before the launch.
export interface ScenarioLaunchBlockers {
  // Injects running a revoked payload: skipped, and the server refuses the launch (F9, F18).
  revokedInjects: string[];
  // Targetable injects without a target: launch stays disabled until each has one (section 6.3).
  untargetedInjects: string[];
  // What a catalogue scenario needs from the platform (injectors, executors, edition).
  prerequisites: MissingPrerequisite[];
  // The scenario health checks: the run starts, but these injects may fail.
  healthchecks: HealthCheck[];
}

// Target checks are covered by the targets list, which also names the injects.
const TARGET_HEALTHCHECKS = ['TEAMS', 'ASSETS', 'ASSET_GROUPS'];

export const hardBlockerCount = (blockers: ScenarioLaunchBlockers) => (blockers.revokedInjects.length > 0 ? 1 : 0)
  + (blockers.untargetedInjects.length > 0 ? 1 : 0)
  + blockers.prerequisites.length;

const loadScenarioLaunchBlockers = (scenario: Scenario): Promise<ScenarioLaunchBlockers> => {
  // Silent: a background check, the server still refuses a launch with a revoked payload.
  const injects = searchScenarioInjectsSimple(scenario.scenario_id, {
    page: 0,
    size: 1000,
  }, false).then(({ data }: { data: { content: InjectOutput[] } }) => data.content
    .filter(inject => inject.inject_enabled && inject.inject_payload_revoked)
    .map(inject => inject.inject_title))
    .catch(() => [] as string[]);
  const targets = fetchScenarioTargets(scenario.scenario_id)
    .then(({ data }) => data.injects
      .filter(inject => inject.target_fields.length > 0 && !inject.is_targeted)
      .map(inject => inject.inject_title))
    .catch(() => [] as string[]);
  const catalogId = scenario.scenario_catalog_id;
  const version = scenario.scenario_version;
  const prerequisites = catalogId && version
    ? fetchCatalogScenarioInstallPreview(catalogId, version)
        // Declared locally until the API types are regenerated, as in the install drawer.
        .then(({ data }) => (data as { missing_prerequisites?: MissingPrerequisite[] }).missing_prerequisites ?? [])
        .catch(() => [] as MissingPrerequisite[])
    : Promise.resolve([] as MissingPrerequisite[]);
  const healthchecks = searchScenarioHealthcheks(scenario.scenario_id)
    .then(({ data }: { data: HealthCheck[] }) => data.filter(healthcheck => !TARGET_HEALTHCHECKS.includes(healthcheck.type)))
    .catch(() => [] as HealthCheck[]);
  return Promise.all([injects, targets, prerequisites, healthchecks])
    .then(([revokedInjects, untargetedInjects, missingPrerequisites, scenarioHealthchecks]) => ({
      revokedInjects,
      untargetedInjects,
      prerequisites: missingPrerequisites,
      healthchecks: scenarioHealthchecks,
    }));
};

export default loadScenarioLaunchBlockers;

import { fetchCatalogScenarioInstallPreview, fetchScenarioTargets } from '../../../../actions/catalog/scenario-catalog-actions';
import { searchScenarioHealthcheks } from '../../../../actions/scenarios/scenario-actions';
import { searchScenarioInjectsSimple } from '../../../../actions/scenarios/scenario-inject-actions';
import { type HealthCheck, type InjectOutput, type Scenario } from '../../../../utils/api-types';
import { type MissingPrerequisite } from '../catalog/ScenarioTargetsDrawer';

// Everything that stops a scenario from launching, or makes the run fail, gathered before the launch.
// What the check needs: a scenario page passes the scenario, a catalogue card its installed copy.
export type LaunchTarget = Pick<Scenario, 'scenario_id' | 'scenario_name'> & Partial<Pick<Scenario, 'scenario_catalog_id' | 'scenario_version'>>;

export interface RevokedInject {
  id: string;
  title: string;
  // The injector contract of the revoked payload version, to move the inject to another version.
  contractId?: string;
}

export interface ScenarioLaunchBlockers {
  // Injects running a revoked payload: skipped and failed when the simulation runs (F9, F18).
  // The launch is not refused any more: the check warns and offers another version.
  revokedInjects: RevokedInject[];
  // Targetable injects without a target: launch stays disabled until each has one (section 6.3).
  untargetedInjects: string[];
  // What a catalogue scenario needs from the platform (injectors, executors, edition).
  prerequisites: MissingPrerequisite[];
  // The scenario health checks: the run starts, but these injects may fail.
  healthchecks: HealthCheck[];
  // The prerequisites could not be read (the catalogue preview needs the right to create scenarios).
  prerequisitesUnchecked: boolean;
}

// Covered elsewhere in the check: targets by the targets list, a revoked payload ("Revoked action") by its own block.
const COVERED_HEALTHCHECKS = ['TEAMS', 'ASSETS', 'ASSET_GROUPS', 'PAYLOAD'];

// What keeps Launch disabled: injects without target (section 6.3) and what the platform lacks.
export const hardBlockerCount = (blockers: ScenarioLaunchBlockers) => (blockers.untargetedInjects.length > 0 ? 1 : 0)
  + blockers.prerequisites.length;

// What lets the launch start but makes injects fail: a revoked payload (its injects are skipped).
export const failingInjectCount = (blockers: ScenarioLaunchBlockers) => blockers.revokedInjects.length;

const loadScenarioLaunchBlockers = (scenario: LaunchTarget): Promise<ScenarioLaunchBlockers> => {
  // Silent: a background check, the server still refuses a launch with a revoked payload.
  const injects = searchScenarioInjectsSimple(scenario.scenario_id, {
    page: 0,
    size: 1000,
  }, false).then(({ data }: { data: { content: InjectOutput[] } }) => data.content
    .filter(inject => inject.inject_enabled && inject.inject_payload_revoked)
    .map(inject => ({
      id: inject.inject_id,
      title: inject.inject_title,
      contractId: inject.inject_injector_contract?.injector_contract_id,
    })))
    .catch(() => [] as RevokedInject[]);
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
        .catch(() => null)
    : Promise.resolve([] as MissingPrerequisite[]);
  const healthchecks = searchScenarioHealthcheks(scenario.scenario_id)
    .then(({ data }: { data: HealthCheck[] }) => data.filter(healthcheck => !COVERED_HEALTHCHECKS.includes(healthcheck.type)))
    .catch(() => [] as HealthCheck[]);
  return Promise.all([injects, targets, prerequisites, healthchecks])
    .then(([revokedInjects, untargetedInjects, missingPrerequisites, scenarioHealthchecks]) => ({
      revokedInjects,
      untargetedInjects,
      prerequisites: missingPrerequisites ?? [],
      prerequisitesUnchecked: missingPrerequisites === null,
      healthchecks: scenarioHealthchecks,
    }));
};

export default loadScenarioLaunchBlockers;

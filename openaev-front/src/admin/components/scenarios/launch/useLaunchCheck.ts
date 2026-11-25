import { useEffect, useState } from 'react';

import { fetchScenarioLaunchCheck, searchScenarioHealthcheks } from '../../../../actions/scenarios/scenario-actions';
import { type HealthCheck, type RevokedInjectOutput, type ScenarioLaunchCheckOutput } from '../../../../utils/api-types';
import isScopeLaunchBlocked from '../../common/healthchecks/scopeHealthcheck';

// Shown by the launch check in their own block: targets, and injects on a revoked payload.
const COVERED_HEALTHCHECKS: HealthCheck['type'][] = ['TEAMS', 'ASSETS', 'ASSET_GROUPS', 'PAYLOAD'];

export interface LaunchCheck {
  check: ScenarioLaunchCheckOutput;
  untargeted: ScenarioLaunchCheckOutput['launch_check_injects'];
  // The injects on a revoked payload version, by the action that moves them to another version.
  revokedByAction: [string, RevokedInjectOutput[]][];
  healthchecks: HealthCheck[];
  scopeBlocked: boolean;
  // What keeps the launch disabled.
  blocking: number;
  // What lets the launch start but makes injects fail.
  failing: number;
}

const toLaunchCheck = (check: ScenarioLaunchCheckOutput, healthchecks: HealthCheck[]): LaunchCheck => {
  const untargeted = check.launch_check_injects.filter(inject => !inject.inject_targeted);
  const revokedByAction = Array.from(check.launch_check_revoked_injects.reduce((byAction, inject) => byAction.set(
    inject.inject_action_id,
    [...(byAction.get(inject.inject_action_id) ?? []), inject],
  ), new Map<string, RevokedInjectOutput[]>()).entries());
  const shown = healthchecks.filter(healthcheck => !COVERED_HEALTHCHECKS.includes(healthcheck.type));
  const scopeBlocked = isScopeLaunchBlocked(healthchecks);
  const warnings = shown.filter(healthcheck => !isScopeLaunchBlocked([healthcheck]));
  return {
    check,
    untargeted,
    revokedByAction,
    healthchecks: shown,
    scopeBlocked,
    blocking: (untargeted.length > 0 ? 1 : 0) + check.launch_check_missing_requirements.length + (scopeBlocked ? 1 : 0),
    failing: (revokedByAction.length > 0 ? 1 : 0) + warnings.length,
  };
};

// Everything that stops a scenario from launching, or makes its injects fail, read again on demand.
const useLaunchCheck = (scenarioId: string) => {
  const [launchCheck, setLaunchCheck] = useState<LaunchCheck | null>(null);
  const [checking, setChecking] = useState(false);

  const load = () => {
    setChecking(true);
    Promise.all([
      fetchScenarioLaunchCheck(scenarioId),
      searchScenarioHealthcheks(scenarioId).then(({ data }: { data: HealthCheck[] }) => data),
    ])
      .then(([check, healthchecks]) => setLaunchCheck(toLaunchCheck(check, healthchecks)))
      .catch(() => undefined)
      .finally(() => setChecking(false));
  };

  useEffect(load, [scenarioId]);

  return {
    launchCheck,
    checking,
    checkAgain: load,
  };
};

export default useLaunchCheck;

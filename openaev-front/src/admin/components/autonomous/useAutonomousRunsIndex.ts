import { useEffect, useMemo, useState } from 'react';

import { fetchAutonomousRuns } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun } from '../../../actions/autonomous/autonomous-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';

export interface AutonomousRunsIndex {
  /** Look up the autonomous run owning a scenario, or null for a manual (non-AI) scenario. */
  byScenario: (scenarioId: string | null | undefined) => AutonomousRun | null;
  /** Look up the autonomous run driving a simulation, or null for a manual (non-AI) simulation. */
  bySimulation: (simulationId: string | null | undefined) => AutonomousRun | null;
  /** True once the runs have been fetched (or detection was skipped because the tenant is not eligible). */
  resolved: boolean;
}

/**
 * Loads every autonomous run for the tenant once and indexes them by scenario and simulation id, so
 * list views (Scenarios, Simulations) can apply the same delete guard as the detail cockpits without
 * firing one detection request per row. The endpoint is EE- and preview-feature-gated, so we only
 * call it when the preview feature, an EE license and a configured XTM One are all present - a plain
 * Community build never fires an expected-403/404 request.
 */
const useAutonomousRunsIndex = (): AutonomousRunsIndex => {
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  // Autonomy is a launch mode of chained scenarios, so it rides the chaining feature flag - there is
  // no dedicated autonomous flag anymore.
  const featureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const xtmOneReady = settings.platform_xtm_one_configured === true;
  const eligible = featureEnabled && isEnterpriseEdition && xtmOneReady;

  const [runs, setRuns] = useState<AutonomousRun[]>([]);
  const [resolved, setResolved] = useState(false);

  useEffect(() => {
    if (!eligible) {
      setRuns([]);
      setResolved(true);
      return () => {};
    }
    let stale = false;
    setResolved(false);
    fetchAutonomousRuns()
      .then((res) => {
        if (!stale) setRuns(res.data ?? []);
      })
      .catch(() => {
        if (!stale) setRuns([]);
      })
      .finally(() => {
        if (!stale) setResolved(true);
      });
    return () => {
      stale = true;
    };
  }, [eligible]);

  const { scenarioIndex, simulationIndex } = useMemo(() => {
    const scenarios = new Map<string, AutonomousRun>();
    const simulations = new Map<string, AutonomousRun>();
    runs.forEach((run) => {
      if (run.autonomous_run_scenario_id) scenarios.set(run.autonomous_run_scenario_id, run);
      if (run.autonomous_run_simulation_id) simulations.set(run.autonomous_run_simulation_id, run);
    });
    return {
      scenarioIndex: scenarios,
      simulationIndex: simulations,
    };
  }, [runs]);

  return {
    byScenario: scenarioId => (scenarioId ? scenarioIndex.get(scenarioId) ?? null : null),
    bySimulation: simulationId => (simulationId ? simulationIndex.get(simulationId) ?? null : null),
    resolved,
  };
};

export default useAutonomousRunsIndex;

import { useCallback, useEffect, useState } from 'react';

import { fetchAutonomousRunByScenario, fetchAutonomousRunBySimulation } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun } from '../../../actions/autonomous/autonomous-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';

interface AutonomousRunDetection {
  /** The run driving this entity, or null when it is a manual (non-AI) scenario/simulation. */
  run: AutonomousRun | null;
  /** True once detection has finished (so callers don't flash the manual UI before we know). */
  resolved: boolean;
  /** Push a fresher run (status transitions) up from the live reasoning panel / header controls,
   *  so the hero and tab set stay in lockstep with the orchestrator without a second poll loop. */
  setRun: (run: AutonomousRun) => void;
}

/**
 * Shared detection core for both the scenario and simulation cockpits. The lookup endpoints are EE-
 * and preview-feature-gated; we only call them when the preview feature, an EE license and a
 * configured XTM One are all present, so a plain scenario/simulation on a Community build never
 * fires an expected-404/403 request.
 */
const useAutonomousRunDetection = (
  id: string | undefined,
  fetcher: (id: string) => Promise<{ data: AutonomousRun }>,
): AutonomousRunDetection => {
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const featureEnabled = isFeatureEnabled('AUTONOMOUS_ATTACK_PATH');
  const xtmOneReady = settings.platform_xtm_one_configured === true;
  const eligible = featureEnabled && isEnterpriseEdition && xtmOneReady;

  const [run, setRun] = useState<AutonomousRun | null>(null);
  const [resolved, setResolved] = useState(false);

  const pushRun = useCallback((next: AutonomousRun) => setRun(next), []);

  useEffect(() => {
    if (!id || !eligible) {
      setRun(null);
      setResolved(true);
      return () => {};
    }
    let stale = false;
    setResolved(false);
    fetcher(id)
      .then((res) => {
        if (!stale) setRun(res.data);
      })
      .catch(() => {
        if (!stale) setRun(null);
      })
      .finally(() => {
        if (!stale) setResolved(true);
      });
    return () => {
      stale = true;
    };
  }, [id, eligible, fetcher]);

  return {
    run,
    resolved,
    setRun: pushRun,
  };
};

/**
 * Detects whether a simulation is an autonomous (AI-driven) attack-path run, so the simulation
 * detail page can render the AI cockpit (reasoning panel + gated tabs) instead of the manual
 * chaining editor.
 */
const useAutonomousRunForSimulation = (simulationId: string | undefined): AutonomousRunDetection =>
  useAutonomousRunDetection(simulationId, fetchAutonomousRunBySimulation);

/**
 * Scenario-side twin: detects the autonomous run owning a scenario so the scenario detail page can
 * render the same AI cockpit and steer its single underlying simulation.
 */
export const useAutonomousRunForScenario = (scenarioId: string | undefined): AutonomousRunDetection =>
  useAutonomousRunDetection(scenarioId, fetchAutonomousRunByScenario);

export default useAutonomousRunForSimulation;

import { useCallback, useEffect, useRef, useState } from 'react';

import { fetchAutonomousRunByScenario, fetchAutonomousRunBySimulation } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun } from '../../../actions/autonomous/autonomous-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import { isFeatureEnabled } from '../../../utils/utils';

/** How often to re-probe for an autonomous run until we find one (transient error / late creation). */
const DISCOVERY_POLL_MS = 10000;

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
  // Optional authoritative hint from the already-loaded entity: `false` = the entity itself says it
  // is manual (e.g. scenario.scenario_autonomous === false), so we skip the lookup entirely and
  // never touch the network; `true`/`undefined` = autonomous or unknown, so we probe as usual. This
  // is what lets a plain chained scenario avoid the autonomous-run lookup (and its 404) altogether.
  knownAutonomous?: boolean,
): AutonomousRunDetection => {
  const { settings } = useAuth();
  const { isValidated: isEnterpriseEdition } = useEnterpriseEdition();
  const featureEnabled = isFeatureEnabled('AUTONOMOUS_ATTACK_PATH');
  const xtmOneReady = settings.platform_xtm_one_configured === true;
  // A positive "this entity is manual" hint makes the run non-existent by definition, so there is
  // nothing to look up regardless of EE / feature / XTM One state.
  const knownManual = knownAutonomous === false;
  const eligible = featureEnabled && isEnterpriseEdition && xtmOneReady && !knownManual;

  const [run, setRun] = useState<AutonomousRun | null>(null);
  const [resolved, setResolved] = useState(false);
  // A run bumps this to force a re-probe (discovery poll below); it is NOT a status poll.
  const [attempt, setAttempt] = useState(0);
  // Authoritative "this is a manual (non-AI) entity" verdict from a 404. Once set we STOP probing:
  // a manual scenario/simulation never becomes autonomous (an autonomous run provisions its OWN
  // scenario), so re-polling it forever only spams an expected 404 and churns state - which, via the
  // caller's `!resolved` full-page Loader, forced a whole-page remount + refetch every poll. Only a
  // TRANSIENT error (5xx / network) keeps the discovery poll alive.
  const [manual, setManual] = useState(false);
  // Once we have confirmed this entity is autonomous, it STAYS autonomous for the life of the
  // mount. An eligibility blip (XTM One health / settings refresh flipping platform_xtm_one_configured)
  // or a transient lookup error must never collapse a live AI cockpit back into the manual view -
  // that was the "stopped run becomes a normal scenario and loses Restart" bug.
  const detectedRef = useRef(false);
  const prevIdRef = useRef<string | undefined>(undefined);
  // Whether detection has finished at least once for the CURRENT id. A background re-probe (a
  // transient-error retry) must NOT flip `resolved` back to false, otherwise the caller's Loader
  // unmounts and remounts the entire page on every poll.
  const resolvedOnceRef = useRef(false);

  const pushRun = useCallback((next: AutonomousRun) => {
    detectedRef.current = true;
    setManual(false);
    setRun(next);
  }, []);

  useEffect(() => {
    // Fresh entity: reset the per-id detection latches so a new scenario/simulation is probed anew.
    if (prevIdRef.current !== id) {
      prevIdRef.current = id;
      resolvedOnceRef.current = false;
      detectedRef.current = false;
      setManual(false);
    }
    if (!id) {
      setRun(null);
      resolvedOnceRef.current = true;
      setResolved(true);
      return () => {};
    }
    if (!eligible) {
      // Do not erase an already-detected autonomous run just because eligibility briefly flipped.
      if (!detectedRef.current) setRun(null);
      resolvedOnceRef.current = true;
      setResolved(true);
      return () => {};
    }
    let stale = false;
    // Only show the detection loader on the FIRST probe for this id; a background re-probe must
    // never flip `resolved` back to false and flash / remount the whole page.
    if (!resolvedOnceRef.current) setResolved(false);
    fetcher(id)
      .then((res) => {
        if (stale) return;
        detectedRef.current = true;
        setManual(false);
        setRun(res.data);
      })
      .catch((err: unknown) => {
        if (stale) return;
        const status = (err as { response?: { status?: number } })?.response?.status;
        // A 404 is the authoritative "this is a manual (non-AI) entity" signal - only then do we
        // clear AND stop probing. Any other error (403 / 5xx / network / a transient reconcile)
        // must leave a previously-detected run intact so a blip can't strand the manual UI, and
        // keeps the discovery poll alive so a transient first-load failure still recovers.
        if (status === 404) {
          detectedRef.current = false;
          setManual(true);
          setRun(null);
        }
      })
      .finally(() => {
        if (stale) return;
        resolvedOnceRef.current = true;
        setResolved(true);
      });
    return () => {
      stale = true;
    };
  }, [id, eligible, fetcher, attempt]);

  // Keep probing until we discover a run, then stop (the header/panel keep it fresh via setRun).
  // This recovers from a transient first-load failure and picks up a run created while the page is
  // already open, without adding a permanent poll once the cockpit is live. A confirmed-manual
  // entity (404) stops the poll entirely, so a normal chained scenario never re-probes.
  useEffect(() => {
    if (!eligible || !id || run || manual) return () => {};
    const timer = setInterval(() => setAttempt(a => a + 1), DISCOVERY_POLL_MS);
    return () => clearInterval(timer);
  }, [eligible, id, run, manual]);

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
 *
 * <p>Pass the scenario's own {@code scenario_autonomous} flag once the scenario is loaded: when it
 * is {@code false} the scenario is authoritatively manual and the lookup is skipped entirely, so a
 * plain chained scenario never fires the autonomous-run request (nor re-polls it). Leave it
 * {@code undefined} while the scenario is still loading to probe as before.
 */
export const useAutonomousRunForScenario = (
  scenarioId: string | undefined,
  knownAutonomous?: boolean,
): AutonomousRunDetection =>
  useAutonomousRunDetection(scenarioId, fetchAutonomousRunByScenario, knownAutonomous);

export default useAutonomousRunForSimulation;

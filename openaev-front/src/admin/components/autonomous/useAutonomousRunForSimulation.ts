import { useCallback, useEffect, useRef, useState } from 'react';

import { fetchAutonomousRunByScenario, fetchAutonomousRunBySimulation } from '../../../actions/autonomous/autonomous-actions';
import { type AutonomousRun } from '../../../actions/autonomous/autonomous-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';

/** How often to re-probe for an autonomous run until we find one (transient error / late creation). */
export const DISCOVERY_POLL_MS = 10000;
/**
 * Consecutive 404s required before concluding a scenario/simulation is manual (non-AI). A single
 * 404 can be transient right after a page reload - the run row is briefly unqueryable during the
 * post-start reconcile window - and latching to manual on the FIRST miss permanently stranded the
 * live AI cockpit until a full reload (the reported "reload and I lost the AI overview, the run is
 * still going" bug). Requiring a short streak lets a transient miss recover on the next discovery
 * poll, while a genuinely manual entity still stops quickly (bounded - never re-probes forever).
 */
export const MAX_DISCOVERY_NOT_FOUND = 3;
/**
 * Fast re-probe delay used while a DECLARED-autonomous entity (knownAutonomous === true) is
 * pending on a below-threshold 404 streak. Such an entity keeps the caller's Loader up instead of
 * rendering the (affirmatively wrong) manual UI, so the streak must settle in seconds, not in
 * {@link DISCOVERY_POLL_MS} steps: a transient reload miss re-attaches the cockpit after ~one
 * retry, and the rare torn-down-run edge (marker outlives the run row) settles to manual after
 * MAX_DISCOVERY_NOT_FOUND fast probes.
 */
export const DISCOVERY_RETRY_MS = 2000;

/**
 * sessionStorage key prefix for the "this entity was detected autonomous THIS session" sticky hint.
 * Once a run is discovered for an id, that id is remembered for the tab session so a later reload -
 * before the fresh probe resolves - treats it as declared-autonomous even when the loaded
 * scenario/simulation carries a falsy marker (legacy entities provisioned before the durable marker
 * existed read exercise_autonomous=false while still being autonomous). This is what stops those
 * legacy entities from flashing the manual UI across a reload.
 */
const STICKY_AUTONOMOUS_PREFIX = 'openaev.autonomous.detected.';

/** Whether this id was detected autonomous earlier in the tab session (best-effort). */
const readStickyAutonomous = (id: string | undefined): boolean => {
  if (!id) {
    return false;
  }
  try {
    return sessionStorage.getItem(`${STICKY_AUTONOMOUS_PREFIX}${id}`) === '1';
  } catch {
    // sessionStorage unavailable (private mode / disabled): degrade to marker-only behaviour.
    return false;
  }
};

/** Remember that this id is autonomous for the tab session (survives a reload, not a new tab). */
const markStickyAutonomous = (id: string | undefined): void => {
  if (!id) {
    return;
  }
  try {
    sessionStorage.setItem(`${STICKY_AUTONOMOUS_PREFIX}${id}`, '1');
  } catch {
    // Best-effort UX aid only; silently degrade when sessionStorage is unavailable.
  }
};

/** Forget the sticky hint - the run was torn down server-side (operator converted to manual). */
const clearStickyAutonomous = (id: string | undefined): void => {
  if (!id) {
    return;
  }
  try {
    sessionStorage.removeItem(`${STICKY_AUTONOMOUS_PREFIX}${id}`);
  } catch {
    // Best-effort; ignore.
  }
};

interface AutonomousRunDetection {
  /** The run driving this entity, or null when it is a manual (non-AI) scenario/simulation. */
  run: AutonomousRun | null;
  /** True once detection has finished (so callers don't flash the manual UI before we know). */
  resolved: boolean;
  /** Push a fresher run (status transitions) up from the live reasoning panel / header controls,
   *  so the hero and tab set stay in lockstep with the orchestrator without a second poll loop. */
  setRun: (run: AutonomousRun) => void;
  /** Forget the detected run and treat the entity as manual, WITHOUT waiting for the discovery poll
   *  to re-probe. Used when an operator action has just torn the run down server-side (a normal
   *  launch supersedes a settled AI outcome): the by-scenario lookup now 404s, but the latched run
   *  would otherwise keep rendering the stale AI plan outcome until a full page reload. */
  clearRun: () => void;
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
  const xtmOneReady = settings.platform_xtm_one_configured === true;
  // A positive "this entity is manual" hint makes the run non-existent by definition, so there is
  // nothing to look up regardless of EE / feature / XTM One state.
  const knownManual = knownAutonomous === false;
  const eligible = isEnterpriseEdition && xtmOneReady && !knownManual;
  // A positive "this entity IS autonomous" hint (scenario_autonomous / exercise_autonomous). For a
  // declared-autonomous entity the manual UI is affirmatively wrong, so while its run has not been
  // found (and manual has not been latched by a full 404 streak) detection reports UNRESOLVED -
  // the caller keeps its Loader up instead of flashing the manual page for a discovery-poll period
  // after a transient reload miss - and misses re-probe on the fast retry cadence instead of the
  // discovery poll so the pending window stays short.
  // The marker-declared signal (scenario_autonomous / exercise_autonomous). This is the ONLY part of
  // the declared-autonomous signal the probe effect depends on: the marker can arrive AFTER the
  // first probe (the entity document lands later) and its arrival must re-probe, whereas the sticky
  // hint below flips as a SIDE EFFECT of a successful detection and must NEVER re-fire a probe.
  const declaredByMarker = knownAutonomous === true;
  // A session-sticky "this id is autonomous" hint set once a run has been detected. OR-ed into the
  // declared-autonomous signal so a legacy entity (falsy marker but actually autonomous) still keeps
  // the Loader up across a reload and re-probes fast, instead of flashing the manual UI. Read in the
  // probe effect through a ref (never a dependency) so setting it on detection cannot re-probe.
  const [stickyAutonomous, setStickyAutonomous] = useState<boolean>(() => readStickyAutonomous(id));
  const stickyAutonomousRef = useRef(stickyAutonomous);
  stickyAutonomousRef.current = stickyAutonomous;
  const declaredAutonomous = declaredByMarker || stickyAutonomous;

  const [run, setRun] = useState<AutonomousRun | null>(null);
  const [resolved, setResolved] = useState(false);
  // A run bumps this to force a re-probe (discovery poll below); it is NOT a status poll.
  const [attempt, setAttempt] = useState(0);
  // Authoritative "this is a manual (non-AI) entity" verdict from a STREAK of consecutive 404s (see
  // MAX_DISCOVERY_NOT_FOUND). Once set we STOP probing: a manual scenario/simulation never becomes
  // autonomous (an autonomous run provisions its OWN scenario), so re-polling it forever only spams
  // an expected 404 and churns state - which, via the caller's `!resolved` full-page Loader, forced
  // a whole-page remount + refetch every poll. A single transient 404 (5xx / network too) keeps the
  // discovery poll alive so a post-reload miss on a live AI run still recovers.
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
  // Consecutive 404 count for the discovery probe (see MAX_DISCOVERY_NOT_FOUND). Reset on any
  // success and whenever the id changes, so only an UNBROKEN streak of misses concludes "manual".
  const notFoundStreakRef = useRef(0);
  // One-shot fast re-probe (see DISCOVERY_RETRY_MS) scheduled after a below-threshold 404 on a
  // declared-autonomous entity, so its pending window settles in seconds instead of waiting for
  // the next discovery-poll tick. Cleared on every probe teardown (id change / unmount / re-run).
  const fastRetryRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const pushRun = useCallback((next: AutonomousRun) => {
    detectedRef.current = true;
    notFoundStreakRef.current = 0;
    markStickyAutonomous(id);
    setStickyAutonomous(true);
    setManual(false);
    setRun(next);
  }, [id]);

  // Drop the detected run and pin the entity to manual so the discovery poll does NOT immediately
  // re-detect it (the run was just torn down server-side, so a probe would 404 and land on manual
  // anyway - pinning avoids a redundant round-trip and a flash of the stale outcome). A later real
  // run (a fresh AI build / launch) clears this again through pushRun.
  const clearRun = useCallback(() => {
    detectedRef.current = false;
    clearStickyAutonomous(id);
    setStickyAutonomous(false);
    setManual(true);
    setRun(null);
  }, [id]);

  useEffect(() => {
    // Fresh entity: reset the per-id detection latches so a new scenario/simulation is probed anew.
    if (prevIdRef.current !== id) {
      prevIdRef.current = id;
      resolvedOnceRef.current = false;
      detectedRef.current = false;
      notFoundStreakRef.current = 0;
      setManual(false);
      // Re-read the new id's session sticky hint so a reload of a legacy autonomous entity keeps
      // the Loader up instead of flashing manual before the fresh probe resolves.
      setStickyAutonomous(readStickyAutonomous(id));
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
        notFoundStreakRef.current = 0;
        markStickyAutonomous(id);
        setStickyAutonomous(true);
        setManual(false);
        setRun(res.data);
      })
      .catch((err: unknown) => {
        if (stale) return;
        const status = (err as { response?: { status?: number } })?.response?.status;
        // A 404 is the "this is a manual (non-AI) entity" signal - but a SINGLE 404 can be a
        // transient post-reload reconcile race on an entity that IS autonomous, so only conclude
        // manual (clear AND stop probing) after a streak of consecutive 404s. Until the streak is
        // reached we leave any previously-detected run intact and let the discovery poll retry, so
        // a reload no longer strands a live AI cockpit on one transient miss.
        if (status === 404) {
          notFoundStreakRef.current += 1;
          if (notFoundStreakRef.current >= MAX_DISCOVERY_NOT_FOUND) {
            detectedRef.current = false;
            setManual(true);
            setRun(null);
          } else if (declaredByMarker || stickyAutonomousRef.current) {
            // A declared-autonomous entity is PENDING (Loader up) while its streak settles, so
            // re-probe on the fast cadence instead of leaving the operator on a spinner for a
            // whole discovery-poll period.
            if (fastRetryRef.current) clearTimeout(fastRetryRef.current);
            fastRetryRef.current = setTimeout(() => setAttempt(a => a + 1), DISCOVERY_RETRY_MS);
          }
        } else {
          // Any other error (403 / 5xx / network / a transient reconcile) must leave a previously-
          // detected run intact so a blip can't strand the UI, and keeps the discovery poll alive
          // so a transient first-load failure still recovers. Reset the not-found streak so a mix
          // of transient errors never accumulates into a false manual verdict.
          notFoundStreakRef.current = 0;
        }
      })
      .finally(() => {
        if (stale) return;
        resolvedOnceRef.current = true;
        setResolved(true);
      });
    return () => {
      stale = true;
      if (fastRetryRef.current) {
        clearTimeout(fastRetryRef.current);
        fastRetryRef.current = null;
      }
    };
    // declaredByMarker is a dependency on purpose: the MARKER often arrives AFTER the first probe
    // (the scenario/simulation document lands later), and its arrival must re-probe immediately -
    // the miss that just resolved may have been the transient reload race on a live AI run. The
    // STICKY hint is deliberately NOT a dependency (read via ref above): it flips as a side effect
    // of a successful detection, and depending on it would fire a spurious extra probe right after
    // every detection.
  }, [id, eligible, fetcher, attempt, declaredByMarker]);

  // Keep probing until we discover a run, then stop (the header/panel keep it fresh via setRun).
  // This recovers from a transient first-load failure and picks up a run created while the page is
  // already open, without adding a permanent poll once the cockpit is live. A confirmed-manual
  // entity (404) stops the poll entirely, so a normal chained scenario never re-probes.
  useEffect(() => {
    // A sticky-autonomous entity keeps re-probing even after a manual latch, so a transient blip
    // that exhausted the not-found streak self-heals on the discovery cadence instead of stranding
    // the live cockpit on the manual UI until a full reload. A genuinely manual entity (never
    // sticky) still stops on the latch, so a normal chained scenario never re-probes.
    if (!eligible || !id || run || (manual && !stickyAutonomous)) return () => {};
    const timer = setInterval(() => setAttempt(a => a + 1), DISCOVERY_POLL_MS);
    return () => clearInterval(timer);
  }, [eligible, id, run, manual, stickyAutonomous]);

  // A DECLARED-autonomous entity with neither a run nor a latched manual verdict reports
  // unresolved even after a probe answered: rendering the manual page for it would be affirmatively
  // wrong, and this is what turned a single transient post-reload 404 into "the AI cockpit dropped
  // to the manual scenario for a whole discovery-poll period". Derived synchronously (not via
  // effects) so the manual UI cannot even flash for one frame; bounded because a full 404 streak
  // latches `manual` (torn-down-run edge) and misses re-probe on the fast retry cadence.
  const pendingDeclaredAutonomous = Boolean(id) && eligible && declaredAutonomous && !run && !manual;
  return {
    run,
    resolved: resolved && !pendingDeclaredAutonomous,
    setRun: pushRun,
    clearRun,
  };
};

/**
 * Detects whether a simulation is an autonomous (AI-driven) attack-path run, so the simulation
 * detail page can render the AI cockpit (reasoning panel + gated tabs) instead of the manual
 * chaining editor.
 *
 * <p>Pass {@code true} for {@code knownAutonomous} when the loaded simulation carries the durable
 * {@code exercise_autonomous} marker: detection then stays pending (Loader) across a transient
 * post-reload 404 instead of flashing the manual editor, and misses re-probe on the fast retry
 * cadence. Leave it {@code undefined} otherwise - a {@code false} marker must NOT be forwarded as
 * a manual hint, since simulations provisioned before the marker existed carry {@code false} while
 * still being autonomous.
 */
const useAutonomousRunForSimulation = (
  simulationId: string | undefined,
  knownAutonomous?: boolean,
): AutonomousRunDetection =>
  useAutonomousRunDetection(simulationId, fetchAutonomousRunBySimulation, knownAutonomous);

/**
 * Scenario-side twin: detects the CURRENT autonomous run of a scenario so the scenario detail page
 * can render the same AI cockpit and steer that run's simulation. A scenario carries at most one
 * live run at a time (older runs are superseded, their finished simulations kept as history), so
 * this resolves to the current/last run - never assuming a scenario owns exactly one simulation.
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

/**
 * Persistent / Resolved / New-Regressed findings classification for Scenario
 * reports (spec section 1, "Scenario Report").
 *
 * ALGORITHM (pseudo-code):
 *
 *   runs = allRunsOf(scenario) within comparisonWindow, ordered oldest -> newest
 *   latestRun = last(runs)
 *   previousRuns = runs[0 .. runs.length - 2]
 *
 *   for each techniqueId ever seen across `runs`:
 *     passInLatest   = techniqueId passed (prevented/detected) in latestRun
 *     everFailedBefore = techniqueId failed in at least one of previousRuns
 *     failedInEveryRun = techniqueId failed in latestRun AND in every previousRun
 *
 *     if failedInEveryRun:
 *         -> PERSISTENT   (never resolved within the window)
 *     else if everFailedBefore AND passInLatest:
 *         -> RESOLVED     (used to fail, now passes: improvement)
 *     else if NOT everFailedBefore AND NOT passInLatest AND techniqueId in latestRun:
 *         -> NEW_OR_REGRESSED  (didn't fail before / wasn't tested before, now fails)
 *     else:
 *         -> no change (either always passing, or not present in latest run)
 *
 * This module provides a real (non-mocked) implementation of that
 * classification, operating on a simple `RunResult[]` shape so it can later
 * be fed with actual per-run pass/fail data pulled from the existing
 * inject-expectation endpoints, instead of the mock generator in this
 * prototype.
 */

export type PassFail = 'pass' | 'fail';

export interface TechniqueRunResult {
  techniqueId: string;
  status: PassFail;
}

export interface RunResult {
  runId: string;
  runLabel: string;
  date: string; // ISO date, used to order runs chronologically
  results: TechniqueRunResult[];
}

export interface ClassifiedFinding {
  techniqueId: string;
  bucket: 'persistent' | 'resolved' | 'new_or_regressed';
}

/**
 * Classifies every technique observed across `runs` (already filtered to the
 * comparison window and sorted or not - this function sorts them) into
 * persistent / resolved / new_or_regressed, comparing the latest run against
 * every earlier run in the window.
 */
export const classifyFindings = (runs: RunResult[]): ClassifiedFinding[] => {
  if (runs.length === 0) return [];

  const orderedRuns = [...runs].sort((a, b) => a.date.localeCompare(b.date));
  const latestRun = orderedRuns[orderedRuns.length - 1];
  const previousRuns = orderedRuns.slice(0, -1);

  const statusOf = (run: RunResult, techniqueId: string): PassFail | undefined => run.results.find(r => r.techniqueId === techniqueId)?.status;

  const allTechniqueIds = new Set<string>();
  orderedRuns.forEach(run => run.results.forEach(r => allTechniqueIds.add(r.techniqueId)));

  const classified: ClassifiedFinding[] = [];

  allTechniqueIds.forEach((techniqueId) => {
    const latestStatus = statusOf(latestRun, techniqueId);
    const presentInLatest = latestStatus !== undefined;

    const previousStatuses = previousRuns
      .map(run => statusOf(run, techniqueId))
      .filter((s): s is PassFail => s !== undefined);

    const everFailedBefore = previousStatuses.some(s => s === 'fail');
    const failedInEveryRunIncludingLatest = latestStatus === 'fail' && previousStatuses.length > 0 && previousStatuses.every(s => s === 'fail');

    if (failedInEveryRunIncludingLatest) {
      classified.push({
        techniqueId,
        bucket: 'persistent',
      });
    } else if (everFailedBefore && latestStatus === 'pass') {
      classified.push({
        techniqueId,
        bucket: 'resolved',
      });
    } else if (!everFailedBefore && presentInLatest && latestStatus === 'fail') {
      // Either genuinely new (not tested before) or a regression (passed
      // before, fails now) - both are framed the same way to the user:
      // "this wasn't a known gap before, and now it is".
      classified.push({
        techniqueId,
        bucket: 'new_or_regressed',
      });
    }
    // else: no bucket change (always passing, or absent from the latest run).
  });

  return classified;
};

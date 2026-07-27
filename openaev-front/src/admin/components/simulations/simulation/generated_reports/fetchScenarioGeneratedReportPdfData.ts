import { exerciseInjectsResultOutput, fetchExerciseInjectExpectationResults } from '../../../../../actions/exercises/exercise-action';
import { searchScenarioExercises } from '../../../../../actions/scenarios/scenario-actions';
import { type ExerciseSimple, type InjectExpectationResultsByAttackPattern, type InjectResultOutput, type Scenario } from '../../../../../utils/api-types';
import { fetchAttackPatternNames } from './attackPatternNames';
import { type ComparisonWindow, type ComparisonWindowInput, windowStartDate } from './comparisonWindow';
import { successRateOf } from './fetchGeneratedReportPdfData';
import { type ClassifiedFinding, classifyFindings, type RunResult } from './findingsAggregation';
import { type InjectGroup } from './technicalVariantAdapters';

/** Same fan-out safety bound used by the Global report fetcher. */
const MAX_ATTACK_DETAIL_RUNS = 15;

/** @deprecated use `ComparisonWindow` from `./comparisonWindow` - kept as an alias so existing imports keep working. */
export type ScenarioComparisonWindow = ComparisonWindow;

/** @deprecated use `ComparisonWindowInput` from `./comparisonWindow` - kept as an alias so existing imports keep working. */
export type ScenarioComparisonWindowInput = ComparisonWindowInput;

export interface ScenarioRunSummary {
  exerciseId: string;
  exerciseName: string;
  date?: string;
  score: number;
}

export interface ScenarioFinding {
  techniqueId: string;
  /** Human-readable technique name (resolved via the attack-pattern options endpoint) - used
   *  as the Executive report's finding label so no raw MITRE id ever leaks into that variant. */
  name: string;
  label: string;
  severity: 'critical' | 'high' | 'medium' | 'low';
  /** Exact historical pass/fail counts per run, for the Technical report's full-depth listing. */
  passCountInWindow: number;
  failCountInWindow: number;
}

/** One row of the Technical report's mandatory "previous run -> current run" per-technique
 *  comparison table (attack-based, one line per technique, no aggregation). */
export interface TechniqueRunComparison {
  techniqueId: string;
  name: string;
  previousStatus: 'pass' | 'fail' | 'n/a';
  currentStatus: 'pass' | 'fail';
  severity: ScenarioFinding['severity'];
}

/** Aggregate per-technique coverage, feeding the Executive report's coverage donut/heatmap
 *  (no per-inject/technical drill-down - aggregate only, per the content spec). */
export interface AttackCoverageRow {
  techniqueId: string;
  name: string;
  passRate: number;
}

export interface ScenarioGeneratedReportPdfData {
  scenario: Scenario;
  window: ScenarioComparisonWindowInput;
  runs: ScenarioRunSummary[];
  currentScore: number;
  trendDirection: 'up' | 'down' | 'flat';
  persistentFindings: ScenarioFinding[];
  resolvedFindings: ScenarioFinding[];
  newOrRegressedFindings: ScenarioFinding[];
  /** Per-run technique pass/fail, exposed for the Control/Timeline Technical variants. */
  runResults: RunResult[];
  /** Mandatory Technical-report comparison: previous run status -> current run status, one row
   *  per technique (attack-based, full itemized list - not just an aggregate trend). */
  techniqueRunComparison: TechniqueRunComparison[];
  /** Aggregate per-technique coverage for the Executive report's coverage donut/heatmap. */
  attackCoverage: AttackCoverageRow[];
  /** Bounded per-inject data across runs in window, feeding the Control-centric variant. */
  sampleInjects: InjectResultOutput[];
  /** Same bounded sample, grouped per run and ordered LATEST run first - feeds the
   *  Control-centric variant's full itemized detail table and the Timeline-centric variant's
   *  per-run blocks (whose "latest run first" ordering requirement this satisfies directly). */
  sampleInjectsByRun: InjectGroup[];
  generatedAt: string;
}

const severityFor = (failRatio: number): ScenarioFinding['severity'] => {
  if (failRatio >= 0.75) return 'critical';
  if (failRatio >= 0.5) return 'high';
  if (failRatio >= 0.25) return 'medium';
  return 'low';
};

/**
 * Gathers every simulation run of a scenario within the requested comparison
 * window, reusing the existing `/scenarios/{id}/exercises/search` endpoint
 * (same one used for the per-simulation report's "comparison" trend) plus
 * the per-exercise ATT&CK breakdown endpoint, then classifies findings into
 * persistent / resolved / new-or-regressed using the real (non-mocked)
 * `classifyFindings` diffing logic already built for the Report Lab
 * prototype (`reports_lab/model/findingsAggregation.ts`).
 */
const fetchScenarioGeneratedReportPdfData = async (
  scenario: Scenario,
  windowInput: ScenarioComparisonWindowInput,
): Promise<ScenarioGeneratedReportPdfData> => {
  const scenarioExercisesRes = await searchScenarioExercises(scenario.scenario_id, {
    page: 0,
    size: 50,
    sorts: [{
      property: 'exercise_start_date',
      direction: 'desc',
    }],
  });
  const allExercises: ExerciseSimple[] = scenarioExercisesRes?.data?.content ?? [];

  const floor = windowStartDate(windowInput);
  let inWindow = allExercises
    .filter(e => !!e.exercise_start_date)
    .filter(e => (floor ? new Date(e.exercise_start_date!) >= floor : true));
  if (windowInput.window === 'CUSTOM' && windowInput.endDate) {
    const ceiling = new Date(windowInput.endDate);
    inWindow = inWindow.filter(e => new Date(e.exercise_start_date!) <= ceiling);
  }
  if (windowInput.window === 'LAST_RUN') {
    inWindow = allExercises.slice(0, 2);
  }
  // Oldest -> newest, so the last element is always the latest run.
  inWindow = [...inWindow].sort((a, b) => (a.exercise_start_date ?? '').localeCompare(b.exercise_start_date ?? ''));

  // Per-run ATT&CK technique pass/fail, to feed the persistent/resolved/new-regressed
  // diff AND the per-run score (moved up so `runs` can reuse this data instead of the
  // scenario-exercises-search endpoint's always-empty `exercise_global_score`, which
  // used to make every run's score show as 0%).
  const attackResultsPerRun = await Promise.all(
    inWindow.map(e => fetchExerciseInjectExpectationResults(e.exercise_id)
      .then(res => (res?.data ?? []) as InjectExpectationResultsByAttackPattern[])
      .catch(() => [] as InjectExpectationResultsByAttackPattern[])),
  );

  // Real per-run score: flatten every (inject × technique) PREVENTION/DETECTION
  // result for that run and compute the weighted success rate, rather than relying
  // on the lightweight scenario-exercises-search endpoint (which never computes
  // `exercise_global_score` for performance reasons and always returns `[]`).
  const runs: ScenarioRunSummary[] = inWindow.map((e, index) => {
    const patterns = attackResultsPerRun[index] ?? [];
    const flattenedResults = patterns.flatMap(pattern => (pattern.inject_expectation_results ?? []).flatMap(r => r.results ?? []));
    return {
      exerciseId: e.exercise_id,
      exerciseName: e.exercise_name,
      date: e.exercise_start_date,
      score: successRateOf(flattenedResults),
    };
  });

  const currentScore = runs.length > 0 ? runs[runs.length - 1].score : 0;
  const previousScore = runs.length > 1 ? runs[runs.length - 2].score : currentScore;
  let trendDirection: ScenarioGeneratedReportPdfData['trendDirection'] = 'flat';
  if (currentScore > previousScore) trendDirection = 'up';
  else if (currentScore < previousScore) trendDirection = 'down';

  // Bounded per-inject detail across runs in window, feeding the Control-centric
  // Technical variant (same fan-out safety bound as the Global report fetcher).
  const injectDetailRuns = inWindow.slice(-MAX_ATTACK_DETAIL_RUNS);
  const sampleInjectsPerRun = await Promise.all(
    injectDetailRuns.map(e => exerciseInjectsResultOutput(e.exercise_id)
      .then(res => (res?.data ?? []) as InjectResultOutput[])
      .catch(() => [] as InjectResultOutput[])),
  );
  const sampleInjects = sampleInjectsPerRun.flat();

  // `injectDetailRuns` is the tail-end (most recent) slice of `inWindow`, which is sorted
  // oldest -> newest, so its index offset within `attackResultsPerRun` (computed over the full
  // `inWindow`) must be shifted by that same amount. `.reverse()` then flips the grouped result
  // to latest-first, matching the Timeline-centric variant's "latest run first" requirement.
  const injectDetailRunsOffset = inWindow.length - injectDetailRuns.length;
  const sampleInjectsByRun: InjectGroup[] = injectDetailRuns.map((exercise, index) => ({
    exerciseId: exercise.exercise_id,
    exerciseName: exercise.exercise_name,
    date: exercise.exercise_start_date,
    injects: sampleInjectsPerRun[index] ?? [],
    attackPatternResults: attackResultsPerRun[injectDetailRunsOffset + index] ?? [],
  })).reverse();

  const passFailCounts = new Map<string, {
    pass: number;
    fail: number;
  }>();
  const runResults: RunResult[] = inWindow.map((exercise, index) => {
    const patterns = attackResultsPerRun[index] ?? [];
    return {
      runId: exercise.exercise_id,
      runLabel: exercise.exercise_name,
      date: exercise.exercise_start_date ?? new Date().toISOString(),
      results: patterns.map((pattern) => {
        const rate = successRateOf((pattern.inject_expectation_results ?? []).flatMap(r => r.results ?? []));
        const status: 'pass' | 'fail' = rate >= 50 ? 'pass' : 'fail';
        const key = pattern.inject_attack_pattern ?? 'N/A';
        const counts = passFailCounts.get(key) ?? {
          pass: 0,
          fail: 0,
        };
        if (status === 'pass') {
          counts.pass += 1;
        } else {
          counts.fail += 1;
        }
        passFailCounts.set(key, counts);
        return {
          techniqueId: key,
          status,
        };
      }),
    };
  });

  const classified: ClassifiedFinding[] = classifyFindings(runResults);

  // Resolve human-readable names for every technique id involved, so the Executive
  // report can show a business-friendly finding name (no raw MITRE id) while the
  // Technical report keeps the id alongside the name for the mandatory MITRE mapping.
  const allTechniqueIds = [...passFailCounts.keys()];
  const nameById = await fetchAttackPatternNames(allTechniqueIds);
  const nameFor = (techniqueId: string): string => nameById[techniqueId]?.name ?? techniqueId;

  const toFinding = (finding: ClassifiedFinding): ScenarioFinding => {
    const counts = passFailCounts.get(finding.techniqueId) ?? {
      pass: 0,
      fail: 0,
    };
    const total = counts.pass + counts.fail;
    return {
      techniqueId: finding.techniqueId,
      name: nameFor(finding.techniqueId),
      label: nameFor(finding.techniqueId),
      severity: severityFor(total > 0 ? counts.fail / total : 0),
      passCountInWindow: counts.pass,
      failCountInWindow: counts.fail,
    };
  };

  // Mandatory Technical-report comparison: previous run status -> current run status,
  // one row per technique (full itemized list, e.g. "T1566.001: Prevented (last run) ->
  // Not Prevented (this run)").
  const previousRun = runResults.length > 1 ? runResults[runResults.length - 2] : undefined;
  const currentRun = runResults.length > 0 ? runResults[runResults.length - 1] : undefined;
  const techniqueRunComparison: TechniqueRunComparison[] = (currentRun?.results ?? []).map((current) => {
    const previous = previousRun?.results.find(r => r.techniqueId === current.techniqueId);
    const counts = passFailCounts.get(current.techniqueId) ?? {
      pass: 0,
      fail: 0,
    };
    const total = counts.pass + counts.fail;
    return {
      techniqueId: current.techniqueId,
      name: nameFor(current.techniqueId),
      previousStatus: previous?.status ?? 'n/a',
      currentStatus: current.status,
      severity: severityFor(total > 0 ? counts.fail / total : 0),
    };
  });

  // Aggregate per-technique coverage (pass rate), for the Executive report's
  // coverage donut/heatmap - aggregate only, no technical drill-down.
  const attackCoverage: AttackCoverageRow[] = allTechniqueIds.map((techniqueId) => {
    const counts = passFailCounts.get(techniqueId) ?? {
      pass: 0,
      fail: 0,
    };
    const total = counts.pass + counts.fail;
    return {
      techniqueId,
      name: nameFor(techniqueId),
      passRate: total > 0 ? Math.round((counts.pass / total) * 100) : 0,
    };
  });

  return {
    scenario,
    window: windowInput,
    runs,
    currentScore,
    trendDirection,
    persistentFindings: classified.filter(f => f.bucket === 'persistent').map(toFinding),
    resolvedFindings: classified.filter(f => f.bucket === 'resolved').map(toFinding),
    newOrRegressedFindings: classified.filter(f => f.bucket === 'new_or_regressed').map(toFinding),
    runResults,
    techniqueRunComparison,
    attackCoverage,
    sampleInjects,
    sampleInjectsByRun,
    generatedAt: new Date().toISOString(),
  };
};

export default fetchScenarioGeneratedReportPdfData;

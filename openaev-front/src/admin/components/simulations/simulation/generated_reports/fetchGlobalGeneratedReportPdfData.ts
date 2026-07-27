import { searchExercises } from '../../../../../actions/Exercise';
import { exerciseInjectsResultOutput, fetchExerciseInjectExpectationResults } from '../../../../../actions/exercises/exercise-action';
import { type ExerciseSimple, type InjectExpectationResultsByAttackPattern, type InjectResultOutput } from '../../../../../utils/api-types';
import { type ComparisonWindowInput, windowStartDate } from './comparisonWindow';
import { successRateOf } from './fetchGeneratedReportPdfData';
import { type InjectGroup } from './technicalVariantAdapters';

export interface GlobalExerciseSummary {
  exerciseId: string;
  exerciseName: string;
  startDate?: string;
  status?: string;
  successRate: number;
  detectionRate: number;
  preventionRate: number;
}

export interface GlobalAttackPatternAggregate {
  pattern: string;
  successRate: number;
  injectCount: number;
}

export interface GlobalGeneratedReportPdfData {
  exercises: GlobalExerciseSummary[];
  totalSimulations: number;
  overallSuccessRate: number;
  overallDetectionRate: number;
  overallPreventionRate: number;
  attackPatternAggregate: GlobalAttackPatternAggregate[];
  attackCoverageSampleSize: number;
  generatedAt: string;
  /** The comparison window used to scope this report, mirroring the Scenario report's `window` field. */
  window: ComparisonWindowInput;
  /** Bounded (most recent simulations) inject-level detail, for the Control-centric and Timeline-centric Technical variants. */
  sampleInjects: InjectResultOutput[];
  /** Same bounded sample, grouped per simulation (latest-first, matching `exercisesRes`'
   *  descending sort) with its own attack-pattern results - feeds the Control-centric variant's
   *  full itemized detail table and the Timeline-centric variant's per-run blocks. */
  sampleInjectsByExercise: InjectGroup[];
}

// Every simulation is listed in the summary table (lightweight, single
// paginated call, no N+1). The ATT&CK aggregate needs one extra call per
// simulation (results-by-attack-patterns), so it is bounded to the most
// recent simulations to avoid an unbounded fan-out across a platform's
// entire history.
const MAX_EXERCISES = 200;
const MAX_ATTACK_DETAIL_EXERCISES = 15;

const average = (values: number[]): number => (values.length === 0 ? 0 : Math.round(values.reduce((sum, v) => sum + v, 0) / values.length));

/**
 * Gathers the data needed for the "global" (all-simulations) Executive &
 * Technical PDF reports, reusing existing endpoints/actions only:
 * - `/api/exercises/search` (same list endpoint as the Simulations page),
 *   whose `ExerciseSimple` items already carry a precomputed
 *   `exercise_global_score` (no per-simulation extra call needed for the
 *   summary table / overall KPIs).
 * - `/api/exercises/{id}/injects/results-by-attack-patterns` (same endpoint
 *   used by the per-simulation report), called for a bounded, most-recent
 *   subset of simulations to build a cross-simulation ATT&CK coverage
 *   aggregate without an unbounded N+1 fan-out.
 *
 * `windowInput` scopes the report to a comparison window (Last run/1 week/1
 * month/Custom), the same control used by the Scenario report, applied here
 * across every simulation platform-wide instead of a single scenario's runs.
 * `LAST_RUN` keeps only the 2 most recent simulations platform-wide (mirrors
 * the Scenario report's "vs previous run" comparison). Omitting `windowInput`
 * keeps the previous unscoped (all-time) behavior.
 */
const fetchGlobalGeneratedReportPdfData = async (windowInput?: ComparisonWindowInput): Promise<GlobalGeneratedReportPdfData> => {
  const exercisesRes = await searchExercises({
    page: 0,
    size: MAX_EXERCISES,
    sorts: [{
      property: 'exercise_start_date',
      direction: 'DESC',
    }],
  });
  const fetchedExercises: ExerciseSimple[] = exercisesRes?.data?.content ?? [];

  let allExercises = fetchedExercises;
  if (windowInput) {
    const floor = windowStartDate(windowInput);
    allExercises = fetchedExercises
      .filter(e => !!e.exercise_start_date)
      .filter(e => (floor ? new Date(e.exercise_start_date!) >= floor : true));
    if (windowInput.window === 'CUSTOM' && windowInput.endDate) {
      const ceiling = new Date(windowInput.endDate);
      allExercises = allExercises.filter(e => new Date(e.exercise_start_date!) <= ceiling);
    }
    if (windowInput.window === 'LAST_RUN') {
      allExercises = fetchedExercises.slice(0, 2);
    }
  }

  const exercises: GlobalExerciseSummary[] = allExercises.map((exercise) => {
    const scores = exercise.exercise_global_score ?? [];
    const detection = scores.filter(r => r.type === 'DETECTION');
    const prevention = scores.filter(r => r.type === 'PREVENTION');
    return {
      exerciseId: exercise.exercise_id,
      exerciseName: exercise.exercise_name,
      startDate: exercise.exercise_start_date,
      status: exercise.exercise_status,
      successRate: successRateOf(scores),
      detectionRate: successRateOf(detection),
      preventionRate: successRateOf(prevention),
    };
  });

  const overallSuccessRate = average(exercises.map(e => e.successRate));
  const overallDetectionRate = average(exercises.map(e => e.detectionRate));
  const overallPreventionRate = average(exercises.map(e => e.preventionRate));

  const attackDetailExercises = allExercises.slice(0, MAX_ATTACK_DETAIL_EXERCISES);
  const attackResultsPerExercise = await Promise.all(
    attackDetailExercises.map(exercise => fetchExerciseInjectExpectationResults(exercise.exercise_id)
      .then(res => (res?.data ?? []) as InjectExpectationResultsByAttackPattern[])
      .catch(() => [] as InjectExpectationResultsByAttackPattern[])),
  );

  const sampleInjectsPerExercise = await Promise.all(
    attackDetailExercises.map(exercise => exerciseInjectsResultOutput(exercise.exercise_id)
      .then(res => (res?.data ?? []) as InjectResultOutput[])
      .catch(() => [] as InjectResultOutput[])),
  );
  const sampleInjects = sampleInjectsPerExercise.flat();

  // `attackDetailExercises` is already latest-first (`exercisesRes` is sorted DESC by
  // `exercise_start_date`), so this grouping is ready to feed the Timeline-centric variant's
  // "latest run first" per-run blocks without any extra sort/reverse step.
  const sampleInjectsByExercise: InjectGroup[] = attackDetailExercises.map((exercise, index) => ({
    exerciseId: exercise.exercise_id,
    exerciseName: exercise.exercise_name,
    date: exercise.exercise_start_date,
    injects: sampleInjectsPerExercise[index] ?? [],
    attackPatternResults: attackResultsPerExercise[index] ?? [],
  }));

  const patternAggregateMap = new Map<string, {
    rates: number[];
    injectCount: number;
  }>();
  attackResultsPerExercise.flat().forEach((pattern) => {
    const key = pattern.inject_attack_pattern ?? 'N/A';
    const results = pattern.inject_expectation_results ?? [];
    const rate = successRateOf(results.flatMap(r => r.results ?? []));
    const current = patternAggregateMap.get(key) ?? {
      rates: [],
      injectCount: 0,
    };
    current.rates.push(rate);
    current.injectCount += results.length;
    patternAggregateMap.set(key, current);
  });
  const attackPatternAggregate: GlobalAttackPatternAggregate[] = Array.from(patternAggregateMap.entries())
    .map(([pattern, value]) => ({
      pattern,
      successRate: average(value.rates),
      injectCount: value.injectCount,
    }))
    .sort((a, b) => a.successRate - b.successRate);

  return {
    exercises,
    totalSimulations: exercises.length,
    overallSuccessRate,
    overallDetectionRate,
    overallPreventionRate,
    attackPatternAggregate,
    attackCoverageSampleSize: attackDetailExercises.length,
    generatedAt: new Date().toISOString(),
    window: windowInput ?? { window: 'LAST_MONTH' },
    sampleInjects,
    sampleInjectsByExercise,
  };
};

export default fetchGlobalGeneratedReportPdfData;

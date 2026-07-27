import {
  exerciseInjectsResultOutput,
  fetchExerciseExpectationResult,
  fetchExerciseInjectExpectationResults,
  fetchExercisesGlobalScores,
} from '../../../../../actions/exercises/exercise-action';
import { searchScenarioExercises } from '../../../../../actions/scenarios/scenario-actions';
import { simpleCall } from '../../../../../utils/Action';
import {
  type Exercise,
  type ExerciseSimple,
  type ExpectationResultsByType,
  type InjectExpectationResultsByAttackPattern,
  type InjectResultOutput,
  type Team,
} from '../../../../../utils/api-types';

export interface ComparisonPoint {
  exerciseId: string;
  exerciseName: string;
  startDate?: string;
  successRate: number;
}

export interface GeneratedReportPdfData {
  exercise: Exercise;
  teams: Team[];
  injects: InjectResultOutput[];
  expectationResults: ExpectationResultsByType[];
  attackPatternResults: InjectExpectationResultsByAttackPattern[];
  comparison: ComparisonPoint[];
}

/**
 * Computes a real percentage score from the *granular* per-outcome
 * distribution (SUCCESS/PARTIAL/FAILED counts, e.g. "2 Prevented out of 8"),
 * not just the coarse `avgResult` category. Using `avgResult` alone (one
 * label per type row) only ever produced 0%/50%/100% - it collapsed every
 * exercise's actual pass/fail spread down to a handful of flat values,
 * which is why every simulation used to look identical / stuck at 0%.
 * SUCCESS counts full credit, PARTIAL half credit (matching the backend's
 * own 1.0/0.5/0.0 normalization in `InjectExpectationResultUtils`),
 * PENDING/UNKNOWN entries are excluded from the denominator.
 */
const successRateOf = (results: ExpectationResultsByType[]): number => {
  let weightedSuccess = 0;
  let total = 0;
  results.forEach((result) => {
    (result.distribution ?? []).forEach((bucket) => {
      if (bucket.id === 'PENDING' || bucket.id === 'UNKNOWN') return;
      total += bucket.value;
      if (bucket.id === 'SUCCESS') weightedSuccess += bucket.value;
      else if (bucket.id === 'PARTIAL') weightedSuccess += bucket.value * 0.5;
    });
  });
  if (total === 0) return 0;
  return Math.round((weightedSuccess / total) * 100);
};

/**
 * Gathers every piece of data the Executive & Technical PDF builders need,
 * reusing the existing simulation REST endpoints/actions (no duplicate data
 * service is created): exercise metadata, teams, per-inject result output,
 * aggregated expectation results, per-ATT&CK-pattern breakdown (already
 * computed server-side by `/injects/results-by-attack-patterns`), and a
 * cross-simulation comparison/trend built from the existing
 * `/scenarios/{id}/exercises/search` endpoint for the sibling exercise list,
 * plus a single batched `/api/exercises/global-scores` call to resolve their
 * real scores. `ExerciseSimple.exercise_global_score` from the search
 * endpoint above is always `[]` by design (`exercisesWithEmptyGlobalScore`,
 * a performance optimization for the simulations-under-scenario list view),
 * so it cannot be used directly - the batched global-scores endpoint is the
 * correct/real source (one extra call, no N+1 per sibling exercise).
 */
const fetchGeneratedReportPdfData = async (exerciseId: Exercise['exercise_id']): Promise<GeneratedReportPdfData> => {
  const [exerciseRes, teamsRes, injectsRes, expectationResultsRes, attackPatternResultsRes] = await Promise.all([
    simpleCall(`/api/exercises/${exerciseId}`),
    simpleCall(`/api/exercises/${exerciseId}/teams`),
    exerciseInjectsResultOutput(exerciseId),
    fetchExerciseExpectationResult(exerciseId),
    fetchExerciseInjectExpectationResults(exerciseId),
  ]);

  const exercise: Exercise = exerciseRes?.data;
  const teams: Team[] = teamsRes?.data ?? [];

  let comparison: ComparisonPoint[] = [];
  if (exercise?.exercise_scenario) {
    try {
      const scenarioExercisesRes = await searchScenarioExercises(exercise.exercise_scenario, {
        page: 0,
        size: 6,
        sorts: [{
          property: 'exercise_start_date',
          direction: 'desc',
        }],
      });
      const scenarioExercises: ExerciseSimple[] = scenarioExercisesRes?.data?.content ?? [];
      const globalScoresRes = scenarioExercises.length > 0
        ? await fetchExercisesGlobalScores({ exercise_ids: scenarioExercises.map(e => e.exercise_id) })
        : undefined;
      const scoresByExerciseId: Record<string, ExpectationResultsByType[]> = globalScoresRes?.data?.global_scores_by_exercise_ids ?? {};
      comparison = scenarioExercises
        .map(e => ({
          exerciseId: e.exercise_id,
          exerciseName: e.exercise_name,
          startDate: e.exercise_start_date,
          successRate: successRateOf(scoresByExerciseId[e.exercise_id] ?? []),
        }))
        .sort((a, b) => (a.startDate ?? '').localeCompare(b.startDate ?? ''));
    } catch {
      // Comparison/trend is a best-effort enhancement: never fail report
      // generation because sibling scenario exercises couldn't be resolved.
      comparison = [];
    }
  }

  return {
    exercise,
    teams,
    injects: injectsRes?.data ?? [],
    expectationResults: expectationResultsRes?.data ?? [],
    attackPatternResults: attackPatternResultsRes?.data ?? [],
    comparison,
  };
};

export default fetchGeneratedReportPdfData;
export { successRateOf };

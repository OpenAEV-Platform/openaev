import { type Article, type Exercise, type Scenario } from '../../utils/api-types';

export interface ArticlesHelper {
  getArticlesMap: () => Record<string, Article>;
  getExerciseArticles: (exerciseId: Exercise['exercise_id']) => Article[];
  getScenarioArticles: (scenarioId: Scenario['scenario_id']) => Article[];
}

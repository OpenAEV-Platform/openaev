import { type Exercise, type ExerciseSimple, type InjectExpectation, type LessonsAnswer, type LessonsCategory, type LessonsQuestion, type Objective, type Team } from '../../utils/api-types';

export interface ExercisesHelper {
  isExercise: (exerciseId: string) => boolean;
  getExercise: (exerciseId: string) => Exercise;
  getExercises: () => ExerciseSimple[];
  getExercisesMap: () => Record<string, Exercise>;
  getExerciseTeams: (exerciseId: string) => Team[];
  getExerciseInjectExpectations: (exerciseId: Exercise['exercise_id']) => InjectExpectation[];
  getExerciseObjectives: (exerciseId: string) => Objective[];
  getExerciseLessonsCategories: (exerciseId: string) => LessonsCategory[];
  getExerciseLessonsQuestions: (exerciseId: string) => LessonsQuestion[];
  getExerciseLessonsAnswers: (exerciseId: string) => LessonsAnswer[];
  getExerciseUserLessonsAnswers: (exerciseId: string, userId: string) => LessonsAnswer[];
}

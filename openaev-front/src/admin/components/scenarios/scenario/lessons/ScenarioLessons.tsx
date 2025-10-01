import { useMemo } from 'react';
import { useParams } from 'react-router';

import { addScenarioEvaluation, fetchScenarioEvaluations, updateScenarioEvaluation } from '../../../../../actions/Evaluation';
import { type ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { type UserHelper } from '../../../../../actions/helper';
import { type InjectHelper } from '../../../../../actions/injects/inject-helper';
import { type LessonsTemplatesHelper } from '../../../../../actions/lessons/lesson-helper';
import { addScenarioObjective, deleteScenarioObjective, fetchScenarioObjectives, updateScenarioObjective } from '../../../../../actions/Objective';
import {
  addLessonsCategory,
  addLessonsQuestion,
  applyLessonsTemplate,
  deleteLessonsCategory,
  deleteLessonsQuestion,
  emptyLessonsCategories,
  fetchLessonsCategories,
  fetchLessonsQuestions,
  fetchScenarioTeams,
  updateLessonsCategory,
  updateLessonsCategoryTeams,
  updateLessonsQuestion,
  updateScenarioLessons,
} from '../../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { fetchTeams } from '../../../../../actions/teams/team-actions';
import { type TeamsHelper } from '../../../../../actions/teams/team-helper';
import { useHelper } from '../../../../../store';
import {
  type EvaluationInput,
  type LessonsCategoryCreateInput,
  type LessonsCategoryTeamsInput,
  type LessonsCategoryUpdateInput,
  type LessonsQuestionCreateInput,
  type LessonsQuestionUpdateInput,
  type ObjectiveInput, type Scenario,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useScenarioPermissions from '../../../../../utils/permissions/useScenarioPermissions';
import { LessonContext, type LessonContextType } from '../../../common/Context';
import Lessons from '../../../lessons/scenarios/Lessons';

const ScenarioLessons = () => {
  const dispatch = useAppDispatch();

  // Fetching data
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const processToGenericSource = (scenario: Scenario) => {
    return {
      id: scenario.scenario_id,
      type: 'scenario',
      name: scenario.scenario_name,
      lessons_anonymized: scenario.scenario_lessons_anonymized ?? false,
    };
  };

  const {
    scenario,
    objectives,
    teams,
    teamsMap,
    lessonsCategories,
    lessonsQuestions,
    lessonsTemplates,
  } = useHelper((helper: ExercisesHelper & InjectHelper & LessonsTemplatesHelper & ScenariosHelper & TeamsHelper & UserHelper) => {
    const scenarioData = helper.getScenario(scenarioId);
    return {
      scenario: scenarioData,
      objectives: helper.getScenarioObjectives(scenarioId),
      lessonsCategories: helper.getScenarioLessonsCategories(scenarioId),
      lessonsQuestions: helper.getScenarioLessonsQuestions(scenarioId),
      lessonsTemplates: helper.getLessonsTemplates(),
      teamsMap: helper.getTeamsMap(),
      teams: helper.getScenarioTeams(scenarioId),
    };
  });
  useDataLoader(() => {
    dispatch(fetchTeams());
    dispatch(fetchLessonsCategories(scenarioId));
    dispatch(fetchLessonsQuestions(scenarioId));
    dispatch(fetchScenarioObjectives(scenarioId));
    dispatch(fetchScenarioTeams(scenarioId));
  });

  const source = useMemo(
    () => processToGenericSource(scenario),
    [scenario],
  );

  const permissions = useScenarioPermissions(scenarioId);

  const context: LessonContextType = {
    onApplyLessonsTemplate: (data: string) => dispatch(applyLessonsTemplate(scenarioId, data)),
    onEmptyLessonsCategories: () => dispatch(emptyLessonsCategories(scenarioId)),
    onUpdateSourceLessons: (data: boolean) => dispatch(updateScenarioLessons(scenarioId, { lessons_anonymized: data })),
    // Categories
    onAddLessonsCategory: (data: LessonsCategoryCreateInput) => dispatch(addLessonsCategory(scenarioId, data)),
    onDeleteLessonsCategory: (data: string) => dispatch(deleteLessonsCategory(scenarioId, data)),
    onUpdateLessonsCategory: (lessonCategoryId: string, data: LessonsCategoryUpdateInput) => dispatch(updateLessonsCategory(scenarioId, lessonCategoryId, data)),
    onUpdateLessonsCategoryTeams: (lessonCategoryId: string, data: LessonsCategoryTeamsInput) => dispatch(updateLessonsCategoryTeams(scenarioId, lessonCategoryId, data)),
    // Questions
    onDeleteLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string) => dispatch(
      deleteLessonsQuestion(
        scenarioId,
        lessonsCategoryId,
        lessonsQuestionId,
      ),
    ),
    onUpdateLessonsQuestion: (lessonsCategoryId: string, lessonsQuestionId: string, data: LessonsQuestionUpdateInput) => dispatch(
      updateLessonsQuestion(
        scenarioId,
        lessonsCategoryId,
        lessonsQuestionId,
        data,
      ),
    ),
    onAddLessonsQuestion: (lessonsCategoryId: string, data: LessonsQuestionCreateInput) => dispatch(addLessonsQuestion(scenarioId, lessonsCategoryId, data)),
    // Objectives
    onAddObjective: (data: ObjectiveInput) => dispatch(addScenarioObjective(scenarioId, data)),
    onUpdateObjective: (objectiveId: string, data: ObjectiveInput) => dispatch(updateScenarioObjective(scenarioId, objectiveId, data)),
    onDeleteObjective: (objectiveId: string) => dispatch(deleteScenarioObjective(scenarioId, objectiveId)),
    // Evaluation
    onAddEvaluation: (objectiveId: string, data: EvaluationInput) => dispatch(addScenarioEvaluation(scenarioId, objectiveId, data)),
    onUpdateEvaluation: (objectiveId: string, evaluationId: string, data: EvaluationInput) => dispatch(updateScenarioEvaluation(objectiveId, evaluationId, data)),
    onFetchEvaluation: (objectiveId: string) => dispatch(fetchScenarioEvaluations(scenarioId, objectiveId)),
  };

  return (
    <LessonContext.Provider value={context}>
      <Lessons
        source={{
          ...source,
          isReadOnly: permissions.readOnly,
          isUpdatable: permissions.canManage,
        }}
        objectives={objectives}
        teamsMap={teamsMap}
        teams={teams}
        lessonsCategories={lessonsCategories}
        lessonsQuestions={lessonsQuestions}
        lessonsTemplates={lessonsTemplates}
      >
      </Lessons>
    </LessonContext.Provider>
  );
};

export default ScenarioLessons;

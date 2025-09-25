import { useContext } from 'react';
import { useParams } from 'react-router';

import { updateExercise } from '../../../../../actions/Exercise';
import {
  attackPathsBySimulation,
  countBySimulation,
  entitiesBySimulation, fetchCustomDashboardFromSimulation, seriesBySimulation, widgetToEntitiesBySimulation,
} from '../../../../../actions/exercises/exercise-action';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import { useHelper } from '../../../../../store';
import {
  type CustomDashboard,
  type Exercise,
  type WidgetToEntitiesInput,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { AbilityContext, Can } from '../../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import type { ParameterOption } from '../../../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardWrapper from '../../../workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from '../../../workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from '../../../workspaces/custom_dashboards/SelectDashboardButton';
import { ALL_TIME_TIME_RANGE } from '../../../workspaces/custom_dashboards/widgets/configuration/common/TimeRangeUtils';

const SimulationAnalysis = () => {
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const exercise = useHelper((helper: ExercisesHelper) => {
    return helper.getExercise(exerciseId);
  });

  const handleSelectNewDashboard = (dashboardId: string) => {
    dispatch(updateExercise(exercise.exercise_id, {
      ...exercise,
      exercise_custom_dashboard: dashboardId,
    }));
  };

  const paramsBuilder = (dashboardParameters: CustomDashboard['custom_dashboard_parameters']) => {
    const params: Record<string, ParameterOption> = {};
    dashboardParameters?.forEach((p) => {
      let value = '';
      let hidden = false;
      if ('simulation' === p.custom_dashboards_parameter_type) {
        value = exerciseId;
        hidden = true;
      } else if ('scenario' === p.custom_dashboards_parameter_type) {
        value = exercise.exercise_scenario ?? '';
        hidden = true;
      } else if ('timeRange' === p.custom_dashboards_parameter_type) {
        value = ALL_TIME_TIME_RANGE;
        hidden = true;
      } else if (['startDate', 'endDate'].includes(p.custom_dashboards_parameter_type)) {
        hidden = true;
      } else {
        value = p.custom_dashboards_parameter_id;
      }
      params[p.custom_dashboards_parameter_id] = {
        value,
        hidden,
      };
    });
    return params;
  };

  const configuration = {
    customDashboardId: exercise?.exercise_custom_dashboard,
    paramLocalStorageKey: 'custom-dashboard-simulation-' + exerciseId,
    paramsBuilder,
    parentContextId: exerciseId,
    canChooseDashboard: ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, exerciseId),
    handleSelectNewDashboard,
    fetchCustomDashboard: () => fetchCustomDashboardFromSimulation(exerciseId),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => countBySimulation(exerciseId, widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => seriesBySimulation(exerciseId, widgetId, params),
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => widgetToEntitiesBySimulation(exerciseId, widgetId, input),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>) => entitiesBySimulation(exerciseId, widgetId, params),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => attackPathsBySimulation(exerciseId, widgetId, params),
  };

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      noDashboardSlot={(
        <NoDashboardComponent
          actionComponent={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.RESOURCE} field={exerciseId}>
              <SelectDashboardButton variant="text" scenarioOrSimulationId={exerciseId} handleApplyChange={handleSelectNewDashboard} />
            </Can>
          )}
        />
      )}
    />
  );
};

export default SimulationAnalysis;

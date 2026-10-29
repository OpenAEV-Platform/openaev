import { useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router';

import { updateExercise } from '../../../../../actions/Exercise';
import {
  attackPathsBySimulation, averageBySimulation,
  countBySimulation,
  entitiesBySimulation, fetchCustomDashboardFromSimulation, seriesBySimulation, widgetToEntitiesBySimulation,
} from '../../../../../actions/exercises/exercise-action';
import type { ExercisesHelper } from '../../../../../actions/exercises/exercise-helper';
import type { LoggedHelper } from '../../../../../actions/helper';
import { useHelper } from '../../../../../store';
import {
  type CustomDashboard,
  type Exercise, type Pagination,
  type TenantSettingsOutput,
  type WidgetToEntitiesInput,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { Can, useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import type { ParameterOption } from '../../../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardWrapper from '../../../workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from '../../../workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from '../../../workspaces/custom_dashboards/SelectDashboardButton';
import { ALL_TIME_TIME_RANGE } from '../../../workspaces/custom_dashboards/widgets/configuration/common/TimeRangeUtils';

const SimulationAnalysis = () => {
  const dispatch = useAppDispatch();
  const ability = useAbility();
  const { exerciseId } = useParams() as { exerciseId: Exercise['exercise_id'] };

  const { exercise, tenantSettings }: {
    exercise: Exercise;
    tenantSettings: TenantSettingsOutput;
  } = useHelper((helper: ExercisesHelper & LoggedHelper) => ({
    exercise: helper.getExercise(exerciseId),
    tenantSettings: helper.getTenantSettings(),
  }));

  const exerciseRef = useRef(exercise);
  exerciseRef.current = exercise;

  const handleSelectNewDashboard = useCallback((dashboardId: string) => {
    const current = exerciseRef.current;
    if (!current) {
      return;
    }
    dispatch(updateExercise(current.exercise_id, {
      ...current,
      exercise_custom_dashboard: dashboardId,
    }));
  }, [dispatch]);

  const paramsBuilder = useCallback((dashboardParameters: CustomDashboard['custom_dashboard_parameters']) => {
    const params: Record<string, ParameterOption> = {};
    dashboardParameters?.forEach((p) => {
      let value = '';
      let hidden = false;
      if ('simulation' === p.custom_dashboards_parameter_type) {
        value = exerciseId;
        hidden = true;
      } else if ('scenario' === p.custom_dashboards_parameter_type) {
        value = exercise?.exercise_scenario ?? '';
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
  }, [exerciseId, exercise?.exercise_scenario]);

  // The Statistics tab always has a dashboard to show out of the box: the one
  // attached to the simulation, or the tenant "Default simulation dashboard"
  // from the settings (the backend applies the same fallback when resolving it).
  const effectiveDashboardId = exercise?.exercise_custom_dashboard
    || tenantSettings?.platform_simulation_dashboard
    || undefined;

  const configuration = useMemo(() => ({
    customDashboardId: effectiveDashboardId,
    paramLocalStorageKey: 'custom-dashboard-simulation-' + exerciseId,
    resultsSource: {
      source: 'simulation' as const,
      contextId: exerciseId,
    },
    paramsBuilder,
    parentContextId: exerciseId,
    canChooseDashboard: ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, exerciseId),
    handleSelectNewDashboard,
    fetchCustomDashboard: () => fetchCustomDashboardFromSimulation(exerciseId),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => countBySimulation(exerciseId, widgetId, params),
    fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => averageBySimulation(exerciseId, widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => seriesBySimulation(exerciseId, widgetId, params),
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => widgetToEntitiesBySimulation(exerciseId, widgetId, input),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>, pagination?: Pagination) => entitiesBySimulation(exerciseId, widgetId, params, pagination),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => attackPathsBySimulation(exerciseId, widgetId, params),
  }), [effectiveDashboardId, exerciseId, paramsBuilder, ability, handleSelectNewDashboard]);

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

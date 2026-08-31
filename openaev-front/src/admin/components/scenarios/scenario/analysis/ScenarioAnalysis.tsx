import { useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router';

import type { LoggedHelper } from '../../../../../actions/helper';
import {
  attackPathsByScenario, averageByScenario,
  countByScenario,
  entitiesByScenario,
  fetchCustomDashboardFromScenario, searchScenarioExercises,
  seriesByScenario,
  updateScenario, widgetToEntitiesByByScenario,
} from '../../../../../actions/scenarios/scenario-actions';
import type { ScenariosHelper } from '../../../../../actions/scenarios/scenario-helper';
import { SCENARIO_SIMULATIONS } from '../../../../../components/common/queryable/filter/constants';
import { useHelper } from '../../../../../store';
import {
  type CustomDashboard, type Pagination,
  type Scenario,
  type SortField,
  type TenantSettingsOutput,
  type WidgetToEntitiesInput,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { Can, useAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { type ParameterOption } from '../../../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardWrapper from '../../../workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from '../../../workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from '../../../workspaces/custom_dashboards/SelectDashboardButton';

const ScenarioAnalysis = () => {
  const dispatch = useAppDispatch();
  const ability = useAbility();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };

  const { scenario, tenantSettings }: {
    scenario: Scenario;
    tenantSettings: TenantSettingsOutput;
  } = useHelper((helper: ScenariosHelper & LoggedHelper) => ({
    scenario: helper.getScenario(scenarioId),
    tenantSettings: helper.getTenantSettings(),
  }));

  const scenarioRef = useRef(scenario);
  scenarioRef.current = scenario;

  const handleSelectNewDashboard = useCallback((dashboardId: string) => {
    const current = scenarioRef.current;
    if (!current) {
      return;
    }
    dispatch(updateScenario(current.scenario_id, {
      ...current,
      scenario_custom_dashboard: dashboardId,
    }));
  }, [dispatch]);

  const lastSimulationEndedId = useCallback(async () => {
    const { data } = await searchScenarioExercises(scenarioId, {
      size: 1,
      page: 0,
      sorts: [
        {
          property: 'exercise_end_date',
          direction: 'DESC',
          nullHandling: 'NULLS_LAST' as SortField['nullHandling'],
        },
        {
          property: 'exercise_updated_at',
          direction: 'DESC',
        },
      ],
    });
    return data.content?.[0]?.exercise_id;
  }, [scenarioId]);

  const paramsBuilder = useCallback(async (dashboardParameters: CustomDashboard['custom_dashboard_parameters'], localStorageParams: Record<string, ParameterOption>) => {
    const paramsList = await Promise.all(
      (dashboardParameters || []).map(async (p) => {
        const paramId = p.custom_dashboards_parameter_id;
        let paramOptions;
        const value = localStorageParams[paramId]?.value;
        if ('scenario' === p.custom_dashboards_parameter_type) {
          paramOptions = {
            value: scenarioId,
            hidden: true,
          };
        } else if ('simulation' === p.custom_dashboards_parameter_type) {
          const valueToSet = value == undefined ? await lastSimulationEndedId() : value;
          paramOptions = {
            value: valueToSet,
            hidden: false,
            searchOptionsConfig: {
              filterKey: SCENARIO_SIMULATIONS,
              contextId: scenarioId,
            },
          };
        } else {
          paramOptions = {
            value: value,
            hidden: false,
          };
        }
        return [paramId, paramOptions];
      }));

    return Object.fromEntries(paramsList);
  }, [scenarioId, lastSimulationEndedId]);

  // The Statistics tab always has a dashboard to show out of the box: the one
  // attached to the scenario, or the tenant "Default scenario dashboard" from
  // the settings (the backend applies the same fallback when resolving it).
  const effectiveDashboardId = scenario?.scenario_custom_dashboard
    || tenantSettings?.platform_scenario_dashboard
    || undefined;

  const configuration = useMemo(() => ({
    customDashboardId: effectiveDashboardId,
    paramLocalStorageKey: 'custom-dashboard-scenario-' + scenarioId,
    resultsSource: {
      source: 'scenario' as const,
      contextId: scenarioId,
    },
    paramsBuilder,
    parentContextId: scenarioId,
    canChooseDashboard: ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, scenarioId),
    handleSelectNewDashboard,
    fetchCustomDashboard: () => fetchCustomDashboardFromScenario(scenarioId),
    fetchCount: (widgetId: string, params: Record<string, string | undefined>) => countByScenario(scenarioId, widgetId, params),
    fetchAverage: (widgetId: string, params: Record<string, string | undefined>) => averageByScenario(scenarioId, widgetId, params),
    fetchSeries: (widgetId: string, params: Record<string, string | undefined>) => seriesByScenario(scenarioId, widgetId, params),
    fetchEntities: (widgetId: string, params: Record<string, string | undefined>, pagination?: Pagination) => entitiesByScenario(scenarioId, widgetId, params, pagination),
    fetchEntitiesRuntime: (widgetId: string, input: WidgetToEntitiesInput) => widgetToEntitiesByByScenario(scenarioId, widgetId, input),
    fetchAttackPaths: (widgetId: string, params: Record<string, string | undefined>) => attackPathsByScenario(scenarioId, widgetId, params),
  }), [effectiveDashboardId, scenarioId, paramsBuilder, ability, handleSelectNewDashboard]);

  return (
    <CustomDashboardWrapper
      configuration={configuration}
      noDashboardSlot={(
        <NoDashboardComponent
          actionComponent={(
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.RESOURCE} field={scenarioId}>
              <SelectDashboardButton
                variant="text"
                handleApplyChange={handleSelectNewDashboard}
                scenarioOrSimulationId={scenarioId}
              />
            </Can>
          )}
        />
      )}
    />
  );
};

export default ScenarioAnalysis;

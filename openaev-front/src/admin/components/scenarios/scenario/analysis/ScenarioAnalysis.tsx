import { useCallback, useContext, useMemo, useRef } from 'react';
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
import { AbilityContext, Can } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import { type ParameterOption } from '../../../workspaces/custom_dashboards/CustomDashboardContext';
import CustomDashboardWrapper from '../../../workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from '../../../workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from '../../../workspaces/custom_dashboards/SelectDashboardButton';

const ScenarioAnalysis = () => {
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
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
    // Explicit payload: PUT /scenarios/{id} is a full update, so spreading the whole scenario
    // object would silently reset any field the read model does not expose (see #email fields).
    dispatch(updateScenario(current.scenario_id, {
      scenario_name: current.scenario_name,
      scenario_subtitle: current.scenario_subtitle,
      scenario_description: current.scenario_description,
      scenario_category: current.scenario_category,
      scenario_main_focus: current.scenario_main_focus,
      scenario_severity: current.scenario_severity,
      scenario_default_kill_chain: current.scenario_default_kill_chain,
      scenario_external_reference: current.scenario_external_reference,
      scenario_external_url: current.scenario_external_url,
      scenario_tags: current.scenario_tags,
      scenario_message_header: current.scenario_message_header,
      scenario_message_footer: current.scenario_message_footer,
      scenario_mail_from_name: current.scenario_mail_from_name,
      scenario_mails_reply_to: current.scenario_mails_reply_to,
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

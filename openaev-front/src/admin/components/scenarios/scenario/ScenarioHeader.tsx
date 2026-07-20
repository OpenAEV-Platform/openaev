import {
  AutoAwesomeOutlined,
  GroupsOutlined,
  HubOutlined,
  InsertChartOutlined,
  NotificationsOutlined,
  PersonOutlined,
  PlayArrowOutlined,
  RouteOutlined,
  Stop,
  TrackChangesOutlined,
  TuneOutlined,
  UpdateOutlined,
} from '@mui/icons-material';
import { alpha, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type Dispatch, type SetStateAction, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import { createCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import {
  createRunningExerciseFromScenario,
  searchScenarioHealthcheks,
  updateScenario,
  updateScenarioRecurrence,
} from '../../../../actions/scenarios/scenario-actions';
import { type ScenariosHelper } from '../../../../actions/scenarios/scenario-helper';
import { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import { DetailHero, HeroStat } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemSeverity from '../../../../components/ItemSeverity';
import { SIMULATION_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import {
  type CustomDashboard,
  type Exercise,
  type HealthCheck,
  type Scenario,
} from '../../../../utils/api-types';
import { MESSAGING$, useQueryParameter } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Cron } from '../../../../utils/period/Cron';
import handle from '../../../../utils/period/Period';
import { type PeriodExpressionHandler } from '../../../../utils/period/PeriodExpressionHandler';
import useScenarioPermissions from '../../../../utils/permissions/useScenarioPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import HealthcheckIndicator from '../../common/healthchecks/HealthcheckIndicator';
import { type CustomDashboardFormType } from '../../workspaces/custom_dashboards/CustomDashboardForm';
import DashboardCreationDrawer from '../../workspaces/custom_dashboards/DashboardCreationDrawer';
import ScenarioConfiguration from './ScenarioConfiguration';
import ScenarioPopover from './ScenarioPopover';
import ScenarioRecurringFormDialog from './ScenarioRecurringFormDialog';

interface ScenarioHeaderProps {
  cronObject: PeriodExpressionHandler | null;
  setCronObject: Dispatch<SetStateAction<PeriodExpressionHandler | null>>;
  setSelectRecurring: Dispatch<SetStateAction<string>>;
  selectRecurring: string;
  setOpenScenarioRecurringFormDialog: Dispatch<SetStateAction<boolean>>;
  setOpenInstantiateSimulationAndStart: Dispatch<SetStateAction<boolean>>;
  openScenarioRecurringFormDialog: boolean;
  openInstantiateSimulationAndStart: boolean;
  noRepeat: boolean;
  editNotification: boolean;
  setOpenScenarioNotificationRuleDrawer: Dispatch<SetStateAction<boolean>>;
}

const ScenarioHeader = ({
  cronObject,
  setCronObject,
  setSelectRecurring,
  selectRecurring,
  noRepeat,
  openScenarioRecurringFormDialog,
  setOpenScenarioRecurringFormDialog,
  openInstantiateSimulationAndStart,
  setOpenInstantiateSimulationAndStart,
  editNotification,
  setOpenScenarioNotificationRuleDrawer,
}: ScenarioHeaderProps) => {
  // Standard hooks
  const { t, locale, fld } = useFormatter();
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const theme = useTheme();
  const { scenarioId } = useParams() as { scenarioId: Scenario['scenario_id'] };
  const [openScenarioAssistantQueryParam] = useQueryParameter(['openScenarioAssistant']);
  const { canLaunch, canManage } = useScenarioPermissions(scenarioId);

  const [openCreateDashboard, setOpenCreateDashboard] = useState(false);
  const [openConfiguration, setOpenConfiguration] = useState(false);
  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);

  // Preserve the deep link that used to open the assistant drawer: it now
  // routes to the dedicated full-page assistant.
  useEffect(() => {
    if (openScenarioAssistantQueryParam === 'true') {
      navigate(`/admin/scenarios/${scenarioId}/assistant`, { replace: true });
    }
  }, [openScenarioAssistantQueryParam, scenarioId]);
  // Fetching data
  const { scenario }: { scenario: Scenario } = useHelper((helper: ScenariosHelper) => ({ scenario: helper.getScenario(scenarioId) }));

  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const scenarioWorkflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id as string | undefined;
  const isScenarioChaining = isChainingFeatureEnabled && !!scenarioWorkflowId;
  const isScopeMissing = isScenarioChaining
    && healthchecks.some((hc: HealthCheck) => hc.type === ('SCOPE_DEFINITION' as HealthCheck['type']) && hc.detail === 'EMPTY');

  // Local
  const ended = scenario.scenario_recurrence_end && new Date(scenario.scenario_recurrence_end).getTime() < new Date().getTime();
  const isScheduled = !!scenario.scenario_recurrence;

  // Headline stats surfaced right in the hero so they are visible on every tab.
  const injectsCount = scenario.scenario_injects?.length ?? 0;
  const simulationsCount = scenario.scenario_exercises?.length ?? 0;
  const teamsCount = scenario.scenario_teams?.length ?? 0;
  const playersCount = scenario.scenario_all_users_number ?? scenario.scenario_users_number ?? 0;

  useEffect(() => {
    searchScenarioHealthcheks(scenarioId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [scenarioId, scenario]);

  const onSubmit = (cron: Cron, start: string, end?: string) => {
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: cron.toCronExpression(),
      scenario_recurrence_start: start,
      scenario_recurrence_end: end,
    })).then((result: { [x: string]: string }) => {
      if (!Object.prototype.hasOwnProperty.call(result, 'FINAL_FORM/form-error')) {
        setCronObject(cron);
      }
    });
    setOpenScenarioRecurringFormDialog(false);
  };

  useEffect(() => {
    if (scenario.scenario_recurrence != null) {
      const newCron = handle(scenario.scenario_recurrence);
      setCronObject(newCron);
      if (noRepeat) {
        setSelectRecurring('noRepeat');
      } else {
        setSelectRecurring(newCron?.getRecurrenceMagnitude() || 'daily');
      }
    } else {
      setCronObject(null);
    }
  }, [scenario.scenario_recurrence]);

  const stop = () => {
    setCronObject(null);
    dispatch(updateScenarioRecurrence(scenarioId, {
      scenario_recurrence: undefined,
      scenario_recurrence_start: undefined,
      scenario_recurrence_end: undefined,
    }));
  };

  const humanReadableScheduling = () => {
    if (!cronObject?.isValid()) {
      return null;
    }
    let sentence = `${cronObject.toTranslatableStringArray(locale).map(element => t(element)).join(' ')}`;
    if (scenario.scenario_recurrence_end) {
      sentence += ` ${t('recurrence_from')} ${fld(scenario.scenario_recurrence_start)}`;
      sentence += ` ${t('recurrence_to')} ${fld(scenario.scenario_recurrence_end)}`;
    } else {
      sentence += ` ${t('recurrence_starting_from')} ${fld(scenario.scenario_recurrence_start)}`;
    }
    return sentence;
  };

  const hasDashboard = !!scenario.scenario_custom_dashboard;

  // "Analyze" quick action: an already-attached dashboard opens straight away;
  // otherwise we create a fresh one (pre-scoped to this scenario), attach it and
  // jump to the scenario-scoped dashboard view.
  const onDashboardAction = () => {
    if (hasDashboard) {
      navigate(`/admin/scenarios/${scenarioId}/dashboard`);
    } else {
      setOpenCreateDashboard(true);
    }
  };

  const attachDashboard = async (dashboardId: string) => {
    setOpenCreateDashboard(false);
    await dispatch(updateScenario(scenario.scenario_id, {
      ...scenario,
      scenario_custom_dashboard: dashboardId,
    }));
    navigate(`/admin/scenarios/${scenarioId}/dashboard`);
  };

  const onCreateDashboard = async (data: CustomDashboardFormType) => {
    const response = await createCustomDashboard(data);
    const newDashboardId = (response.data as CustomDashboard | undefined)?.custom_dashboard_id;
    if (!newDashboardId) {
      setOpenCreateDashboard(false);
      return;
    }
    await attachDashboard(newDashboardId);
  };

  const scheduleLabel = cronObject?.isValid() ? humanReadableScheduling() : t('Not scheduled');

  // Setup actions consolidated into the single hero overflow menu, so the action
  // bar stays to one AI action + one primary CTA + one kebab instead of a row of
  // loose icons. Each is permission-gated via userRight (ScenarioPopover filters).
  const setupEntries: PopoverEntry[] = canManage
    ? [
        {
          label: hasDashboard ? 'Open dashboard' : 'Create dashboard',
          icon: <InsertChartOutlined fontSize="small" />,
          action: onDashboardAction,
          userRight: true,
        },
        ...(!isScenarioChaining
          ? [{
              label: 'Configuration',
              icon: <TuneOutlined fontSize="small" />,
              action: () => setOpenConfiguration(true),
              userRight: true,
            }]
          : []),
        {
          label: 'Notification rules',
          icon: <NotificationsOutlined fontSize="small" color={editNotification ? 'success' : undefined} />,
          action: () => setOpenScenarioNotificationRuleDrawer(true),
          userRight: true,
        },
        {
          label: 'Scheduling',
          icon: <UpdateOutlined fontSize="small" />,
          action: () => setOpenScenarioRecurringFormDialog(true),
          userRight: true,
        },
      ]
    : [];

  return (
    <>
      <Box sx={{ marginBottom: 2 }}>
        <DetailHero
          icon={RouteOutlined}
          title={truncate(scenario.scenario_name, 80) ?? ''}
          chips={(
            <>
              <ItemSeverity severity={scenario.scenario_severity} label={t(scenario.scenario_severity ?? 'Unknown')} />
              <ItemCategory category={scenario.scenario_category ?? 'Unknown'} label={t(scenario.scenario_category ?? 'Unknown')} />
              <Tooltip title={scheduleLabel ?? ''}>
                <Chip
                  size="small"
                  variant="outlined"
                  label={isScheduled ? t('Scheduled') : t('Not scheduled')}
                  sx={{
                    borderRadius: 1,
                    height: 22,
                    fontSize: 11,
                    color: isScheduled ? theme.palette.success.main : theme.palette.text.disabled,
                    borderColor: isScheduled ? alpha(theme.palette.success.main, 0.4) : theme.palette.divider,
                  }}
                />
              </Tooltip>
            </>
          )}
          action={(
            <>
              {/* Contextual configuration alert - self-hides when healthy. */}
              {canManage && (
                <HealthcheckIndicator healthchecks={healthchecks} scenarioId={scenarioId} />
              )}
              {/* One AI action, kept visible for discoverability. */}
              {canManage && !isScenarioChaining && (
                <Button
                  variant="outlined"
                  size="small"
                  startIcon={<AutoAwesomeOutlined />}
                  sx={{
                    'color': theme.palette.ai.main,
                    'borderColor': alpha(theme.palette.ai.main, 0.5),
                    'backgroundColor': alpha(theme.palette.ai.main, 0.06),
                    '&:hover': {
                      borderColor: theme.palette.ai.main,
                      backgroundColor: alpha(theme.palette.ai.main, 0.12),
                    },
                  }}
                  onClick={() => navigate(`/admin/scenarios/${scenarioId}/assistant`)}
                >
                  {t('Scenario assistant')}
                </Button>
              )}
              {/* The single prominent CTA. */}
              {canLaunch && isScheduled && !ended
                ? (
                    <Button
                      startIcon={<Stop />}
                      variant="outlined"
                      color="inherit"
                      size="small"
                      onClick={stop}
                    >
                      {t('Stop')}
                    </Button>
                  )
                : canLaunch && (
                  <Tooltip title={isScopeMissing ? t('A Chaining Scenario requires a defined scope.') : ''}>
                    <span style={{ display: 'inline-flex' }}>
                      <Button
                        startIcon={<PlayArrowOutlined />}
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={() => setOpenInstantiateSimulationAndStart(true)}
                        disabled={isScopeMissing}
                      >
                        {t('Launch now')}
                      </Button>
                    </span>
                  </Tooltip>
                )}
              {/* Everything else - analyze, setup, and CRUD - in one overflow menu. */}
              <ScenarioPopover
                scenario={scenario}
                actions={isScenarioChaining ? ['Update', 'Delete', 'Export'] : ['Duplicate', 'Update', 'Delete', 'Export']}
                onDelete={() => navigate('/admin/scenarios')}
                leadingEntries={setupEntries}
              />
            </>
          )}
          stats={(
            <>
              <HeroStat
                icon={TrackChangesOutlined}
                label={t('Injects')}
                value={injectsCount}
                color={theme.palette.warning.main}
                to={`/admin/scenarios/${scenarioId}/injects`}
              />
              <HeroStat
                icon={HubOutlined}
                label={t('Simulations')}
                value={simulationsCount}
                color={theme.palette.primary.main}
              />
              <HeroStat
                icon={GroupsOutlined}
                label={t('Teams')}
                value={teamsCount}
                color={theme.palette.secondary.main}
              />
              <HeroStat
                icon={PersonOutlined}
                label={t('Players')}
                value={playersCount}
                color={theme.palette.success.main}
              />
            </>
          )}
        />
      </Box>

      <ScenarioRecurringFormDialog
        cronObject={cronObject}
        setCronObject={setCronObject}
        selectRecurring={selectRecurring}
        onSelectRecurring={setSelectRecurring}
        open={openScenarioRecurringFormDialog}
        setOpen={setOpenScenarioRecurringFormDialog}
        onSubmit={onSubmit}
        initialValues={scenario}
      />
      <Dialog
        open={openInstantiateSimulationAndStart}
        TransitionComponent={Transition}
        onClose={() => setOpenInstantiateSimulationAndStart(false)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('A simulation will be launched based on this scenario and will start immediately. Are you sure you want to proceed?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenInstantiateSimulationAndStart(false)}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            onClick={async () => {
              setOpenInstantiateSimulationAndStart(false);
              const exercise: Exercise = (await createRunningExerciseFromScenario(scenarioId)).data;
              navigate(`${SIMULATION_BASE_URL}/${exercise.exercise_id}`);
              MESSAGING$.notifySuccess(t('New simulation successfully created and started'));
            }}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openConfiguration}
        handleClose={() => setOpenConfiguration(false)}
        title={t('Scenario configuration')}
      >
        <ScenarioConfiguration />
      </Drawer>
      <DashboardCreationDrawer
        open={openCreateDashboard}
        onClose={() => setOpenCreateDashboard(false)}
        defaultName={scenario.scenario_name}
        parameterType="scenario"
        resourceId={scenarioId}
        onSelectExisting={attachDashboard}
        onCreateNew={onCreateDashboard}
      />
    </>
  );
};

export default ScenarioHeader;

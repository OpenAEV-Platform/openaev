import {
  CancelOutlined,
  GroupsOutlined,
  InsertChartOutlined,
  PauseOutlined,
  PersonOutlined,
  PlayArrowOutlined,
  PlayCircleOutlineOutlined,
  RestartAltOutlined,
  TrackChangesOutlined,
  TuneOutlined,
  UpdateOutlined,
} from '@mui/icons-material';
import { Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { createCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import { searchExerciseHealthchecks, updateExercise, updateExerciseStatus } from '../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import { DetailHero, HeroStat } from '../../../../components/common/detail/EntityDetailCommon';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import ItemCategory from '../../../../components/ItemCategory';
import ItemSeverity from '../../../../components/ItemSeverity';
import { useHelper } from '../../../../store';
import { type CustomDashboard, type Exercise, type Exercise as ExerciseType, type HealthCheck, type SimulationDetails } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import HealthcheckIndicator from '../../common/healthchecks/HealthcheckIndicator';
import { type CustomDashboardFormType } from '../../workspaces/custom_dashboards/CustomDashboardForm';
import DashboardCreationDrawer from '../../workspaces/custom_dashboards/DashboardCreationDrawer';
import ExerciseDatePopover from './ExerciseDatePopover';
import ExercisePopover, { type ExerciseActionPopover } from './ExercisePopover';
import ExerciseStatus from './ExerciseStatus';
import SimulationConfiguration from './SimulationConfiguration';

const Buttons = ({ exerciseId, exerciseStatus, exerciseName, onLoading, isLoading, isScopeMissing }: {
  exerciseId: Exercise['exercise_id'];
  exerciseStatus: Exercise['exercise_status'];
  exerciseName: Exercise['exercise_name'];
  onLoading: (loading: boolean) => void;
  isLoading: boolean;
  isScopeMissing: boolean;
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const permissions = useSimulationPermissions(exerciseId);
  const [openChangeStatus, setOpenChangeStatus] = useState<Exercise['exercise_status'] | null>(null);

  const submitUpdateStatus = async (status: { exercise_status: Exercise['exercise_status'] | null }) => {
    setOpenChangeStatus(null);
    onLoading(true);
    try {
      await dispatch(updateExerciseStatus(exerciseId, { exercise_status: status.exercise_status ?? undefined }));
    } finally {
      onLoading(false);
    }
  };
  const executionButton = () => {
    switch (exerciseStatus) {
      case 'SCHEDULED': {
        if (permissions.canLaunch) {
          return (
            <Tooltip
              title={isScopeMissing ? t('A Chaining Simulation requires a defined scope.') : ''}
            >
              <span style={{ display: 'inline-flex' }}>
                <Button
                  startIcon={<PlayArrowOutlined />}
                  variant="contained"
                  size="small"
                  color="primary"
                  onClick={() => setOpenChangeStatus('RUNNING')}
                  disabled={isLoading || isScopeMissing}
                >
                  {t('Start now')}
                </Button>
              </span>
            </Tooltip>
          );
        }
        return (<div />);
      }
      case 'RUNNING': {
        if (permissions.canLaunch) {
          return (
            <Button
              startIcon={<PauseOutlined />}
              variant="outlined"
              color="warning"
              size="small"
              onClick={() => setOpenChangeStatus('PAUSED')}
              disabled={isLoading}
            >
              {t('Pause')}
            </Button>
          );
        }
        return (<div />);
      }
      case 'PAUSED': {
        if (permissions.canLaunch) {
          return (
            <Button
              variant="outlined"
              startIcon={<PlayArrowOutlined />}
              color="success"
              size="small"
              onClick={() => setOpenChangeStatus('RUNNING')}
              disabled={isLoading}
            >
              {t('Resume')}
            </Button>
          );
        }
        return <div />;
      }
      default:
        return <div />;
    }
  };

  const dangerousButton = () => {
    switch (exerciseStatus) {
      case 'RUNNING':
      case 'PAUSED': {
        if (permissions.canLaunch) {
          return (
            <Button
              variant="outlined"
              startIcon={<CancelOutlined />}
              color="error"
              size="small"
              onClick={() => setOpenChangeStatus('CANCELED')}
              disabled={isLoading}
            >
              {t('Stop')}
            </Button>
          );
        }
        return <div />;
      }
      case 'FINISHED':
      case 'CANCELED': {
        if (permissions.canLaunch) {
          return (
            <Button
              variant="outlined"
              startIcon={<RestartAltOutlined />}
              color="warning"
              size="small"
              onClick={() => setOpenChangeStatus('SCHEDULED')}
              disabled={isLoading}
            >
              {t('Reset')}
            </Button>
          );
        }
        return <div />;
      }
      default:
        return <div />;
    }
  };

  const dialogContentText = () => {
    switch (openChangeStatus) {
      case 'RUNNING':
        return `${exerciseName} ${t('will be started, do you want to continue?')}`;
      case 'PAUSED':
        return `${t('Injects will be paused, do you want to continue?')}`;
      case 'SCHEDULED':
        return `${exerciseName} ${t('data will be reset, do you want to restart?')}`;
      case 'CANCELED':
        return `${exerciseName} ${t('data will be reset, do you want to restart?')}`;
      default:
        return 'Do you want to change the status of this simulation?';
    }
  };
  return (
    <>
      {executionButton()}
      {dangerousButton()}
      <Dialog
        open={Boolean(openChangeStatus)}
        TransitionComponent={Transition}
        onClose={() => setOpenChangeStatus(null)}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {dialogContentText()}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpenChangeStatus(null)}>
            {t('Cancel')}
          </Button>
          <Button
            color="secondary"
            onClick={() => submitUpdateStatus({ exercise_status: openChangeStatus })}
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

const ExerciseHeader = ({ onLoading, isLoading }: {
  onLoading: (loading: boolean) => void;
  isLoading: boolean;
}) => {
  // Standard hooks
  const theme = useTheme();
  const { t, fldt } = useFormatter();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();

  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => {
    return { exercise: helper.getExercise(exerciseId) as SimulationDetails };
  });
  const permissions = useSimulationPermissions(exerciseId, exercise);

  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');
  const exerciseWorkflowId = exercise.exercise_workflow_id as string | undefined;
  const isSimulationChaining = isChainingFeatureEnabled && !!exerciseWorkflowId;

  const { workflowConfiguration } = useHelper(
    (helper: WorkflowConfigurationHelper) => ({
      workflowConfiguration: exerciseWorkflowId
        ? helper.getWorkflowConfiguration(exerciseWorkflowId)
        : undefined,
    }),
  );

  const [healthchecks, setHealthchecks] = useState<HealthCheck[]>([]);
  const [openCreateDashboard, setOpenCreateDashboard] = useState(false);
  const [openConfiguration, setOpenConfiguration] = useState(false);
  const [openDateDialog, setOpenDateDialog] = useState(false);

  const isScopeMissing = isSimulationChaining
    && healthchecks.some((hc: HealthCheck) => hc.type === ('SCOPE_DEFINITION' as HealthCheck['type']) && hc.detail === 'EMPTY');

  useEffect(() => {
    searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
  }, [exerciseId, exercise, workflowConfiguration]);

  const actions: ExerciseActionPopover[] = isSimulationChaining
    ? ['Update', 'Export', 'Delete']
    : ['Update', 'Duplicate', 'Export', 'Delete', 'Access reports'];

  // Headline stats surfaced right in the hero so they are visible on every tab.
  const injectsCount = exercise.exercise_injects?.length ?? 0;
  const teamsCount = exercise.exercise_teams?.length ?? 0;
  const playersCount = exercise.exercise_all_users_number ?? exercise.exercise_users_number ?? 0;
  const hasDashboard = !!exercise.exercise_custom_dashboard;

  // "Analyze" quick action: an already-attached dashboard opens straight away;
  // otherwise create a fresh one (pre-scoped to this simulation), attach it and
  // jump to the simulation-scoped dashboard view.
  const onDashboardAction = () => {
    if (hasDashboard) {
      navigate(`/admin/simulations/${exerciseId}/dashboard`);
    } else {
      setOpenCreateDashboard(true);
    }
  };

  const attachDashboard = async (dashboardId: string) => {
    setOpenCreateDashboard(false);
    await dispatch(updateExercise(exercise.exercise_id, {
      ...exercise,
      exercise_custom_dashboard: dashboardId,
    }));
    navigate(`/admin/simulations/${exerciseId}/dashboard`);
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

  // Setup actions consolidated into the single hero overflow menu, so the action
  // bar stays to the status CTAs + one kebab instead of a row of loose icons.
  const setupEntries: PopoverEntry[] = permissions.canManage
    ? [
        {
          label: hasDashboard ? 'Open dashboard' : 'Create dashboard',
          icon: <InsertChartOutlined fontSize="small" />,
          action: onDashboardAction,
          userRight: true,
        },
        ...(!isSimulationChaining
          ? [{
              label: 'Configuration',
              icon: <TuneOutlined fontSize="small" />,
              action: () => setOpenConfiguration(true),
              userRight: true,
            }]
          : []),
        {
          label: 'Modify the scheduling',
          icon: <UpdateOutlined fontSize="small" />,
          action: () => setOpenDateDialog(true),
          disabled: exercise.exercise_status !== 'SCHEDULED',
          userRight: true,
        },
      ]
    : [];

  return (
    <>
      <Box sx={{ marginBottom: 2 }}>
        <DetailHero
          icon={PlayCircleOutlineOutlined}
          title={truncate(exercise.exercise_name, 80) ?? ''}
          chips={(
            <>
              <ExerciseStatus exerciseStatus={exercise.exercise_status} exerciseStartDate={exercise.exercise_start_date} variant="list" />
              <ItemSeverity severity={exercise.exercise_severity} label={t(exercise.exercise_severity ?? 'Unknown')} />
              {exercise.exercise_category && (
                <ItemCategory category={exercise.exercise_category} label={t(exercise.exercise_category)} />
              )}
              <Chip
                size="small"
                variant="outlined"
                label={exercise.exercise_start_date ? fldt(exercise.exercise_start_date) : t('Manual')}
                sx={{
                  borderRadius: 1,
                  height: 22,
                  fontSize: 11,
                  color: theme.palette.text.secondary,
                  borderColor: theme.palette.divider,
                }}
              />
            </>
          )}
          action={(
            <>
              {/* Contextual configuration alert - self-hides when healthy. */}
              {permissions.canManage && (
                <HealthcheckIndicator healthchecks={healthchecks} exerciseId={exerciseId} />
              )}
              {/* Lifecycle CTAs (start / pause / resume / stop / reset). */}
              <Buttons
                exerciseId={exercise.exercise_id}
                exerciseStatus={exercise.exercise_status}
                exerciseName={exercise.exercise_name}
                onLoading={onLoading}
                isLoading={isLoading}
                isScopeMissing={isScopeMissing}
              />
              {/* Everything else - analyze, setup, scheduling, CRUD - in one menu. */}
              <ExercisePopover
                exercise={exercise}
                actions={actions}
                onDelete={() => navigate('/admin/simulations')}
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
                to={`/admin/simulations/${exerciseId}/injects`}
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

      <Drawer
        open={openConfiguration}
        handleClose={() => setOpenConfiguration(false)}
        title={t('Simulation configuration')}
      >
        <SimulationConfiguration />
      </Drawer>
      <ExerciseDatePopover
        exercise={exercise}
        open={openDateDialog}
        onOpenChange={setOpenDateDialog}
        showTrigger={false}
      />
      <DashboardCreationDrawer
        open={openCreateDashboard}
        onClose={() => setOpenCreateDashboard(false)}
        defaultName={exercise.exercise_name}
        parameterType="simulation"
        resourceId={exerciseId}
        onSelectExisting={attachDashboard}
        onCreateNew={onCreateDashboard}
      />
    </>
  );
};

export default ExerciseHeader;

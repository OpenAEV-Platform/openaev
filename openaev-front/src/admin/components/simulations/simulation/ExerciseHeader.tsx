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
} from '@mui/icons-material';
import { alpha, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Paper, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';

import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { createCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import { searchExerciseHealthchecks, updateExercise, updateExerciseStatus } from '../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import { HeroStat, HeroStats } from '../../../../components/common/detail/EntityDetailCommon';
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
                  sx={{ lineHeight: 'initial' }}
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
              sx={{ lineHeight: 'initial' }}
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
              sx={{ lineHeight: 'initial' }}
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
              sx={{ lineHeight: 'initial' }}
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
              sx={{ lineHeight: 'initial' }}
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
  const accent = theme.palette.primary.main;
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

  return (
    <>
      <Paper
        variant="outlined"
        sx={{
          padding: 2,
          borderRadius: 1,
          marginBottom: 2,
          background: `linear-gradient(135deg, ${alpha(accent, 0.08)}, transparent 60%)`,
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
      >
        {/* Row 1: identity + actions */}
        <Box sx={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 2,
          flexWrap: 'wrap',
        }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            minWidth: 0,
          }}
          >
            <Box sx={{
              width: 52,
              height: 52,
              borderRadius: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0,
              color: accent,
              backgroundColor: alpha(accent, 0.12),
              border: `1px solid ${alpha(accent, 0.3)}`,
            }}
            >
              <PlayCircleOutlineOutlined />
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Tooltip title={exercise.exercise_name}>
                <Typography
                  variant="h1"
                  sx={{
                    margin: 0,
                    whiteSpace: 'nowrap',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                  }}
                >
                  {truncate(exercise.exercise_name, 80)}
                </Typography>
              </Tooltip>
              <Box sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                marginTop: 0.5,
                flexWrap: 'wrap',
              }}
              >
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
              </Box>
            </Box>
          </Box>

          <Box sx={{
            'display': 'flex',
            'alignItems': 'center',
            'gap': 1,
            'flexWrap': 'wrap',
            // Uniform 32px height for every hero action (text buttons - even
            // Tooltip/span-wrapped ones - and the kebab) so the top-right row
            // lines up perfectly. The icon toolbar cluster sizes its own
            // IconButtons below (its border is part of the 32px). Popover/drawer
            // actions render in portals, so they are unaffected.
            '& .MuiButton-root': { height: 32 },
            '& .MuiToggleButton-root': {
              width: 32,
              height: 32,
            },
          }}
          >
            {permissions.canManage && (
              <HealthcheckIndicator healthchecks={healthchecks} exerciseId={exerciseId} />
            )}
            {permissions.canManage && (
              <Button
                variant="outlined"
                color="inherit"
                size="small"
                startIcon={<InsertChartOutlined />}
                sx={{
                  lineHeight: 'initial',
                  borderColor: theme.palette.divider,
                }}
                onClick={onDashboardAction}
              >
                {hasDashboard ? t('Open dashboard') : t('Create dashboard')}
              </Button>
            )}
            <Buttons
              exerciseId={exercise.exercise_id}
              exerciseStatus={exercise.exercise_status}
              exerciseName={exercise.exercise_name}
              onLoading={onLoading}
              isLoading={isLoading}
              isScopeMissing={isScopeMissing}
            />
            {permissions.canManage && (
              <Box sx={{
                'display': 'flex',
                'alignItems': 'center',
                'marginLeft': 0.5,
                'height': 32,
                'borderRadius': 1,
                'border': `1px solid ${theme.palette.divider}`,
                'overflow': 'hidden',
                '& .MuiIconButton-root': {
                  borderRadius: 0,
                  height: 30,
                },
                '& .MuiIconButton-root:not(:first-of-type)': { borderLeft: `1px solid ${theme.palette.divider}` },
              }}
              >
                {!isSimulationChaining && (
                  <Tooltip title={t('Configuration')}>
                    <IconButton size="small" onClick={() => setOpenConfiguration(true)}>
                      <TuneOutlined fontSize="small" color="primary" />
                    </IconButton>
                  </Tooltip>
                )}
                <ExerciseDatePopover exercise={exercise} />
              </Box>
            )}
            <ExercisePopover
              exercise={exercise}
              actions={actions}
              onDelete={() => navigate('/admin/simulations')}
            />
          </Box>
        </Box>

        {/* Row 2: headline stats (custom-dashboard NumberWidget look) */}
        <HeroStats>
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
        </HeroStats>
      </Paper>

      <Drawer
        open={openConfiguration}
        handleClose={() => setOpenConfiguration(false)}
        title={t('Simulation configuration')}
      >
        <SimulationConfiguration />
      </Drawer>
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

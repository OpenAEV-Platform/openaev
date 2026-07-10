import { CancelOutlined, PauseOutlined, PlayArrowOutlined, RestartAltOutlined } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import type { WorkflowConfigurationHelper } from '../../../../actions/chaining/workflow-helper';
import { searchExerciseHealthchecks, updateExerciseStatus } from '../../../../actions/Exercise';
import { type ExercisesHelper } from '../../../../actions/exercises/exercise-helper';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type Exercise, type Exercise as ExerciseType, type HealthCheck } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useSimulationPermissions from '../../../../utils/permissions/useSimulationPermissions';
import { truncate } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import ExercisePopover, { type ExerciseActionPopover } from './ExercisePopover';
import ExerciseStatus from './ExerciseStatus';

const useStyles = makeStyles()(() => ({
  title: {
    float: 'left',
    marginRight: 10,
  },
  actions: {
    margin: '-6px 0 0 0',
    float: 'right',
    display: 'flex',
  },
}));

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
                  style={{
                    marginRight: 10,
                    lineHeight: 'initial',
                  }}
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
              style={{ marginRight: 10 }}
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
              style={{ marginRight: 10 }}
              variant="outlined"
              startIcon={<PlayArrowOutlined />}
              color="success"
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
              style={{ marginRight: 10 }}
              variant="outlined"
              startIcon={<CancelOutlined />}
              color="error"
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
              style={{ marginRight: 10 }}
              variant="outlined"
              startIcon={<RestartAltOutlined />}
              color="warning"
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
  const { classes } = useStyles();
  const navigate = useNavigate();

  const { exerciseId } = useParams() as { exerciseId: ExerciseType['exercise_id'] };
  const { exercise } = useHelper((helper: ExercisesHelper) => {
    return { exercise: helper.getExercise(exerciseId) };
  });

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

  const isScopeMissing = isSimulationChaining
    && healthchecks.some((hc: HealthCheck) => hc.type === ('SCOPE_DEFINITION' as HealthCheck['type']) && hc.detail === 'EMPTY');

  useEffect(() => {
    if (isChainingFeatureEnabled && exerciseWorkflowId) {
      searchExerciseHealthchecks(exerciseId).then((result: { data: HealthCheck[] }) => setHealthchecks(result.data));
    }
  }, [exerciseId, exercise, isChainingFeatureEnabled, workflowConfiguration]);

  const actions: ExerciseActionPopover[] = isSimulationChaining
    ? ['Update', 'Delete', 'Access reports']
    : ['Update', 'Duplicate', 'Export', 'Delete', 'Access reports'];

  return (
    <>
      <Tooltip title={exercise.exercise_name}>
        <Typography variant="h1" gutterBottom={true} classes={{ root: classes.title }}>
          {truncate(exercise.exercise_name, 80)}
        </Typography>
      </Tooltip>
      <div style={{
        float: 'left',
        margin: '3px 10px 0 8px',
        color: theme.palette.text?.disabled,
        borderLeft: `1px solid ${theme.palette.text?.disabled}`,
        height: 20,
      }}
      />
      <ExerciseStatus exerciseStatus={exercise.exercise_status} exerciseStartDate={exercise.exercise_start_date} />
      <div className={classes.actions}>
        <Buttons
          exerciseId={exercise.exercise_id}
          exerciseStatus={exercise.exercise_status}
          exerciseName={exercise.exercise_name}
          onLoading={onLoading}
          isLoading={isLoading}
          isScopeMissing={isScopeMissing}
        />
        <ExercisePopover
          exercise={exercise}
          actions={actions}
          onDelete={() => navigate('/admin/simulations')}
        />
      </div>
      <div className="clearfix" />
    </>
  );
};

export default ExerciseHeader;

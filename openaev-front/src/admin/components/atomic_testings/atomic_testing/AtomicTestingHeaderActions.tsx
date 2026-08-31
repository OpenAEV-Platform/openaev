import { PlayArrowOutlined, SettingsOutlined, Stop, TrackChangesOutlined, UpdateOutlined } from '@mui/icons-material';
import { Alert, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogContentText, FormControlLabel, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchMe } from '../../../../actions/Application';
import {
  dismissAtomicTestingExpectationsDrift,
  fetchAtomicTestingExpectationsDrift,
  fetchInjectResultOverviewOutput,
  launchAtomicTesting,
  realignAtomicTestingExpectations,
  relaunchAtomicTesting,
  updateAtomicTestingRecurrence,
} from '../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../components/i18n';
import type { ExpectationsDriftOutput, InjectResultOverviewOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { type Cron } from '../../../../utils/period/Cron';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ExpectationsDriftIndicator from '../../common/injects/expectations/ExpectationsDriftIndicator';
import SchedulingDialog from '../../common/scheduling/SchedulingDialog';
import EntityReportsPanel from '../../reporting/EntityReportsPanel';
import AtomicTestingPopover from './AtomicTestingPopover';
import AtomicTestingUpdate from './AtomicTestingUpdate';

interface Props {
  injectResultOverview: InjectResultOverviewOutput;
  setInjectResultOverview: (injectResultOverviewOutput: InjectResultOverviewOutput) => void;
}

const AtomicTestingHeaderActions = ({ injectResultOverview, setInjectResultOverview }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const navigate = useNavigate();
  const ability = useAbility();
  const { setEEFeatureDetectedInfo } = useEnterpriseEdition();
  const dispatch = useAppDispatch();
  const hasAbility = ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectResultOverview.inject_id);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectResultOverview.inject_id);

  const [edition, setEdition] = useState(false);
  const [openDialog, setOpenDialog] = useState(false);
  const [openScheduling, setOpenScheduling] = useState(false);
  const [canLaunch, setCanLaunch] = useState(true);
  const [expectationsDrift, setExpectationsDrift] = useState<ExpectationsDriftOutput | null>(null);
  // Relaunching a drifted atomic testing without realigning replays the outdated
  // expectations forever - hence realign is opt-out (checked by default).
  const [realignOnRelaunch, setRealignOnRelaunch] = useState(true);

  // Expectation drift between the injector contract template and the inject
  // content - recomputed when the atomic testing is updated.
  useEffect(() => {
    // A stale response from a previous atomic testing must not overwrite the
    // current one. simpleCall has already notified the user on failure, hence
    // the deliberately empty catch.
    let stale = false;
    fetchAtomicTestingExpectationsDrift(injectResultOverview.inject_id)
      .then((result: { data: ExpectationsDriftOutput }) => {
        if (!stale) setExpectationsDrift(result.data);
      })
      .catch(() => {});
    return () => {
      stale = true;
    };
  }, [injectResultOverview.inject_id, injectResultOverview.inject_updated_at]);

  const onRealignExpectations = async () => {
    await realignAtomicTestingExpectations(injectResultOverview.inject_id);
    const result = await fetchAtomicTestingExpectationsDrift(injectResultOverview.inject_id);
    setExpectationsDrift(result.data);
    await fetchInjectResultOverviewOutput(injectResultOverview.inject_id).then((overview: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverview(overview.data);
    });
  };

  // Dismissal is persisted in database (shared between users); the endpoint
  // returns the refreshed drift report.
  const onDismissExpectations = async (dismissed: boolean) => {
    const result = await dismissAtomicTestingExpectationsDrift(injectResultOverview.inject_id, dismissed);
    setExpectationsDrift(result.data);
  };

  // Recurring scheduling (mirrors scenario scheduling): the backend relaunches
  // the atomic testing on each occurrence via the minutely job.
  const isScheduled = !!injectResultOverview.inject_recurrence;
  const scheduleEnded = !!injectResultOverview.inject_recurrence_end
    && new Date(injectResultOverview.inject_recurrence_end).getTime() < Date.now();

  const onSubmitScheduling = (cron: Cron, start: string, end?: string) => {
    updateAtomicTestingRecurrence(injectResultOverview.inject_id, {
      inject_recurrence: cron.toCronExpression(),
      inject_recurrence_start: start,
      inject_recurrence_end: end,
    }).then((result: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverview(result.data);
    });
    setOpenScheduling(false);
  };

  const stopScheduling = () => {
    updateAtomicTestingRecurrence(injectResultOverview.inject_id, {}).then((result: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverview(result.data);
    });
  };

  // Handlers
  const handleCloseDialog = () => setOpenDialog(false);
  const handleCanLaunch = () => setCanLaunch(true);
  const handleCannotLaunch = () => setCanLaunch(false);
  const handleOpenDialog = () => {
    setRealignOnRelaunch(true);
    setOpenDialog(true);
  };
  const handleOpenEdit = () => setEdition(true);
  const handleCloseEdit = () => setEdition(false);

  const submitLaunch = async () => {
    handleCloseDialog();
    handleCannotLaunch();
    if (injectResultOverview?.inject_id) {
      await launchAtomicTesting(injectResultOverview.inject_id).then((result: { data: InjectResultOverviewOutput }) => {
        setInjectResultOverview(result.data);
      }).catch((error) => {
        // NOTE: The parsing below depends on the current backend error message format.
        // If the backend message for LICENSE_RESTRICTION changes, this logic may need
        // to be updated to match the new format.
        if (error?.message === 'LICENSE_RESTRICTION') {
          const startMessage = 'Some asset will be executed through ';
          const rawMessage = error?.errors?.children?.message?.errors?.[0];
          if (typeof rawMessage === 'string' && rawMessage.startsWith(startMessage)) {
            const executors = rawMessage
              .slice(startMessage.length)
              .split(' and ')
              .join(` ${t('and')} `);
            setEEFeatureDetectedInfo(
              t('some injects will be executed through {executors} agents.', { executors }),
            );
          }
        }
      });
    }
    handleCanLaunch();
  };

  const submitRelaunch = async () => {
    handleCloseDialog();
    handleCannotLaunch();
    try {
      if (injectResultOverview?.inject_id) {
        // Relaunch duplicates the inject content before deleting the old one:
        // realigning first makes the new atomic testing inherit the current
        // threat arsenal expectations instead of carrying the drifted ones over.
        if (canManage && expectationsDrift?.drift_detected && realignOnRelaunch) {
          await realignAtomicTestingExpectations(injectResultOverview.inject_id);
        }
        await relaunchAtomicTesting(injectResultOverview.inject_id).then((result) => {
          dispatch(fetchMe()).then(() => {
            navigate(`/admin/atomic_testings/${result.data.inject_id}`);
          });
        });
      }
    } catch {
      // The API layer already notified the user (simplePostCall rethrows after
      // notifying); abort the relaunch and let them retry.
    } finally {
      handleCanLaunch();
    }
  };

  function getActionButton(injectResultOverviewOutput: InjectResultOverviewOutput) {
    if (!injectResultOverviewOutput.inject_injector_contract) return null;

    const hasManageAbility = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectResultOverview.inject_id);
    const hasLaunchAbility = ability.can(ACTIONS.LAUNCH, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.LAUNCH, SUBJECTS.RESOURCE, injectResultOverview.inject_id);
    if (injectResultOverviewOutput.inject_ready && hasLaunchAbility) {
      const launchOrRelaunchKey = !injectResultOverviewOutput.inject_status?.status_id ? 'Launch now' : 'Relaunch now';
      return (
        <Button
          style={{ whiteSpace: 'nowrap' }}
          startIcon={<PlayArrowOutlined />}
          variant="contained"
          color="primary"
          size="small"
          onClick={handleOpenDialog}
          disabled={!canLaunch}
        >
          {t(launchOrRelaunchKey)}
        </Button>
      );
    } else if (hasManageAbility) {
      return (
        <>
          <Button
            startIcon={<SettingsOutlined />}
            variant="contained"
            color="warning"
            size="small"
            onClick={handleOpenEdit}
          >
            {t('Configure')}
          </Button>
          <AtomicTestingUpdate open={edition} handleClose={handleCloseEdit} atomic={injectResultOverviewOutput} />
        </>
      );
    } else {
      return null;
    }
  }

  function getDialog(injectResultOverviewOutput: InjectResultOverviewOutput) {
    return (
      <Dialog open={openDialog} onClose={handleCloseDialog} slotProps={{ paper: { elevation: 1 } }}>
        <DialogContent>
          <DialogContentText>
            {injectResultOverviewOutput.inject_ready && !injectResultOverviewOutput.inject_status?.status_id
              ? t('Do you want to launch this atomic testing: {title}?', { title: injectResultOverviewOutput.inject_title })
              : t('Do you want to relaunch this atomic testing: {title}?', { title: injectResultOverviewOutput.inject_title })}
          </DialogContentText>
          {injectResultOverviewOutput.inject_ready && injectResultOverviewOutput.inject_status?.status_id && (
            <Alert severity="warning" style={{ marginTop: theme.spacing(2) }}>
              {t('This atomic testing and its previous results will be deleted')}
            </Alert>
          )}
          {injectResultOverviewOutput.inject_ready && injectResultOverviewOutput.inject_status?.status_id
            && canManage && expectationsDrift?.drift_detected && (
            <Alert
              severity="warning"
              icon={<TrackChangesOutlined fontSize="inherit" />}
              style={{ marginTop: theme.spacing(1) }}
            >
              {t('The expectations of this atomic testing no longer match the validation requirements defined by its action.')}
              <FormControlLabel
                sx={{
                  display: 'flex',
                  marginTop: 0.5,
                }}
                control={(
                  <Checkbox
                    size="small"
                    checked={realignOnRelaunch}
                    onChange={event => setRealignOnRelaunch(event.target.checked)}
                  />
                )}
                label={t('Realign expectations to the current action before relaunching')}
                slotProps={{ typography: { variant: 'body2' } }}
              />
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button variant="outlined" color="primary" onClick={handleCloseDialog}>{t('Cancel')}</Button>
          <Button
            variant="contained"
            color="primary"
            onClick={
              injectResultOverviewOutput.inject_ready && !injectResultOverviewOutput.inject_status?.status_id
                ? submitLaunch
                : submitRelaunch
            }
          >
            {t('Confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    );
  }

  return (
    <>
      {/* Rendered inside the DetailHero action cluster, which provides the
          flex layout, the gap and the 32px control normalization. */}
      {/* Expectation drift warning - self-hides when aligned or dismissed. */}
      {canManage && (
        <ExpectationsDriftIndicator
          drift={expectationsDrift}
          variant="atomic"
          onRealign={onRealignExpectations}
          onDismiss={onDismissExpectations}
          placement="warning"
        />
      )}
      {/* Entity-scoped reports - self-hides without the reporting access
          capability. */}
      <EntityReportsPanel
        contextType="ATOMIC_TESTING"
        contextId={injectResultOverview.inject_id}
        entityName={injectResultOverview.inject_title}
      />
      {canManage && (
        <Tooltip title={t('Scheduling')}>
          <IconButton size="small" color="primary" onClick={() => setOpenScheduling(true)}>
            <UpdateOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
      {/* Dismissed drift downgraded to a discreet icon within the compact icon
          cluster - the drift is acknowledged but still reviewable. */}
      {canManage && (
        <ExpectationsDriftIndicator
          drift={expectationsDrift}
          variant="atomic"
          onRealign={onRealignExpectations}
          onDismiss={onDismissExpectations}
          placement="dismissed"
        />
      )}
      {canManage && isScheduled && !scheduleEnded && (
        <Button
          startIcon={<Stop />}
          variant="outlined"
          color="inherit"
          size="small"
          onClick={stopScheduling}
        >
          {t('Stop')}
        </Button>
      )}
      {hasAbility && getActionButton(injectResultOverview)}
      <AtomicTestingPopover
        atomic={injectResultOverview}
        actions={['Export', 'Update', 'Duplicate', 'Delete']}
        onDelete={() => navigate('/admin/atomic_testings')}
      />
      {getDialog(injectResultOverview)}
      <SchedulingDialog
        open={openScheduling}
        onClose={() => setOpenScheduling(false)}
        initialValues={{
          recurrence: injectResultOverview.inject_recurrence,
          recurrenceStart: injectResultOverview.inject_recurrence_start,
          recurrenceEnd: injectResultOverview.inject_recurrence_end,
        }}
        onSubmit={onSubmitScheduling}
      />
    </>
  );
};

export default AtomicTestingHeaderActions;

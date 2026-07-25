import { PlayArrowOutlined, SettingsOutlined } from '@mui/icons-material';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext, useEffect, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchMe } from '../../../../actions/Application';
import {
  fetchAtomicTestingExpectationsDrift,
  fetchInjectResultOverviewOutput,
  launchAtomicTesting,
  realignAtomicTestingExpectations,
  relaunchAtomicTesting,
} from '../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../components/i18n';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import { type ExpectationsDriftOutput } from '../../../../utils/api-types-custom';
import { useAppDispatch } from '../../../../utils/hooks';
import useEnterpriseEdition from '../../../../utils/hooks/useEnterpriseEdition';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import ExpectationsDriftIndicator from '../../common/injects/expectations/ExpectationsDriftIndicator';
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
  const ability = useContext(AbilityContext);
  const { setEEFeatureDetectedInfo } = useEnterpriseEdition();
  const dispatch = useAppDispatch();
  const hasAbility = ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectResultOverview.inject_id);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, injectResultOverview.inject_id);

  const [edition, setEdition] = useState(false);
  const [openDialog, setOpenDialog] = useState(false);
  const [canLaunch, setCanLaunch] = useState(true);
  const [expectationsDrift, setExpectationsDrift] = useState<ExpectationsDriftOutput | null>(null);

  // Expectation drift between the injector contract template and the inject
  // content - recomputed when the atomic testing is updated.
  useEffect(() => {
    fetchAtomicTestingExpectationsDrift(injectResultOverview.inject_id).then((result: { data: ExpectationsDriftOutput }) => setExpectationsDrift(result.data));
  }, [injectResultOverview.inject_id, injectResultOverview.inject_updated_at]);

  const onRealignExpectations = async () => {
    await realignAtomicTestingExpectations(injectResultOverview.inject_id);
    const result = await fetchAtomicTestingExpectationsDrift(injectResultOverview.inject_id);
    setExpectationsDrift(result.data);
    await fetchInjectResultOverviewOutput(injectResultOverview.inject_id).then((overview: { data: InjectResultOverviewOutput }) => {
      setInjectResultOverview(overview.data);
    });
  };

  // Handlers
  const handleCloseDialog = () => setOpenDialog(false);
  const handleCanLaunch = () => setCanLaunch(true);
  const handleCannotLaunch = () => setCanLaunch(false);
  const handleOpenDialog = () => setOpenDialog(true);
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
    if (injectResultOverview?.inject_id) {
      await relaunchAtomicTesting(injectResultOverview.inject_id).then((result) => {
        dispatch(fetchMe()).then(() => {
          navigate(`/admin/atomic_testings/${result.data.inject_id}`);
        });
      });
    }
    handleCanLaunch();
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
      {/* Expectation drift warning - self-hides when aligned. */}
      {canManage && (
        <ExpectationsDriftIndicator
          drift={expectationsDrift}
          variant="atomic"
          onRealign={onRealignExpectations}
        />
      )}
      {hasAbility && getActionButton(injectResultOverview)}
      <AtomicTestingPopover
        atomic={injectResultOverview}
        actions={['Export', 'Update', 'Duplicate', 'Delete']}
        onDelete={() => navigate('/admin/atomic_testings')}
      />
      {getDialog(injectResultOverview)}
    </>
  );
};

export default AtomicTestingHeaderActions;

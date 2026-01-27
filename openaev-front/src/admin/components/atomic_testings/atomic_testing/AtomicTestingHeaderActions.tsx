import { PlayArrowOutlined, SettingsOutlined } from '@mui/icons-material';
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useContext, useState } from 'react';
import { useNavigate } from 'react-router';

import { fetchMe } from '../../../../actions/Application';
import { launchAtomicTesting, relaunchAtomicTesting } from '../../../../actions/atomic_testings/atomic-testing-actions';
import { useFormatter } from '../../../../components/i18n';
import type { InjectResultOverviewOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
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
  const dispatch = useAppDispatch();
  const hasAbility = ability.can(ACTIONS.ACCESS, SUBJECTS.ASSESSMENT) || ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, injectResultOverview.inject_id);

  const [edition, setEdition] = useState(false);
  const [openDialog, setOpenDialog] = useState(false);
  const [canLaunch, setCanLaunch] = useState(true);

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
          style={{
            marginRight: theme.spacing(1),
            whiteSpace: 'nowrap',
          }}
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
            style={{ marginRight: theme.spacing(1) }}
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
          <Button onClick={handleCloseDialog}>{t('Cancel')}</Button>
          <Button
            color="secondary"
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
      <Box display="flex" flexDirection="row" alignItems="center">
        {hasAbility && getActionButton(injectResultOverview)}
        <AtomicTestingPopover
          atomic={injectResultOverview}
          actions={['Export', 'Update', 'Duplicate', 'Delete']}
          onDelete={() => navigate('/admin/atomic_testings')}
        />
      </Box>
      {getDialog(injectResultOverview)}
    </>
  );
};

export default AtomicTestingHeaderActions;

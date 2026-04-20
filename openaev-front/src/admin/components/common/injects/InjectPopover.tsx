import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';
import { Link } from 'react-router';

import { duplicateInjectForExercise, duplicateInjectForScenario } from '../../../../actions/Inject';
import { type InjectStore } from '../../../../actions/injects/Inject';
import { exportInject } from '../../../../actions/injects/inject-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import DialogDuplicate from '../../../../components/common/DialogDuplicate';
import DialogTest from '../../../../components/common/DialogTest';
import ExportOptionsDialog from '../../../../components/common/export/ExportOptionsDialog';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import type {
  Inject,
  InjectIndividualExportRequestInput,
  InjectStatus,
  InjectTestStatusOutput,
} from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { useAppDispatch } from '../../../../utils/hooks';
import { download } from '../../../../utils/utils';
import { InjectContext, InjectTestContext, PermissionsContext } from '../Context';

type InjectPopoverType = {
  inject_id: string;
  inject_exercise?: string;
  inject_scenario?: string;
  inject_status?: InjectStatus;
  inject_testable?: boolean;
  inject_teams?: string[];
  inject_type?: string;
  inject_enabled?: boolean;
  inject_title?: string;
};

interface Props {
  inject: InjectPopoverType;
  setSelectedInjectId: (injectId: Inject['inject_id']) => void;
  isDisabled?: boolean;
  canBeTested?: boolean;
  canDone?: boolean;
  canTriggerNow?: boolean;
  onCreate?: (result: {
    result: string;
    entities: { injects: Record<string, InjectStore> };
  }) => void;
  onUpdate?: (result: {
    result: string;
    entities: { injects: Record<string, InjectStore> };
  }) => void;
  onDelete?: (result: string) => void;
}

const InjectPopover: FunctionComponent<Props> = ({
  inject,
  setSelectedInjectId,
  isDisabled = false,
  canBeTested = false,
  canDone = false,
  canTriggerNow = false,
  onCreate,
  onUpdate,
  onDelete,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { permissions } = useContext(PermissionsContext);
  const {
    onUpdateInjectTrigger,
    onUpdateInjectActivation,
    onInjectDone,
    onDeleteInject,
  } = useContext(InjectContext);

  const {
    contextId,
    testInject,
    url,
  } = useContext(InjectTestContext);

  const [openDelete, setOpenDelete] = useState(false);
  const [duplicate, setDuplicate] = useState(false);
  const [openTest, setOpenTest] = useState(false);
  const [openEnable, setOpenEnable] = useState(false);
  const [openDisable, setOpenDisable] = useState(false);
  const [openDone, setOpenDone] = useState(false);
  const [openTrigger, setOpenTrigger] = useState(false);
  const [openExportDialog, setOpenExportDialog] = useState(false);

  const handleOpenDuplicate = () => {
    setDuplicate(true);
  };
  const handleCloseDuplicate = () => setDuplicate(false);

  const submitDuplicate = () => {
    if (inject.inject_exercise) {
      dispatch(duplicateInjectForExercise(inject.inject_exercise, inject.inject_id)).then((result: {
        result: string;
        entities: { injects: Record<string, InjectStore> };
      }) => {
        onCreate?.(result);
      });
    }
    if (inject.inject_scenario) {
      dispatch(duplicateInjectForScenario(inject.inject_scenario, inject.inject_id)).then((result: {
        result: string;
        entities: { injects: Record<string, InjectStore> };
      }) => {
        onCreate?.(result);
      });
    }
    handleCloseDuplicate();
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
  };
  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    onDeleteInject(inject.inject_id).then(() => {
      onDelete?.(inject.inject_id);
      handleCloseDelete();
    });
  };

  const handleOpenTest = () => {
    setOpenTest(true);
  };
  const handleCloseTest = () => setOpenTest(false);

  const handleExportOpen = () => setOpenExportDialog(true);
  const handleExportClose = () => setOpenExportDialog(false);

  const handleExportJsonSingle = (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => {
    const exportData: InjectIndividualExportRequestInput = {
      options: {
        with_players: withPlayers,
        with_teams: withTeams,
        with_variable_values: withVariableValues,
      },
    };
    exportInject(inject.inject_id, exportData).then((result) => {
      const contentDisposition = result.headers['content-disposition'];
      const match = contentDisposition.match(/filename\s*=\s*(.*)/i);
      const filename = match[1];
      download(result.data, filename, result.headers['content-type']);
    });
    handleExportClose();
  };

  const submitTest = () => {
    if (testInject) {
      testInject(contextId, inject.inject_id).then((result: { data: InjectTestStatusOutput }) => {
        MESSAGING$.notifySuccess(t('Inject test has been sent, you can view test logs details on {itsDedicatedPage}.', { itsDedicatedPage: <Link to={`${url}${result.data.status_id}`}>{t('its dedicated page')}</Link> }));
      });
    }
    handleCloseTest();
  };

  const handleOpenEnable = () => {
    setOpenEnable(true);
  };
  const handleCloseEnable = () => setOpenEnable(false);

  const submitEnable = () => {
    onUpdateInjectActivation(inject.inject_id, { inject_enabled: true }).then((result) => {
      onUpdate?.(result);
      handleCloseEnable();
    });
  };

  const handleOpenDisable = () => {
    setOpenDisable(true);
  };
  const handleCloseDisable = () => setOpenDisable(false);

  const submitDisable = () => {
    onUpdateInjectActivation(inject.inject_id, { inject_enabled: false }).then((result) => {
      onUpdate?.(result);
      handleCloseDisable();
    });
  };

  const handleOpenDone = () => {
    setOpenDone(true);
  };
  const handleCloseDone = () => setOpenDone(false);

  const submitDone = () => {
    onInjectDone?.(inject.inject_id).then((result) => {
      onUpdate?.(result);
      handleCloseDone();
    });
  };

  const handleOpenEditContent = () => {
    setSelectedInjectId(inject.inject_id);
  };

  const handleOpenTrigger = () => {
    setOpenTrigger(true);
  };
  const handleCloseTrigger = () => setOpenTrigger(false);

  const submitTrigger = () => {
    onUpdateInjectTrigger?.(inject.inject_id).then((result) => {
      onUpdate?.(result);
      handleCloseTrigger();
    });
  };

  // Button Popover
  const entries = [];
  entries.push({
    label: 'Update',
    action: () => handleOpenEditContent(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  entries.push({
    label: 'Duplicate',
    action: () => handleOpenDuplicate(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  if (inject.inject_testable && canBeTested) entries.push({
    label: 'Test',
    action: () => handleOpenTest(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  entries.push({
    label: 'inject_export_json_single',
    action: () => handleExportOpen(),
    disabled: isDisabled,
    userRight: true,
  });
  if (!inject.inject_status && onInjectDone && canDone) entries.push({
    label: 'Mark as done',
    action: () => handleOpenDone(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  if (inject.inject_type !== 'openaev_manual' && canTriggerNow && onUpdateInjectTrigger) entries.push({
    label: 'Trigger now',
    action: () => handleOpenTrigger(),
    disabled: isDisabled || !permissions.isRunning,
    userRight: permissions.canLaunch,
  });
  if (inject.inject_enabled) entries.push({
    label: 'Disable',
    action: () => handleOpenDisable(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  else entries.push({
    label: 'Enable',
    action: () => handleOpenEnable(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });
  entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    disabled: isDisabled,
    userRight: permissions.canManage,
  });

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />

      <DialogDuplicate
        open={duplicate}
        handleClose={handleCloseDuplicate}
        handleSubmit={submitDuplicate}
        text={`${t('Do you want to duplicate this inject:')} ${inject.inject_title} ?`}
      />
      <Dialog
        slots={{ transition: Transition }}
        open={openDone}
        onClose={handleCloseDone}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(`Do you want to mark this inject as done: ${inject.inject_title}?`)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDone}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitDone}>
            {t('Mark')}
          </Button>
        </DialogActions>
      </Dialog>
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this inject:')} ${inject.inject_title} ?`}
      />
      <DialogTest
        open={openTest}
        handleClose={handleCloseTest}
        handleSubmit={submitTest}
        text={`${t('Do you want to test this inject:')} ${inject.inject_title} ?`}
      />
      <Dialog
        slots={{ transition: Transition }}
        open={openEnable}
        onClose={handleCloseEnable}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(`Do you want to enable this inject: ${inject.inject_title}?`)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseEnable}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitEnable}>
            {t('Enable')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        slots={{ transition: Transition }}
        open={openDisable}
        onClose={handleCloseDisable}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {`${t('Do you want to disable this inject:')} ${inject.inject_title} ?`}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDisable}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitDisable}>
            {t('Disable')}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        slots={{ transition: Transition }}
        open={openTrigger}
        onClose={handleCloseTrigger}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t(`Do you want to trigger this inject now: ${inject.inject_title}?`)}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseTrigger}>
            {t('Cancel')}
          </Button>
          <Button color="secondary" onClick={submitTrigger}>
            {t('Trigger')}
          </Button>
        </DialogActions>
      </Dialog>
      <ExportOptionsDialog
        title={t('inject_export_prompt')}
        open={openExportDialog}
        onCancel={handleExportClose}
        onClose={handleExportClose}
        onSubmit={handleExportJsonSingle}
      />
    </>
  );
};

export default InjectPopover;

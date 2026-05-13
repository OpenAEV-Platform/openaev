import { MoreVert } from '@mui/icons-material';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  IconButton,
  Menu,
  MenuItem,
} from '@mui/material';
import { type MouseEvent, useContext, useState } from 'react';

import { deletePayload } from '../../../actions/payloads/payload-actions';
import {
  duplicateThreatArsenalAction,
  exportThreatArsenalAction,
  fetchThreatArsenalAction,
  updateThreatArsenalAction,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import Drawer from '../../../components/common/Drawer';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import {
  type ThreatArsenalAction,
  type ThreatArsenalActionCreateInput,
  type ThreatArsenalActionFullOutput,
} from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import { useAppDispatch } from '../../../utils/hooks';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { download } from '../../../utils/utils';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';
import SnapshotRemediationProvider from './utils/SnapshotRemediationProvider';

interface PayloadPopoverNewProps {
  actionId: string;
  payloadId: string;
  name: string;
  onUpdate?: (action: ThreatArsenalAction) => void;
  onDelete?: () => void;
  onDuplicate?: (action: ThreatArsenalAction) => void;
  disableUpdate?: boolean;
  disableDelete?: boolean;
}

const buildInitialValues = (action: ThreatArsenalActionFullOutput, actionName: string): Partial<ThreatArsenalActionCreateCustomInput> & { payload_id?: string } => {
  const remediations: Record<string, DetectionRemediationForm> = {};
  action.action_detection_remediations?.forEach((remediation) => {
    remediations[remediation.detection_remediation_collector_type ?? ''] = {
      content: remediation.detection_remediation_values ?? '',
      remediationId: remediation.detection_remediation_id ?? '',
      author_rule: remediation.author_rule,
    };
  });

  return {
    action_id: action.action_id,
    action_name: actionName,
    action_description: action.action_description,
    action_type: action.action_type as ThreatArsenalActionCreateCustomInput['action_type'],
    command_executor: action.command_executor as string | undefined,
    command_content: action.command_content as string | undefined,
    dns_resolution_hostname: action.dns_resolution_hostname as string | undefined,
    action_arguments: action.action_arguments?.map(arg => ({
      ...arg,
      subtype: arg.subtype ?? undefined,
      description: arg.description ?? undefined,
      separator: arg.separator ?? undefined,
    })),
    action_prerequisites: action.action_prerequisites,
    file_drop_file: action.file_drop_file as string | undefined,
    action_attack_patterns: action.action_attack_patterns,
    action_tags: action.action_tags as string[] | undefined,
    action_expectations: action.action_expectations ?? ['PREVENTION', 'DETECTION'],
    action_execution_arch: action.action_execution_arch,
    action_output_parsers: action.action_output_parsers as ThreatArsenalActionCreateCustomInput['action_output_parsers'],
    action_platforms: action.action_platforms,
    executable_file: action.executable_file as string | undefined,
    action_cleanup_executor: action.action_cleanup_executor ?? '',
    action_cleanup_command: action.action_cleanup_command ?? '',
    remediations: remediations as ThreatArsenalActionCreateCustomInput['remediations'],
    action_domains: action.action_domains,
  } as Partial<ThreatArsenalActionCreateCustomInput> & { action_id?: string };
};

const handleCleanupCommandValue = (value: string): string | null => (value === '' ? null : value);

const handleCleanupExecutorValue = (executor: string, command: string): string | null => {
  if (executor !== '' && handleCleanupCommandValue(command) !== null) return executor;
  return null;
};

const ThreatArsenalActionPopover = ({
  actionId,
  payloadId,
  name,
  onUpdate,
  onDelete,
  onDuplicate,
  disableUpdate,
  disableDelete,
}: PayloadPopoverNewProps) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [openEdit, setOpenEdit] = useState(false);
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const [deletion, setDeletion] = useState(false);
  const [fetchedAction, setFetchedAction] = useState<ThreatArsenalActionFullOutput | null>(null);

  const dispatch = useAppDispatch();
  const { t, tPick } = useFormatter();
  const ability = useContext(AbilityContext);

  // -- Popover --
  const handlePopoverOpen = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);

  // -- Edit --
  const handleOpenEdit = async () => {
    handlePopoverClose();
    const response = await fetchThreatArsenalAction(actionId);
    setFetchedAction(response.data as ThreatArsenalActionFullOutput);
    setOpenEdit(true);
  };

  const handleCloseEdit = () => {
    setOpenEdit(false);
    setFetchedAction(null);
  };

  const onSubmitEdit = async (data: ThreatArsenalActionCreateCustomInput) => {
    const inputValues: ThreatArsenalActionCreateInput = {
      ...data,
      action_cleanup_executor: handleCleanupExecutorValue(
        data.action_cleanup_executor as string ?? '',
        data.action_cleanup_command as string ?? '',
      ),
      action_cleanup_command: handleCleanupCommandValue(data.action_cleanup_command as string ?? ''),
      action_detection_remediations: Object.entries(data.remediations ?? {})
        .filter(([, value]) => value)
        .map(([key, value]) => {
          const remediation = value as unknown as DetectionRemediationForm;
          return {
            detection_remediation_collector: key,
            detection_remediation_values: remediation.content,
            detection_remediation_id: remediation.remediationId,
            author_rule: remediation.author_rule,
          };
        }),
    } as ThreatArsenalActionCreateInput;

    const response = await updateThreatArsenalAction(actionId, inputValues);
    if (response.data && onUpdate) {
      onUpdate(response.data as ThreatArsenalAction);
    }
    handleCloseEdit();
  };

  // -- Delete --
  const handleOpenDelete = () => setDeletion(true);
  const handleCloseDelete = () => setDeletion(false);

  const submitDelete = () => {
    dispatch(deletePayload(payloadId)).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete();
    });
  };

  // -- Duplicate --
  const handleOpenDuplicate = () => {
    setOpenDuplicate(true);
    handlePopoverClose();
  };
  const handleCloseDuplicate = () => setOpenDuplicate(false);

  const submitDuplicate = async () => {
    const response = await duplicateThreatArsenalAction(actionId);
    if (response.data && onDuplicate) {
      onDuplicate(response.data as ThreatArsenalAction);
    }
    handleCloseDuplicate();
  };

  const hasUpdateCapability = ability.can(ACTIONS.MANAGE, SUBJECTS.PAYLOADS) || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, payloadId);
  const hasDeleteCapability = ability.can(ACTIONS.DELETE, SUBJECTS.PAYLOADS) || ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, payloadId);

  // -- Export --
  const handleExportJsonSingle = async () => {
    handlePopoverClose();
    const response = await exportThreatArsenalAction(actionId);
    const match = (response.headers['content-disposition'] as string).match(/filename="?([^"]+)"?/);
    const filename = match?.[1] ?? 'payload.zip';
    download(response.data, filename, 'application/zip');
  };

  return (
    <>
      <IconButton color="primary" onClick={handlePopoverOpen} aria-haspopup="true" size="large">
        <MoreVert />
      </IconButton>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PAYLOADS}>
          <MenuItem onClick={handleOpenDuplicate}>{t('Duplicate')}</MenuItem>
        </Can>
        <MenuItem onClick={handleExportJsonSingle}>{t('Export')}</MenuItem>
        {hasUpdateCapability && (
          <MenuItem onClick={handleOpenEdit} disabled={disableUpdate}>{t('Update')}</MenuItem>
        )}
        {hasDeleteCapability && (
          <MenuItem onClick={handleOpenDelete} disabled={disableDelete}>{t('Delete')}</MenuItem>
        )}
      </Menu>

      <DialogDelete
        open={deletion}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this action: ')} ${name ?? actionId} ?`}
      />

      <Dialog
        open={openDuplicate}
        slots={{ transition: Transition }}
        onClose={handleCloseDuplicate}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to duplicate this action?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDuplicate}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDuplicate}>{t('Duplicate')}</Button>
        </DialogActions>
      </Dialog>

      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the action')}
      >
        {fetchedAction && (
          <SnapshotRemediationProvider>
            <ThreatArsenalActionForm
              onSubmit={onSubmitEdit}
              handleClose={handleCloseEdit}
              editing
              initialValues={buildInitialValues(fetchedAction, tPick(fetchedAction.action_labels))}
            />
          </SnapshotRemediationProvider>
        )}
      </Drawer>
    </>
  );
};

export default ThreatArsenalActionPopover;

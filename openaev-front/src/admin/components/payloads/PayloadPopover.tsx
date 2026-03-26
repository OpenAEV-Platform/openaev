import { MoreVert } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import { type MouseEvent, useContext, useState } from 'react';

import { deletePayload, duplicatePayload, exportPayload, fetchPayload, updatePayload } from '../../../actions/payloads/payload-actions';
import Button from '../../../components/common/button/Button';
import DialogDelete from '../../../components/common/DialogDelete';
import Drawer from '../../../components/common/Drawer';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import {
  Domain,
  InjectorContractActionOutput,
  Payload, PayloadOutput,
  PayloadUpdateInput,
} from '../../../utils/api-types';
import { type PayloadCreateInput } from '../../../utils/api-types-custom';
import { useAppDispatch } from '../../../utils/hooks';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { download } from '../../../utils/utils';
import PayloadForm from './PayloadForm';
import { type DetectionRemediationForm } from './utils/payloadFormToPayloadInput';
import SnapshotRemediationProvider from './utils/SnapshotRemediationProvider';

interface PayloadPopoverNewProps {
  payloadId: string;
  name: string;
  onUpdate?: (payload: InjectorContractActionOutput) => void;
  onDelete?: () => void;
  onDuplicate?: (payload: InjectorContractActionOutput) => void;
  disableUpdate?: boolean;
  disableDelete?: boolean;
}

const buildInitialValues = (payload: PayloadOutput): Partial<PayloadCreateInput> & { payload_id?: string } => {
  const remediations: Record<string, DetectionRemediationForm> = {};
  payload.payload_detection_remediations?.forEach((remediation) => {
    remediations[remediation.detection_remediation_collector_type ?? ''] = {
      content: remediation.detection_remediation_values ?? '',
      remediationId: remediation.detection_remediation_id ?? '',
      author_rule: remediation.author_rule,
    };
  });

  return {
    payload_id: payload.payload_id,
    payload_name: payload.payload_name,
    payload_description: payload.payload_description,
    payload_type: payload.payload_type as PayloadCreateInput['payload_type'],
    command_executor: payload.command_executor as string | undefined,
    command_content: payload.command_content as string | undefined,
    dns_resolution_hostname: payload.dns_resolution_hostname as string | undefined,
    payload_arguments: payload.payload_arguments,
    payload_prerequisites: payload.payload_prerequisites,
    file_drop_file: payload.file_drop_file as string | undefined,
    payload_attack_patterns: payload.payload_attack_patterns,
    payload_tags: payload.payload_tags as string[] | undefined,
    payload_expectations: payload.payload_expectations ?? ['PREVENTION', 'DETECTION'],
    payload_execution_arch: payload.payload_execution_arch,
    payload_output_parsers: payload.payload_output_parsers as PayloadCreateInput['payload_output_parsers'],
    payload_platforms: payload.payload_platforms,
    executable_file: payload.executable_file as string | undefined,
    payload_cleanup_executor: payload.payload_cleanup_executor ?? '',
    payload_cleanup_command: payload.payload_cleanup_command ?? '',
    remediations: remediations as PayloadCreateInput['remediations'],
    payload_domains: payload.payload_domains,
  } as Partial<PayloadCreateInput> & { payload_id?: string };
};

const handleCleanupCommandValue = (value: string): string | null => (value === '' ? null : value);

const handleCleanupExecutorValue = (executor: string, command: string): string | null => {
  if (executor !== '' && handleCleanupCommandValue(command) !== null) return executor;
  return null;
};

const PayloadPopover = ({
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
  const [fetchedPayload, setFetchedPayload] = useState<PayloadOutput | null>(null);

  const dispatch = useAppDispatch();
  const { t } = useFormatter();
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
    const response = await fetchPayload(payloadId);
    setFetchedPayload(response.data as PayloadOutput);
    setOpenEdit(true);
  };

  const handleCloseEdit = () => {
    setOpenEdit(false);
    setFetchedPayload(null);
  };

  const onSubmitEdit = (data: PayloadCreateInput) => {
    const inputValues: PayloadUpdateInput = {
      ...data,
      payload_domains: data.payload_domains.map((domain: Domain) => domain.domain_id),
      payload_cleanup_executor: handleCleanupExecutorValue(
        data.payload_cleanup_executor as string ?? '',
        data.payload_cleanup_command as string ?? '',
      ),
      payload_cleanup_command: handleCleanupCommandValue(data.payload_cleanup_command as string ?? ''),
      payload_detection_remediations: Object.entries(data.remediations ?? {})
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
    } as PayloadUpdateInput;

    return dispatch(updatePayload(payloadId, inputValues)).then((result: { entities: { payloads: Record<string, InjectorContractActionOutput> }; result: string }) => {
      debugger;
      if (onUpdate) {
        onUpdate(result.entities.payloads[result.result]);
      }
      handleCloseEdit();
    });
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

  const submitDuplicate = () => {
    return dispatch(duplicatePayload(payloadId)).then((result: { entities: { payloads: Record<string, InjectorContractActionOutput> }; result: string }) => {
      if (onDuplicate) {
        onDuplicate(result.entities.payloads[result.result]);
      }
      handleCloseDuplicate();
    });
  };

  // -- Export --
  const handleExportJsonSingle = async () => {
    handlePopoverClose();
    const response = await exportPayload(payloadId);
    const match = (response.headers['content-disposition'] as string).match(/filename="?([^"]+)"?/);
    const filename = match?.[1] ?? 'payload.zip';
    download(response.data, filename, 'application/zip');
  };

  const hasUpdateCapability = ability.can(ACTIONS.MANAGE, SUBJECTS.PAYLOADS) || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, payloadId);
  const hasDeleteCapability = ability.can(ACTIONS.DELETE, SUBJECTS.PAYLOADS) || ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, payloadId);

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
        {hasUpdateCapability && (
          <MenuItem onClick={handleOpenEdit} disabled={disableUpdate}>{t('Update')}</MenuItem>
        )}
        <MenuItem onClick={handleExportJsonSingle}>{t('Export')}</MenuItem>
        {hasDeleteCapability && (
          <MenuItem onClick={handleOpenDelete} disabled={disableDelete}>{t('Delete')}</MenuItem>
        )}
      </Menu>

      <DialogDelete
        open={deletion}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this payload: ')} ${name ?? payloadId} ?`}
      />

      <Dialog
        open={openDuplicate}
        slots={{ transition: Transition }}
        onClose={handleCloseDuplicate}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to duplicate this payload?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button variant="secondary" onClick={handleCloseDuplicate}>{t('Cancel')}</Button>
          <Button variant="primary" onClick={submitDuplicate}>{t('Duplicate')}</Button>
        </DialogActions>
      </Dialog>

      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the payload')}
      >
        {fetchedPayload && (
          <SnapshotRemediationProvider>
            <PayloadForm
              onSubmit={onSubmitEdit}
              handleClose={handleCloseEdit}
              editing
              initialValues={buildInitialValues(fetchedPayload)}
            />
          </SnapshotRemediationProvider>
        )}
      </Drawer>
    </>
  );
};

export default PayloadPopover;


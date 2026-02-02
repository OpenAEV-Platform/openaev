import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import { useContext, useState } from 'react';
import { useDispatch } from 'react-redux';

import { deletePayload, duplicatePayload, exportPayload, updatePayload } from '../../../actions/payloads/payload-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import Drawer from '../../../components/common/Drawer';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { AbilityContext, Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { download } from '../../../utils/utils';
import PayloadForm from './PayloadForm';
import SnapshotRemediationProvider from './utils/SnapshotRemediationProvider';

const PayloadPopover = ({ payload, onUpdate, onDelete, onDuplicate, disableUpdate, disableDelete }) => {
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const dispatch = useDispatch();
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  const handlePopoverOpen = (event) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };
  const handlePopoverClose = () => setAnchorEl(null);
  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };
  const handleCloseEdit = () => setOpenEdit(false);
  const onSubmitEdit = (data) => {
    function handleCleanupCommandValue(payload_cleanup_command) {
      return payload_cleanup_command === '' ? null : payload_cleanup_command;
    }

    function handleCleanupExecutorValue(payload_cleanup_executor, payload_cleanup_command) {
      if (payload_cleanup_executor !== '' && handleCleanupCommandValue(payload_cleanup_command) !== null) {
        return payload_cleanup_executor;
      }
      return null;
    }

    const inputValues = {
      ...data,
      payload_domains: data.payload_domains.map(domain => domain.domain_id),
      payload_cleanup_executor: handleCleanupExecutorValue(data.payload_cleanup_executor, data.payload_cleanup_command),
      payload_cleanup_command: handleCleanupCommandValue(data.payload_cleanup_command),
      payload_detection_remediations: Object.entries(data.remediations)
        .filter(([, value]) => value)
        .map(([key, value]) => ({
          detection_remediation_collector: key,
          detection_remediation_values: value.content,
          detection_remediation_id: value.remediationId,
          author_rule: value.author_rule,
        })),
    };

    return dispatch(updatePayload(payload.payload_id, inputValues)).then((result) => {
      if (onUpdate) {
        const payloadUpdated = result.entities.payloads[result.result];
        onUpdate(payloadUpdated);
      }
      handleCloseEdit();
    });
  };

  // Deletion
  const [deletion, setDeletion] = useState(false);
  const handleOpenDelete = () => setDeletion(true);
  const handleCloseDelete = () => setDeletion(false);
  const submitDelete = () => {
    dispatch(deletePayload(payload.payload_id)).then(() => {
      handleCloseDelete();
      if (onDelete) onDelete(payload.payload_id);
    });
  };

  // Duplicate
  const handleOpenDuplicate = () => {
    setOpenDuplicate(true);
    handlePopoverClose();
  };
  const handleCloseDuplicate = () => setOpenDuplicate(false);
  const submitDuplicate = () => {
    return dispatch(duplicatePayload(payload.payload_id)).then((result) => {
      if (onDuplicate) {
        const payloadUpdated = result.entities.payloads[result.result];
        onDuplicate(payloadUpdated);
      }
      handleCloseDuplicate();
    });
  };

  const handleExportJsonSingle = async () => {
    handlePopoverClose();
    const response = await exportPayload(payload.payload_id);

    const match = response.headers['content-disposition'].match(/filename="?([^"]+)"?/);
    const filename = match[1];
    download(response.data, filename, 'application/zip');
  };

  const initialValues = {
    payload_id: payload.payload_id,
    payload_name: payload.payload_name,
    payload_description: payload.payload_description,
    payload_type: payload.payload_type,
    command_executor: payload.command_executor,
    command_content: payload.command_content,
    dns_resolution_hostname: payload.dns_resolution_hostname,
    payload_arguments: payload.payload_arguments,
    payload_prerequisites: payload.payload_prerequisites,
    file_drop_file: payload.file_drop_file,
    payload_attack_patterns: payload.payload_attack_patterns,
    payload_tags: payload.payload_tags,
    payload_expectations: payload.payload_expectations ?? ['PREVENTION', 'DETECTION'],
    payload_execution_arch: payload.payload_execution_arch,
    payload_output_parsers: payload.payload_output_parsers,
    payload_platforms: payload.payload_platforms,
    executable_file: payload.executable_file,
    payload_cleanup_executor: payload.payload_cleanup_executor === null ? '' : payload.payload_cleanup_executor,
    payload_cleanup_command: payload.payload_cleanup_command === null ? '' : payload.payload_cleanup_command,
    remediations: {},
    payload_domains: payload.payload_domains,
  };
  payload.payload_detection_remediations?.forEach((remediation) => {
    initialValues.remediations[remediation.detection_remediation_collector_type] = {
      content: remediation.detection_remediation_values,
      remediationId: remediation.detection_remediation_id,
      author_rule: remediation.author_rule,
    };
  });
  const hasUpdateCapability = ability.can(ACTIONS.MANAGE, SUBJECTS.PAYLOADS) || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, payload.payload_id);
  const hasDeleteCapability = ability.can(ACTIONS.DELETE, SUBJECTS.PAYLOADS) || ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, payload.payload_id);
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
        {hasUpdateCapability
          && <MenuItem onClick={handleOpenEdit} disabled={disableUpdate}>{t('Update')}</MenuItem>}
        <MenuItem onClick={handleExportJsonSingle}>{t('Export')}</MenuItem>
        {hasDeleteCapability
          && <MenuItem onClick={handleOpenDelete} disabled={disableDelete}>{t('Delete')}</MenuItem>}
      </Menu>
      <DialogDelete
        open={deletion}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete this payload: ')} ${payload.payload_name} ?`}
      />
      <Dialog
        open={openDuplicate}
        TransitionComponent={Transition}
        onClose={handleCloseDuplicate}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to duplicate this payload?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDuplicate}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDuplicate}>
            {t('Duplicate')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the payload')}
      >
        <SnapshotRemediationProvider>
          <PayloadForm
            onSubmit={onSubmitEdit}
            handleClose={handleCloseEdit}
            editing
            initialValues={initialValues}
          />
        </SnapshotRemediationProvider>
      </Drawer>
    </>
  );
};

export default PayloadPopover;

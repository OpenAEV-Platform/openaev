import { Button, IconButton } from '@filigran/design-system';
import { MoreVert } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, Divider, Menu, MenuItem } from '@mui/material';
import { type MouseEvent, useState } from 'react';

import {
  customizeThreatArsenalAction,
  deleteThreatArsenalAction,
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
  type ThreatArsenalActionFullOutput,
  type ThreatArsenalActionUpdateInput,
} from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { download, isFeatureEnabled } from '../../../utils/utils';
import InjectorContractForm, { type InjectorContractFormValues } from '../integrations/injectors/injector_contracts/InjectorContractForm';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';
import fetchFiligranValues from './customization/filigranValues';
import useCanUpdateAction from './hook/useCanUpdateAction';
import PayloadRevocationDialog from './revocation/PayloadRevocationDialog';
import usePayloadRevocation from './revocation/usePayloadRevocation';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';
import { type ActionFormValues, buildInitialValues } from './utils/actionFormValues';
import SnapshotRemediationProvider from './utils/SnapshotRemediationProvider';
import PayloadNewVersionDialog from './versions/PayloadNewVersionDialog';
import useNewVersionNotice from './versions/useNewVersionNotice';

interface PayloadPopoverNewProps {
  action: ThreatArsenalAction;
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void;
  onDelete?: () => void;
  onDuplicate?: (action: ThreatArsenalAction) => void;
  onShowVersions?: (action: ThreatArsenalAction) => void;
}

const handleCleanupCommandValue = (value: string): string | null => (value === '' ? null : value);

const handleCleanupExecutorValue = (executor: string, command: string): string | null => {
  if (executor !== '' && handleCleanupCommandValue(command) !== null) return executor;
  return null;
};

const ThreatArsenalActionPopover = ({
  action,
  onUpdate,
  onDuplicate,
  onDelete,
  onShowVersions,
}: PayloadPopoverNewProps) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [openEdit, setOpenEdit] = useState(false);
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const [deletion, setDeletion] = useState(false);
  const [fetchedAction, setFetchedAction] = useState<ThreatArsenalActionFullOutput | null>(null);
  const [filigranValues, setFiligranValues] = useState<ActionFormValues | undefined>(undefined);

  const { t, tPick } = useFormatter();
  const ability = useAbility();

  const actionId = action.injector_contract_id;
  const { notice, afterSave, closeNotice } = useNewVersionNotice(actionId, onUpdate);
  const payloadId = action.action_payload?.payload_id ?? '';
  const grantResourceId = action.action_grant_resource_id;
  const name = tPick(action.action_labels);
  // Dummy injectors were removed (#6779): an action whose injector is not registered has no injector type.
  const isUnregistered = action.action_injector_type == null;
  const isFromCollector = action.action_payload?.payload_collector_type != null;
  const catalog = isFeatureEnabled('CATALOG');
  const customized = catalog && !!action.action_payload_customized;
  const disableUpdate = isFromCollector || isUnregistered;
  const disableDuplicate = action.action_payload == null || isUnregistered;
  const disableJsonExport = action.action_payload == null || isUnregistered;
  // Orphaned actions can always be purged; collector actions only once deprecated.
  const disableDelete = !isUnregistered
    && (action.action_payload == null || (isFromCollector && action.action_payload.payload_status !== 'DEPRECATED'));

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
    setFiligranValues(customized ? await fetchFiligranValues(actionId) : undefined);
    setFetchedAction(response.data as ThreatArsenalActionFullOutput);
    setOpenEdit(true);
  };

  const handleCloseEdit = () => {
    setOpenEdit(false);
    setFetchedAction(null);
    setFiligranValues(undefined);
  };

  const onSubmitEdit = async (data: ThreatArsenalActionCreateCustomInput) => {
    const inputValues: ThreatArsenalActionUpdateInput = {
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
            detection_remediation_security_platform: key,
            detection_remediation_values: remediation.content,
            detection_remediation_id: remediation.remediationId,
            author_rule: remediation.author_rule,
          };
        }),
    } as ThreatArsenalActionUpdateInput;

    const response = await updateThreatArsenalAction(actionId, inputValues);
    handleCloseEdit();
    if (response.data) {
      afterSave(response.data as ThreatArsenalAction);
    }
  };

  const onSubmitInjectorContractEdit = (data: InjectorContractFormValues) => {
    const updatedDataInput: ThreatArsenalActionCreateCustomInput = {
      ...fetchedAction,
      action_name: fetchedAction?.action_labels ? tPick(fetchedAction?.action_labels) : '',
      action_tags: data.injector_contract_tags,
      action_attack_patterns: data.injector_contract_attack_patterns,
      action_domains: data.injector_contract_domains,
    } as ThreatArsenalActionCreateCustomInput;
    onSubmitEdit(updatedDataInput);
  };

  // -- Delete --
  const handleOpenDelete = () => setDeletion(true);
  const handleCloseDelete = () => setDeletion(false);

  const submitDelete = () => {
    if (!actionId) {
      handleCloseDelete();
      return;
    }
    deleteThreatArsenalAction(actionId).then(() => {
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

  // -- Customize --
  const submitCustomize = async () => {
    handlePopoverClose();
    const response = await customizeThreatArsenalAction(actionId);
    if (response.data) {
      onDuplicate?.(response.data as ThreatArsenalAction);
      MESSAGING$.notifySuccess(t('Customized copy created: it keeps getting Filigran updates'));
    }
  };

  const hasDuplicateCapability = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const hasUpdateCapability = useCanUpdateAction(action);
  const canCustomize = hasDuplicateCapability && catalog && !!action.action_payload_managed && !action.action_payload_revoked;
  const hasDeleteCapability = ability.can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS) || ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, grantResourceId);

  const duplicateOff = !hasDuplicateCapability || !!disableDuplicate;
  const updateOff = !hasUpdateCapability || !!disableUpdate;
  const deleteOff = !hasDeleteCapability || !!disableDelete;
  // -- Revocation --
  const { canRevoke, revocable, liftable, revocation, openRevocation, closeRevocation, revocationDone } = usePayloadRevocation(
    action,
    !!anchorEl,
    (changed) => {
      onUpdate?.(changed);
      onShowVersions?.(changed);
    },
  );
  const allDisabled = duplicateOff && !!disableJsonExport && updateOff && deleteOff && !canRevoke && !canCustomize;

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
      <IconButton
        icon={<MoreVert fontSize="small" />}
        aria-label={t('More actions')}
        onClick={handlePopoverOpen}
        aria-haspopup="true"
        disabled={allDisabled}
        priority="tertiary"
        size="sm"
      />
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        {hasDuplicateCapability && (
          <MenuItem onClick={handleOpenDuplicate} disabled={disableDuplicate}>{t('Duplicate')}</MenuItem>
        )}
        {canCustomize && (
          <MenuItem onClick={submitCustomize}>{t('Customize')}</MenuItem>
        )}
        <MenuItem onClick={handleExportJsonSingle} disabled={disableJsonExport}>{t('Export')}</MenuItem>
        {hasUpdateCapability && (
          <MenuItem onClick={handleOpenEdit} disabled={disableUpdate}>{t('Update')}</MenuItem>
        )}
        {hasDeleteCapability && (
          <MenuItem onClick={handleOpenDelete} disabled={disableDelete}>{t('Delete')}</MenuItem>
        )}
        {(revocable.length > 0 || liftable.length > 0) && <Divider />}
        {revocable.length > 0 && (
          <MenuItem onClick={() => {
            handlePopoverClose();
            openRevocation('revoke');
          }}
          >
            {t('Revoke')}
          </MenuItem>
        )}
        {liftable.length > 0 && (
          <MenuItem onClick={() => {
            handlePopoverClose();
            openRevocation('lift');
          }}
          >
            {t('Lift revocation')}
          </MenuItem>
        )}
      </Menu>

      <PayloadRevocationDialog
        open={revocation !== null}
        name={name}
        lift={revocation === 'lift'}
        versions={revocation === 'lift' ? liftable : revocable}
        currentPayloadId={payloadId}
        actionId={actionId}
        onClose={closeRevocation}
        onDone={revocationDone}
      />

      {notice && (
        <PayloadNewVersionDialog
          action={notice.action}
          olderVersions={notice.olderVersions}
          onClose={closeNotice}
        />
      )}

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
          <Button type="button" priority="secondary" onClick={handleCloseDuplicate}>{t('Cancel')}</Button>
          <Button type="button" onClick={submitDuplicate}>{t('Duplicate')}</Button>
        </DialogActions>
      </Dialog>

      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={`${t('Update the action :')} ${name}`}
      >
        <>
          {fetchedAction && !!payloadId && (
            <SnapshotRemediationProvider>
              <ThreatArsenalActionForm
                onSubmit={onSubmitEdit}
                handleClose={handleCloseEdit}
                editing
                filigranValues={filigranValues}
                initialValues={{
                  ...buildInitialValues(fetchedAction, tPick(fetchedAction.action_labels)),
                  payload_id: payloadId,
                }}
              />
            </SnapshotRemediationProvider>
          )}
          {fetchedAction && !payloadId && (
            <InjectorContractForm
              initialValues={{
                injector_contract_attack_patterns: fetchedAction.action_attack_patterns,
                injector_contract_domains: fetchedAction.action_domains,
                injector_contract_tags: fetchedAction.action_tags,
              }}
              editing
              onSubmit={onSubmitInjectorContractEdit}
              handleClose={handleCloseEdit}
            />
          )}
        </>
      </Drawer>
    </>
  );
};

export default ThreatArsenalActionPopover;

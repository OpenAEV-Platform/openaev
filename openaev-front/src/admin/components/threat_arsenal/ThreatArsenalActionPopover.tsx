import { Button, IconButton } from '@filigran/design-system';
import { MoreVert } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText, Divider, Menu, MenuItem } from '@mui/material';
import { type MouseEvent, useState } from 'react';

import { uninstallCatalogPayload } from '../../../actions/catalog/payload-catalog-actions';
import { liftPayloadRevocation, revokePayload } from '../../../actions/payloads/payload-revocation-actions';
import { customizePayload, liftInheritedRevocation, type VariantSaveOutput } from '../../../actions/payloads/payload-variant-actions';
import {
  deleteThreatArsenalAction,
  duplicateThreatArsenalAction,
  exportThreatArsenalAction,
  fetchThreatArsenalAction,
  updateThreatArsenalAction,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogConfirmation from '../../../components/common/DialogConfirmation';
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
import { download } from '../../../utils/utils';
import InjectorContractForm, { type InjectorContractFormValues } from '../integrations/injectors/injector_contracts/InjectorContractForm';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';
import { buildInitialValues, handleCleanupCommandValue, handleCleanupExecutorValue } from './actionFormValues';
import PayloadNewVersionDialog from './PayloadNewVersionDialog';
import PayloadRevocationDialog from './PayloadRevocationDialog';
import ThreatArsenalActionForm from './ThreatArsenalActionForm';
import SnapshotRemediationProvider from './utils/SnapshotRemediationProvider';
import PayloadVariantEditor from './variants/PayloadVariantEditor';

interface PayloadPopoverNewProps {
  actionId: string;
  payloadId?: string;
  name: string;
  onUpdate?: (action: ThreatArsenalAction) => void;
  onDelete?: () => void;
  onDuplicate?: (action: ThreatArsenalAction, open?: boolean) => void;
  disableUpdate?: boolean;
  disableDuplicate?: boolean;
  disableJsonExport?: boolean;
  disableDelete?: boolean;
  revoked?: boolean;
  // A catalogue payload version: uninstalled, never deleted (F26).
  managed?: boolean;
  // A customized catalogue payload: changed through its overrides, not "Update".
  variantIdentityId?: string;
  onRevocationChange?: (revoked: boolean, status?: ThreatArsenalActionFullOutput['action_status']) => void;
}

const ThreatArsenalActionPopover = ({
  actionId,
  payloadId,
  name,
  onUpdate,
  onDuplicate,
  onDelete,
  disableUpdate,
  disableDuplicate,
  disableJsonExport,
  disableDelete,
  revoked,
  managed,
  variantIdentityId,
  onRevocationChange,
}: PayloadPopoverNewProps) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const [openEdit, setOpenEdit] = useState(false);
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const [deletion, setDeletion] = useState(false);
  const [uninstall, setUninstall] = useState(false);
  const [editingVariant, setEditingVariant] = useState<string | null>(null);
  const [revocation, setRevocation] = useState(false);
  const [lifting, setLifting] = useState(false);
  const [fetchedAction, setFetchedAction] = useState<ThreatArsenalActionFullOutput | null>(null);

  const { t, tPick } = useFormatter();
  const ability = useAbility();

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

  const [newVersion, setNewVersion] = useState<ThreatArsenalAction | null>(null);

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
    const saved = response.data as ThreatArsenalAction | undefined;
    if (saved && onUpdate) {
      onUpdate(saved);
    }
    handleCloseEdit();
    if (saved?.action_previous_version_id && (saved.action_previous_version_usages ?? 0) > 0) {
      setNewVersion(saved);
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

  // -- Uninstall: a catalogue payload goes back to Available, its versions hidden (F26) --
  const submitUninstall = () => {
    uninstallCatalogPayload(payloadId ?? '').then(() => {
      setUninstall(false);
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

  // -- Revocation --
  const handleOpenRevocation = () => {
    handlePopoverClose();
    setRevocation(true);
  };

  const submitRevocation = async (revokedId: string, reason: string) => {
    await revokePayload(revokedId, { content_revocation_reason: reason });
    if (revokedId === payloadId) {
      onRevocationChange?.(true, 'REVOKED');
    }
    setRevocation(false);
  };

  const submitCustomize = () => {
    handlePopoverClose();
    customizePayload(payloadId ?? '').then((response: { data: VariantSaveOutput }) => {
      const variant = response.data.action;
      if (variant && onDuplicate) {
        onDuplicate(variant, false);
      }
      MESSAGING$.notifySuccess(t('Customized copy created: it keeps receiving Filigran updates'));
      // The copy is made to be changed: its editor opens right away.
      if (variant?.action_payload?.payload_identity_id) {
        setEditingVariant(variant.action_payload.payload_identity_id);
      }
    });
  };

  // A customized version revoked through its base is freed with a reason; its own revocation is lifted.
  const submitLiftRevocation = async (liftedId: string, reason: string, inherited: boolean) => {
    if (inherited) {
      await liftInheritedRevocation(liftedId, reason);
    } else {
      await liftPayloadRevocation(liftedId);
    }
    if (liftedId === payloadId) {
      // Lifting restores the status the payload had before; read it back.
      const response = await fetchThreatArsenalAction(actionId);
      onRevocationChange?.(false, (response.data as ThreatArsenalActionFullOutput | undefined)?.action_status);
    }
    setLifting(false);
  };

  const hasDuplicateCapability = ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS);
  const hasUpdateCapability = hasDuplicateCapability || ability.can(ACTIONS.MANAGE, SUBJECTS.RESOURCE, payloadId);
  const hasDeleteCapability = ability.can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS) || ability.can(ACTIONS.DELETE, SUBJECTS.RESOURCE, payloadId);

  const duplicateOff = !hasDuplicateCapability || !!disableDuplicate;
  const updateOff = !hasUpdateCapability || !!disableUpdate;
  const deleteOff = !hasDeleteCapability || !!disableDelete;
  const allDisabled = duplicateOff && !!disableJsonExport && updateOff && deleteOff;

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
        <MenuItem onClick={handleExportJsonSingle} disabled={disableJsonExport}>{t('Export')}</MenuItem>
        {hasDuplicateCapability && managed && !revoked && !!payloadId && (
          <MenuItem onClick={submitCustomize}>{t('Customize')}</MenuItem>
        )}
        {hasUpdateCapability && !variantIdentityId && (
          <MenuItem onClick={handleOpenEdit} disabled={disableUpdate}>{t('Update')}</MenuItem>
        )}
        {hasUpdateCapability && variantIdentityId && (
          <MenuItem onClick={() => {
            handlePopoverClose();
            setEditingVariant(variantIdentityId);
          }}
          >
            {t('Edit customization')}
          </MenuItem>
        )}
        {hasDeleteCapability && !managed && (
          <MenuItem onClick={handleOpenDelete} disabled={disableDelete}>{t('Delete')}</MenuItem>
        )}
        {hasDeleteCapability && (managed || !!payloadId) && <Divider />}
        {hasDeleteCapability && managed && (
          <MenuItem onClick={() => {
            handlePopoverClose();
            setUninstall(true);
          }}
          >
            {t('Uninstall')}
          </MenuItem>
        )}
        {hasDeleteCapability && !!payloadId && (
          <MenuItem onClick={handleOpenRevocation}>{t('Revoke')}</MenuItem>
        )}
        {hasDeleteCapability && !!payloadId && (
          <MenuItem onClick={() => {
            handlePopoverClose();
            setLifting(true);
          }}
          >
            {t('Lift revocation')}
          </MenuItem>
        )}
      </Menu>

      {editingVariant && (
        <PayloadVariantEditor
          identityId={editingVariant}
          onClose={() => setEditingVariant(null)}
          onSaved={(output) => {
            if (output.action && onUpdate) {
              onUpdate({
                ...output.action,
                action_previous_version_id: output.previous_action_id ?? output.action.action_previous_version_id,
              });
            }
            if (output.new_version && output.previous_action_id && output.previous_version_usages > 0 && output.action) {
              setNewVersion({
                ...output.action,
                action_previous_version_id: output.previous_action_id,
                action_previous_version_usages: output.previous_version_usages,
              });
            }
          }}
        />
      )}

      <PayloadRevocationDialog
        open={revocation}
        name={name}
        actionId={actionId}
        payloadId={payloadId}
        onClose={() => setRevocation(false)}
        onSubmit={submitRevocation}
      />

      <PayloadRevocationDialog
        open={lifting}
        name={name}
        lift
        customized={!!variantIdentityId}
        actionId={actionId}
        payloadId={payloadId}
        onClose={() => setLifting(false)}
        onSubmit={submitLiftRevocation}
      />

      <DialogConfirmation
        open={uninstall}
        handleClose={() => setUninstall(false)}
        handleSubmit={submitUninstall}
        submitLabel={t('Uninstall')}
        submitColor="error"
        text={t('Do you want to uninstall this action? All its versions go back to the catalog; scenarios that use it block the uninstall.')}
      />

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
      {newVersion?.action_previous_version_id && newVersion.action_payload?.payload_id && (
        <PayloadNewVersionDialog
          previousActionId={newVersion.action_previous_version_id}
          newPayloadId={newVersion.action_payload.payload_id}
          newVersion={newVersion.action_payload.payload_version ?? ''}
          usages={newVersion.action_previous_version_usages ?? 0}
          onClose={() => setNewVersion(null)}
        />
      )}
    </>
  );
};

export default ThreatArsenalActionPopover;

import { Button, IconButton, Menu, MenuContent, MenuItem, MenuSeparator, MenuTrigger } from '@filigran/design-system';
import { MoreVert } from '@mui/icons-material';
import { Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type MouseEvent, useState } from 'react';

import {
  customizeThreatArsenalAction,
  deleteThreatArsenalAction,
  duplicateThreatArsenalAction,
  exportThreatArsenalAction,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import DialogDelete from '../../../components/common/DialogDelete';
import Transition from '../../../components/common/Transition';
import { useFormatter } from '../../../components/i18n';
import { type ThreatArsenalAction } from '../../../utils/api-types';
import { MESSAGING$ } from '../../../utils/Environment';
import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { download, isFeatureEnabled } from '../../../utils/utils';
import useCanUpdateAction from './hook/useCanUpdateAction';
import PayloadRevocationDialog from './revocation/PayloadRevocationDialog';
import usePayloadRevocation from './revocation/usePayloadRevocation';
import ThreatArsenalActionEditDrawer from './ThreatArsenalActionEditDrawer';
import { isCustomizedCopy } from './utils/threatArsenalStatusUtils';
import PayloadNewVersionDialog from './versions/PayloadNewVersionDialog';
import useNewVersionNotice from './versions/useNewVersionNotice';

interface PayloadPopoverNewProps {
  action: ThreatArsenalAction;
  onUpdate?: (action: ThreatArsenalAction, replacedId?: string) => void;
  onDelete?: () => void;
  onDuplicate?: (action: ThreatArsenalAction) => void;
  onShowVersions?: (action: ThreatArsenalAction) => void;
}

const ThreatArsenalActionPopover = ({
  action,
  onUpdate,
  onDuplicate,
  onDelete,
  onShowVersions,
}: PayloadPopoverNewProps) => {
  const [menuOpen, setMenuOpen] = useState(false);
  // The action whose edit drawer is open: this one, or the copy Customize just made.
  const [editing, setEditing] = useState<ThreatArsenalAction | null>(null);
  const [openDuplicate, setOpenDuplicate] = useState(false);
  const [deletion, setDeletion] = useState(false);

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
  const customized = isCustomizedCopy(action);
  const disableUpdate = isFromCollector || isUnregistered;
  const disableDuplicate = action.action_payload == null || isUnregistered;
  const disableJsonExport = action.action_payload == null || isUnregistered;
  // Orphaned actions can always be purged; collector actions only once deprecated.
  const disableDelete = !isUnregistered
    && (action.action_payload == null || (isFromCollector && action.action_payload.payload_status !== 'DEPRECATED'));

  // -- Edit --
  // A new copy has no older version in use: its card is replaced at once.
  const editSaved = (saved: ThreatArsenalAction) => (editing === action
    ? afterSave(saved)
    : onUpdate?.(saved, editing?.injector_contract_id));

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
  const handleOpenDuplicate = () => setOpenDuplicate(true);
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
    const response = await customizeThreatArsenalAction(actionId);
    if (response.data) {
      const copy = response.data as ThreatArsenalAction;
      onDuplicate?.(copy);
      MESSAGING$.notifySuccess(t('Customized copy created: it keeps receiving Filigran updates'));
      setEditing(copy);
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
    menuOpen,
    (changed) => {
      onUpdate?.(changed);
      onShowVersions?.(changed);
    },
  );
  const allDisabled = duplicateOff && !!disableJsonExport && updateOff && deleteOff && !canRevoke && !canCustomize;

  // -- Export --
  const handleExportJsonSingle = async () => {
    const response = await exportThreatArsenalAction(actionId);
    const match = (response.headers['content-disposition'] as string).match(/filename="?([^"]+)"?/);
    const filename = match?.[1] ?? 'payload.zip';
    download(response.data, filename, 'application/zip');
  };

  return (
    <>
      <Menu open={menuOpen} onOpenChange={setMenuOpen}>
        <MenuTrigger asChild>
          <IconButton
            icon={<MoreVert fontSize="small" />}
            aria-label={t('More actions')}
            onClick={(event: MouseEvent) => event.stopPropagation()}
            disabled={allDisabled}
            priority="tertiary"
            size="sm"
          />
        </MenuTrigger>
        {/* The card behind opens its drawer on click: a click in the menu stays in the menu. */}
        <MenuContent align="end" onClick={event => event.stopPropagation()}>
          {hasDuplicateCapability && (
            <MenuItem onSelect={handleOpenDuplicate} disabled={disableDuplicate}>{t('Duplicate')}</MenuItem>
          )}
          {canCustomize && (
            <MenuItem onSelect={submitCustomize}>{t('Customize')}</MenuItem>
          )}
          <MenuItem onSelect={handleExportJsonSingle} disabled={disableJsonExport}>{t('Export')}</MenuItem>
          {hasUpdateCapability && (
            <MenuItem onSelect={() => setEditing(action)} disabled={disableUpdate}>{customized ? t('Edit customization') : t('Update')}</MenuItem>
          )}
          {hasDeleteCapability && (
            <MenuItem onSelect={handleOpenDelete} disabled={disableDelete}>{t('Delete')}</MenuItem>
          )}
          {(revocable.length > 0 || liftable.length > 0) && <MenuSeparator />}
          {revocable.length > 0 && (
            <MenuItem onSelect={() => openRevocation('revoke')}>{t('Revoke')}</MenuItem>
          )}
          {liftable.length > 0 && (
            <MenuItem onSelect={() => openRevocation('lift')}>{t('Lift revocation')}</MenuItem>
          )}
        </MenuContent>
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

      <ThreatArsenalActionEditDrawer action={editing} onClose={() => setEditing(null)} onSaved={editSaved} />
    </>
  );
};

export default ThreatArsenalActionPopover;

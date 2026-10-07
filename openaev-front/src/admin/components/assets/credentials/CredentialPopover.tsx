import { Button } from '@filigran/design-system';
import { Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import {
  deleteCredential,
  updateCredential,
} from '../../../../actions/assets/credential-actions';
import ButtonPopover, { type PopoverEntry, type VariantButtonPopover } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type CredentialOutput } from '../../../../utils/api-types';
import { useAbility } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CredentialForm from './CredentialForm';
import { type CredentialFormInitialValues } from './credentialUtils';

export interface CredentialPopoverProps {
  /** Placement, forwarded to the kebab: `icon` in a list row, `toggle` in a
      detail header, where the controls are 36px. */
  variant?: VariantButtonPopover;
  credentialId: string;
  credentialName: string;
  resolveInitialValues?: () => Promise<CredentialFormInitialValues>;
  onUpdate?: (result: CredentialOutput) => void;
  onDelete?: (credentialId: string) => void;
  onRemove?: (credentialId: string) => void;
  disabled?: boolean;
}

const CredentialPopover: FunctionComponent<CredentialPopoverProps> = ({
  variant = 'icon',
  credentialId,
  credentialName,
  resolveInitialValues,
  onUpdate,
  onDelete,
  onRemove,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const ability = useAbility();

  const [openDelete, setOpenDelete] = useState(false);
  const [openRemove, setOpenRemove] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [isLoadingEditValues, setIsLoadingEditValues] = useState(false);
  const [editValues, setEditValues] = useState<CredentialFormInitialValues>();

  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);
  const handleCloseEdit = () => setOpenEdit(false);

  const handleOpenEdit = async () => {
    if (!credentialId) {
      return;
    }
    setOpenEdit(true);
    setIsLoadingEditValues(true);
    if (!resolveInitialValues) {
      setIsLoadingEditValues(false);
      return;
    }
    resolveInitialValues()
      .then(values => setEditValues(values))
      .catch(() => setOpenEdit(false))
      .finally(() => setIsLoadingEditValues(false));
  };

  const submitEdit = (formData: FormData) => {
    return updateCredential(credentialId, formData)
      .then((result: { data: CredentialOutput }) => {
        onUpdate?.(result.data);
        handleCloseEdit();
        return result;
      });
  };

  const submitDelete = () => {
    deleteCredential(credentialId).then(() => {
      onDelete?.(credentialId);
      handleCloseDelete();
    });
  };

  const handleOpenRemove = () => setOpenRemove(true);
  const handleCloseRemove = () => setOpenRemove(false);

  const submitRemove = () => {
    onRemove?.(credentialId);
    handleCloseRemove();
  };

  const entries: PopoverEntry[] = [
    ...(onUpdate
      ? [{
          label: 'Update',
          action: handleOpenEdit,
          userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.CREDENTIALS),
        }]
      : []),
    ...(onRemove
      ? [{
          label: 'Remove from the inject',
          action: handleOpenRemove,
          userRight: true,
        }]
      : []),
    ...(onDelete
      ? [{
          label: 'Delete',
          action: handleOpenDelete,
          userRight: ability.can(ACTIONS.DELETE, SUBJECTS.CREDENTIALS),
        }]
      : []),
  ];

  return (
    <>
      <ButtonPopover variant={variant} disabled={disabled} entries={entries} />
      {openEdit && (
        <Drawer
          open
          handleClose={handleCloseEdit}
          title={t('Update the credential')}
        >
          {isLoadingEditValues
            ? <Loader variant="inElement" />
            : (
                <CredentialForm
                  onSubmit={submitEdit}
                  handleClose={handleCloseEdit}
                  editing
                  initialValues={editValues}
                />
              )}
        </Drawer>
      )}
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={`${t('Do you want to delete the credential:')} ${credentialName}?`}
      />
      <Dialog
        open={openRemove}
        slots={{ transition: Transition }}
        onClose={handleCloseRemove}
        slotProps={{ paper: { elevation: 1 } }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to remove this credential from the inject?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button priority="secondary" onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button priority="primary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default CredentialPopover;

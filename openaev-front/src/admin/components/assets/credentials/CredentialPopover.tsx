import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import { type FunctionComponent, useContext, useState } from 'react';

import {
  deleteCredential,
  updateCredential,
} from '../../../../actions/assets/credential-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import { type CredentialOutput } from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CredentialForm from './CredentialForm';
import { type CredentialFormInitialValues } from './credentialUtils';

export interface CredentialPopoverProps {
  credentialId: string;
  credentialName: string;
  resolveInitialValues?: () => Promise<CredentialFormInitialValues>;
  onUpdate?: (result: CredentialOutput) => void;
  onDelete?: (credentialId: string) => void;
  onRemove?: (credentialId: string) => void;
  disabled?: boolean;
}

const CredentialPopover: FunctionComponent<CredentialPopoverProps> = ({
  credentialId,
  credentialName,
  resolveInitialValues,
  onUpdate,
  onDelete,
  onRemove,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

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
      <ButtonPopover disabled={disabled} entries={entries} />
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
          <Button variant="outlined" color="primary" onClick={handleCloseRemove}>{t('Cancel')}</Button>
          <Button variant="contained" color="primary" onClick={submitRemove}>
            {t('Remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default CredentialPopover;

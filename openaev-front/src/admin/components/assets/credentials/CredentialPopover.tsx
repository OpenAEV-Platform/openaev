import { type FunctionComponent, useContext, useState } from 'react';

import {
  deleteCredential,
  updateCredential,
} from '../../../../actions/assets/credential-actions';
import ButtonPopover, { type PopoverEntry } from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import {
  type CredentialInput,
  type CredentialOutput,
} from '../../../../utils/api-types';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CredentialForm from './CredentialForm';

interface CredentialPopoverProps {
  credentialId: string;
  credentialName: string;
  resolveInitialValues?: () => Promise<CredentialInput>;
  onUpdate: (result: CredentialOutput) => void;
  onDelete: (credentialId: string) => void;
  disabled?: boolean;
}

const CredentialPopover: FunctionComponent<CredentialPopoverProps> = ({
  credentialId,
  credentialName,
  resolveInitialValues,
  onUpdate,
  onDelete,
  disabled = false,
}) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);

  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [isLoadingEditValues, setIsLoadingEditValues] = useState(false);
  const [editValues, setEditValues] = useState<CredentialInput>();

  const handleOpenDelete = () => setOpenDelete(true);
  const handleCloseDelete = () => setOpenDelete(false);
  const handleCloseEdit = () => setOpenEdit(false);

  const handleOpenEdit = async () => {
    setOpenEdit(true);
    setIsLoadingEditValues(true);
    if (!credentialId) {
      return;
    }
    resolveInitialValues?.().then((values) => {
      setEditValues(values);
      setIsLoadingEditValues(false);
    });
  };

  const submitEdit = (input: CredentialInput) => {
    return updateCredential(credentialId, input)
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

  const entries: PopoverEntry[] = [
    {
      label: 'Update',
      action: handleOpenEdit,
      userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
    },
    {
      label: 'Delete',
      action: handleOpenDelete,
      userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_SETTINGS),
    },
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
    </>
  );
};

export default CredentialPopover;

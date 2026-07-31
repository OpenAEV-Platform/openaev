import { type FunctionComponent, useState } from 'react';

import { createCredential } from '../../../../actions/assets/credential-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CredentialInput, type CredentialOutput } from '../../../../utils/api-types';
import CredentialForm from './CredentialForm';

interface Props { onCreate?: (result: CredentialOutput) => void }

const CredentialCreation: FunctionComponent<Props> = ({ onCreate }) => {
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const onSubmit = (input: CredentialInput) => {
    console.log('INPUT --', input);
    return createCredential(input).then((result: { data: CredentialOutput }) => {
      onCreate?.(result.data);
      handleClose();
      return result;
    });
  };

  return (
    <>
      <ButtonCreate onClick={handleOpen} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new credential')}
      >
        <CredentialForm
          onSubmit={onSubmit}
          handleClose={handleClose}
          initialValues={{
            credential_name: '',
            credential_type: 'IDENTITY',
            credential_tags: [],
          } as CredentialInput}
        />
      </Drawer>
    </>
  );
};
export default CredentialCreation;

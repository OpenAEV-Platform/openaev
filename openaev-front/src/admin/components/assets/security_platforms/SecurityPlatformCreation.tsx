import { type FunctionComponent, useState } from 'react';

import { addSecurityPlatform } from '../../../../actions/assets/securityPlatform-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type SecurityPlatform, type SecurityPlatformInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import SecurityPlatformForm from './SecurityPlatformForm';

interface Props { onCreate: (result: SecurityPlatform) => void }

const SecurityPlatformCreation: FunctionComponent<Props> = ({ onCreate }) => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const dispatch = useAppDispatch();
  const onSubmit = (data: SecurityPlatformInput) => {
    dispatch(addSecurityPlatform(data)).then(
      (result: {
        result: string;
        entities: { securityplatforms: Record<string, SecurityPlatform> };
      }) => {
        if (result.entities) {
          if (onCreate) {
            const securityPlatformCreated = result.entities.securityplatforms[result.result];
            onCreate(securityPlatformCreated);
          }
          setOpen(false);
        }
        return result;
      },
    );
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new security platform')}
      >
        <SecurityPlatformForm
          onSubmit={onSubmit}
          handleClose={() => setOpen(false)}
        />
      </Drawer>
    </>
  );
};

export default SecurityPlatformCreation;

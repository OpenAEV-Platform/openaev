import { useState } from 'react';

import { createRole } from '../../../../actions/roles/roles-actions';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { RoleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import RoleForm, { type RoleCreateInput } from './RoleForm';

interface CreateRoleProps { onCreate?: (result: RoleOutput) => void }

const CreateRole = ({ onCreate }: CreateRoleProps) => {
  // Standard hooks
  const [open, setOpen] = useState(false);
  const { t } = useFormatter();

  const dispatch = useAppDispatch();
  const onSubmit = (data: RoleCreateInput) => {
    dispatch(createRole(data))
      .then((result) => {
        if (onCreate) {
          const roleCreated = result.data;
          onCreate(roleCreated);
        }
        return (result.data ? setOpen(false) : result);
      });
  };

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} style={{ right: 230 }} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a new role')}
      >
        <RoleForm onSubmit={onSubmit} handleClose={() => setOpen(false)} editing={false} />
      </Drawer>
    </>
  );
};

export default CreateRole;

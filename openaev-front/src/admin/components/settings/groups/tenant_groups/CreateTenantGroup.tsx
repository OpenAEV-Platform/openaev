import { type FunctionComponent, useCallback } from 'react';

import { addGroup as addGroupAction } from '../../../../../actions/Group';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import useDialog from '../../../../../components/common/dialog/useDialog';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type { Group } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import GroupForm, { type TenantGroupFormInput } from './GroupForm';

interface Props { onCreate: (result: Group) => void }

const CreateTenantGroup: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { open, handleOpen, handleClose } = useDialog();

  const handleSubmit = useCallback(
    async (data: TenantGroupFormInput) => {
      const result = await dispatch(addGroupAction(data));
      if (!result?.result) return result;
      const created: Group = result.entities.groups[result.result];
      onCreate(created);
      handleClose();
      return result;
    },
    [dispatch, onCreate, handleClose],
  );

  return (
    <>
      <ButtonCreate onClick={handleOpen} variant="rightMenu" />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a tenant group')}
      >
        <GroupForm
          onSubmit={handleSubmit}
          onCancel={handleClose}
        />
      </Drawer>
    </>
  );
};

export default CreateTenantGroup;

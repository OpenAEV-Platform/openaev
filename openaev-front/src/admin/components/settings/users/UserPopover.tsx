import { type FunctionComponent, useCallback, useContext, useMemo, useState } from 'react';

import { DEFAULT_TENANT_UUID, deleteTenantUser } from '../../../../actions/tenant/users/user-tenant-action';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import { useFormatter } from '../../../../components/i18n';
import type { UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import UserUpdate from './UserUpdate';

type ActionType = 'Update' | 'Delete';

interface Props {
  user: UserOutput;
  actions: ActionType[];
  onUpdate?: (result: UserOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const UserPopover: FunctionComponent<Props> = ({
  user,
  actions = [],
  onUpdate,
  onDelete,
  inList = false,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const [openEdit, setOpenEdit] = useState(false);
  const [openDelete, setOpenDelete] = useState(false);

  const handleOpenEdit = useCallback(() => setOpenEdit(true), []);
  const handleCloseEdit = useCallback(() => setOpenEdit(false), []);

  const handleOpenDelete = useCallback(() => setOpenDelete(true), []);
  const handleCloseDelete = useCallback(() => setOpenDelete(false), []);

  const submitDelete = useCallback(() => {
    dispatch(deleteTenantUser(DEFAULT_TENANT_UUID, user.user_id)).then(() => {
      onDelete?.(user.user_id);
    });
    handleCloseDelete();
  }, [dispatch, user.user_id, onDelete, handleCloseDelete]);

  const entries = useMemo(() => {
    const items = [];
    if (actions.includes('Update')) {
      items.push({
        label: 'Update',
        action: handleOpenEdit,
        userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
      });
    }
    if (actions.includes('Delete')) {
      items.push({
        label: 'Delete',
        action: handleOpenDelete,
        userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_SETTINGS),
      });
    }
    return items;
  }, [actions, ability, handleOpenEdit, handleOpenDelete]);

  return (
    <>
      <ButtonPopover entries={entries} variant={inList ? 'icon' : undefined} />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to remove this user from the tenant?')}
      />
      <UserUpdate
        user={user}
        open={openEdit}
        onClose={handleCloseEdit}
        onUpdate={onUpdate}
      />
    </>
  );
};

export default UserPopover;

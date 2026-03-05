import {type FunctionComponent, useCallback, useContext, useMemo, useState} from 'react';
import type {UserOutput} from '../../../../../utils/api-types';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import {useFormatter} from '../../../../../components/i18n';
import {useAppDispatch} from '../../../../../utils/hooks';
import {AbilityContext} from '../../../../../utils/permissions/PermissionsProvider';
import {ACTIONS, SUBJECTS} from '../../../../../utils/permissions/types';
import PlatformUserUpdate from './PlatformUserUpdate';
import {deleteUser} from "../../../../../actions/platform/users/user-action";

type ActionType = 'Update' | 'Delete';

interface Props {
  user: UserOutput;
  actions: ActionType[];
  onUpdate?: (result: UserOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const PlatformUserPopover: FunctionComponent<Props> = ({
  user,
  actions = [],
  onUpdate,
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  // Edition
  const [isEditOpen, setIsEditOpen] = useState(false);
  const handleOpenEdit = useCallback(() => {
    setIsEditOpen(true);
  }, []);
  const handleCloseEdit = useCallback(() => {
    setIsEditOpen(false);
  }, []);

  // Deletion
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);
  const handleOpenDelete = useCallback(() => {
    setIsDeleteOpen(true);
  }, []);
  const handleCloseDelete = useCallback(() => {
    setIsDeleteOpen(false);
  }, []);
  const handleDelete = useCallback(async () => {
    await dispatch(deleteUser(user.user_id));
    handleCloseDelete();
    onDelete?.(user.user_id);
  }, [dispatch, user.user_id, onDelete, handleCloseDelete]);

  // Button Popover
  const entries = useMemo(() => {
    const result = [];
    if (actions.includes('Update')) {
      result.push({
        label: t('Update'),
        action: handleOpenEdit,
        userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      });
    }
    if (actions.includes('Delete')) {
      result.push({
        label: t('Delete'),
        action: handleOpenDelete,
        userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      });
    }
    return result;
  }, [actions, ability, handleOpenEdit, handleOpenDelete]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
      {actions.includes('Update')
        && (
          <PlatformUserUpdate
            user={user}
            open={isEditOpen}
            onClose={handleCloseEdit}
            onUpdate={onUpdate}
          />
        )}
      {actions.includes('Delete')
        && (
          <DialogDelete
            open={isDeleteOpen}
            handleClose={handleCloseDelete}
            handleSubmit={handleDelete}
            text={`${t('Do you want to delete this user?')}`}
          />
        )}
    </>
  );
};

export default PlatformUserPopover;


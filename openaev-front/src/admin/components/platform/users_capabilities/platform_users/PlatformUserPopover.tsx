import {type FunctionComponent, useCallback, useContext, useMemo, useState} from 'react';
import type {UserOutput} from '../../../../../utils/api-types';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../../components/common/DialogDelete';
import {useFormatter} from '../../../../../components/i18n';
import {useAppDispatch} from '../../../../../utils/hooks';
import {AbilityContext} from '../../../../../utils/permissions/PermissionsProvider';
import {ACTIONS, SUBJECTS} from '../../../../../utils/permissions/types';
import {deleteUser} from "../../../../../actions/platform/users/user-action";

type ActionType = 'Delete';

interface Props {
  user: UserOutput;
  actions: ActionType[];
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const PlatformUserPopover: FunctionComponent<Props> = ({
  user,
  actions = [],
  onDelete,
  inList = false,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

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
    if (actions.includes('Delete')) {
      result.push({
        label: t('Delete'),
        action: handleOpenDelete,
        userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_GROUPS_AND_ROLES),
      });
    }
    return result;
  }, [actions, ability, handleOpenDelete]);

  return (
    <>
      {entries.length > 0 && <ButtonPopover entries={entries} variant={inList ? 'icon' : 'toggle'} />}
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


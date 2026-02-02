import { useContext, useState } from 'react';

import { type OrganizationHelper, type TagHelper } from '../../../../actions/helper';
import { deleteUser, updateUser, updateUserPassword } from '../../../../actions/users/User';
import { type UserInputForm, type UserResult } from '../../../../actions/users/users-helper';
import ButtonPopover from '../../../../components/common/ButtonPopover';
import DialogDelete from '../../../../components/common/DialogDelete';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type ChangePasswordInput, type UpdateUserInput, type User, type UserOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option, organizationOption, tagOptions } from '../../../../utils/Option';
import { AbilityContext } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import UserForm from './UserForm';
import UserPasswordForm from './UserPasswordForm';

interface UserPopoverProps {
  user: UserOutput;
  onUpdate: (result: User) => void;
  onDelete: (result: string) => void;
}

const UserPopover = ({ user, onUpdate, onDelete }: UserPopoverProps) => {
  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [openEditPassword, setOpenEditPassword] = useState(false);
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);

  const { t } = useFormatter();

  const { organizationsMap, tagsMap } = useHelper(
    (
      helper: OrganizationHelper & TagHelper,
    ) => {
      return {
        organizationsMap: helper.getOrganizationsMap(),
        tagsMap: helper.getTagsMap(),
      };
    },
  );

  const handleOpenEdit = () => setOpenEdit(true);

  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit = (data: UserInputForm) => {
    const inputValues: UpdateUserInput = {
      ...data,
      user_organization: data.user_organization?.id,
      user_tags: data.user_tags?.map((tag: Option) => tag.id),
    };

    return dispatch(updateUser(user.user_id, inputValues)).then((result: UserResult) => {
      if (result?.entities?.users && onUpdate) {
        const userUpdated = result.entities.users[result.result];

        const orgId = userUpdated.user_organization;
        const org = orgId ? organizationsMap[orgId] : undefined;

        const userToUpdate = {
          ...userUpdated,
          user_organization_name: org ? org.organization_name : '',
          user_organization_id: org ? org.organization_id : '',
        };

        onUpdate(userToUpdate);
      }
      return result.result ? handleCloseEdit() : result;
    });
  };

  const handleOpenEditPassword = () => setOpenEditPassword(true);

  const handleCloseEditPassword = () => setOpenEditPassword(false);

  const onSubmitEditPassword = (data: ChangePasswordInput) => dispatch(updateUserPassword(user.user_id, data)).then(() => handleCloseEditPassword());

  const handleOpenDelete = () => setOpenDelete(true);

  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deleteUser(user.user_id)).then(
      () => {
        if (onDelete) {
          onDelete(user.user_id);
        }
      },
    );
    handleCloseDelete();
  };

  const initialValues: UserInputForm = {
    ...user,
    user_organization: organizationOption(
      user.user_organization_id,
      organizationsMap,
    ),
    user_tags: tagOptions(user.user_tags, tagsMap),
  };

  // Button Popover
  const entries = [];
  entries.push({
    label: 'Update',
    action: () => handleOpenEdit(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
  });
  entries.push({
    label: 'Update password',
    action: () => handleOpenEditPassword(),
    userRight: ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS),
  });
  if (user.user_email !== 'admin@openaev.io') entries.push({
    label: 'Delete',
    action: () => handleOpenDelete(),
    userRight: ability.can(ACTIONS.DELETE, SUBJECTS.PLATFORM_SETTINGS),
  });

  return (
    <>
      <ButtonPopover entries={entries} variant="icon" />
      <DialogDelete
        open={openDelete}
        handleClose={handleCloseDelete}
        handleSubmit={submitDelete}
        text={t('Do you want to delete this user?')}
      />
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the user')}
      >
        <UserForm
          initialValues={initialValues}
          editing
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
        />
      </Drawer>
      <Drawer
        open={openEditPassword}
        handleClose={handleCloseEditPassword}
        title={t('Update the user password')}
      >
        <UserPasswordForm
          onSubmit={onSubmitEditPassword}
          handleClose={handleCloseEditPassword}
        />
      </Drawer>
    </>
  );
};

export default UserPopover;

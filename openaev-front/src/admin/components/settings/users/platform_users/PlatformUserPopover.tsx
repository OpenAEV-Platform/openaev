import { type FunctionComponent, useCallback } from 'react';

import { deletePlatformUser, updatePlatformUser } from '../../../../../actions/platform/users/platform-user-action';
import { PLATFORM_USER_SCHEMA_KEY } from '../../../../../actions/platform/users/platform-user-schema';
import { updateUserPassword } from '../../../../../actions/users/User';
import { type UserInputForm } from '../../../../../actions/users/users-helper';
import { useFormatter } from '../../../../../components/i18n';
import type { ChangePasswordInput, UserOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { type Option } from '../../../../../utils/Option';
import { ACTIONS, SUBJECTS } from '../../../../../utils/permissions/types';
import UserPopover from '../tenant_users/UserPopover';

interface Props {
  platformUser: UserOutput;
  actions?: ('Update' | 'Update password' | 'Delete')[];
  onUpdate?: (result: UserOutput) => void;
  onDelete?: (result: string) => void;
  inList?: boolean;
}

const PlatformUserPopover: FunctionComponent<Props> = ({
  platformUser,
  actions = ['Update', 'Update password', 'Delete'],
  onUpdate,
  onDelete,
  inList = false,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const handleUpdate = useCallback((data: UserInputForm) => {
    const { user_organization, user_tags, user_tenants, ...rest } = data;
    const inputValues = {
      ...rest,
      user_organization: user_organization?.id,
      user_tags: user_tags?.map((tag: Option) => tag.id),
      user_tenants: user_tenants?.map((tenant: Option) => tenant.id),
    };
    dispatch(updatePlatformUser(platformUser.user_id, inputValues)).then(
      (result: {
        entities: Record<string, Record<string, UserOutput>>;
        result: string;
      }) => {
        if (result?.result) {
          const updated = result.entities[PLATFORM_USER_SCHEMA_KEY][result.result];
          onUpdate?.(updated);
        }
      },
    );
  }, [dispatch, platformUser.user_id, onUpdate]);

  const handleDelete = useCallback(() => {
    dispatch(deletePlatformUser(platformUser.user_id)).then(() => {
      onDelete?.(platformUser.user_id);
    });
  }, [dispatch, platformUser.user_id, onDelete]);

  const handlePassword = useCallback((data: ChangePasswordInput) => {
    dispatch(updateUserPassword(platformUser.user_id, data));
  }, [dispatch, platformUser.user_id]);

  return (
    <UserPopover
      user={platformUser}
      actions={actions}
      onSubmitUpdate={handleUpdate}
      onSubmitDelete={handleDelete}
      onSubmitPassword={handlePassword}
      deleteMessage={t('Do you want to delete this platform user?')}
      type="PLATFORM"
      permissions={{
        manage: [ACTIONS.MANAGE, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES],
        delete: [ACTIONS.DELETE, SUBJECTS.PLATFORM_USERS_GROUPS_AND_ROLES],
      }}
      inList={inList}
    />
  );
};

export default PlatformUserPopover;

import {type FunctionComponent, useCallback, useMemo} from 'react';
import type {UserInput, UserOutput} from '../../../../../utils/api-types';
import Drawer from '../../../../../components/common/Drawer';
import {useFormatter} from '../../../../../components/i18n';
import {useAppDispatch} from '../../../../../utils/hooks';
import PlatformUserForm from "./PlatformUserForm";
import {updateUser} from "../../../../../actions/platform/users/user-action";
import {PLATFORM_USER_SCHEMA_KEY} from "../../../../../actions/platform/users/user-schema";

interface Props {
  user: UserOutput;
  open: boolean;
  onClose: () => void;
  onUpdate?: (result: UserOutput) => void;
}

const PlatformUserUpdate: FunctionComponent<Props> = ({
  user,
  open,
  onClose,
  onUpdate,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  // Form

  const initialValues = useMemo<UserInput>(
    () => ({
        user_email: user.user_email,
        user_firstname: user.user_firstname ?? '',
        user_lastname: user.user_lastname ?? '',
        user_phone: user.user_phone ?? '',
        user_phone2: user.user_phone2 ?? '',
        user_organization: user.user_organization_id ?? '',
        user_tags: user.user_tags ?? [],
    }),
    [user],
  );

  const handleSubmit = useCallback(
    async (data: UserInput) => {
      const result = await dispatch(updateUser(user.user_id, data));

      if (!result?.result) {
        return;
      }

      const updatedUser = result.entities[PLATFORM_USER_SCHEMA_KEY][result.result];
      onUpdate?.(updatedUser);
      onClose();
    },
    [dispatch, user.user_id, onUpdate, onClose],
  );

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Update user')}
    >
      <PlatformUserForm
        initialValues={initialValues}
        editing
        hasPassword={user.user_has_password}
        hasPgpKey={user.user_has_pgp_key}
        onSubmit={handleSubmit}
        onCancel={onClose}
      />
    </Drawer>
  );
};

export default PlatformUserUpdate;



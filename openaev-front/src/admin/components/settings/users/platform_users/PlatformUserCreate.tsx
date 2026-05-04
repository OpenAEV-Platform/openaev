import { type FunctionComponent, useCallback } from 'react';

import { addPlatformUser } from '../../../../../actions/platform/users/platform-user-action';
import { PLATFORM_USER_SCHEMA_KEY } from '../../../../../actions/platform/users/platform-user-schema';
import { type UserInputForm } from '../../../../../actions/users/users-helper';
import type { UserOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { type Option } from '../../../../../utils/Option';
import UserCreate from '../tenant_users/UserCreate';

interface Props { onCreate: (result: UserOutput) => void }

const PlatformUserCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const dispatch = useAppDispatch();

  const handleSubmit = useCallback(
    async (data: UserInputForm) => {
      const { user_organization, user_tags, user_tenants, ...rest } = data;
      const inputValues = {
        ...rest,
        user_organization: user_organization?.id,
        user_tags: user_tags?.map((tag: Option) => tag.id),
        user_tenants: user_tenants?.map((tenant: Option) => tenant.id),
      };
      const result = await dispatch(addPlatformUser(inputValues));

      if (!result?.result) {
        return result;
      }

      const createdPlatformUser = result.entities[PLATFORM_USER_SCHEMA_KEY][result.result];
      onCreate(createdPlatformUser);

      return result;
    },
    [dispatch, onCreate],
  );

  return (
    <UserCreate
      onSubmit={handleSubmit}
      type="PLATFORM"
      buttonVariant="rightMenu"
    />
  );
};

export default PlatformUserCreate;

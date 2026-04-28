import { type FunctionComponent, useCallback } from 'react';

import { addPlatformUser } from '../../../../../actions/platform/users/platform-user-action';
import { PLATFORM_USER_SCHEMA_KEY } from '../../../../../actions/platform/users/platform-user-schema';
import { type UserInputForm } from '../../../../../actions/users/users-helper';
import type { UserOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import { type Option } from '../../../../../utils/Option';
import UserCreate from '../../../settings/users/UserCreate';

interface Props { onCreate: (result: UserOutput) => void }

const PlatformUserCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const dispatch = useAppDispatch();

  const handleSubmit = useCallback(
    async (data: UserInputForm) => {
      const inputValues = {
        ...data,
        user_organization: typeof data.user_organization === 'object'
          ? data.user_organization?.id
          : data.user_organization,
        user_tags:  data.user_tags?.map((tag: Option | string) =>
          typeof tag === 'object' ? tag.id : tag),
        user_tenants: data.user_tenants?.map((tenant: Option | string) =>
          typeof tenant === 'object' ? tenant.id : tenant),
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
      platform={true}
      type="PLATFORM"
      buttonVariant="rightMenu"
    />
  );
};

export default PlatformUserCreate;

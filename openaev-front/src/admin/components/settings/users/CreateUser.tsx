import { type FunctionComponent, useCallback } from 'react';

import { addUser } from '../../../../actions/users/User';
import { type UserResult } from '../../../../actions/users/users-helper';
import { type User, type UserInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import UserCreate from './UserCreate';

interface CreateUserProps { onCreate?: (user: User) => void }

const CreateUser: FunctionComponent<CreateUserProps> = ({ onCreate }) => {
  const dispatch = useAppDispatch();

  const handleSubmit = useCallback(
    (data: UserInput) => {
      const inputValues = {
        ...data,
        user_organization: data.user_organization,
        user_tags: data.user_tags?.map((tag: Option | string) =>
          typeof tag === 'object' ? tag.id : tag),
      };
      return dispatch(addUser(inputValues)).then((result: UserResult) => {
        if (result?.entities?.users && onCreate) {
          const userCreated = result.entities.users[result.result];
          onCreate(userCreated);
        }
      });
    },
    [dispatch, onCreate],
  );

  return (
    <UserCreate
      onSubmit={handleSubmit}
      type="TENANT"
      buttonStyle={{ right: 230 }}
    />
  );
};

export default CreateUser;

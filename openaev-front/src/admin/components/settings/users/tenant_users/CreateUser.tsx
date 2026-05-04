import { type FunctionComponent, useCallback } from 'react';

import { addUser } from '../../../../../actions/users/User';
import { type UserInputForm, type UserResult } from '../../../../../actions/users/users-helper';
import { type User } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import UserCreate from './UserCreate';

interface CreateUserProps { onCreate?: (user: User) => void }

const CreateUser: FunctionComponent<CreateUserProps> = ({ onCreate }) => {
  const dispatch = useAppDispatch();

  const handleSubmit = useCallback(
    (data: UserInputForm) => {
      const inputValues = { ...data };
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

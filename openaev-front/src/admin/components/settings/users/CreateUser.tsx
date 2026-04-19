import { type FunctionComponent, useCallback } from 'react';

import { addUser } from '../../../../actions/users/User';
import { type UserInputForm, type UserResult } from '../../../../actions/users/users-helper';
import { useFormatter } from '../../../../components/i18n';
import { type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import UserCreate from './UserCreate';

interface CreateUserProps { onCreate?: (user: User) => void }

const CreateUser: FunctionComponent<CreateUserProps> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const handleSubmit = useCallback(
    (data: UserInputForm) => {
      const inputValues = {
        ...data,
        user_organization: data.user_organization?.id,
        user_tags: data.user_tags?.map((tag: Option) => tag.id),
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
      title={t('Create a new user')}
      buttonStyle={{ right: 230 }}
    />
  );
};

export default CreateUser;

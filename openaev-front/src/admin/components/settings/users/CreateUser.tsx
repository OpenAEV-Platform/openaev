import { useState } from 'react';
import { type Dispatch } from 'redux';

import { getOrganizationsMapSelector } from '../../../../actions/selectors';
import { addUser } from '../../../../actions/users/User';
import { type UserInputForm } from '../../../../actions/users/users-helper';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { type CustomAxiosResponse } from '../../../../network';
import { useSelectorHelper } from '../../../../store';
import { type User } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { type Option } from '../../../../utils/Option';
import UserForm from './UserForm';

interface CreateUserProps { onCreate?: (user: User) => void }

const CreateUser = ({ onCreate }: CreateUserProps) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [open, setOpen] = useState(false);

  const handleOpen = () => setOpen(true);
  const handleClose = () => setOpen(false);

  const organizationsMap = useSelectorHelper(getOrganizationsMapSelector);
  const onSubmit = (data: UserInputForm) => {
    const inputValues = {
      ...data,
      user_organization: data.user_organization?.id,
      user_tags: data.user_tags?.map((tag: Option) => tag.id),
    };

    return dispatch(addUser(inputValues) as (dispatch: Dispatch) => Promise<CustomAxiosResponse<User>>).then((result) => {
      if (result?.data && onCreate) {
        const userCreated = result.data;

        const orgId = userCreated.user_organization;
        const org = orgId ? organizationsMap[orgId] : undefined;

        const userToCreate = {
          ...userCreated,
          user_organization_name: org ? org.organization_name : '',
          user_organization_id: org ? org.organization_id : '',
        };

        onCreate(userToCreate);
      }
      return result.data ? handleClose() : result;
    });
  };

  return (
    <>
      <ButtonCreate onClick={handleOpen} style={{ right: 230 }} />

      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a new user')}
      >
        <UserForm
          editing={false}
          onSubmit={onSubmit}
          handleClose={handleClose}
          initialValues={{ user_tags: [] }}
        />
      </Drawer>
    </>
  );
};

export default CreateUser;

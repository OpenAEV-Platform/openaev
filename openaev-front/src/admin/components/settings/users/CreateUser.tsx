import { useState } from 'react';

import type { OrganizationHelper } from '../../../../actions/helper';
import { addUser } from '../../../../actions/users/User';
import { type UserInputForm, type UserResult } from '../../../../actions/users/users-helper';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
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

  const { organizationsMap } = useHelper(
    (
      helper: OrganizationHelper,
    ) => {
      return { organizationsMap: helper.getOrganizationsMap() };
    },
  );
  const onSubmit = (data: UserInputForm) => {
    const inputValues = {
      ...data,
      user_organization: data.user_organization?.id,
      user_tags: data.user_tags?.map((tag: Option) => tag.id),
    };

    return dispatch(addUser(inputValues)).then((result: UserResult) => {
      if (result?.entities?.users && onCreate) {
        const userCreated = result.entities.users[result.result];

        const orgId = userCreated.user_organization;
        const org = orgId ? organizationsMap[orgId] : undefined;

        const userToCreate = {
          ...userCreated,
          user_organization_name: org ? org.organization_name : '',
          user_organization_id: org ? org.organization_id : '',
        };

        onCreate(userToCreate);
      }
      return result.result ? handleClose() : result;
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

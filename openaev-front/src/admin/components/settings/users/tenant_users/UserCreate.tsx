import { type FunctionComponent, useCallback } from 'react';

import { type UserInputForm, type UserType } from '../../../../../actions/users/users-helper';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import useDialog from '../../../../../components/common/dialog/useDialog';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import UserForm from './UserForm';

interface UserCreateProps {
  onSubmit: (data: UserInputForm) => Promise<void> | void;
  type: UserType;
  buttonVariant?: 'rightMenu' | undefined;
  buttonStyle?: React.CSSProperties;
}

const UserCreate: FunctionComponent<UserCreateProps> = ({
  onSubmit,
  type,
  buttonVariant,
  buttonStyle,
}) => {
  const { t } = useFormatter();
  const { open, handleOpen, handleClose } = useDialog();

  const handleSubmit = useCallback(
    (data: UserInputForm) => {
      return Promise.resolve(onSubmit(data)).then((result) => {
        handleClose();
        return result;
      });
    },
    [onSubmit, handleClose],
  );

  return (
    <>
      <ButtonCreate onClick={handleOpen} variant={buttonVariant} style={buttonStyle} />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={type === 'TENANT' ? t('Create a new user') : t('Create a platform user')}
      >
        <UserForm
          editing={false}
          onSubmit={handleSubmit}
          handleClose={handleClose}
          initialValues={{ user_tags: [] }}
          type={type}
        />
      </Drawer>
    </>
  );
};

export default UserCreate;

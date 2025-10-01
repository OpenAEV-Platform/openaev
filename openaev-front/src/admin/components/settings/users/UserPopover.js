import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem, Slide } from '@mui/material';
import * as R from 'ramda';
import { forwardRef, useState } from 'react';
import { useDispatch } from 'react-redux';

import { deleteUser, updateUser, updateUserPassword } from '../../../../actions/User';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { tagOptions } from '../../../../utils/Option';
import { Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import UserForm from './UserForm';
import UserPasswordForm from './UserPasswordForm';

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

const UserPopover = ({ user, organizationsMap, tagsMap, onUpdate, onDelete }) => {
  const [openDelete, setOpenDelete] = useState(false);
  const [openEdit, setOpenEdit] = useState(false);
  const [openEditPassword, setOpenEditPassword] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);
  const dispatch = useDispatch();
  const { t } = useFormatter();

  const handlePopoverOpen = (event) => {
    event.stopPropagation();
    setAnchorEl(event.currentTarget);
  };

  const handlePopoverClose = () => setAnchorEl(null);

  const handleOpenEdit = () => {
    setOpenEdit(true);
    handlePopoverClose();
  };

  const handleCloseEdit = () => setOpenEdit(false);

  const onSubmitEdit = (data) => {
    const inputValues = R.pipe(
      R.assoc(
        'user_organization',
        data.user_organization && data.user_organization.id
          ? data.user_organization.id
          : data.user_organization,
      ),
      R.assoc('user_tags', R.pluck('id', data.user_tags)),
    )(data);
    return dispatch(updateUser(user.user_id, inputValues)).then((result) => {
      if (onUpdate) {
        const userUpdated = result.entities.users[result.result];
        onUpdate(userUpdated);
      }
      handleCloseEdit();
    });
  };

  const handleOpenEditPassword = () => {
    setOpenEditPassword(true);
    handlePopoverClose();
  };

  const handleCloseEditPassword = () => setOpenEditPassword(false);

  const onSubmitEditPassword = (data) => {
    dispatch(updateUserPassword(user.user_id, data)).then(() => handleCloseEditPassword());
  };

  const handleOpenDelete = () => {
    setOpenDelete(true);
    handlePopoverClose();
  };

  const handleCloseDelete = () => setOpenDelete(false);

  const submitDelete = () => {
    dispatch(deleteUser(user.user_id)).then(
      () => {
        if (onDelete) {
          onDelete(user.user_id);
        }
      },
    );
    handleCloseDelete();
  };

  const org = organizationsMap[user.user_organization];
  const userOrganization = org
    ? {
        id: org.organization_id,
        label: org.organization_name,
      }
    : null;
  const userTags = tagOptions(user.user_tags, tagsMap);
  const initialValues = R.pipe(
    R.assoc('user_organization', userOrganization),
    R.assoc('user_tags', userTags),
    R.pick([
      'user_firstname',
      'user_lastname',
      'user_email',
      'user_organization',
      'user_phone',
      'user_phone2',
      'user_pgp_key',
      'user_tags',
      'user_admin',
    ]),
  )(user);
  return (
    <>
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
        <IconButton color="primary" onClick={handlePopoverOpen} aria-haspopup="true" size="large">
          <MoreVert />
        </IconButton>
      </Can>
      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handlePopoverClose}
      >
        <MenuItem onClick={handleOpenEdit}>{t('Update')}</MenuItem>
        <MenuItem onClick={handleOpenEditPassword}>
          {t('Update password')}
        </MenuItem>
        {user.user_email !== 'admin@openaev.io' && (
          <MenuItem onClick={handleOpenDelete}>{t('Delete')}</MenuItem>
        )}
      </Menu>
      <Dialog
        open={openDelete}
        TransitionComponent={Transition}
        onClose={handleCloseDelete}
        PaperProps={{ elevation: 1 }}
      >
        <DialogContent>
          <DialogContentText>
            {t('Do you want to delete this user?')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDelete}>{t('Cancel')}</Button>
          <Button color="secondary" onClick={submitDelete}>
            {t('Delete')}
          </Button>
        </DialogActions>
      </Dialog>
      <Drawer
        open={openEdit}
        handleClose={handleCloseEdit}
        title={t('Update the user')}
      >
        <UserForm
          initialValues={initialValues}
          editing={true}
          onSubmit={onSubmitEdit}
          handleClose={handleCloseEdit}
        />
      </Drawer>
      <Drawer
        open={openEditPassword}
        handleClose={handleCloseEditPassword}
        title={t('Update the user password')}
      >
        <UserPasswordForm
          onSubmit={onSubmitEditPassword}
          handleClose={handleCloseEditPassword}
        />
      </Drawer>
    </>
  );
};

export default UserPopover;

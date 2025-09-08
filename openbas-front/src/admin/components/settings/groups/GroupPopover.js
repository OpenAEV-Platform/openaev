import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { deleteGroup, fetchGroup, updateGroupInformation, updateGroupRoles, updateGroupUsers } from '../../../../actions/Group';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import inject18n from '../../../../components/i18n';
import { Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import GroupManageGrants from './grants/GroupManageGrants.tsx';
import GroupForm from './GroupForm';
import GroupManageRoles from './GroupManageRoles.js';
import GroupManageUsers from './GroupManageUsers';

class GroupPopover extends Component {
  constructor(props) {
    super(props);
    this.state = {
      openDelete: false,
      openEdit: false,
      openUsers: false,
      openGrants: false,
      openPopover: false,
      keyword: '',
      tags: [],
      usersIds: props.groupUsersIds,
      rolesIds: props.groupRolesIds,
    };
  }

  handlePopoverOpen(event) {
    this.setState({ anchorEl: event.currentTarget });
  }

  handlePopoverClose() {
    this.setState({ anchorEl: null });
  }

  handleOpenEdit() {
    this.setState({ openEdit: true });
    this.handlePopoverClose();
  }

  handleCloseEdit() {
    this.setState({ openEdit: false });
  }

  async onSubmitEdit(data) {
    return this.props
      .updateGroupInformation(this.props.group.group_id, data)
      .then((result) => {
        if (this.props.onUpdate) {
          const groupUpdated = result.entities.groups[result.result];
          this.props.onUpdate(groupUpdated);
        }
        this.setState({ openEdit: false });
      });
  }

  handleOpenUsers() {
    this.setState({
      openUsers: true,
      usersIds: this.props.groupUsersIds,
    });
    this.handlePopoverClose();
  }

  handleAddTag(value) {
    if (value) {
      this.setState({ tags: [value] });
    }
  }

  handleCloseUsers() {
    this.setState({
      openUsers: false,
      keyword: '',
    });
  }

  submitUpdateUsers(userIds) {
    this.props.updateGroupUsers(this.props.group.group_id, { group_users: userIds }).then(this.fetchAndUpdateGroup.bind(this));
    this.handleCloseUsers();
  }

  handleOpenGrants() {
    this.setState({ openGrants: true });
    this.handlePopoverClose();
  }

  handleCloseGrants() {
    this.setState({ openGrants: false });
  }

  handleOpenRoles() {
    this.setState({
      openRoles: true,
      rolesIds: this.props.groupRolesIds,
    });
    this.handlePopoverClose();
  }

  submitUpdateRoles(roleIds) {
    this.props.updateGroupRoles(this.props.group.group_id, { group_roles: roleIds }).then(this.fetchAndUpdateGroup.bind(this));
    this.handleCloseRoles();
  }

  handleCloseRoles() {
    this.setState({ openRoles: false });
  }

  fetchAndUpdateGroup() {
    this.props.fetchGroup(this.props.group.group_id).then((result) => {
      if (this.props.onUpdate) {
        this.props.onUpdate(result.entities.groups[this.props.group.group_id]);
      }
    });
  }

  handleOpenDelete() {
    this.setState({ openDelete: true });
    this.handlePopoverClose();
  }

  handleCloseDelete() {
    this.setState({ openDelete: false });
  }

  submitDelete() {
    this.props.deleteGroup(this.props.group.group_id).then(
      () => {
        if (this.props.onDelete) {
          this.props.onDelete(this.props.group.group_id);
        }
      },
    );
    this.handleCloseDelete();
  }

  render() {
    const { t, group } = this.props;
    const initialValues = R.pick(
      [
        'group_name',
        'group_description',
        'group_default_user_assign',
      ],
      group,
    );
    return (
      <>

        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <IconButton
            color="primary"
            onClick={this.handlePopoverOpen.bind(this)}
            aria-haspopup="true"
            size="large"
          >
            <MoreVert />
          </IconButton>
        </Can>
        <Menu
          anchorEl={this.state.anchorEl}
          open={Boolean(this.state.anchorEl)}
          onClose={this.handlePopoverClose.bind(this)}
        >
          <MenuItem onClick={this.handleOpenEdit.bind(this)}>
            {t('Update')}
          </MenuItem>
          <MenuItem onClick={this.handleOpenUsers.bind(this)}>
            {t('Manage users')}
          </MenuItem>
          <MenuItem onClick={this.handleOpenGrants.bind(this)}>
            {t('Manage grants')}
          </MenuItem>
          <MenuItem onClick={this.handleOpenRoles.bind(this)}>
            {t('Manage roles')}
          </MenuItem>
          <MenuItem onClick={this.handleOpenDelete.bind(this)}>
            {t('Delete')}
          </MenuItem>
        </Menu>
        <Dialog
          open={this.state.openDelete}
          TransitionComponent={Transition}
          onClose={this.handleCloseDelete.bind(this)}
          PaperProps={{ elevation: 1 }}
        >
          <DialogContent>
            <DialogContentText>
              {t('Do you want to delete this group?')}
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={this.handleCloseDelete.bind(this)}>
              {t('Cancel')}
            </Button>
            <Button color="secondary" onClick={this.submitDelete.bind(this)}>
              {t('Delete')}
            </Button>
          </DialogActions>
        </Dialog>
        <Drawer
          open={this.state.openEdit}
          handleClose={this.handleCloseEdit.bind(this)}
          title={t('Update the group')}
        >
          <GroupForm
            initialValues={initialValues}
            editing={true}
            onSubmit={this.onSubmitEdit.bind(this)}
            handleClose={this.handleCloseEdit.bind(this)}
          />
        </Drawer>
        <GroupManageUsers
          initialState={this.state.usersIds}
          open={this.state.openUsers}
          onClose={this.handleCloseUsers.bind(this)}
          onSubmit={this.submitUpdateUsers.bind(this)}
        />
        <GroupManageRoles
          initialState={this.state.rolesIds}
          open={this.state.openRoles}
          onClose={this.handleCloseRoles.bind(this)}
          onSubmit={this.submitUpdateRoles.bind(this)}
        />
        <GroupManageGrants
          group={group}
          openGrants={this.state.openGrants}
          handleCloseGrants={this.handleCloseGrants.bind(this)}
          fetchAndUpdateGroup={this.fetchAndUpdateGroup.bind(this)}
        />
      </>
    );
  }
}

GroupPopover.propTypes = {
  t: PropTypes.func,
  group: PropTypes.object,
  fetchGroup: PropTypes.func,
  updateGroupUsers: PropTypes.func,
  updateGroupRoles: PropTypes.func,
  updateGroupInformation: PropTypes.func,
  deleteGroup: PropTypes.func,
  groupUsersIds: PropTypes.array,
  groupRolesIds: PropTypes.array,
};

const select = () => {
  return {};
};

export default R.compose(
  connect(select, {
    fetchGroup,
    updateGroupInformation,
    updateGroupUsers,
    updateGroupRoles,
    deleteGroup,
  }),
  inject18n,
)(GroupPopover);

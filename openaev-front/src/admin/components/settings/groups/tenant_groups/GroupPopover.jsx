import { Button, Dialog, DialogActions, DialogContent, DialogContentText } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { deleteGroup, fetchGroup, updateGroupInformation, updateGroupRoles, updateGroupUsers } from '../../../../../actions/Group';
import ButtonPopover from '../../../../../components/common/ButtonPopover';
import Drawer from '../../../../../components/common/Drawer';
import Transition from '../../../../../components/common/Transition';
import inject18n from '../../../../../components/i18n';
import { withAbility } from '../../../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED, SUBJECTS } from '../../../../../utils/permissions/types';
import GroupManageGrants from './grants/GroupManageGrants.tsx';
import GroupForm from './GroupForm';
import GroupManageRoles from './GroupManageRoles';
import GroupManageUsers from './GroupManageUsers';

class GroupPopoverComponent extends Component {
  constructor(props) {
    super(props);
    this.state = {
      openDelete: false,
      openEdit: false,
      openUsers: false,
      openGrants: false,
      keyword: '',
      tags: [],
      usersIds: props.groupUsersIds,
      rolesIds: props.groupRolesIds,
    };
  }

  handleOpenEdit() {
    this.setState({ openEdit: true });
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
  }

  handleCloseGrants() {
    this.setState({ openGrants: false });
  }

  handleOpenRoles() {
    this.setState({
      openRoles: true,
      rolesIds: this.props.groupRolesIds,
    });
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
    const { t, group, ability } = this.props;
    // Reading the group is enough to open the menu; the actions inside are greyed out instead,
    // each carrying the shared "Permission required" tooltip.
    const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES);
    const canDelete = ability.can(ACTIONS.DELETE, SUBJECTS.TENANT_USERS_GROUPS_AND_ROLES);
    const entries = [
      {
        label: 'Update',
        action: this.handleOpenEdit.bind(this),
        disabled: !canManage,
      },
      {
        label: 'Manage users',
        action: this.handleOpenUsers.bind(this),
        disabled: !canManage,
      },
      {
        label: 'Manage grants',
        action: this.handleOpenGrants.bind(this),
        disabled: !canManage,
      },
      {
        label: 'Manage roles',
        action: this.handleOpenRoles.bind(this),
        disabled: !canManage,
      },
      {
        label: 'Delete',
        action: this.handleOpenDelete.bind(this),
        disabled: !canDelete,
      },
    ].map(entry => ({
      ...entry,
      userRight: true,
      disabledMessage: PERMISSION_REQUIRED,
    }));
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

        <ButtonPopover entries={entries} variant="icon" />
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
            <Button variant="outlined" color="primary" onClick={this.handleCloseDelete.bind(this)}>
              {t('Cancel')}
            </Button>
            <Button variant="contained" color="error" onClick={this.submitDelete.bind(this)}>
              {t('Delete')}
            </Button>
          </DialogActions>
        </Dialog>
        <Drawer
          open={this.state.openEdit}
          handleClose={this.handleCloseEdit.bind(this)}
          title={`${t('Update')} ${group.group_name}`}
        >
          <GroupForm
            initialValues={initialValues}
            editing
            onSubmit={this.onSubmitEdit.bind(this)}
            onCancel={this.handleCloseEdit.bind(this)}
          />
        </Drawer>
        <GroupManageUsers
          initialState={this.state.usersIds}
          groupName={group.group_name}
          open={this.state.openUsers}
          onClose={this.handleCloseUsers.bind(this)}
          onSubmit={this.submitUpdateUsers.bind(this)}
        />
        <GroupManageRoles
          initialState={this.state.rolesIds}
          groupName={group.group_name}
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

GroupPopoverComponent.propTypes = {
  t: PropTypes.func,
  group: PropTypes.object,
  fetchGroup: PropTypes.func,
  updateGroupUsers: PropTypes.func,
  updateGroupRoles: PropTypes.func,
  updateGroupInformation: PropTypes.func,
  deleteGroup: PropTypes.func,
  groupUsersIds: PropTypes.array,
  groupRolesIds: PropTypes.array,
  ability: PropTypes.object,
};

const select = () => {
  return {};
};

const GroupPopover = R.compose(
  connect(select, {
    fetchGroup,
    updateGroupInformation,
    updateGroupUsers,
    updateGroupRoles,
    deleteGroup,
  }),
  inject18n,
  withAbility,
)(GroupPopoverComponent);

export default GroupPopover;

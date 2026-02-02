import { MoreVert } from '@mui/icons-material';
import {
  Button, Dialog, DialogActions, DialogContent, DialogContentText,
  IconButton, Menu, MenuItem,
} from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { deleteOrganization, updateOrganization } from '../../../../actions/Organization';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import inject18n from '../../../../components/i18n';
import { tagOptions } from '../../../../utils/Option';
import { Can } from '../../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import OrganizationForm from './OrganizationForm';

class OrganizationPopoverComponent extends Component {
  constructor(props) {
    super(props);
    this.state = {
      openDelete: false,
      openEdit: props.openEditOnInit,
      openPopover: false,
    };
  }

  handlePopoverOpen(event) {
    event.stopPropagation();
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

  onSubmitEdit(data) {
    const inputValues = R.pipe(
      R.assoc('organization_tags', R.pluck('id', data.organization_tags)),
    )(data);
    return this.props
      .updateOrganization(this.props.organization.organization_id, inputValues)
      .then(() => this.handleCloseEdit());
  }

  handleOpenDelete() {
    this.setState({ openDelete: true });
    this.handlePopoverClose();
  }

  handleCloseDelete() {
    this.setState({ openDelete: false });
  }

  submitDelete() {
    this.props.deleteOrganization(this.props.organization.organization_id);
    this.handleCloseDelete();
  }

  render() {
    const { t, organization, tagsMap } = this.props;
    const organizationTags = tagOptions(
      organization.organization_tags,
      tagsMap,
    );
    const initialValues = R.pipe(
      R.assoc('organization_tags', organizationTags),
      R.pick([
        'organization_name',
        'organization_description',
        'organization_tags',
      ]),
    )(organization);
    return (
      <div>
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <IconButton
            onClick={this.handlePopoverOpen.bind(this)}
            aria-haspopup="true"
            size="large"
            color="primary"
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
              {t('Do you want to delete this organization?')}
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
          title={t('Update the organization')}
        >
          <OrganizationForm
            initialValues={initialValues}
            editing
            onSubmit={this.onSubmitEdit.bind(this)}
            handleClose={this.handleCloseEdit.bind(this)}
          />
        </Drawer>
      </div>
    );
  }
}

OrganizationPopoverComponent.propTypes = {
  t: PropTypes.func,
  organization: PropTypes.object,
  tagsMap: PropTypes.object,
  updateOrganization: PropTypes.func,
  deleteOrganization: PropTypes.func,
  openEditOnInit: PropTypes.bool,
};

const OrganizationPopover = R.compose(
  connect(null, {
    updateOrganization,
    deleteOrganization,
  }),
  inject18n,
)(OrganizationPopoverComponent);

export default OrganizationPopover;

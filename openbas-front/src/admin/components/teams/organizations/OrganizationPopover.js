import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton, Menu, MenuItem, Slide } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component, forwardRef } from 'react';
import { connect } from 'react-redux';

import { deleteOrganization, updateOrganization } from '../../../../actions/Organization';
import inject18n from '../../../../components/i18n';
import { tagOptions } from '../../../../utils/Option';
import OrganizationForm from './OrganizationForm';

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

class OrganizationPopover extends Component {
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
        <IconButton
          onClick={this.handlePopoverOpen.bind(this)}
          aria-haspopup="true"
          size="large"
          color="primary"
        >
          <MoreVert />
        </IconButton>
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
        <Dialog
          TransitionComponent={Transition}
          open={this.state.openEdit}
          onClose={this.handleCloseEdit.bind(this)}
          fullWidth={true}
          maxWidth="md"
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Update the organization')}</DialogTitle>
          <DialogContent>
            <OrganizationForm
              initialValues={initialValues}
              editing={true}
              onSubmit={this.onSubmitEdit.bind(this)}
              handleClose={this.handleCloseEdit.bind(this)}
            />
          </DialogContent>
        </Dialog>
      </div>
    );
  }
}

OrganizationPopover.propTypes = {
  t: PropTypes.func,
  organization: PropTypes.object,
  tagsMap: PropTypes.object,
  updateOrganization: PropTypes.func,
  deleteOrganization: PropTypes.func,
  openEditOnInit: PropTypes.bool,
};

export default R.compose(
  connect(null, {
    updateOrganization,
    deleteOrganization,
  }),
  inject18n,
)(OrganizationPopover);

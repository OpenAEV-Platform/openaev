import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, IconButton, Menu, MenuItem } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { deleteKillChainPhase, updateKillChainPhase } from '../../../../actions/KillChainPhase';
import Drawer from '../../../../components/common/Drawer';
import Transition from '../../../../components/common/Transition';
import inject18n from '../../../../components/i18n';
import { Can } from '../../../../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types.js';
import KillChainPhaseForm from './KillChainPhaseForm';

class KillChainPhasePopover extends Component {
  constructor(props) {
    super(props);
    this.state = {
      openDelete: false,
      openEdit: false,
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
    return this.props
      .updateKillChainPhase(this.props.killChainPhase.phase_id, data)
      .then((result) => {
        if (this.props.onUpdate) {
          const killChainPhaseUpdated = result.entities.killchainphases[result.result];
          this.props.onUpdate(killChainPhaseUpdated);
        }
        this.handleCloseEdit();
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
    this.props.deleteKillChainPhase(this.props.killChainPhase.phase_id).then(
      () => {
        if (this.props.onDelete) {
          this.props.onDelete(this.props.killChainPhase.phase_id);
        }
      },
    );
    this.handleCloseDelete();
  }

  render() {
    const { t } = this.props;
    const initialValues = R.pipe(R.pick(['phase_name', 'phase_shortname', 'phase_kill_chain_name', 'phase_order', 'phase_external_id']))(
      this.props.killChainPhase,
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
              {t('Do you want to delete this kill chain phase?')}
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
          title={t('Update the kill chain phase')}
        >
          <KillChainPhaseForm
            initialValues={initialValues}
            editing={true}
            onSubmit={this.onSubmitEdit.bind(this)}
            handleClose={this.handleCloseEdit.bind(this)}
          />
        </Drawer>
      </>
    );
  }
}

KillChainPhasePopover.propTypes = {
  t: PropTypes.func,
  killChainPhase: PropTypes.object,
  updateKillChainPhase: PropTypes.func,
  onUpdate: PropTypes.func,
  deleteKillChainPhase: PropTypes.func,
  onDelete: PropTypes.func,
};

export default R.compose(
  connect(null, {
    updateKillChainPhase,
    deleteKillChainPhase,
  }),
  inject18n,
)(KillChainPhasePopover);

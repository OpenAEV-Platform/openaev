import { MoreVert } from '@mui/icons-material';
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton, Menu, MenuItem } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { deleteLog, updateLog } from '../../../../../actions/Log';
import { storeHelper } from '../../../../../actions/Schema';
import Transition from '../../../../../components/common/Transition';
import inject18n from '../../../../../components/i18n';
import { tagOptions } from '../../../../../utils/Option';
import LogForm from './LogForm';

class LogPopover extends Component {
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
    const inputValues = R.pipe(
      R.assoc('log_tags', R.pluck('id', data.log_tags)),
    )(data);
    return this.props
      .updateLog(this.props.exerciseId, this.props.log.log_id, inputValues)
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
    this.props.deleteLog(this.props.exerciseId, this.props.log.log_id);
    this.handleCloseDelete();
  }

  render() {
    const { t, log, tagsMap } = this.props;
    const logTags = tagOptions(log.log_tags, tagsMap);
    const initialValues = R.pipe(
      R.assoc('log_tags', logTags),
      R.pick(['log_title', 'log_content', 'log_tags']),
    )(log);

    return (
      <div>
        <IconButton
          onClick={this.handlePopoverOpen.bind(this)}
          aria-haspopup="true"
          size="large"
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
              {t('Do you want to delete this log?')}
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
          <DialogTitle>{t('Update the log')}</DialogTitle>
          <DialogContent>
            <LogForm
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

LogPopover.propTypes = {
  t: PropTypes.func,
  exerciseId: PropTypes.string,
  exercise: PropTypes.object,
  log: PropTypes.object,
  updateLog: PropTypes.func,
  deleteLog: PropTypes.func,
};

const select = (state, ownProps) => {
  const helper = storeHelper(state);
  const { exerciseId } = ownProps;
  return {
    exercise: helper.getExercise(exerciseId).toJS(),
    tagsMap: helper.getTagsMap().toJS(),
  };
};

export default R.compose(
  connect(select, {
    updateLog,
    deleteLog,
  }),
  inject18n,
)(LogPopover);

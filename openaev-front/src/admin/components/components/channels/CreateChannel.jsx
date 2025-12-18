import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addChannel } from '../../../../actions/channels/channel-action';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import ChannelForm from './ChannelForm';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
});

class CreateChannel extends Component {
  constructor(props) {
    super(props);
    this.state = { open: false };
  }

  handleOpen() {
    this.setState({ open: true });
  }

  handleClose() {
    this.setState({ open: false });
  }

  onSubmit(data) {
    return this.props
      .addChannel(data)
      .then(result => (result.result ? this.handleClose() : result));
  }

  render() {
    const { classes, t } = this.props;
    return (
      <div>
        <Fab
          onClick={this.handleOpen.bind(this)}
          color="primary"
          aria-label="Add"
          className={classes.createButton}
        >
          <Add />
        </Fab>
        <Drawer
          open={this.state.open}
          handleClose={this.handleClose.bind(this)}
          title={t('Create a new channel')}
        >
          <ChannelForm
            onSubmit={this.onSubmit.bind(this)}
            initialValues={{}}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </div>
    );
  }
}

CreateChannel.propTypes = {
  classes: PropTypes.object,
  t: PropTypes.func,
  addChannel: PropTypes.func,
};

export default R.compose(
  connect(null, { addChannel }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateChannel);

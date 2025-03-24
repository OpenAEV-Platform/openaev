import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addPayload } from '../../../actions/payloads/payload-actions';
import Drawer from '../../../components/common/Drawer';
import inject18n from '../../../components/i18n';
import PayloadForm from './PayloadForm.tsx';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
});

class CreatePayload extends Component {
  constructor(props) {
    super(props);
    this.state = { open: false };
  }

  handleOpen() {
    this.setState({ open: true });
  }

  handleClose() {
    this.setState({
      open: false,
      activeStep: 0,
      selectedType: null,
    });
  }

  onSubmit(data) {
    function handleCleanupCommandValue(payload_cleanup_command) {
      return payload_cleanup_command === '' ? null : payload_cleanup_command;
    }

    function handleCleanupExecutorValue(payload_cleanup_executor, payload_cleanup_command) {
      if (payload_cleanup_executor !== '' && handleCleanupCommandValue(payload_cleanup_command) !== null) {
        return payload_cleanup_executor;
      }
      return null;
    }

    const inputValues = R.pipe(
      R.assoc('payload_source', 'MANUAL'),
      R.assoc('payload_status', 'VERIFIED'),
      R.assoc('payload_platforms', R.pluck('id', data.payload_platforms)),
      R.assoc('payload_tags', data.payload_tags),
      R.assoc('payload_attack_patterns', data.payload_attack_patterns),
      R.assoc('executable_file', data.executable_file?.id),
      R.assoc('payload_cleanup_executor', handleCleanupExecutorValue(data.payload_cleanup_executor, data.payload_cleanup_command)),
      R.assoc('payload_cleanup_command', handleCleanupCommandValue(data.payload_cleanup_command)),
    )(data);
    return this.props
      .addPayload(inputValues)
      .then((result) => {
        if (this.props.onCreate) {
          const payloadCreated = result.entities.payloads[result.result];
          this.props.onCreate(payloadCreated);
        }
        return (result.result ? this.handleClose() : result);
      });
  }

  render() {
    const { classes, t } = this.props;
    const { open } = this.state;

    return (
      <>
        <Fab
          onClick={this.handleOpen.bind(this)}
          color="primary"
          aria-label="Add"
          className={classes.createButton}
        >
          <Add />
        </Fab>
        <Drawer
          open={open}
          handleClose={this.handleClose.bind(this)}
          title={t('Create a new payload')}
          containerStyle={{ height: '100%' }}
        >
          <PayloadForm
            editing={false}
            onSubmit={this.onSubmit.bind(this)}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </>
    );
  }
}

CreatePayload.propTypes = {
  t: PropTypes.func,
  organizations: PropTypes.array,
  addPayload: PropTypes.func,
  onCreate: PropTypes.func,
};

export default R.compose(
  connect(null, { addPayload }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreatePayload);

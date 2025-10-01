import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addKillChainPhase } from '../../../../actions/KillChainPhase';
import { storeHelper } from '../../../../actions/Schema.js';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import KillChainPhaseForm from './KillChainPhaseForm';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
});

class CreateKillChainPhase extends Component {
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
      .addKillChainPhase(data)
      .then((result) => {
        if (this.props.onCreate) {
          const killChainPhaseCreated = result.entities.killchainphases[result.result];
          this.props.onCreate(killChainPhaseCreated);
        }
        return (result.result ? this.handleClose() : result);
      });
  }

  render() {
    const { classes, t } = this.props;
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
          open={this.state.open}
          handleClose={this.handleClose.bind(this)}
          title={t('Create a new kill chain phase')}
        >
          <KillChainPhaseForm
            editing={false}
            onSubmit={this.onSubmit.bind(this)}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </>
    );
  }
}

CreateKillChainPhase.propTypes = {
  t: PropTypes.func,
  addKillChainPhase: PropTypes.func,
  onCreate: PropTypes.func,
};

const select = (state) => {
  const helper = storeHelper(state);
  return { organizations: helper.getOrganizations().toJS() };
};

export default R.compose(
  connect(select, { addKillChainPhase }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateKillChainPhase);

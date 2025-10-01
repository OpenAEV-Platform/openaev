import { RouteOutlined } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle } from '@mui/material';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addKillChainPhase } from '../actions/KillChainPhase';
import { storeHelper } from '../actions/Schema';
import KillChainPhaseForm from '../admin/components/settings/kill_chain_phases/KillChainPhaseForm';
import { Can } from '../utils/permissions/PermissionsProvider.js';
import { ACTIONS, SUBJECTS } from '../utils/permissions/types.js';
import Autocomplete from './Autocomplete';
import inject18n from './i18n';

const styles = () => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
});

class KillChainPhaseField extends Component {
  constructor(props) {
    super(props);
    this.state = {
      killChainPhaseCreation: false,
      killChainPhaseInput: '',
    };
  }

  handleOpenKillChainPhaseCreation() {
    this.setState({ killChainPhaseCreation: true });
  }

  handleCloseKillChainPhaseCreation() {
    this.setState({ killChainPhaseCreation: false });
  }

  onSubmit(data) {
    const { name, setFieldValue, values } = this.props;
    this.props.addKillChainPhase(data).then((result) => {
      if (result.result) {
        const newKillChainPhase = result.entities.killchainphases[result.result];
        const killChainPhases = R.append(
          {
            id: newKillChainPhase.phase_id,
            label: `[${newKillChainPhase.phase_kill_chain_name}] ${newKillChainPhase.phase_name}`,
          },
          values[name],
        );
        setFieldValue(name, killChainPhases);
        return this.handleCloseKillChainPhaseCreation();
      }
      return result;
    });
  }

  render() {
    const {
      t,
      name,
      killChainPhases,
      classes,
      onKeyDown,
      style,
      label,
      placeholder,
    } = this.props;
    const killChainPhasesOptions = killChainPhases.map(
      n => ({
        id: n.phase_id,
        label: `[${n.phase_kill_chain_name}] ${n.phase_name}`,
      }),
    );

    return (
      <>
        <Autocomplete
          variant="standard"
          size="small"
          name={name}
          fullWidth={true}
          multiple={true}
          label={label}
          placeholder={placeholder}
          options={killChainPhasesOptions}
          style={style}
          openCreate={this.handleOpenKillChainPhaseCreation.bind(this)}
          onKeyDown={onKeyDown}
          renderOption={(props, option) => (
            <Box component="li" {...props} key={option.id}>
              <div className={classes.icon}>
                <RouteOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </Box>
          )}
          classes={{ clearIndicator: classes.autoCompleteIndicator }}
        />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.PLATFORM_SETTINGS}>
          <Dialog
            open={this.state.killChainPhaseCreation}
            onClose={this.handleCloseKillChainPhaseCreation.bind(this)}
            PaperProps={{ elevation: 1 }}
          >
            <DialogTitle>{t('Create a new kill chain phase')}</DialogTitle>
            <DialogContent>
              <KillChainPhaseForm
                onSubmit={this.onSubmit.bind(this)}
                handleClose={this.handleCloseKillChainPhaseCreation.bind(this)}
              />
            </DialogContent>
          </Dialog>
        </Can>
      </>
    );
  }
}

const select = (state) => {
  const helper = storeHelper(state);
  return { killChainPhases: helper.getKillChainPhases().toJS() };
};

export default R.compose(
  connect(select, { addKillChainPhase }),
  inject18n,
  Component => withStyles(Component, styles),
)(KillChainPhaseField);

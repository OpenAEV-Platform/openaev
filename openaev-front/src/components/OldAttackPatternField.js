import { RouteOutlined } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle } from '@mui/material';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addAttackPattern, fetchAttackPatterns } from '../actions/AttackPattern';
import { storeHelper } from '../actions/Schema';
import AttackPatternForm from '../admin/components/settings/attack_patterns/AttackPatternForm';
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

class OldAttackPatternField extends Component {
  constructor(props) {
    super(props);
    this.state = {
      attackPatternCreation: false,
      attackPatternInput: '',
    };
  }

  componentDidMount() {
    this.props.fetchAttackPatterns();
  }

  handleOpenAttackPatternCreation() {
    this.setState({ attackPatternCreation: true });
  }

  handleCloseAttackPatternCreation() {
    this.setState({ attackPatternCreation: false });
  }

  onSubmit(data) {
    const { name, setFieldValue, values, killChainPhasesMap, useExternalId } = this.props;
    this.props.addAttackPattern(data).then((result) => {
      if (result.result) {
        const newAttackPattern = result.entities.attackpatterns[result.result];
        const killChainPhase = R.head(newAttackPattern.attack_pattern_kill_chain_phases);
        const killChainName = killChainPhase ? killChainPhasesMap[killChainPhase]?.phase_kill_chain_name ?? null : null;
        const attackPatterns = R.append(
          {
            id: useExternalId ? newAttackPattern.attack_pattern_external_id : newAttackPattern.attack_pattern_id,
            label: killChainName ? `[${killChainName}] [${newAttackPattern.attack_pattern_external_id}] ${newAttackPattern.attack_pattern_name}` : `[${newAttackPattern.attack_pattern_external_id}] ${newAttackPattern.attack_pattern_name}`,
          },
          values[name],
        );
        setFieldValue(name, attackPatterns);
        return this.handleCloseAttackPatternCreation();
      }
      return result;
    });
  }

  render() {
    const {
      t,
      name,
      attackPatterns,
      killChainPhasesMap,
      classes,
      onKeyDown,
      style,
      label,
      placeholder,
      useExternalId,
    } = this.props;
    const attackPatternsOptions = attackPatterns.map(
      (n) => {
        const killChainPhase = R.head(n.attack_pattern_kill_chain_phases);
        const killChainName = killChainPhase ? killChainPhasesMap[killChainPhase]?.phase_kill_chain_name ?? null : null;
        return {
          id: useExternalId ? n.attack_pattern_external_id : n.attack_pattern_id,
          label: killChainName ? `[${killChainName}] [${n.attack_pattern_external_id}] ${n.attack_pattern_name}` : `[${n.attack_pattern_external_id}] ${n.attack_pattern_name}`,
        };
      },
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
          options={attackPatternsOptions}
          style={style}
          openCreate={this.handleOpenAttackPatternCreation.bind(this)}
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
            open={this.state.attackPatternCreation}
            onClose={this.handleCloseAttackPatternCreation.bind(this)}
            PaperProps={{ elevation: 1 }}
          >
            <DialogTitle>{t('Create a new attack pattern')}</DialogTitle>
            <DialogContent>
              <AttackPatternForm
                onSubmit={this.onSubmit.bind(this)}
                handleClose={this.handleCloseAttackPatternCreation.bind(this)}
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
  return {
    killChainPhasesMap: helper.getKillChainPhasesMap().toJS(),
    attackPatterns: helper.getAttackPatterns().toJS(),
  };
};

export default R.compose(
  connect(select, {
    fetchAttackPatterns,
    addAttackPattern,
  }),
  inject18n,
  Component => withStyles(Component, styles),
)(OldAttackPatternField);

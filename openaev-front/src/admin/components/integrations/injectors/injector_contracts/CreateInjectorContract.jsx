import { Add, SmartButtonOutlined } from '@mui/icons-material';
import { Fab, List, ListItemButton, ListItemIcon, ListItemText, Step, StepLabel, Stepper } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';
import { v4 as uuid } from 'uuid';

import { addInjectorContract } from '../../../../../actions/InjectorContracts';
import Drawer from '../../../../../components/common/Drawer';
import inject18n from '../../../../../components/i18n';
import InjectorContractCustomForm from './InjectorContractCustomForm';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 30,
  },
});

class CreateInjectorContract extends Component {
  constructor(props) {
    super(props);
    this.state = {
      open: false,
      activeStep: 0,
      selectedInjectorContract: null,
    };
  }

  handleOpen() {
    this.setState({ open: true });
  }

  handleClose() {
    this.setState({
      open: false,
      activeStep: 0,
      selectedInjectorContract: null,
    });
  }

  goToStep(step) {
    this.setState({ activeStep: step });
  }

  handleSelectInjectorContract(injectorContractId) {
    this.setState({
      selectedInjectorContract: injectorContractId,
      activeStep: 1,
    });
  }

  onSubmit(data, fields) {
    const { injector, injectorContracts } = this.props;
    const { selectedInjectorContract } = this.state;
    const injectorContract = injectorContracts.filter(n => n.injector_contract_id === selectedInjectorContract).at(0);
    const injectorContractContent = JSON.parse(injectorContract.injector_contract_content);
    const newInjectorContractContent = {
      ...injectorContractContent,
      label: { en: data.injector_contract_name },
      fields: injectorContractContent.fields.map((field) => {
        const newField = field;
        if (!R.isNil(fields[field.key]?.readOnly)) {
          newField.readOnly = fields[field.key]?.readOnly;
        }
        if (!R.isNil(fields[field.key]?.defaultValue)) {
          newField.defaultValue = field.cardinality === '1' ? fields[field.key]?.defaultValue : [fields[field.key]?.defaultValue];
        }
        return newField;
      }),
    };
    const inputValues = R.pipe(
      R.assoc('injector_id', injector.injector_id),
      R.assoc('contract_id', `${injector.injector_name.toLowerCase()}--${uuid()}`),
      R.assoc('contract_labels', { en: data.injector_contract_name }),
      R.assoc('contract_attack_patterns_external_ids', R.pluck('id', data.injector_contract_attack_patterns)),
      R.assoc('contract_content', JSON.stringify(newInjectorContractContent)),
      R.dissoc('injector_contract_attack_patterns'),
    )(data);
    return this.props
      .addInjectorContract(inputValues)
      .then(result => (result.result ? this.handleClose() : result));
  }

  renderInjectorContracts() {
    const { injectorContracts, tPick } = this.props;
    return (
      <List>
        {injectorContracts.map((injectorContract) => {
          return (
            <ListItemButton
              key={injectorContract.injector_contract_id}
              divider={true}
              onClick={this.handleSelectInjectorContract.bind(this, injectorContract.injector_contract_id)}
            >
              <ListItemIcon color="primary">
                <SmartButtonOutlined color="primary" />
              </ListItemIcon>
              <ListItemText primary={tPick(injectorContract.injector_contract_labels)} />
            </ListItemButton>
          );
        })}
      </List>
    );
  }

  render() {
    const { classes, t, injectorContracts } = this.props;
    const { open, activeStep, selectedInjectorContract } = this.state;
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
          title={t('Create a new injector contract')}
        >
          <>
            <Stepper activeStep={this.state.activeStep} style={{ marginBottom: 20 }}>
              <Step>
                <StepLabel>{t('Select the template')}</StepLabel>
              </Step>
              <Step>
                <StepLabel>{t('Create the injector contract')}</StepLabel>
              </Step>
            </Stepper>
            {activeStep === 0 && this.renderInjectorContracts()}
            {activeStep === 1 && (
              <InjectorContractCustomForm
                editing={false}
                onSubmit={this.onSubmit.bind(this)}
                initialValues={{ injector_contract_attack_patterns: [] }}
                handleClose={this.handleClose.bind(this)}
                contractTemplate={injectorContracts.filter(n => n.injector_contract_id === selectedInjectorContract).at(0)}
              />
            )}
          </>
        </Drawer>
      </>
    );
  }
}

CreateInjectorContract.propTypes = {
  t: PropTypes.func,
  injector: PropTypes.object,
  injectorContracts: PropTypes.array,
  killChainPhasesMap: PropTypes.object,
  attackPatternsMap: PropTypes.object,
  addInjectorContract: PropTypes.func,
};

export default R.compose(
  connect(null, { addInjectorContract }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateInjectorContract);

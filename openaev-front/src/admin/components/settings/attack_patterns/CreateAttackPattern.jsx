import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { addAttackPattern } from '../../../../actions/AttackPattern';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import AttackPatternForm from './AttackPatternForm';

class CreateAttackPatternComponent extends Component {
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
    const inputValues = R.pipe(
      R.assoc('attack_pattern_kill_chain_phases', R.pluck('id', data.attack_pattern_kill_chain_phases)),
    )(data);
    return this.props
      .addAttackPattern(inputValues)
      .then((result) => {
        if (this.props.onCreate) {
          const attackPatternCreated = result.entities.attackpatterns[result.result];
          this.props.onCreate(attackPatternCreated);
        }
        return (result.result ? this.handleClose() : result);
      });
  }

  render() {
    const { t } = this.props;
    return (
      <>
        <ButtonCreate onClick={this.handleOpen.bind(this)} />
        <Drawer
          open={this.state.open}
          handleClose={this.handleClose.bind(this)}
          title={t('Create a new attack pattern')}
        >
          <AttackPatternForm
            editing={false}
            onSubmit={this.onSubmit.bind(this)}
            initialValues={{ attack_pattern_kill_chain_phases: [] }}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </>
    );
  }
}

CreateAttackPatternComponent.propTypes = {
  t: PropTypes.func,
  organizations: PropTypes.array,
  addAttackPattern: PropTypes.func,
  onCreate: PropTypes.func,
};

const CreateAttackPattern = R.compose(
  connect(null, { addAttackPattern }),
  inject18n,
)(CreateAttackPatternComponent);

export default CreateAttackPattern;

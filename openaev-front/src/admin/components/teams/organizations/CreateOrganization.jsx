import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { addOrganization } from '../../../../actions/Organization';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import OrganizationForm from './OrganizationForm';

class CreateOrganizationComponent extends Component {
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
      R.assoc('organization_tags', R.pluck('id', data.organization_tags)),
    )(data);
    return this.props
      .addOrganization(inputValues)
      .then(result => (result.result ? this.handleClose() : result));
  }

  render() {
    const { t } = this.props;
    return (
      <div>
        <ButtonCreate onClick={this.handleOpen.bind(this)} />
        <Drawer
          open={this.state.open}
          handleClose={this.handleClose.bind(this)}
          title={t('Create an organization')}
        >
          <OrganizationForm
            onSubmit={this.onSubmit.bind(this)}
            initialValues={{ organization_tags: [] }}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </div>
    );
  }
}

CreateOrganizationComponent.propTypes = {
  t: PropTypes.func,
  addOrganization: PropTypes.func,
};

const CreateOrganization = R.compose(
  connect(null, { addOrganization }),
  inject18n,
)(CreateOrganizationComponent);

export default CreateOrganization;

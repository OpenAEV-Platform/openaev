import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { addChannel } from '../../../../actions/channels/channel-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import ChannelForm from './ChannelForm';

class CreateChannelComponent extends Component {
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
    const { t } = this.props;
    return (
      <div>
        <ButtonCreate onClick={this.handleOpen.bind(this)} />
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

CreateChannelComponent.propTypes = {
  t: PropTypes.func,
  addChannel: PropTypes.func,
};

const CreateChannel = R.compose(
  connect(null, { addChannel }),
  inject18n,
)(CreateChannelComponent);

export default CreateChannel;

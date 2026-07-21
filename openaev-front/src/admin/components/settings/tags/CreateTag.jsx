import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';

import { addTag } from '../../../../actions/tags/tag-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import TagForm from './TagForm';

class CreateTagComponent extends Component {
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
    this.props
      .addTag(data)
      .then((result) => {
        if (this.props.onCreate) {
          const tagCreated = result.entities.tags[result.result];
          this.props.onCreate(tagCreated);
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
          title={t('Create a new tag')}
        >
          <TagForm
            onSubmit={this.onSubmit.bind(this)}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </>
    );
  }
}

CreateTagComponent.propTypes = {
  t: PropTypes.func,
  addTag: PropTypes.func,
  onCreate: PropTypes.func,
};

const CreateTag = R.compose(
  connect(null, { addTag }),
  inject18n,
)(CreateTagComponent);

export default CreateTag;

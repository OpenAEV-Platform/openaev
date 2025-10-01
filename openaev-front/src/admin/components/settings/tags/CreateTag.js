import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addTag } from '../../../../actions/Tag';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import TagForm from './TagForm';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
});

class CreateTag extends Component {
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

CreateTag.propTypes = {
  t: PropTypes.func,
  classes: PropTypes.object,
  addTag: PropTypes.func,
  onCreate: PropTypes.func,
};

export default R.compose(
  connect(null, { addTag }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateTag);

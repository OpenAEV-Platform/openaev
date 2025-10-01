import { Add } from '@mui/icons-material';
import { Fab, Slide } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component, forwardRef } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addUser } from '../../../../actions/User';
import Drawer from '../../../../components/common/Drawer';
import inject18n from '../../../../components/i18n';
import UserForm from './UserForm';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
});

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

class CreateUser extends Component {
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
      R.assoc(
        'user_organization',
        data.user_organization && data.user_organization.id
          ? data.user_organization.id
          : data.user_organization,
      ),
      R.assoc('user_tags', R.pluck('id', data.user_tags)),
    )(data);
    return this.props
      .addUser(inputValues)
      .then((result) => {
        if (this.props.onCreate) {
          const userCreated = result.entities.users[result.result];
          this.props.onCreate(userCreated);
        }
        return result.result ? this.handleClose() : result;
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
          title={t('Create a new user')}
        >
          <UserForm
            editing={false}
            onSubmit={this.onSubmit.bind(this)}
            initialValues={{ user_tags: [] }}
            handleClose={this.handleClose.bind(this)}
          />
        </Drawer>
      </>
    );
  }
}

CreateUser.propTypes = {
  t: PropTypes.func,
  organizations: PropTypes.array,
  addUser: PropTypes.func,
};

export default R.compose(
  connect(null, { addUser }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateUser);

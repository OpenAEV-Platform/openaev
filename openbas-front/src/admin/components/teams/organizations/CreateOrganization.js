import { Add } from '@mui/icons-material';
import { Dialog, DialogContent, DialogTitle, Fab, Slide } from '@mui/material';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component, forwardRef } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addOrganization } from '../../../../actions/Organization';
import inject18n from '../../../../components/i18n';
import OrganizationForm from './OrganizationForm';

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

const styles = () => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: 230,
  },
});

class CreateOrganization extends Component {
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
    const { classes, t } = this.props;
    return (
      <div>
        <Fab
          onClick={this.handleOpen.bind(this)}
          color="primary"
          aria-label="Add"
          className={classes.createButton}
        >
          <Add />
        </Fab>
        <Dialog
          open={this.state.open}
          TransitionComponent={Transition}
          onClose={this.handleClose.bind(this)}
          fullWidth={true}
          maxWidth="md"
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Create a new organization')}</DialogTitle>
          <DialogContent>
            <OrganizationForm
              onSubmit={this.onSubmit.bind(this)}
              initialValues={{ organization_tags: [] }}
              handleClose={this.handleClose.bind(this)}
            />
          </DialogContent>
        </Dialog>
      </div>
    );
  }
}

CreateOrganization.propTypes = {
  classes: PropTypes.object,
  t: PropTypes.func,
  addOrganization: PropTypes.func,
};

export default R.compose(
  connect(null, { addOrganization }),
  inject18n,
  Component => withStyles(Component, styles),
)(CreateOrganization);

import { DomainOutlined } from '@mui/icons-material';
import { Box, Dialog, DialogContent, DialogTitle } from '@mui/material';
import * as R from 'ramda';
import { Component } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { addOrganization, fetchOrganizations } from '../actions/Organization';
import { storeHelper } from '../actions/Schema';
import OrganizationForm from '../admin/components/teams/organizations/OrganizationForm';
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

class OrganizationField extends Component {
  constructor(props) {
    super(props);
    this.state = {
      organizationCreation: false,
      organizationInput: '',
    };
  }

  componentDidMount() {
    this.props.fetchOrganizations();
  }

  handleOpenOrganizationCreation() {
    this.setState({ organizationCreation: true });
  }

  handleCloseOrganizationCreation() {
    this.setState({ organizationCreation: false });
  }

  onSubmit(data) {
    const { name, setFieldValue } = this.props;
    const inputValues = R.pipe(
      R.assoc('organization_tags', R.pluck('id', data.organization_tags)),
    )(data);
    this.props.addOrganization(inputValues).then((result) => {
      if (result.result) {
        const newOrganization = result.entities.organizations[result.result];
        const organization = {
          id: newOrganization.organization_id,
          label: newOrganization.organization_name,
        };
        setFieldValue(name, organization);
        return this.handleCloseOrganizationCreation();
      }
      return result;
    });
  }

  render() {
    const { t, name, organizations, classes } = this.props;
    const organizationsOptions = R.map(
      n => ({
        id: n.organization_id,
        label: n.organization_name,
      }),
      organizations,
    );
    return (
      <>
        <Autocomplete
          variant="standard"
          size="small"
          name={name}
          fullWidth={true}
          multiple={false}
          label={t('Organization')}
          options={organizationsOptions}
          style={{ marginTop: 20 }}
          openCreate={this.props.userRight && this.handleOpenOrganizationCreation.bind(this)}
          renderOption={(props, option) => (
            <Box component="li" {...props} key={option.id}>
              <div className={classes.icon}>
                <DomainOutlined />
              </div>
              <div className={classes.text}>{option.label}</div>
            </Box>
          )}
          classes={{ clearIndicator: classes.autoCompleteIndicator }}
        />
        <Dialog
          open={this.state.organizationCreation}
          onClose={this.handleCloseOrganizationCreation.bind(this)}
          PaperProps={{ elevation: 1 }}
        >
          <DialogTitle>{t('Create a new organization')}</DialogTitle>
          <DialogContent>
            <OrganizationForm
              onSubmit={this.onSubmit.bind(this)}
              initialValues={{ organization_tags: [] }}
              handleClose={this.handleCloseOrganizationCreation.bind(this)}
            />
          </DialogContent>
        </Dialog>
      </>
    );
  }
}

const select = (state) => {
  const helper = storeHelper(state);
  return { organizations: helper.getOrganizations().toJS() };
};

export default R.compose(
  connect(select, {
    fetchOrganizations,
    addOrganization,
  }),
  inject18n,
  Component => withStyles(Component, styles),
)(OrganizationField);

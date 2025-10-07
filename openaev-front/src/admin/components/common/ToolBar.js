import {
  AddOutlined,
  BrushOutlined,
  CancelOutlined,
  ClearOutlined,
  CloseOutlined,
  DeleteOutlined,
  DevicesOtherOutlined, FileDownloadOutlined,
  ForwardToInbox,
  GroupsOutlined,
} from '@mui/icons-material';
import {
  Autocomplete,
  Button,
  Drawer,
  FormControl,
  Grid,
  IconButton,
  InputLabel,
  MenuItem,
  Select,
  Slide,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import * as PropTypes from 'prop-types';
import * as R from 'ramda';
import { Component, forwardRef } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { fetchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { fetchEndpoints } from '../../../actions/assets/endpoint-actions';
import { storeHelper } from '../../../actions/Schema';
import DialogDelete from '../../../components/common/DialogDelete';
import DialogTest from '../../../components/common/DialogTest';
import ExportOptionsDialog from '../../../components/common/export/ExportOptionsDialog.js';
import inject18n from '../../../components/i18n';
import { MESSAGING$ } from '../../../utils/Environment';

const styles = theme => ({
  bottomNav: {
    padding: 0,
    zIndex: 1100,
    display: 'flex',
    height: 50,
    overflow: 'hidden',
  },
  bottomNavWithLargePadding: {
    zIndex: 1100,
    padding: '0 250px 0 0',
    display: 'flex',
    height: 50,
    overflow: 'hidden',
  },
  bottomNavWithMediumPadding: {
    zIndex: 1100,
    padding: '0 200px 0 0',
    display: 'flex',
    height: 50,
    overflow: 'hidden',
  },
  drawerPaper: {
    minHeight: '100vh',
    width: '50%',
    position: 'fixed',
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
    padding: 0,
  },
  header: {
    backgroundColor: theme.palette.background.nav,
    padding: '20px 20px 20px 60px',
  },
  closeButton: {
    position: 'absolute',
    top: 12,
    left: 5,
    color: 'inherit',
  },
  buttons: {
    marginTop: 20,
    textAlign: 'right',
  },
  button: { marginLeft: theme.spacing(2) },
  buttonAdd: {
    width: '100%',
    height: 20,
  },
  container: { padding: '10px 20px 20px 20px' },
  aliases: { margin: '0 7px 7px 0' },
  title: {
    flex: '1 1 100%',
    fontSize: '12px',
  },
  chipValue: { margin: 0 },
  filter: { margin: '5px 10px 5px 0' },
  operator: {
    fontFamily: 'Consolas, monaco, monospace',
    backgroundColor: theme.palette.background.accent,
    margin: '5px 10px 5px 0',
  },
  step: {
    position: 'relative',
    width: '100%',
    margin: '0 0 20px 0',
    padding: 15,
    verticalAlign: 'middle',
    border: `1px solid ${theme.palette.primary.main}`,
    borderRadius: 4,
    display: 'flex',
  },
  formControl: { width: '100%' },
  formControlPanel: { width: '100%' },
  stepType: {
    margin: 0,
    paddingRight: 20,
    width: '30%',
  },
  stepField: {
    margin: 0,
    paddingRight: 20,
    width: '30%',
  },
  stepValues: {
    paddingRight: 20,
    margin: 0,
  },
  stepCloseButton: {
    position: 'absolute',
    top: -20,
    right: -20,
  },
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
  numberOfSelectedElements: {
    padding: '2px 5px 2px 5px',
    marginRight: 5,
    backgroundColor: theme.palette.background.accent,
  },
});

const Transition = forwardRef((props, ref) => (
  <Slide direction="up" ref={ref} {...props} />
));
Transition.displayName = 'TransitionSlide';

class ToolBar extends Component {
  constructor(props) {
    super(props);
    this.state = {
      displayExport: false,
      displayUpdate: false,
      displayBulkDelete: false,
      displayBulkTest: false,
      actions: [],
      actionsInputs: [{}],
      navOpen: localStorage.getItem('navOpen') === 'true',
    };
  }

  componentDidMount() {
    if (this.props.canManage) {
      this.props.fetchEndpoints();
      this.props.fetchAssetGroups();
    }
    this.subscription = MESSAGING$.toggleNav.subscribe({ next: () => this.setState({ navOpen: localStorage.getItem('navOpen') === 'true' }) });
  }

  componentWillUnmount() {
    this.subscription.unsubscribe();
  }

  handleOpenUpdate() {
    this.setState({ displayUpdate: true });
  }

  handleCloseUpdate() {
    this.setState({
      displayUpdate: false,
      actionsInputs: [{}],
    });
  }

  handleOpenExport() {
    this.setState({ displayExport: true });
  }

  handleCloseExport() {
    this.setState({
      displayExport: false,
      actionsInputs: [{}],
    });
  }

  handleSubmitExport(withPlayers, withTeams, withVariableValues) {
    this.handleCloseExport();
    this.props.handleClearSelectedElements();
    this.props.handleExport(withPlayers, withTeams, withVariableValues);
  }

  handleOpenBulkTest() {
    this.setState({ displayBulkTest: true });
  }

  handleCloseBulkTest() {
    this.setState({
      displayBulkTest: false,
      actionsInputs: [{}],
    });
  }

  handleSubmitBulkTest = () => {
    this.handleCloseBulkTest();
    this.props.handleClearSelectedElements();
    this.props.handleBulkTest(this.state.actionsInputs);
  };

  handleAddStep() {
    this.setState({ actionsInputs: R.append({}, this.state.actionsInputs) });
  }

  handleRemoveStep(i) {
    const { actionsInputs } = this.state;
    actionsInputs.splice(i, 1);
    this.setState({ actionsInputs });
  }

  handleChangeActionInput(i, key, event) {
    const { value } = event.target;
    const { actionsInputs } = this.state;
    actionsInputs[i] = R.assoc(key, value, actionsInputs[i] || {});
    if (key === 'field') {
      const values = [];
      actionsInputs[i] = R.assoc('values', values, actionsInputs[i] || {});
      if (
        value === 'object-marking'
        || value === 'object-label'
        || value === 'created-by'
        || value === 'external-reference'
      ) {
        actionsInputs[i] = R.assoc(
          'fieldType',
          'RELATION',
          actionsInputs[i] || {},
        );
      } else {
        actionsInputs[i] = R.assoc(
          'fieldType',
          'ATTRIBUTE',
          actionsInputs[i] || {},
        );
      }
    }
    this.setState({ actionsInputs });
  }

  handleChangeActionInputValues(i, event, value) {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const { actionsInputs } = this.state;
    actionsInputs[i] = R.assoc(
      'values',
      Array.isArray(value) ? value : [value],
      actionsInputs[i] || {},
    );
    this.setState({ actionsInputs });
  }

  handleChangeActionInputOptions(i, key, event) {
    const { actionsInputs } = this.state;
    actionsInputs[i] = R.assoc(
      'options',
      R.assoc(key, event.target.checked, actionsInputs[i]?.options || {}),
      actionsInputs[i] || {},
    );
    this.setState({ actionsInputs });
  }

  handleChangeActionInputValuesReplace(i, event) {
    const { value } = event.target;
    const { actionsInputs } = this.state;
    actionsInputs[i] = R.assoc(
      'values',
      Array.isArray(value) ? value : [value],
      actionsInputs[i] || {},
    );
    this.setState({ actionsInputs });
  }

  renderFieldOptions(i) {
    const { t } = this.props;
    const { actionsInputs } = this.state;
    const disabled = R.isNil(actionsInputs[i]?.type) || R.isEmpty(actionsInputs[i]?.type);
    let options = [];
    if (actionsInputs[i]?.type === 'ADD') {
      options = [
        {
          label: t('Assets'),
          value: 'assets',
        },
        {
          label: t('Asset Groups'),
          value: 'asset_groups',
        },
        {
          label: t('Teams'),
          value: 'teams',
        },
      ];
    } else if (actionsInputs[i]?.type === 'REPLACE') {
      options = [
        {
          label: t('Assets'),
          value: 'assets',
        },
        {
          label: t('Asset Groups'),
          value: 'asset_groups',
        },
        {
          label: t('Teams'),
          value: 'teams',
        },
      ];
    } else if (actionsInputs[i]?.type === 'REMOVE') {
      options = [
        {
          label: t('Assets'),
          value: 'assets',
        },
        {
          label: t('Asset Groups'),
          value: 'asset_groups',
        },
        {
          label: t('Teams'),
          value: 'teams',
        },
      ];
    }
    return (
      <Select
        variant="standard"
        disabled={disabled}
        value={actionsInputs[i]?.type}
        onChange={this.handleChangeActionInput.bind(this, i, 'field')}
      >
        {options.length > 0 ? (
          R.map(
            n => (
              <MenuItem key={n.value} value={n.value}>
                {n.label}
              </MenuItem>
            ),
            options,
          )
        ) : (
          <MenuItem value="none">{t('None')}</MenuItem>
        )}
      </Select>
    );
  }

  handleSearch(i, event, newValue) {
    if (!event) return;
    const { actionsInputs } = this.state;
    actionsInputs[i] = R.assoc(
      'inputValue',
      newValue && newValue.length > 0 ? newValue : '',
      actionsInputs[i],
    );
    this.setState({ actionsInputs });
  }

  renderValuesOptions(i) {
    const { t, classes } = this.props;
    const { actionsInputs } = this.state;
    const disabled = R.isNil(actionsInputs[i]?.field) || R.isEmpty(actionsInputs[i]?.field);
    switch (actionsInputs[i]?.field) {
      case 'assets':
        return (
          <Autocomplete
            disabled={disabled}
            size="small"
            fullWidth={true}
            selectOnFocus={true}
            autoHighlight={true}
            getOptionLabel={option => (option.label ? option.label : '')}
            value={actionsInputs[i]?.values || []}
            multiple={true}
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Values')}
                fullWidth={true}
                style={{ marginTop: 3 }}
              />
            )}
            noOptionsText={t('No available options')}
            options={this.props.endpoints}
            onInputChange={this.handleSearch.bind(this, i)}
            inputValue={actionsInputs[i]?.inputValue || ''}
            onChange={this.handleChangeActionInputValues.bind(this, i)}
            renderOption={(props, option) => (
              <li {...props}>
                <div className={classes.icon}>
                  <DevicesOtherOutlined />
                </div>
                <div className={classes.text}>{option.label}</div>
              </li>
            )}
          />
        );
      case 'asset_groups':
        return (
          <Autocomplete
            disabled={disabled}
            size="small"
            fullWidth={true}
            selectOnFocus={true}
            autoHighlight={true}
            getOptionLabel={option => (option.label ? option.label : '')}
            value={actionsInputs[i]?.values || []}
            multiple={true}
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Values')}
                fullWidth={true}
                style={{ marginTop: 3 }}
              />
            )}
            noOptionsText={t('No available options')}
            options={this.props.assetGroups}
            onInputChange={this.handleSearch.bind(this, i)}
            inputValue={actionsInputs[i]?.inputValue || ''}
            onChange={this.handleChangeActionInputValues.bind(this, i)}
            renderOption={(props, option) => (
              <li {...props}>
                <div className={classes.icon}>
                  <SelectGroup />
                </div>
                <div className={classes.text}>{option.label}</div>
              </li>
            )}
          />
        );
      case 'teams':
        return (
          <Autocomplete
            disabled={disabled}
            size="small"
            fullWidth={true}
            selectOnFocus={true}
            autoHighlight={true}
            getOptionLabel={option => (option.label ? option.label : '')}
            value={actionsInputs[i]?.values || []}
            multiple={true}
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Values')}
                fullWidth={true}
                style={{ marginTop: 3 }}
              />
            )}
            noOptionsText={t('No available options')}
            options={this.props.teams}
            onInputChange={this.handleSearch.bind(this, i)}
            inputValue={actionsInputs[i]?.inputValue || ''}
            onChange={this.handleChangeActionInputValues.bind(this, i)}
            renderOption={(props, option) => (
              <li {...props}>
                <div className={classes.icon}>
                  <GroupsOutlined />
                </div>
                <div className={classes.text}>{option.label}</div>
              </li>
            )}
          />
        );
      default:
        return (
          <TextField
            variant="standard"
            disabled={disabled}
            label={t('Values')}
            fullWidth={true}
            onChange={this.handleChangeActionInputValuesReplace.bind(this, i)}
          />
        );
    }
  }

  areStepValid() {
    const { actionsInputs } = this.state;
    for (const n of actionsInputs) {
      if (!n || !n.type || !n.field || !n.values || n.values.length === 0) {
        return false;
      }
    }
    return true;
  }

  handleLaunchUpdate() {
    this.handleCloseUpdate();
    this.props.handleClearSelectedElements();
    this.props.handleUpdate(this.state.actionsInputs);
  }

  // Deletion
  handleOpenBulkDelete = () => {
    this.setState({ displayBulkDelete: true });
  };

  handleCloseBulkDelete = () => {
    this.setState({
      displayBulkDelete: false,
      actionsInputs: [{}],
    });
  };

  handleSubmitBulkDelete = () => {
    this.handleCloseBulkDelete();
    this.props.handleClearSelectedElements();
    this.props.handleBulkDelete(this.state.actionsInputs);
  };

  render() {
    const {
      t,
      classes,
      numberOfSelectedElements,
      handleClearSelectedElements,
      variant,
    } = this.props;
    const { actionsInputs, navOpen } = this.state;
    const isOpen = numberOfSelectedElements > 0;
    let paperClass;
    switch (variant) {
      case 'large':
        paperClass = classes.bottomNavWithLargePadding;
        break;
      case 'medium':
        paperClass = classes.bottomNavWithMediumPadding;
        break;
      default:
        paperClass = classes.bottomNav;
    }
    const confirmationText = () => {
      return numberOfSelectedElements === 1
        ? t('Do you want to delete this inject?')
        : t('Do you want to delete these {count} injects?', { count: numberOfSelectedElements });
    };
    const testConfirmationText = () => {
      return numberOfSelectedElements === 1
        ? t('Do you want to test this inject?')
        : t('Do you want to test these {count} injects?', { count: numberOfSelectedElements });
    };
    return (
      <>
        <Drawer
          anchor="bottom"
          variant="persistent"
          classes={{ paper: paperClass }}
          open={isOpen}
          slotProps={{
            paper: {
              variant: 'elevation',
              elevation: 1,
              style: { paddingLeft: navOpen ? 185 : 60 },
            },
          }}
        >
          <Toolbar style={{ minHeight: 54 }} data-testid="opencti-toolbar">
            <Typography
              className={classes.title}
              color="inherit"
              variant="subtitle1"
            >
              <span className={classes.numberOfSelectedElements}>
                {numberOfSelectedElements}
              </span>
              {' '}
              {t('selected')}
              {' '}
              <IconButton
                aria-label="clear"
                disabled={
                  numberOfSelectedElements === 0 || this.state.processing
                }
                onClick={handleClearSelectedElements.bind(this)}
                size="small"
                color="primary"
              >
                <ClearOutlined fontSize="small" />
              </IconButton>
            </Typography>
            <Tooltip title={t('Export')}>
              <span>
                <IconButton
                  aria-label="export"
                  disabled={
                    numberOfSelectedElements === 0
                    || this.state.processing
                  }
                  onClick={this.handleOpenExport.bind(this)}
                  color="primary"
                  size="small"
                >
                  <FileDownloadOutlined fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
            {this.props.canManage && (
              <Tooltip title={t('Update')}>
                <span>
                  <IconButton
                    aria-label="update"
                    disabled={
                      numberOfSelectedElements === 0
                      || this.state.processing
                    }
                    onClick={this.handleOpenUpdate.bind(this)}
                    color="primary"
                    size="small"
                  >
                    <BrushOutlined fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            {this.props.canManage && (
              <Tooltip title={t('Test')}>
                <span>
                  <IconButton
                    aria-label="test"
                    disabled={
                      numberOfSelectedElements === 0
                      || this.state.processing
                    }
                    onClick={this.handleOpenBulkTest.bind(this)}
                    color="primary"
                    size="small"
                  >
                    <ForwardToInbox fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            )}
            {this.props.canManage && (

              <Tooltip title={t('Delete')}>
                <span>
                  <IconButton
                    aria-label="delete"
                    disabled={
                      numberOfSelectedElements === 0
                      || this.state.processing
                    }
                    onClick={this.handleOpenBulkDelete.bind(this)}
                    color="primary"
                    size="small"
                  >
                    <DeleteOutlined fontSize="small" />
                  </IconButton>
                </span>
              </Tooltip>
            )}
          </Toolbar>
          <Drawer
            open={this.state.displayUpdate}
            anchor="right"
            elevation={1}
            sx={{ zIndex: 1202 }}
            classes={{ paper: classes.drawerPaper }}
            onClose={this.handleCloseUpdate.bind(this)}
          >
            <div className={classes.header}>
              <IconButton
                aria-label="Close"
                className={classes.closeButton}
                onClick={this.handleCloseUpdate.bind(this)}
                size="large"
                color="primary"
              >
                <CloseOutlined fontSize="small" color="primary" />
              </IconButton>
              <Typography variant="h6">{t('Update objects')}</Typography>
            </div>
            <div className={classes.container} style={{ marginTop: 20 }}>
              {Array(actionsInputs.length)
                .fill(0)
                .map((_, i) => (
                  <div key={i} className={classes.step}>
                    <IconButton
                      disabled={actionsInputs.length === 1}
                      aria-label="Delete"
                      className={classes.stepCloseButton}
                      onClick={this.handleRemoveStep.bind(this, i)}
                      size="small"
                    >
                      <CancelOutlined fontSize="small" />
                    </IconButton>
                    <Grid container={true} spacing={3} className={classes.formControlPanel}>
                      <Grid size={{ xs: 3 }}>
                        <FormControl className={classes.formControl}>
                          <InputLabel>{t('Action type')}</InputLabel>
                          <Select
                            variant="standard"
                            value={actionsInputs[i]?.type}
                            onChange={this.handleChangeActionInput.bind(
                              this,
                              i,
                              'type',
                            )}
                          >
                            <MenuItem value="ADD">{t('Add')}</MenuItem>
                            <MenuItem value="REPLACE">
                              {t('Replace')}
                            </MenuItem>
                            <MenuItem value="REMOVE">{t('Remove')}</MenuItem>
                          </Select>
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 3 }}>
                        <FormControl className={classes.formControl}>
                          <InputLabel>{t('Field')}</InputLabel>
                          {this.renderFieldOptions(i)}
                        </FormControl>
                      </Grid>
                      <Grid size={{ xs: 6 }}>
                        {this.renderValuesOptions(i)}
                      </Grid>
                    </Grid>
                  </div>
                ))}
              <div className={classes.add}>
                <Button
                  disabled={!this.areStepValid()}
                  variant="contained"
                  color="secondary"
                  size="small"
                  onClick={this.handleAddStep.bind(this)}
                  classes={{ root: classes.buttonAdd }}
                >
                  <AddOutlined fontSize="small" />
                </Button>
              </div>
              <div className={classes.buttons}>
                <Button
                  disabled={!this.areStepValid()}
                  variant="contained"
                  color="primary"
                  onClick={this.handleLaunchUpdate.bind(this)}
                  classes={{ root: classes.button }}
                >
                  {t('Update')}
                </Button>
              </div>
            </div>
          </Drawer>
        </Drawer>
        <ExportOptionsDialog
          title={t('inject_export_json_selection')}
          open={this.state.displayExport}
          onCancel={this.handleCloseExport.bind(this)}
          onClose={this.handleCloseExport.bind(this)}
          onSubmit={this.handleSubmitExport.bind(this)}
        />
        <DialogDelete
          open={this.state.displayBulkDelete}
          handleClose={this.handleCloseBulkDelete.bind(this)}
          handleSubmit={this.handleSubmitBulkDelete.bind(this)}
          text={confirmationText()}
        />
        <DialogTest
          open={this.state.displayBulkTest}
          handleClose={this.handleCloseBulkTest.bind(this)}
          handleSubmit={this.handleSubmitBulkTest.bind(this)}
          text={testConfirmationText()}
          alertText={t('Only SMS and emails related injects will be tested')}
        />
      </>
    );
  }
}

ToolBar.propTypes = {
  classes: PropTypes.object,
  t: PropTypes.func,
  numberOfSelectedElements: PropTypes.number,
  selectedElements: PropTypes.object,
  deSelectedElements: PropTypes.object,
  selectAll: PropTypes.bool,
  filters: PropTypes.object,
  search: PropTypes.string,
  handleClearSelectedElements: PropTypes.func,
  variant: PropTypes.string,
  container: PropTypes.object,
  type: PropTypes.string,
  handleCopy: PropTypes.func,
  warning: PropTypes.bool,
  warningMessage: PropTypes.string,
  rightOffset: PropTypes.number,
  deleteOperationEnabled: PropTypes.bool,
};

const select = (state, ownProps) => {
  const helper = storeHelper(state);
  const endpoints = helper.getEndpoints().toJS()
    .map(n => ({
      label: n.asset_name,
      value: n.asset_id,
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
  const assetGroups = helper.getAssetGroups().toJS()
    .map(n => ({
      label: n.asset_group_name,
      value: n.asset_group_id,
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
  const teams = ownProps.teamsFromExerciseOrScenario
    .map(n => ({
      label: n.team_name,
      value: n.team_id,
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
  return {
    endpoints,
    assetGroups,
    teams,
  };
};

export default R.compose(
  connect(select, {
    fetchEndpoints,
    fetchAssetGroups,
  }),
  inject18n,
  Component => withStyles(Component, styles),
)(ToolBar);

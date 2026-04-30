import {
  AddOutlined,
  BrushOutlined,
  CancelOutlined,
  ClearOutlined,
  CloseOutlined,
  DeleteOutlined,
  DevicesOtherOutlined,
  FileDownloadOutlined,
  ForwardToInbox,
  GroupsOutlined,
  InfoOutlined,
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
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { Component, type ComponentType } from 'react';
import { connect } from 'react-redux';
import { withStyles } from 'tss-react/mui';

import { fetchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { fetchEndpoints } from '../../../actions/assets/endpoint-actions';
import { storeHelper } from '../../../actions/Schema';
import DialogDelete from '../../../components/common/DialogDelete';
import DialogTest from '../../../components/common/DialogTest';
import ExportOptionsDialog from '../../../components/common/export/ExportOptionsDialog';
import inject18n from '../../../components/i18n';
import {
  type ToolBarActionInput,
  type ToolBarActionValue,
  type ToolBarAssetGroupInput,
  type ToolBarBulkUpdateActionInput,
  type ToolBarEndpointInput,
  type ToolBarSelectOption,
  type ToolBarTask,
  type ToolBarTeamInput,
} from '../../../utils/api-types-custom';

const styles = (theme: any) => ({
  drawerPaper: {
    minHeight: '100vh',
    width: '50%',
    position: 'fixed',
    transition: theme.transitions.create('width', {
      easing: theme.transitions.easing.sharp,
      duration: theme.transitions.duration.enteringScreen,
    }),
  },
  header: {
    backgroundColor: theme.palette.background.nav,
    padding: theme.spacing(2.5),
  },
  closeButton: {
    position: 'absolute',
    top: theme.spacing(1.5),
    left: theme.spacing(0.625),
    color: 'inherit',
  },
  buttons: {
    marginTop: theme.spacing(2.5),
    textAlign: 'right',
  },
  button: { marginLeft: theme.spacing(2) },
  buttonAdd: {
    width: '100%',
    height: theme.spacing(2.5),
  },
  container: { padding: theme.spacing(1.25) },
  aliases: { margin: theme.spacing(0, 0.875, 0.875, 0) },
  title: {
    flex: '1 1 100%',
    fontSize: 12,
    display: 'flex',
    alignItems: 'center',
    gap: theme.spacing(0.5),
    textTransform: 'none',
  },
  chipValue: { margin: 0 },
  filter: { margin: theme.spacing(0.625, 1.25, 0.625, 0) },
  operator: {
    fontFamily: 'Consolas, monaco, monospace',
    backgroundColor: theme.palette.background.accent,
    margin: theme.spacing(0.625, 1.25, 0.625, 0),
  },
  step: {
    position: 'relative',
    width: '100%',
    margin: theme.spacing(0, 0, 2.5, 0),
    padding: theme.spacing(1.875),
    verticalAlign: 'middle',
    border: `1px solid ${theme.palette.primary.main}`,
    borderRadius: theme.shape.borderRadius,
    display: 'flex',
  },
  formControl: { width: '100%' },
  formControlPanel: { width: '100%' },
  stepCloseButton: {
    position: 'absolute',
    top: -theme.spacing(2.5),
    right: -theme.spacing(2.5),
  },
  icon: {
    paddingTop: theme.spacing(0.5),
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: theme.spacing(1.25),
  },
  autoCompleteIndicator: { display: 'none' },
  numberOfSelectedElements: {
    padding: theme.spacing(0.25, 0.625),
    marginRight: theme.spacing(0.625),
    backgroundColor: theme.palette.background.accent,
  },
  toolbar: {
    display: 'flex',
    alignItems: 'center',
    flex: '1 1 100%',
    width: '100%',
    backgroundColor: 'rgb(15, 30, 56)',
    paddingRight: theme.spacing(1),
  },
  selectedCount: { fontWeight: 'bold' },
  clearButton: {
    marginLeft: 0,
    padding: theme.spacing(0.5),
  },
  infoWrapper: {
    display: 'flex',
    alignItems: 'center',
    marginRight: theme.spacing(2),
    gap: theme.spacing(0.5),
    textTransform: 'lowercase',
    whiteSpace: 'nowrap',
  },
  infoText: {
    textTransform: 'lowercase',
    whiteSpace: 'nowrap',
  },
});

type StyleClasses = { [key: string]: string };

type ToolBarOwnProps = {
  numberOfSelectedElements: number;
  teamsFromExerciseOrScenario?: ToolBarTeamInput[];
  canManage?: boolean;
  handleClearSelectedElements: () => void;
  handleExport?: (withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) => void;
  handleBulkTest?: (actions?: ToolBarActionInput[]) => void;
  handleUpdate?: (actions: ToolBarBulkUpdateActionInput[]) => Promise<void> | void;
  handleBulkDelete?: (actions?: ToolBarActionInput[]) => void;
  info?: string;
  toolTasks?: ToolBarTask[];
  showExport?: boolean;
  showUpdate?: boolean;
  showBulkTest?: boolean;
  showBulkDelete?: boolean;
};

type ReduxProps = {
  endpoints: ToolBarSelectOption[];
  assetGroups: ToolBarSelectOption[];
  teams: ToolBarSelectOption[];
};

type DispatchProps = {
  fetchEndpoints: () => void;
  fetchAssetGroups: () => void;
};

type I18nProps = { t: (key: string, options?: Record<string, unknown>) => string };

type ClassesProps = { classes: StyleClasses };

type ToolBarProps = ToolBarOwnProps & ReduxProps & DispatchProps & I18nProps & ClassesProps;

type ToolBarState = {
  displayExport: boolean;
  displayUpdate: boolean;
  displayBulkDelete: boolean;
  displayBulkTest: boolean;
  processing: boolean;
  actions: unknown[];
  actionsInputs: ToolBarActionInput[];
};

export class ToolBarComponent extends Component<ToolBarProps, ToolBarState> {
  constructor(props: ToolBarProps) {
    super(props);
    this.state = {
      displayExport: false,
      displayUpdate: false,
      displayBulkDelete: false,
      displayBulkTest: false,
      processing: false,
      actions: [],
      actionsInputs: [{}],
    };
  }

  componentDidMount() {
    if (this.props.canManage && this.props.handleUpdate) {
      this.props.fetchEndpoints();
      this.props.fetchAssetGroups();
    }
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

  handleSubmitExport(withPlayers: boolean, withTeams: boolean, withVariableValues: boolean) {
    this.handleCloseExport();
    this.props.handleClearSelectedElements();
    this.props.handleExport?.(withPlayers, withTeams, withVariableValues);
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
    this.props.handleBulkTest?.(this.state.actionsInputs);
  };

  handleAddStep() {
    this.setState(prevState => ({ actionsInputs: [...prevState.actionsInputs, {}] }));
  }

  handleRemoveStep(i: number) {
    const { actionsInputs } = this.state;
    actionsInputs.splice(i, 1);
    this.setState({ actionsInputs });
  }

  handleChangeActionInput(i: number, key: string, event: { target: { value: string } }) {
    const { value } = event.target;
    const actionsInputs = [...this.state.actionsInputs];
    actionsInputs[i] = {
      ...actionsInputs[i],
      [key]: value,
    };
    if (key === 'field') {
      actionsInputs[i] = {
        ...actionsInputs[i],
        values: [],
      };
      if (
        value === 'object-marking'
        || value === 'object-label'
        || value === 'created-by'
        || value === 'external-reference'
      ) {
        actionsInputs[i] = {
          ...actionsInputs[i],
          fieldType: 'RELATION',
        };
      } else {
        actionsInputs[i] = {
          ...actionsInputs[i],
          fieldType: 'ATTRIBUTE',
        };
      }
    }
    this.setState({ actionsInputs });
  }

  handleChangeActionInputValues(
    i: number,
    event: {
      stopPropagation: () => void;
      preventDefault: () => void;
    } | null,
    value: ToolBarActionValue[] | ToolBarActionValue | null,
  ) {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const actionsInputs = [...this.state.actionsInputs];
    const normalizedValues = Array.isArray(value)
      ? value
      : value
        ? [value]
        : [];
    actionsInputs[i] = {
      ...actionsInputs[i],
      values: normalizedValues,
    };
    this.setState({ actionsInputs });
  }

  handleChangeActionInputValuesReplace(i: number, event: { target: { value: string } }) {
    const { value } = event.target;
    const actionsInputs = [...this.state.actionsInputs];
    actionsInputs[i] = {
      ...(actionsInputs[i]),
      values: [value],
    };
    this.setState({ actionsInputs });
  }

  renderFieldOptions(i: number) {
    const { t } = this.props;
    const { actionsInputs } = this.state;
    const disabled = !actionsInputs[i]?.type;
    let options: ToolBarSelectOption[] = [];
    if (actionsInputs[i]?.type === 'ADD' || actionsInputs[i]?.type === 'REPLACE' || actionsInputs[i]?.type === 'REMOVE') {
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
        value={actionsInputs[i]?.field || ''}
        onChange={event => this.handleChangeActionInput(i, 'field', event as { target: { value: string } })}
      >
        {options.length > 0 ? (
          options.map(n => (
            <MenuItem key={n.value} value={n.value}>
              {n.label}
            </MenuItem>
          ))
        ) : (
          <MenuItem value="none">{t('None')}</MenuItem>
        )}
      </Select>
    );
  }

  handleSearch(i: number, event: unknown, newValue: string) {
    if (!event) return;
    const actionsInputs = [...this.state.actionsInputs];
    actionsInputs[i] = {
      ...(actionsInputs[i]),
      inputValue: newValue && newValue.length > 0 ? newValue : '',
    };
    this.setState({ actionsInputs });
  }

  renderValuesOptions(i: number) {
    const { t, classes } = this.props;
    const { actionsInputs } = this.state;
    const disabled = !actionsInputs[i]?.field;
    switch (actionsInputs[i]?.field) {
      case 'assets':
        return (
          <Autocomplete
            disabled={disabled}
            size="small"
            fullWidth
            selectOnFocus
            autoHighlight
            getOptionLabel={(option: ToolBarSelectOption) => (option.label ? option.label : '')}
            value={(actionsInputs[i]?.values as ToolBarSelectOption[]) || []}
            multiple
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Values')}
                fullWidth
                style={{ marginTop: 3 }}
              />
            )}
            noOptionsText={t('No available options')}
            options={this.props.endpoints}
            onInputChange={(event, value) => this.handleSearch(i, event, value)}
            inputValue={actionsInputs[i]?.inputValue || ''}
            onChange={(event, value) => this.handleChangeActionInputValues(i, event, value)}
            renderOption={(props, option: ToolBarSelectOption) => (
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
            fullWidth
            selectOnFocus
            autoHighlight
            getOptionLabel={(option: ToolBarSelectOption) => (option.label ? option.label : '')}
            value={(actionsInputs[i]?.values as ToolBarSelectOption[]) || []}
            multiple
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Values')}
                fullWidth
                style={{ marginTop: 3 }}
              />
            )}
            noOptionsText={t('No available options')}
            options={this.props.assetGroups}
            onInputChange={(event, value) => this.handleSearch(i, event, value)}
            inputValue={actionsInputs[i]?.inputValue || ''}
            onChange={(event, value) => this.handleChangeActionInputValues(i, event, value)}
            renderOption={(props, option: ToolBarSelectOption) => (
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
            fullWidth
            selectOnFocus
            autoHighlight
            getOptionLabel={(option: ToolBarSelectOption) => (option.label ? option.label : '')}
            value={(actionsInputs[i]?.values as ToolBarSelectOption[]) || []}
            multiple
            renderInput={params => (
              <TextField
                {...params}
                variant="standard"
                label={t('Values')}
                fullWidth
                style={{ marginTop: 3 }}
              />
            )}
            noOptionsText={t('No available options')}
            options={this.props.teams}
            onInputChange={(event, value) => this.handleSearch(i, event, value)}
            inputValue={actionsInputs[i]?.inputValue || ''}
            onChange={(event, value) => this.handleChangeActionInputValues(i, event, value)}
            renderOption={(props, option: ToolBarSelectOption) => (
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
            fullWidth
            onChange={event => this.handleChangeActionInputValuesReplace(i, event as { target: { value: string } })}
          />
        );
    }
  }

  areStepValid() {
    const { actionsInputs } = this.state;
    for (const n of actionsInputs) {
      if (!n?.type || !n.field || !n.values || n.values.length === 0) {
        return false;
      }
    }
    return true;
  }

  handleLaunchUpdate() {
    this.handleCloseUpdate();
    this.props.handleClearSelectedElements();
    const updateActions: ToolBarBulkUpdateActionInput[] = this.state.actionsInputs
      .filter((action): action is Required<Pick<ToolBarActionInput, 'field' | 'type' | 'values'>> => {
        return Boolean(action?.field && action?.type && action?.values?.length);
      })
      .map(action => ({
        field: action.field as ToolBarBulkUpdateActionInput['field'],
        type: action.type as ToolBarBulkUpdateActionInput['type'],
        values: action.values.map(value => ({ value: typeof value === 'string' ? value : value.value })),
      }));
    this.props.handleUpdate?.(updateActions);
  }

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
    this.props.handleBulkDelete?.(this.state.actionsInputs);
  };

  render() {
    const {
      t,
      classes,
      numberOfSelectedElements,
      handleClearSelectedElements,
      canManage = false,
      info,
      toolTasks = [],
      showExport,
      showUpdate,
      showBulkTest,
      showBulkDelete,
    } = this.props;
    const { actionsInputs } = this.state;
    const canExport = showExport ?? Boolean(this.props.handleExport);
    const canUpdate = showUpdate ?? (canManage && Boolean(this.props.handleUpdate));
    const canTest = showBulkTest ?? (canManage && Boolean(this.props.handleBulkTest));
    const canDelete = showBulkDelete ?? (canManage && Boolean(this.props.handleBulkDelete));
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
        <div className={classes.toolbar} data-testid="openaev-toolbar">
          <Typography
            className={classes.title}
            color="inherit"
          >
            <span className={classes.selectedCount}>
              {numberOfSelectedElements}
            </span>
            {' '}
            {t('selected').toLowerCase()}
            <IconButton
              aria-label="clear"
              disabled={numberOfSelectedElements === 0 || this.state.processing}
              onClick={() => handleClearSelectedElements()}
              size="small"
              color="primary"
              className={classes.clearButton}
            >
              <ClearOutlined fontSize="small" />
            </IconButton>
          </Typography>
          {info && (
            <div className={classes.infoWrapper}>
              <InfoOutlined fontSize="small" color="info" />
              <Typography variant="body2">
                {info.toLowerCase()}
              </Typography>
            </div>
          )}
          {canExport && (
            <Tooltip title={t('Export')}>
              <span>
                <IconButton
                  aria-label="export"
                  disabled={numberOfSelectedElements === 0 || this.state.processing}
                  onClick={this.handleOpenExport.bind(this)}
                  color="primary"
                  size="small"
                >
                  <FileDownloadOutlined fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
          {canUpdate && (
            <Tooltip title={t('Update')}>
              <span>
                <IconButton
                  aria-label="update"
                  disabled={numberOfSelectedElements === 0 || this.state.processing}
                  onClick={this.handleOpenUpdate.bind(this)}
                  color="primary"
                  size="small"
                >
                  <BrushOutlined fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
          {canTest && (
            <Tooltip title={t('Test')}>
              <span>
                <IconButton
                  aria-label="test"
                  disabled={numberOfSelectedElements === 0 || this.state.processing}
                  onClick={this.handleOpenBulkTest.bind(this)}
                  color="primary"
                  size="small"
                >
                  <ForwardToInbox fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
          {canDelete && (
            <Tooltip title={t('Delete')}>
              <span>
                <IconButton
                  aria-label="delete"
                  disabled={numberOfSelectedElements === 0 || this.state.processing}
                  onClick={this.handleOpenBulkDelete.bind(this)}
                  color="primary"
                  size="small"
                >
                  <DeleteOutlined fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
          {toolTasks.map(toolTask => (
            <Tooltip key={toolTask.type} title={toolTask.title ?? ''}>
              <span>
                <IconButton
                  aria-label={toolTask.type}
                  disabled={numberOfSelectedElements === 0 || this.state.processing}
                  onClick={toolTask.onClick}
                  color="primary"
                  size="small"
                >
                  {toolTask.icon()}
                </IconButton>
              </span>
            </Tooltip>
          ))}
        </div>
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
            {new Array(actionsInputs.length)
              .fill(0)
              .map((_, i) => (
                <div key={`${actionsInputs[i]?.field || 'field'}-${actionsInputs[i]?.type || 'type'}-${i}`} className={classes.step}>
                  <IconButton
                    disabled={actionsInputs.length === 1}
                    aria-label="Delete"
                    className={classes.stepCloseButton}
                    onClick={this.handleRemoveStep.bind(this, i)}
                    size="small"
                  >
                    <CancelOutlined fontSize="small" />
                  </IconButton>
                  <Grid container spacing={3} className={classes.formControlPanel}>
                    <Grid size={{ xs: 3 }}>
                      <FormControl className={classes.formControl}>
                        <InputLabel>{t('Action type')}</InputLabel>
                        <Select
                          variant="standard"
                          value={actionsInputs[i]?.type || ''}
                          onChange={event => this.handleChangeActionInput(i, 'type', event as { target: { value: string } })}
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
        <ExportOptionsDialog
          title={t('inject_export_json_selection')}
          open={this.state.displayExport}
          onCancel={this.handleCloseExport.bind(this)}
          onClose={this.handleCloseExport.bind(this)}
          onSubmit={this.handleSubmitExport.bind(this)}
        />
        <DialogDelete
          open={canDelete && this.state.displayBulkDelete}
          handleClose={this.handleCloseBulkDelete.bind(this)}
          handleSubmit={this.handleSubmitBulkDelete.bind(this)}
          text={confirmationText()}
        />
        <DialogTest
          open={canTest && this.state.displayBulkTest}
          handleClose={this.handleCloseBulkTest.bind(this)}
          handleSubmit={this.handleSubmitBulkTest.bind(this)}
          text={testConfirmationText()}
          alertText={t('Only SMS and emails related injects will be tested')}
        />
      </>
    );
  }
}

const mapStateToProps = (state: unknown, ownProps: ToolBarOwnProps): ReduxProps => {
  const helper = storeHelper(state as never);
  const endpoints = (helper.getEndpoints().toJS() as ToolBarEndpointInput[])
    .map(n => ({
      label: n.asset_name,
      value: n.asset_id,
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
  const assetGroups = (helper.getAssetGroups().toJS() as ToolBarAssetGroupInput[])
    .map(n => ({
      label: n.asset_group_name,
      value: n.asset_group_id,
    }))
    .sort((a, b) => a.label.localeCompare(b.label));
  const teams = (ownProps.teamsFromExerciseOrScenario ?? [])
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

const StyledToolBar = withStyles(ToolBarComponent as ComponentType<any>, styles as any);
const I18nToolBar = inject18n(StyledToolBar);
const ConnectedToolBar = connect(mapStateToProps, {
  fetchEndpoints,
  fetchAssetGroups,
})(I18nToolBar as ComponentType<unknown>);

export default ConnectedToolBar as ComponentType<ToolBarOwnProps>;

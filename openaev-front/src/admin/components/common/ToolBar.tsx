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
  Box,
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

type ToolBarProps = ToolBarOwnProps & ReduxProps & DispatchProps & I18nProps;

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
    let normalizedValues: ToolBarActionValue[] = [];
    if (Array.isArray(value)) {
      normalizedValues = value;
    } else if (value) {
      normalizedValues = [value];
    }
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
    const { t } = this.props;
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
                <Box sx={{
                  pt: 0.5,
                  display: 'inline-block',
                }}
                >
                  <DevicesOtherOutlined />
                </Box>
                <Box sx={{
                  display: 'inline-block',
                  flexGrow: 1,
                  ml: 1.25,
                }}
                >
                  {option.label}
                </Box>
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
                <Box sx={{
                  pt: 0.5,
                  display: 'inline-block',
                }}
                >
                  <SelectGroup />
                </Box>
                <Box sx={{
                  display: 'inline-block',
                  flexGrow: 1,
                  ml: 1.25,
                }}
                >
                  {option.label}
                </Box>
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
                <Box sx={{
                  pt: 0.5,
                  display: 'inline-block',
                }}
                >
                  <GroupsOutlined />
                </Box>
                <Box sx={{
                  display: 'inline-block',
                  flexGrow: 1,
                  ml: 1.25,
                }}
                >
                  {option.label}
                </Box>
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
        <Box
          data-testid="openaev-toolbar"
          sx={{
            display: 'flex',
            alignItems: 'center',
            flex: '1 1 100%',
            width: '100%',
            backgroundColor: 'rgb(15, 30, 56)',
            pr: 1,
          }}
        >
          <Typography
            sx={{
              flex: '1 1 100%',
              fontSize: 12,
              display: 'flex',
              alignItems: 'center',
              gap: 0.5,
              textTransform: 'none',
            }}
            color="inherit"
          >
            <Box component="span" sx={{ fontWeight: 'bold' }}>
              {numberOfSelectedElements}
            </Box>
            {' '}
            {t('selected').toLowerCase()}
            <IconButton
              aria-label="clear"
              disabled={numberOfSelectedElements === 0 || this.state.processing}
              onClick={() => handleClearSelectedElements()}
              size="small"
              color="primary"
              sx={{
                ml: 0,
                p: 0.5,
              }}
            >
              <ClearOutlined fontSize="small" />
            </IconButton>
          </Typography>
          {info && (
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                mr: 2,
                gap: 0.5,
                textTransform: 'lowercase',
                whiteSpace: 'nowrap',
              }}
            >
              <InfoOutlined fontSize="small" color="info" />
              <Typography variant="body2">
                {info.toLowerCase()}
              </Typography>
            </Box>
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
        </Box>
        <Drawer
          open={this.state.displayUpdate}
          anchor="right"
          elevation={1}
          sx={{
            'zIndex': 1202,
            '& .MuiDrawer-paper': {
              minHeight: '100vh',
              width: '50%',
              position: 'fixed',
              transition: theme => theme.transitions.create('width', {
                easing: theme.transitions.easing.sharp,
                duration: theme.transitions.duration.enteringScreen,
              }),
            },
          }}
          onClose={this.handleCloseUpdate.bind(this)}
        >
          <Box sx={{
            backgroundColor: 'background.nav',
            p: 2.5,
          }}
          >
            <IconButton
              aria-label="Close"
              sx={{
                position: 'absolute',
                top: 1.5,
                left: 0.625,
                color: 'inherit',
              }}
              onClick={this.handleCloseUpdate.bind(this)}
              size="large"
              color="primary"
            >
              <CloseOutlined fontSize="small" color="primary" />
            </IconButton>
            <Typography variant="h6">{t('Update objects')}</Typography>
          </Box>
          <Box sx={{
            p: 1.25,
            mt: 2.5,
          }}
          >
            {new Array(actionsInputs.length)
              .fill(0)
              .map((_, i) => (
                <Box
                  key={`${actionsInputs[i]?.field || 'field'}-${actionsInputs[i]?.type || 'type'}-${i}`}
                  sx={{
                    position: 'relative',
                    width: '100%',
                    mb: 2.5,
                    p: 1.875,
                    verticalAlign: 'middle',
                    border: '1px solid',
                    borderColor: 'primary.main',
                    borderRadius: 1,
                    display: 'flex',
                  }}
                >
                  <IconButton
                    disabled={actionsInputs.length === 1}
                    aria-label="Delete"
                    sx={{
                      position: 'absolute',
                      top: -2.5,
                      right: -2.5,
                    }}
                    onClick={this.handleRemoveStep.bind(this, i)}
                    size="small"
                  >
                    <CancelOutlined fontSize="small" />
                  </IconButton>
                  <Grid container spacing={3} sx={{ width: '100%' }}>
                    <Grid size={{ xs: 3 }}>
                      <FormControl sx={{ width: '100%' }}>
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
                      <FormControl sx={{ width: '100%' }}>
                        <InputLabel>{t('Field')}</InputLabel>
                        {this.renderFieldOptions(i)}
                      </FormControl>
                    </Grid>
                    <Grid size={{ xs: 6 }}>
                      {this.renderValuesOptions(i)}
                    </Grid>
                  </Grid>
                </Box>
              ))}
            <Box>
              <Button
                disabled={!this.areStepValid()}
                variant="contained"
                color="secondary"
                size="small"
                onClick={this.handleAddStep.bind(this)}
                sx={{
                  width: '100%',
                  height: 2.5,
                }}
              >
                <AddOutlined fontSize="small" />
              </Button>
            </Box>
            <Box sx={{
              mt: 2.5,
              textAlign: 'right',
            }}
            >
              <Button
                disabled={!this.areStepValid()}
                variant="contained"
                color="primary"
                onClick={this.handleLaunchUpdate.bind(this)}
                sx={{ ml: 2 }}
              >
                {t('Update')}
              </Button>
            </Box>
          </Box>
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

const I18nToolBar = inject18n(ToolBarComponent);
const ConnectedToolBar = connect(mapStateToProps, {
  fetchEndpoints,
  fetchAssetGroups,
})(I18nToolBar as ComponentType<unknown>);

export default ConnectedToolBar as ComponentType<ToolBarOwnProps>;

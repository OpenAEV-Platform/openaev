import { Button, Combobox, ComboboxChips, ComboboxClear, ComboboxContent, ComboboxControls, ComboboxField, ComboboxInput, ComboboxLabel, ComboboxTrigger, IconButton, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import {
  AddOutlined,
  BrushOutlined,
  CancelOutlined,
  ClearOutlined,
  DeleteOutlined,
  DevicesOtherOutlined,
  FileDownloadOutlined,
  ForwardToInbox,
  GroupsOutlined,
  InfoOutlined,
} from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { Component, type ComponentType, type JSX, type ReactNode } from 'react';
import { connect } from 'react-redux';

import { fetchAssetGroups } from '../../../actions/asset_groups/assetgroup-action';
import { fetchEndpoints } from '../../../actions/assets/endpoint-actions';
import { storeHelper } from '../../../actions/Schema';
import DialogDelete from '../../../components/common/DialogDelete';
import DialogTest from '../../../components/common/DialogTest';
import Drawer from '../../../components/common/Drawer';
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
  customAction?: JSX.Element;
  showExport?: boolean;
  showUpdate?: boolean;
  showBulkTest?: boolean;
  showBulkDelete?: boolean;
  /**
   * Already-translated delete confirmation texts. Defaults to the inject wording
   * for backward compatibility with the injects lists.
   */
  deleteConfirmationSingular?: string;
  deleteConfirmationPlural?: string;
  /** Extra content rendered inside the delete confirmation dialog (below the
   *  confirmation question). Use this to display contextual warnings, e.g. when
   *  some of the selected items are currently running. */
  deleteExtraContent?: ReactNode;
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

  handleSubmitExport(withPlayers: boolean, withTeams: boolean, withVariableValues: boolean, _withScopeDefinition: boolean) {
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
        disabled={disabled}
        value={actionsInputs[i]?.field || ''}
        onValueChange={next => this.handleChangeActionInput(i, 'field', { target: { value: next } })}
      >
        {/* The library field reserves no notch for a floating label, so the
            label is the library's own and sits above the field. */}
        <SelectLabel>{t('Field')}</SelectLabel>
        <SelectTrigger className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {options.length > 0 ? (
            options.map(n => (
              <SelectItem key={n.value} value={n.value}>
                {n.label}
              </SelectItem>
            ))
          ) : (
            <SelectItem value="none">{t('None')}</SelectItem>
          )}
        </SelectContent>
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
    // The three fields differ only in where their options come from and which
    // glyph each row draws. Before one is picked the same field renders,
    // disabled and empty, so it does not change shape under the cursor.
    const sources: Record<string, {
      options: ToolBarSelectOption[];
      Glyph: typeof GroupsOutlined;
    }> = {
      assets: {
        options: this.props.endpoints,
        Glyph: DevicesOtherOutlined,
      },
      asset_groups: {
        options: this.props.assetGroups,
        Glyph: SelectGroup,
      },
      teams: {
        options: this.props.teams,
        Glyph: GroupsOutlined,
      },
    };
    const source = sources[actionsInputs[i]?.field ?? ''];
    const Glyph = source?.Glyph;
    return (
      <Combobox<ToolBarSelectOption>
        multiple
        disabled={disabled}
        options={source?.options ?? []}
        value={(actionsInputs[i]?.values as ToolBarSelectOption[]) || []}
        getOptionLabel={option => option.label ?? ''}
        isOptionEqualToValue={(option, val) => option.value === val.value}
        inputValue={actionsInputs[i]?.inputValue || ''}
        onInputChange={(search, meta) => {
          if (meta.cause === 'type') {
            this.handleSearch(i, meta.event, search);
          }
        }}
        onValueChange={next => this.handleChangeActionInputValues(i, null, next as ToolBarSelectOption[])}
        renderOption={Glyph
          ? option => (
            <>
              <Glyph fontSize="small" />
              <span>{option.label}</span>
            </>
          )
          : undefined}
      >
        <ComboboxLabel>{t('Values')}</ComboboxLabel>
        <ComboboxField>
          <ComboboxChips />
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxClear />
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent emptyMessage={t('No available options')} />
      </Combobox>
    );
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
      customAction,
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
      if (numberOfSelectedElements === 1) {
        return this.props.deleteConfirmationSingular ?? t('Do you want to delete this inject?');
      }
      return this.props.deleteConfirmationPlural
        ?? t('Do you want to delete these {count} injects?', { count: numberOfSelectedElements });
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
            backgroundColor: 'background.accent',
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
              icon={<ClearOutlined fontSize="small" />}
              aria-label="clear"
              disabled={numberOfSelectedElements === 0 || this.state.processing}
              onClick={() => handleClearSelectedElements()}
              priority="tertiary"
              size="md"
            />
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
          {customAction && customAction}
          {canExport && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span>
                  <span className="inline-flex">
                    <IconButton
                      icon={<FileDownloadOutlined fontSize="small" />}
                      aria-label="export"
                      disabled={numberOfSelectedElements === 0 || this.state.processing}
                      onClick={this.handleOpenExport.bind(this)}
                      priority="tertiary"
                      size="md"
                    />
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent>{t('Export')}</TooltipContent>
            </Tooltip>
          )}
          {canUpdate && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span>
                  <span className="inline-flex">
                    <IconButton
                      icon={<BrushOutlined fontSize="small" />}
                      aria-label="update"
                      disabled={numberOfSelectedElements === 0 || this.state.processing}
                      onClick={this.handleOpenUpdate.bind(this)}
                      priority="tertiary"
                      size="md"
                    />
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent>{t('Update')}</TooltipContent>
            </Tooltip>
          )}
          {canTest && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span>
                  <span className="inline-flex">
                    <IconButton
                      icon={<ForwardToInbox fontSize="small" />}
                      aria-label="test"
                      disabled={numberOfSelectedElements === 0 || this.state.processing}
                      onClick={this.handleOpenBulkTest.bind(this)}
                      priority="tertiary"
                      size="md"
                    />
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent>{t('Test')}</TooltipContent>
            </Tooltip>
          )}
          {canDelete && (
            <Tooltip>
              <TooltipTrigger asChild>
                <span>
                  <span className="inline-flex">
                    <IconButton
                      icon={<DeleteOutlined fontSize="small" />}
                      aria-label="delete"
                      disabled={numberOfSelectedElements === 0 || this.state.processing}
                      onClick={this.handleOpenBulkDelete.bind(this)}
                      priority="tertiary"
                      size="md"
                    />
                  </span>
                </span>
              </TooltipTrigger>
              <TooltipContent>{t('Delete')}</TooltipContent>
            </Tooltip>
          )}
          {toolTasks.map(toolTask => (
            <Tooltip key={toolTask.type}>
              <TooltipTrigger asChild>
                <span>
                  <span className="inline-flex">
                    <IconButton
                      icon={toolTask.icon()}
                      aria-label={toolTask.type}
                      disabled={numberOfSelectedElements === 0 || this.state.processing}
                      onClick={toolTask.onClick}
                      priority="tertiary"
                      size="md"
                    />
                  </span>
                </span>
              </TooltipTrigger>
              {(toolTask.title ?? '') && <TooltipContent>{toolTask.title ?? ''}</TooltipContent>}
            </Tooltip>
          ))}
        </Box>
        <Drawer
          open={this.state.displayUpdate}
          handleClose={this.handleCloseUpdate.bind(this)}
          title={t('Update objects')}
        >
          <Box sx={{ mt: 1 }}>
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
                    icon={<CancelOutlined fontSize="small" />}
                    disabled={actionsInputs.length === 1}
                    aria-label="Delete"
                    onClick={this.handleRemoveStep.bind(this, i)}
                    style={{
                      position: 'absolute',
                      top: -2.5,
                      right: -2.5,
                    }}
                    priority="tertiary"
                    size="md"
                  />
                  <Box sx={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: 3,
                    width: '100%',
                  }}
                  >
                    <Box sx={{ flex: '0 0 25%' }}>
                      <Select
                        value={actionsInputs[i]?.type || ''}
                        onValueChange={next => this.handleChangeActionInput(i, 'type', { target: { value: next } })}
                      >
                        <SelectLabel>{t('Action type')}</SelectLabel>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder={t('Action type')} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="ADD">{t('Add')}</SelectItem>
                          <SelectItem value="REPLACE">{t('Replace')}</SelectItem>
                          <SelectItem value="REMOVE">{t('Remove')}</SelectItem>
                        </SelectContent>
                      </Select>
                    </Box>
                    {/* Its trigger is `w-full`, so it needs a real basis: in a shrink-to-fit
                        flex item the 100% would resolve against the content and collapse. */}
                    <Box sx={{ flex: '0 0 25%' }}>
                      {this.renderFieldOptions(i)}
                    </Box>
                    <Box sx={{
                      flex: 1,
                      minWidth: 0,
                    }}
                    >
                      {this.renderValuesOptions(i)}
                    </Box>
                  </Box>
                </Box>
              ))}
            <Box>
              <Button type="button" priority="secondary" fullWidth aria-label={t('Add')} disabled={!this.areStepValid()} onClick={this.handleAddStep.bind(this)}>
                <AddOutlined fontSize="small" />
              </Button>
            </Box>
            <Box sx={{
              mt: 2.5,
              textAlign: 'right',
            }}
            >
              <Button type="button" disabled={!this.areStepValid()} onClick={this.handleLaunchUpdate.bind(this)} style={{ marginLeft: 16 }}>
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
          extraContent={this.props.deleteExtraContent}
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

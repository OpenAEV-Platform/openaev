import { RouteOutlined } from '@mui/icons-material';
import { Box, Checkbox, List, ListItem, ListItemButton, ListItemIcon, ListItemText, ToggleButtonGroup } from '@mui/material';
import { type CSSProperties, useContext, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { bulkDeleteScenarios, searchScenarios } from '../../../actions/scenarios/scenario-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import ItemCategory from '../../../components/ItemCategory';
import ItemSeverity from '../../../components/ItemSeverity';
import ItemTags from '../../../components/ItemTags';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import PlatformIconGroup from '../../../components/PlatformIconGroup';
import { type Scenario, type SearchPaginationInput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { AbilityContext, Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';
import ImportFromHubButton from '../common/ImportFromHubButton';
import ToolBar from '../common/ToolBar';
import ImportUploaderScenario from './ImportUploaderScenario';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import ScenarioType, { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from './scenario/ScenarioType';
import ScenarioCreation from './ScenarioCreation';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  scenario_name: { width: '22%' },
  scenario_severity: { width: '8%' },
  scenario_category: { width: '12%' },
  scenario_type: { width: '12%' },
  scenario_recurrence: { width: '10%' },
  scenario_platforms: { width: '10%' },
  scenario_tags: { width: '16%' },
  scenario_updated_at: { width: '10%' },
};

const Scenarios = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const { isXTMHubAccessible } = useAuth();
  const isChainingFeatureEnabled = isFeatureEnabled('INJECT_CHAINING');

  const [loading, setLoading] = useState<boolean>(true);
  const [reloadCount, setReloadCount] = useState<number>(0);

  // Headers
  const headers = useMemo(() => [
    {
      field: 'scenario_name',
      label: 'Name',
      isSortable: true,
      value: (scenario: Scenario) => scenario.scenario_name,
    },
    {
      field: 'scenario_severity',
      label: 'Severity',
      isSortable: true,
      value: (scenario: Scenario) => (
        <ItemSeverity
          label={t(scenario.scenario_severity ?? 'Unknown')}
          severity={scenario.scenario_severity ?? 'Unknown'}
          variant="inList"
        />
      ),
    },
    {
      field: 'scenario_category',
      label: 'Category',
      isSortable: true,
      value: (scenario: Scenario) => (
        <ItemCategory
          category={scenario.scenario_category ?? 'Unknown'}
          label={t(scenario.scenario_category ?? 'Unknown')}
          size="medium"
        />
      ),
    },
    {
      field: 'scenario_type',
      label: 'Type',
      // Derived engine facet (no single sortable column), mirroring the backend ScenarioSpecification:
      // a scenario carrying a chaining workflow template is Chained, otherwise it is a classic
      // Time-based scenario. Autonomy is a launch-time MODE now, not a scenario type.
      isSortable: false,
      value: (scenario: Scenario) => {
        const workflowId = (scenario as unknown as Record<string, unknown>).scenario_workflow_id;
        const type: ScenarioTypeValue = workflowId ? SCENARIO_TYPE_CHAINED : SCENARIO_TYPE_TIME_BASED;
        return <ScenarioType type={type} variant="list" />;
      },
    },
    {
      field: 'scenario_recurrence',
      label: 'Status',
      isSortable: false,
      value: (scenario: Scenario) => <ScenarioStatus scenario={scenario} variant="list" />,
    },
    {
      field: 'scenario_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (scenario: Scenario) => (
        <PlatformIconGroup platforms={scenario.scenario_platforms} />
      ),
    },
    {
      field: 'scenario_tags',
      label: 'Tags',
      isSortable: false,
      value: (scenario: Scenario) => <ItemTags tags={scenario.scenario_tags} variant="list" />,
    },
    {
      field: 'scenario_updated_at',
      label: 'Updated',
      isSortable: true,
      value: (scenario: Scenario) => nsdt(scenario.scenario_updated_at),
    },
  ], [t, nsdt]);

  const [scenarios, setScenarios] = useState<Scenario[]>([]);

  // Filters
  const availableFilterNames = [
    'scenario_category',
    'scenario_kill_chain_phases',
    'scenario_name',
    'scenario_platforms',
    'scenario_recurrence',
    'scenario_severity',
    'scenario_tags',
    'scenario_type',
    'scenario_updated_at',
  ];

  const {
    queryableHelpers,
    searchPaginationInput,
    setSearchPaginationInput,
  } = useQueryableWithLocalStorage('scenarios', buildSearchPagination({ sorts: initSorting('scenario_updated_at', 'DESC') }));

  // Export
  const exportProps = {
    exportType: 'scenario',
    exportKeys: [
      'scenario_name',
      'scenario_severity',
      'scenario_category',
      'scenario_main_focus',
      'scenario_platforms',
      'scenario_tags',
      'scenario_updated_at',
    ],
    exportData: scenarios,
    exportFileName: `${t('Scenarios')}.csv`,
  };

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchScenarios(input).finally(() => setLoading(false));
  };

  // Bulk selection
  const ability = useContext(AbilityContext);
  const canManage = ability.can(ACTIONS.MANAGE, SUBJECTS.ASSESSMENT);
  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<Scenario>('scenario', scenarios, queryableHelpers.paginationHelpers.getTotalElements());

  const bulkDelete = () => {
    bulkDeleteScenarios({
      search_pagination_input: selectAll ? searchPaginationInput : undefined,
      scenario_ids_to_process: selectAll ? undefined : Object.keys(selectedElements),
      scenario_ids_to_ignore: Object.keys(deSelectedElements),
    }).then((result) => {
      const deletedIds: string[] = result.data ?? [];
      const newTotal = Math.max(0, queryableHelpers.paginationHelpers.getTotalElements() - deletedIds.length);
      setScenarios(scenarios.filter(s => !deletedIds.includes(s.scenario_id)));
      queryableHelpers.paginationHelpers.handleChangeTotalElements(newTotal);
      handleClearSelectedElements();
    });
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Scenarios'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={search}
        reloadContentCount={reloadCount}
        searchPaginationInput={searchPaginationInput}
        setContent={setScenarios}
        entityPrefix="scenario"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            {
              isXTMHubAccessible && (
                <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
                  <ImportFromHubButton serviceIdentifier="openaev_scenarios" />
                </Can>
              )
            }
            <ToggleButtonGroup value="fake" exclusive>
              <ExportButton
                totalElements={queryableHelpers.paginationHelpers.getTotalElements()}
                exportProps={exportProps}
              />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
                <ImportUploaderScenario refresh={() => setReloadCount(count => count + 1)} />
              </Can>
            </ToggleButtonGroup>
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
              <ScenarioCreation />
            </Can>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          sx={numberOfSelectedElements > 0
            ? {
                // Massive-operations toolbar: symmetric vertical padding keeps the
                // checkbox and actions vertically centered in the accent band.
                backgroundColor: 'background.accent',
                paddingBlock: 0.5,
              }
            : { paddingTop: 0 }}
          {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
        >
          {canManage && (
            <ListItemIcon style={{ minWidth: 40 }}>
              <Checkbox
                edge="start"
                checked={selectAll}
                disableRipple
                onChange={handleToggleSelectAll}
              />
            </ListItemIcon>
          )}
          {numberOfSelectedElements > 0 ? (
            <ListItemText
              primary={(
                <ToolBar
                  numberOfSelectedElements={numberOfSelectedElements}
                  handleClearSelectedElements={handleClearSelectedElements}
                  handleBulkDelete={bulkDelete}
                  canManage={canManage}
                  deleteConfirmationSingular={t('Do you want to delete this scenario?')}
                  deleteConfirmationPlural={t('Do you want to delete these {count} scenarios?', { count: String(numberOfSelectedElements) })}
                />
              )}
            />
          ) : (
            <>
              <ListItemIcon />
              <ListItemText
                primary={(
                  <SortHeadersComponentV2
                    headers={headers}
                    inlineStylesHeaders={inlineStyles}
                    sortHelpers={queryableHelpers.sortHelpers}
                  />
                )}
              />
            </>
          )}
        </ListItem>
        {
          loading
            ? <PaginatedListLoader Icon={RouteOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canManage} />
            : scenarios.map((scenario: Scenario) => {
                const isScenarioChaining = isChainingFeatureEnabled && !!(scenario as unknown as Record<string, unknown>).scenario_workflow_id;
                // A chained scenario owns its attack-path logic and is never duplicated by hand
                // (its metadata stays editable); a time-based one may also be duplicated.
                const scenarioActions: ('Duplicate' | 'Update' | 'Delete' | 'Export')[] = isScenarioChaining
                  ? ['Update', 'Export', 'Delete']
                  : ['Duplicate', 'Export', 'Delete'];
                return (
                  <ListItem
                    key={scenario.scenario_id}
                    divider
                    secondaryAction={(
                      <ScenarioPopover
                        scenario={scenario}
                        actions={scenarioActions}
                        onDelete={(result) => {
                          setScenarios(scenarios.filter(e => (e.scenario_id !== result)));
                          setSearchPaginationInput(prev => ({
                            ...prev,
                            size: prev.size - 1,
                          }));
                        }}
                        inList
                      />
                    )}
                    disablePadding
                  >
                    <ListItemButton
                      component={Link}
                      to={`/admin/scenarios/${scenario.scenario_id}`}
                      classes={{ root: classes.item }}
                    >
                      {canManage && (
                        <ListItemIcon
                          style={{ minWidth: 40 }}
                          onClick={event => onToggleEntity(scenario, event)}
                        >
                          <Checkbox
                            edge="start"
                            checked={
                              (selectAll && !(scenario.scenario_id in (deSelectedElements || {})))
                              || scenario.scenario_id in (selectedElements || {})
                            }
                            disableRipple
                          />
                        </ListItemIcon>
                      )}
                      <ListItemIcon>
                        <RouteOutlined color="primary" />
                      </ListItemIcon>
                      <ListItemText
                        primary={(
                          <div style={bodyItemsStyles.bodyItems}>
                            {headers.map(header => (
                              <div
                                key={header.field}
                                style={{
                                  ...bodyItemsStyles.bodyItem,
                                  ...inlineStyles[header.field],
                                }}
                              >
                                {header.value(scenario)}
                              </div>
                            ))}
                          </div>
                        )}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })
        }
      </List>
    </>
  );
};

export default Scenarios;

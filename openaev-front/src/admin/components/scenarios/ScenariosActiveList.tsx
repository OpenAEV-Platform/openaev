import { Checkbox } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type MouseEvent, useMemo } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useFormatter } from '../../../components/i18n';
import ItemCategory from '../../../components/ItemCategory';
import ItemSeverity from '../../../components/ItemSeverity';
import ItemTags from '../../../components/ItemTags';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import PlatformIconGroup from '../../../components/PlatformIconGroup';
import { type Scenario } from '../../../utils/api-types';
import ToolBar from '../common/ToolBar';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import ScenarioType, { SCENARIO_TYPE_CHAINED, SCENARIO_TYPE_TIME_BASED, type ScenarioTypeValue } from './scenario/ScenarioType';

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

interface Props {
  scenarios: Scenario[];
  loading: boolean;
  canManage: boolean;
  sortHelpers: SortHelpers;
  selectAll: boolean;
  numberOfSelectedElements: number;
  isSelected: (scenario: Scenario) => boolean;
  onToggleEntity: (scenario: Scenario, event: MouseEvent) => void;
  onToggleSelectAll: () => void;
  onClearSelectedElements: () => void;
  onBulkDelete: () => void;
  onDelete: (scenarioId: string) => void;
}

const ScenariosActiveList: FunctionComponent<Props> = ({
  scenarios,
  loading,
  canManage,
  sortHelpers,
  selectAll,
  numberOfSelectedElements,
  isSelected,
  onToggleEntity,
  onToggleSelectAll,
  onClearSelectedElements,
  onBulkDelete,
  onDelete,
}) => {
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();

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

  return (
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
              aria-label={t('Select all')}
              checked={selectAll}
              onCheckedChange={onToggleSelectAll}
            />
          </ListItemIcon>
        )}
        {numberOfSelectedElements > 0 ? (
          <ListItemText
            primary={(
              <ToolBar
                numberOfSelectedElements={numberOfSelectedElements}
                handleClearSelectedElements={onClearSelectedElements}
                handleBulkDelete={onBulkDelete}
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
                  sortHelpers={sortHelpers}
                />
              )}
            />
          </>
        )}
      </ListItem>
      {
        loading
          ? <PaginatedListLoader Icon={RouteOutlined} headers={headers} headerStyles={inlineStyles} withCheckbox={canManage} />
          : scenarios.map((scenario: Scenario) => (
              <ListItem
                key={scenario.scenario_id}
                divider
                secondaryAction={(
                  <ScenarioPopover
                    scenario={scenario}
                    // Chained and time-based scenarios share the same duplicate endpoint: a chained one
                    // gets its logic map copied along with its metadata.
                    actions={['Duplicate', 'Export', 'Delete']}
                    onDelete={onDelete}
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
                      <Checkbox aria-label={scenario.scenario_name} checked={isSelected(scenario)} />
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
            ))
      }
    </List>
  );
};

export default ScenariosActiveList;

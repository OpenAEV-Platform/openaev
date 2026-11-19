import { Checkbox } from '@filigran/design-system';
import { RouteOutlined } from '@mui/icons-material';
import { List, ListItem, ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type MouseEvent, type ReactNode, useMemo } from 'react';
import { Link } from 'react-router';

import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import { type SortHelpers } from '../../../../components/common/queryable/sort/SortHelpers';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useFormatter } from '../../../../components/i18n';
import ItemDomains from '../../../../components/ItemDomains';
import ItemTags from '../../../../components/ItemTags';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import PlatformIconGroup from '../../../../components/PlatformIconGroup';
import ToolBar from '../../common/ToolBar';
import ScenarioPopover from '../scenario/ScenarioPopover';
import ScenarioStatus from '../scenario/ScenarioStatus';
import { ScenarioTypeIcon } from '../scenario/ScenarioType';
import { type ScenarioRow } from '../scenarioRow';

const INLINE_STYLES: Record<string, CSSProperties> = {
  scenario_name: { width: '32%' },
  scenario_author_name: { width: '12%' },
  scenario_domains: { width: '18%' },
  scenario_recurrence: { width: '10%' },
  scenario_platforms: { width: '8%' },
  scenario_tags: { width: '10%' },
  scenario_last_run_at: { width: '10%' },
};

interface Props {
  scenarios: ScenarioRow[];
  loading: boolean;
  emptyState: ReactNode;
  sortHelpers: SortHelpers;
  canManage: boolean;
  selectAll: boolean;
  numberOfSelectedElements: number;
  isChecked: (scenario: ScenarioRow) => boolean;
  onToggle: (scenario: ScenarioRow, event: MouseEvent<HTMLElement>) => void;
  onToggleSelectAll: () => void;
  onClearSelection: () => void;
  onBulkDelete: () => void;
  onDelete: (scenarioId: string) => void;
}

const ScenariosList: FunctionComponent<Props> = ({
  scenarios,
  loading,
  emptyState,
  sortHelpers,
  canManage,
  selectAll,
  numberOfSelectedElements,
  isChecked,
  onToggle,
  onToggleSelectAll,
  onClearSelection,
  onBulkDelete,
  onDelete,
}) => {
  const { t, nsdt } = useFormatter();
  const bodyItemsStyles = useBodyItemsStyles();

  const headers = useMemo(() => [
    {
      field: 'scenario_name',
      label: 'Name',
      isSortable: true,
      value: (scenario: ScenarioRow) => scenario.scenario_name,
    },
    {
      field: 'scenario_author_name',
      label: 'Author',
      isSortable: false,
      value: (scenario: ScenarioRow) => scenario.scenario_author_name ?? '-',
    },
    {
      field: 'scenario_domains',
      label: 'Domains',
      isSortable: false,
      value: (scenario: ScenarioRow) => <ItemDomains domains={scenario.scenario_domains ?? []} variant="reduced-view" />,
    },
    {
      field: 'scenario_recurrence',
      label: 'Status',
      isSortable: false,
      value: (scenario: ScenarioRow) => <ScenarioStatus scenario={scenario} variant="list" />,
    },
    {
      field: 'scenario_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (scenario: ScenarioRow) => <PlatformIconGroup platforms={scenario.scenario_platforms} />,
    },
    {
      field: 'scenario_tags',
      label: 'Tags',
      isSortable: false,
      value: (scenario: ScenarioRow) => <ItemTags tags={scenario.scenario_tags} variant="list" />,
    },
    {
      field: 'scenario_last_run_at',
      label: 'Last run',
      isSortable: false,
      value: (scenario: ScenarioRow) => (scenario.scenario_last_run_at ? nsdt(scenario.scenario_last_run_at) : t('Never')),
    },
  ], [t, nsdt]);

  if (!loading && scenarios.length === 0) {
    return <>{emptyState}</>;
  }
  return (
    <List disablePadding>
      <ListItem
        sx={numberOfSelectedElements > 0
          ? {
              backgroundColor: 'background.accent',
              paddingBlock: 0.5,
            }
          : undefined}
        {...(numberOfSelectedElements === 0 ? { secondaryAction: <>&nbsp;</> } : {})}
      >
        {canManage && (
          <ListItemIcon>
            <Checkbox aria-label={t('Select all')} checked={selectAll} onCheckedChange={onToggleSelectAll} />
          </ListItemIcon>
        )}
        {numberOfSelectedElements > 0
          ? (
              <ListItemText
                primary={(
                  <ToolBar
                    numberOfSelectedElements={numberOfSelectedElements}
                    handleClearSelectedElements={onClearSelection}
                    handleBulkDelete={onBulkDelete}
                    canManage={canManage}
                    deleteConfirmationSingular={t('Do you want to delete this scenario?')}
                    deleteConfirmationPlural={t('Do you want to delete these {count} scenarios?', { count: String(numberOfSelectedElements) })}
                  />
                )}
              />
            )
          : (
              <>
                <ListItemIcon />
                <ListItemText primary={<SortHeadersComponentV2 headers={headers} inlineStylesHeaders={INLINE_STYLES} sortHelpers={sortHelpers} />} />
              </>
            )}
      </ListItem>
      {loading
        ? <PaginatedListLoader Icon={RouteOutlined} headers={headers} headerStyles={INLINE_STYLES} withCheckbox={canManage} />
        : scenarios.map(scenario => (
            <ListItem
              key={scenario.scenario_id}
              divider
              disablePadding
              secondaryAction={(
                <ScenarioPopover scenario={scenario} actions={['Duplicate', 'Export', 'Delete']} onDelete={onDelete} inList />
              )}
            >
              <ListItemButton component={Link} to={`/admin/scenarios/${scenario.scenario_id}`}>
                {canManage && (
                  <ListItemIcon onClick={event => onToggle(scenario, event)}>
                    <Checkbox aria-label={scenario.scenario_name} checked={isChecked(scenario)} />
                  </ListItemIcon>
                )}
                <ListItemIcon>
                  <ScenarioTypeIcon scenario={scenario} />
                </ListItemIcon>
                <ListItemText
                  primary={(
                    <div style={bodyItemsStyles.bodyItems}>
                      {headers.map(header => (
                        <div
                          key={header.field}
                          style={{
                            ...bodyItemsStyles.bodyItem,
                            ...INLINE_STYLES[header.field],
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
          ))}
    </List>
  );
};

export default ScenariosList;

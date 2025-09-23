import { MovieFilterOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, ToggleButtonGroup } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { fetchStatistics } from '../../../actions/Application';
import { searchScenarios } from '../../../actions/scenarios/scenario-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import { buildEmptyFilter } from '../../../components/common/queryable/filter/FilterUtils';
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
import PlatformIcon from '../../../components/PlatformIcon';
import { type FilterGroup, type Scenario, type SearchPaginationInput } from '../../../utils/api-types';
import useAuth from '../../../utils/hooks/useAuth';
import { Can } from '../../../utils/permissions/PermissionsProvider';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import ImportFromHubButton from '../common/ImportFromHubButton';
import ImportUploaderScenario from './ImportUploaderScenario';
import ScenarioPopover from './scenario/ScenarioPopover';
import ScenarioStatus from './scenario/ScenarioStatus';
import ScenarioCreation from './ScenarioCreation';

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  scenario_name: { width: '25%' },
  scenario_severity: { width: '8%' },
  scenario_category: { width: '12%' },
  scenario_recurrence: { width: '12%' },
  scenario_platforms: { width: '10%' },
  scenario_tags: { width: '18%' },
  scenario_updated_at: { width: '10%' },
};

const Scenarios = () => {
  // Standard hooks
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();
  const { t, nsdt } = useFormatter();
  const theme = useTheme();
  const { isXTMHubAccessible } = useAuth();

  const [loading, setLoading] = useState<boolean>(true);

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
      field: 'scenario_recurrence',
      label: 'Status',
      isSortable: false,
      value: (scenario: Scenario) => <ScenarioStatus scenario={scenario} variant="list" />,
    },
    {
      field: 'scenario_platforms',
      label: 'Platforms',
      isSortable: false,
      value: (scenario: Scenario) => {
        const platforms = scenario.scenario_platforms ?? [];
        if (platforms.length === 0) {
          return <PlatformIcon platform={t('No inject in this scenario')} tooltip width={25} />;
        }
        return (
          <>
            {platforms.map(
              (platform: string) => <PlatformIcon key={platform} platform={platform} tooltip width={20} marginRight={theme.spacing(2)} />,
            )}
          </>
        );
      },
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
  ], []);

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
    'scenario_updated_at',
  ];

  const quickFilter: FilterGroup = {
    mode: 'and',
    filters: [
      buildEmptyFilter('scenario_category', 'contains'),
      buildEmptyFilter('scenario_kill_chain_phases', 'contains'),
      buildEmptyFilter('scenario_tags', 'contains'),
    ],
  };

  const { queryableHelpers, searchPaginationInput, setSearchPaginationInput } = useQueryableWithLocalStorage('scenarios', buildSearchPagination({
    sorts: initSorting('scenario_updated_at', 'DESC'),
    filterGroup: quickFilter,
  }));

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
        searchPaginationInput={searchPaginationInput}
        setContent={setScenarios}
        entityPrefix="scenario"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1}>
            {
              isXTMHubAccessible && (
                <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
                  <ImportFromHubButton serviceIdentifier="openaev_scenarios" />
                </Can>
              )
            }
            <ToggleButtonGroup value="fake" exclusive>
              <ExportButton totalElements={queryableHelpers.paginationHelpers.getTotalElements()} exportProps={exportProps} />
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
                <ImportUploaderScenario />
              </Can>
            </ToggleButtonGroup>
          </Box>
        )}
      />
      <List>
        <ListItem
          classes={{ root: classes.itemHead }}
          style={{ paddingTop: 0 }}
          secondaryAction={<>&nbsp;</>}
        >
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
        </ListItem>
        {
          loading
            ? <PaginatedListLoader Icon={MovieFilterOutlined} headers={headers} headerStyles={inlineStyles} />
            : scenarios.map((scenario: Scenario) => {
                return (
                  <ListItem
                    key={scenario.scenario_id}
                    divider
                    secondaryAction={(
                      <ScenarioPopover
                        scenario={scenario}
                        actions={['Duplicate', 'Export', 'Delete']}
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
                      <ListItemIcon>
                        <MovieFilterOutlined color="primary" />
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
      <Can I={ACTIONS.MANAGE} a={SUBJECTS.ASSESSMENT}>
        <ScenarioCreation
          onCreate={(result: Scenario) => {
            setScenarios([result, ...scenarios]);
            fetchStatistics();
          }}
        />
      </Can>
    </>
  );
};

export default Scenarios;

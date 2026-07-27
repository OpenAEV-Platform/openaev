import { AnalyticsOutlined, GridViewOutlined, ViewListOutlined } from '@mui/icons-material';
import { Box, List, ListItem, ListItemButton, ListItemIcon, ListItemText, Skeleton, ToggleButton, ToggleButtonGroup, Tooltip } from '@mui/material';
import { type CSSProperties, type SyntheticEvent, useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { importCustomDashboard, searchCustomDashboards } from '../../../../actions/custom_dashboards/customdashboard-action';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import ImportUploaderJsonApiComponent from '../../../../components/common/import/ImportUploaderJsonApiComponent';
import { initSorting } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Header } from '../../../../components/common/SortHeadersList';
import { useFormatter } from '../../../../components/i18n';
import PaginatedListLoader from '../../../../components/PaginatedListLoader';
import type { CustomDashboard, SearchPaginationInput } from '../../../../utils/api-types';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import CustomDashboardCard from './CustomDashboardCard';
import CustomDashboardCreation from './CustomDashboardCreation';
import CustomDashboardPopover from './CustomDashboardPopover';

// Cards by default, with a persisted switch to the compact list (same pattern
// as the security platforms screen).
type ViewMode = 'cards' | 'list';
const VIEW_MODE_STORAGE_KEY = 'custom-dashboards:view-mode';
const readViewMode = (): ViewMode => (typeof window !== 'undefined' && window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'list' ? 'list' : 'cards');

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  custom_dashboard_name: { width: '30%' },
  custom_dashboard_description: { width: '70%' },
};

const CustomDashboards = () => {
  // Standard hooks
  const { t } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  // Pagination
  const [loading, setLoading] = useState<boolean>(true);
  const [customDashboards, setCustomDashboards] = useState<CustomDashboard[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('custom_dashboards', buildSearchPagination({ sorts: initSorting('custom_dashboard_name') }));
  const availableFilterNames = ['custom_dashboard_name'];

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchCustomDashboards(input).finally(() => setLoading(false));
  };

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'custom_dashboard_name',
      label: 'Name',
      isSortable: true,
      value: (customDashboard: CustomDashboard) => customDashboard.custom_dashboard_name,
    },
    {
      field: 'custom_dashboard_description',
      label: 'Description',
      isSortable: false,
      value: (customDashboard: CustomDashboard) => customDashboard.custom_dashboard_description ?? '',
    },
  ], []);

  const handleUpdate = useCallback(
    (customDashboard: CustomDashboard) => {
      setCustomDashboards(prev => prev.map(d =>
        d.custom_dashboard_id === customDashboard.custom_dashboard_id ? customDashboard : d,
      ));
    },
    [],
  );

  const handleDelete = useCallback(
    (id: string) => {
      setCustomDashboards(prev => prev.filter(d => d.custom_dashboard_id !== id));
    },
    [],
  );

  const [viewMode, setViewMode] = useState<ViewMode>(readViewMode);
  const handleViewModeChange = (_: SyntheticEvent, value: ViewMode | null) => {
    if (!value) return;
    setViewMode(value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
    }
  };

  const viewSwitcher = (
    <ToggleButtonGroup
      value={viewMode}
      exclusive
      size="small"
      onChange={handleViewModeChange}
      aria-label={t('View mode')}
      sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
    >
      <ToggleButton value="cards" aria-label={t('Cards view')}>
        <Tooltip title={t('Cards view')}>
          <GridViewOutlined fontSize="small" />
        </Tooltip>
      </ToggleButton>
      <ToggleButton value="list" aria-label={t('List view')}>
        <Tooltip title={t('List view')}>
          <ViewListOutlined fontSize="small" />
        </Tooltip>
      </ToggleButton>
    </ToggleButtonGroup>
  );

  const renderCards = () => {
    if (loading) {
      return (
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 2,
          mt: 2,
        }}
        >
          {Array.from({ length: 8 }).map((_, idx) => (
            <Skeleton key={idx} variant="rectangular" height={150} animation="wave" sx={{ borderRadius: 1 }} />
          ))}
        </Box>
      );
    }
    return (
      <Box sx={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
        gap: 2,
        mt: 2,
      }}
      >
        {customDashboards.map((customDashboard: CustomDashboard) => (
          <CustomDashboardCard
            key={customDashboard.custom_dashboard_id}
            customDashboard={customDashboard}
            onUpdate={handleUpdate}
            onDelete={handleDelete}
          />
        ))}
      </Box>
    );
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{ label: t('Dashboards') }, {
          label: t('Custom dashboards'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setCustomDashboards}
        entityPrefix="custom_dashboard"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            {viewSwitcher}
            <ToggleButtonGroup value="fake" exclusive>
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.DASHBOARDS}>
                <ImportUploaderJsonApiComponent
                  title={t('Import a custom dashboard')}
                  uploadFn={importCustomDashboard}
                />
              </Can>
            </ToggleButtonGroup>
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.DASHBOARDS}>
              <CustomDashboardCreation />
            </Can>
          </Box>
        )}
      />
      {viewMode === 'cards' && renderCards()}
      {viewMode === 'list' && (
        <List>
          <ListItem
            classes={{ root: classes.itemHead }}
            divider={false}
            sx={{ pt: 0 }}
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
              ? <PaginatedListLoader Icon={AnalyticsOutlined} headers={headers} headerStyles={inlineStyles} />
              : customDashboards.map((customDashboard: CustomDashboard) => {
                  return (
                    (
                      <ListItem
                        key={customDashboard.custom_dashboard_id}
                        divider
                        secondaryAction={(
                          <CustomDashboardPopover
                            customDashboard={customDashboard}
                            onUpdate={handleUpdate}
                            onDelete={handleDelete}
                            inList
                          />
                        )}
                        disablePadding
                      >
                        <ListItemButton
                          component={Link}
                          to={`/admin/workspaces/custom_dashboards/${customDashboard.custom_dashboard_id}`}
                          classes={{ root: classes.item }}
                        >
                          <ListItemIcon>
                            <AnalyticsOutlined color="primary" />
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
                                    {header.value?.(customDashboard)}
                                  </div>
                                ))}
                              </div>
                            )}
                          />
                        </ListItemButton>
                      </ListItem>
                    )
                  );
                })
          }
        </List>
      )}
    </>
  );
};

export default CustomDashboards;

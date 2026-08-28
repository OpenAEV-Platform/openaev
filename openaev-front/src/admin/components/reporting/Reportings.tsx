import { ButtonGroup, ButtonGroupItem } from '@filigran/design-system';
import { FileDownloadOutlined, GridViewOutlined, ViewListOutlined } from '@mui/icons-material';
import {
  Box,
  Chip,
  IconButton,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Skeleton,
  Tooltip,
} from '@mui/material';
import { FileChartOutline } from 'mdi-material-ui';
import { type CSSProperties, useMemo, useState } from 'react';
import { Link } from 'react-router';
import { makeStyles } from 'tss-react/mui';

import { downloadReportingGenerationUrl, searchReportings } from '../../../actions/reporting/reporting-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import { initSorting } from '../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import SortHeadersComponentV2 from '../../../components/common/queryable/sort/SortHeadersComponentV2';
import useBodyItemsStyles from '../../../components/common/queryable/style/style';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import type { Header } from '../../../components/common/SortHeadersList';
import { useFormatter } from '../../../components/i18n';
import PaginatedListLoader from '../../../components/PaginatedListLoader';
import type { Reporting, SearchPaginationInput } from '../../../utils/api-types';
import ReportingCard from './ReportingCard';
import {
  latestGeneration,
  REPORTING_CONTEXT_ICONS,
  REPORTING_CONTEXT_LABELS,
} from './ReportingContexts';
import ReportingCreation from './ReportingCreation';
import { ReportingFormatFragment, ReportingStatusChip } from './ReportingFragments';
import ReportingPopover from './ReportingPopover';

// Cards by default, with a persisted switch to the compact list (same pattern
// as the custom dashboards screen).
type ViewMode = 'cards' | 'list';
const VIEW_MODE_STORAGE_KEY = 'reportings:view-mode';
const readViewMode = (): ViewMode => (typeof window !== 'undefined' && window.localStorage.getItem(VIEW_MODE_STORAGE_KEY) === 'list' ? 'list' : 'cards');

const useStyles = makeStyles()(() => ({
  itemHead: { textTransform: 'uppercase' },
  item: { height: 50 },
}));

const inlineStyles: Record<string, CSSProperties> = {
  reporting_name: { width: '30%' },
  reporting_context_type: { width: '15%' },
  reporting_default_format: { width: '10%' },
  reporting_last_generation: { width: '15%' },
  reporting_updated_at: { width: '15%' },
};

const Reportings = () => {
  // Standard hooks
  const { t, fldt } = useFormatter();
  const { classes } = useStyles();
  const bodyItemsStyles = useBodyItemsStyles();

  // Pagination
  const [loading, setLoading] = useState<boolean>(true);
  const [reportings, setReportings] = useState<Reporting[]>([]);
  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage('reportings', buildSearchPagination({ sorts: initSorting('reporting_name') }));
  const availableFilterNames = ['reporting_name', 'reporting_context_type'];

  const search = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchReportings(input).finally(() => setLoading(false));
  };

  // Mutations from the card / row popovers patch the loaded page in place
  // (no full refetch needed for a rename or a removal).
  const onUpdate = (result: Reporting) => {
    setReportings(prev => prev.map(reporting => (reporting.reporting_id === result.reporting_id ? result : reporting)));
  };
  const onDelete = (reportingId: string) => {
    setReportings(prev => prev.filter(reporting => reporting.reporting_id !== reportingId));
  };

  // Headers
  const headers: Header[] = useMemo(() => [
    {
      field: 'reporting_name',
      label: 'Name',
      isSortable: true,
      value: (reporting: Reporting) => reporting.reporting_name,
    },
    {
      field: 'reporting_context_type',
      // Not the legacy 'Subject' key: it translates to "Email subject".
      label: 'Subject type',
      isSortable: true,
      value: (reporting: Reporting) => (
        <Chip
          label={t(REPORTING_CONTEXT_LABELS[reporting.reporting_context_type])}
          size="small"
          variant="outlined"
        />
      ),
    },
    {
      field: 'reporting_default_format',
      label: 'Format',
      isSortable: true,
      value: (reporting: Reporting) => (
        <ReportingFormatFragment format={reporting.reporting_default_format} />
      ),
    },
    {
      field: 'reporting_last_generation',
      label: 'Last generation',
      isSortable: false,
      value: (reporting: Reporting) => {
        const generation = latestGeneration(reporting);
        if (!generation) return '-';
        return <ReportingStatusChip status={generation.reporting_generation_status} />;
      },
    },
    {
      field: 'reporting_updated_at',
      label: 'Updated at',
      isSortable: true,
      value: (reporting: Reporting) => fldt(reporting.reporting_updated_at),
    },
  ], [t, fldt]);

  const [viewMode, setViewMode] = useState<ViewMode>(readViewMode);
  const handleViewModeChange = (next: string) => {
    const value = next as ViewMode;
    setViewMode(value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
    }
  };

  const viewSwitcher = (
    <ButtonGroup
      value={viewMode}
      size="sm"
      onValueChange={handleViewModeChange}
      aria-label={t('View mode')}
    >
      <Tooltip title={t('Cards view')}>
        <ButtonGroupItem value="cards" aria-label={t('Cards view')} icon={<GridViewOutlined fontSize="small" />} />
      </Tooltip>
      <Tooltip title={t('List view')}>
        <ButtonGroupItem value="list" aria-label={t('List view')} icon={<ViewListOutlined fontSize="small" />} />
      </Tooltip>
    </ButtonGroup>
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
            <Skeleton key={idx} variant="rectangular" height={170} animation="wave" sx={{ borderRadius: 1 }} />
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
        {reportings.map((reporting: Reporting) => (
          <ReportingCard
            key={reporting.reporting_id}
            reporting={reporting}
            onUpdate={onUpdate}
            onDelete={onDelete}
          />
        ))}
      </Box>
    );
  };

  return (
    <>
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Reporting'),
          current: true,
        }]}
      />
      <PaginationComponentV2
        fetch={search}
        searchPaginationInput={searchPaginationInput}
        setContent={setReportings}
        entityPrefix="reporting"
        availableFilterNames={availableFilterNames}
        queryableHelpers={queryableHelpers}
        topBarButtons={(
          <Box display="flex" gap={1} alignItems="center">
            {viewSwitcher}
            <ReportingCreation />
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
              ? <PaginatedListLoader Icon={FileChartOutline} headers={headers} headerStyles={inlineStyles} />
              : reportings.map((reporting: Reporting) => {
                  const ContextIcon = REPORTING_CONTEXT_ICONS[reporting.reporting_context_type];
                  const generation = latestGeneration(reporting);
                  const downloadable = generation?.reporting_generation_status === 'SUCCESS' && generation.reporting_generation_document;
                  return (
                    <ListItem
                      key={reporting.reporting_id}
                      divider
                      secondaryAction={(
                        <Box display="flex" alignItems="center">
                          {downloadable && (
                            <Tooltip title={t('Download latest generation')}>
                              <IconButton
                                size="small"
                                color="primary"
                                component="a"
                                href={downloadReportingGenerationUrl(generation.reporting_generation_id)}
                              >
                                <FileDownloadOutlined fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          )}
                          <ReportingPopover
                            reporting={reporting}
                            onUpdate={onUpdate}
                            onDelete={onDelete}
                            inList
                          />
                        </Box>
                      )}
                      disablePadding
                    >
                      <ListItemButton
                        component={Link}
                        to={`/admin/reporting/${reporting.reporting_id}`}
                        classes={{ root: classes.item }}
                      >
                        <ListItemIcon>
                          <ContextIcon color="primary" />
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
                                  {header.value?.(reporting)}
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
      )}
    </>
  );
};

export default Reportings;

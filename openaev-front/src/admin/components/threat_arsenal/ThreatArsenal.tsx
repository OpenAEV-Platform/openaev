import {
  GridViewOutlined,
  ViewListOutlined,
} from '@mui/icons-material';
import {
  Box,
  Checkbox,
  FormControlLabel,
  Skeleton,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { useState } from 'react';

import type { DomainHelper } from '../../../actions/domains/domain-helper';
import {
  exportThreatArsenalCsvMapper,
  searchThreatArsenalActions,
} from '../../../actions/threat_arsenals/threatArsenal-actions';
import Breadcrumbs from '../../../components/Breadcrumbs';
import ExportButton from '../../../components/common/ExportButton';
import PaginationComponentV2 from '../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../components/common/queryable/QueryableUtils';
import { useQueryableWithLocalStorage } from '../../../components/common/queryable/useQueryableWithLocalStorage';
import { useFormatter } from '../../../components/i18n';
import { useHelper } from '../../../store';
import {
  type SearchPaginationInput,
  type ThreatArsenalAction,
} from '../../../utils/api-types';
import useEntityToggle from '../../../utils/hooks/useEntityToggle';
import { Can } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import useDomainIconFilter from '../common/domains/useDomainIconFilter';
import ThreatArsenalRunTestDrawer from './bulk/ThreatArsenalRunTestDrawer';
import CreateThreatArsenalAction from './CreateThreatArsenalAction';
import ImportUploaderThreatArsenal from './ImportUploaderThreatArsenal';
import ThreatArsenalCard from './ThreatArsenalCard';
import ThreatArsenalEmptyState from './ThreatArsenalEmptyState';
import ThreatArsenalHero from './ThreatArsenalHero';
import ThreatArsenalInformationDrawer from './ThreatArsenalInformationDrawer';
import ThreatArsenalListRow from './ThreatArsenalListRow';
import ThreatArsenalQuickFilters from './ThreatArsenalQuickFilters';
import ThreatArsenalSelectionBar from './ThreatArsenalSelectionBar';

type ViewMode = 'grid' | 'list';

const VIEW_MODE_STORAGE_KEY = 'threat-arsenal:view-mode-v2';

const readViewMode = (): ViewMode => {
  if (typeof window === 'undefined') return 'grid';
  const stored = window.localStorage.getItem(VIEW_MODE_STORAGE_KEY);
  return stored === 'list' ? 'list' : 'grid';
};

const ThreatArsenal = () => {
  const { t } = useFormatter();
  const theme = useTheme();

  const [selectedThreatArsenalAction, setSelectedThreatArsenalAction] = useState<ThreatArsenalAction | null>(null);
  const [isRunTestDrawerOpened, setRunTestDrawerOpened] = useState<boolean>(false);
  const [threatArsenalActions, setThreatArsenalActions] = useState<ThreatArsenalAction[]>([]);
  const [viewMode, setViewMode] = useState<ViewMode>(readViewMode);

  const handleViewModeChange = (_: unknown, value: ViewMode | null) => {
    if (!value) return;
    setViewMode(value);
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(VIEW_MODE_STORAGE_KEY, value);
    }
  };

  const { queryableHelpers, searchPaginationInput } = useQueryableWithLocalStorage(
    'threat-arsenal',
    buildSearchPagination({}),
  );

  const [loading, setLoading] = useState<boolean>(false);
  const fetchActions = (input: SearchPaginationInput) => {
    setLoading(true);
    return searchThreatArsenalActions({ ...input }).finally(() => setLoading(false));
  };

  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  const {
    selectedElements,
    deSelectedElements,
    selectAll,
    handleClearSelectedElements,
    handleToggleSelectAll,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<ThreatArsenalAction>(
    'injector_contract',
    threatArsenalActions,
    totalElements,
  );

  const { domainOptions } = useHelper(
    (helper: DomainHelper) => ({ domainOptions: helper.getDomains() }),
  );
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
    apiPrefix: 'threat_arsenals',
    domainFilterKey: 'action_domains',
  });

  // Per-status counts are intentionally NOT computed from `threatArsenalActions`
  // here: that array only holds the currently loaded page, while `totalElements`
  // covers the full filtered dataset, so page-bound counts would be misleading
  // (e.g. "Verified: 23" on page 1, "Verified: 8" on page 2). Users can drill
  // by status via the Status quick filter underneath the hero. If a global
  // aggregation endpoint is ever added, status chips can be wired here.

  const availableFilterNames = [
    'action_injectors',
    'action_platforms',
    'action_domains',
    'action_tags',
    'action_payload_status',
    'action_updated_at',
  ];

  const exportProps = {
    exportType: 'THREAT_ARSENAL_ACTIONS',
    exportKeys: [],
    exportData: threatArsenalActions,
    searchPaginationInput,
  };

  const isSelectedAction = (action: ThreatArsenalAction) =>
    (selectAll && !(action.injector_contract_id in (deSelectedElements || {})))
    || action.injector_contract_id in (selectedElements || {});

  const computeRowFlags = (action: ThreatArsenalAction) => {
    const isDummy = (action.action_injector_type ?? '').endsWith('_dummy');
    return {
      disableUpdate:
        (action.action_payload && action.action_payload?.payload_collector_type !== null)
        || isDummy,
      disableDuplicate: action.action_payload == null || isDummy,
      disableJsonExport: action.action_payload == null || isDummy,
      disableDelete:
        action.action_payload == null
        || (action.action_payload.payload_collector_type !== null && action.action_payload?.payload_status !== 'DEPRECATED')
        || isDummy,
    };
  };

  const handleResetFilters = () => {
    queryableHelpers.filterHelpers.handleClearAllFilters();
    queryableHelpers.textSearchHelpers.handleTextSearch('');
  };

  const hasActiveFilters = !!(
    (searchPaginationInput.filterGroup?.filters?.length ?? 0) > 0
    || (searchPaginationInput.textSearch && searchPaginationInput.textSearch.length > 0)
  );

  const renderGridView = () => {
    if (loading) {
      return (
        <Box sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 2,
        }}
        >
          {Array.from({ length: 12 }).map((_, idx) => (
            <Box
              key={idx}
              sx={{
                height: 200,
                borderRadius: 2,
                overflow: 'hidden',
                border: `1px solid ${theme.palette.divider}`,
              }}
            >
              <Skeleton variant="rectangular" height={64} animation="wave" />
              <Box sx={{ padding: 2 }}>
                <Skeleton variant="text" width="80%" height={28} animation="wave" />
                <Skeleton variant="text" width="60%" height={20} animation="wave" />
                <Box sx={{
                  display: 'flex',
                  gap: 1,
                  marginTop: 2,
                }}
                >
                  <Skeleton variant="circular" width={20} height={20} animation="wave" />
                  <Skeleton variant="circular" width={20} height={20} animation="wave" />
                  <Skeleton variant="rectangular" width={60} height={20} animation="wave" sx={{ borderRadius: 1 }} />
                </Box>
              </Box>
            </Box>
          ))}
        </Box>
      );
    }

    if (threatArsenalActions.length === 0) {
      return <ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />;
    }

    return (
      <Box
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
          gap: 2,
        }}
      >
        {threatArsenalActions.map((action) => {
          const flags = computeRowFlags(action);
          return (
            <ThreatArsenalCard
              key={action.injector_contract_id}
              action={action}
              selected={selectedThreatArsenalAction?.injector_contract_id === action.injector_contract_id}
              checked={isSelectedAction(action)}
              anySelected={numberOfSelectedElements > 0}
              onSelect={() => setSelectedThreatArsenalAction(action)}
              onToggleEntity={event => onToggleEntity(action, event)}
              onUpdate={(result: ThreatArsenalAction) =>
                setThreatArsenalActions(prev => prev.map(a => (a.injector_contract_id === action.injector_contract_id ? result : a)))}
              onDuplicate={(result: ThreatArsenalAction) => setThreatArsenalActions(prev => [result, ...prev])}
              onDelete={() => setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== action.injector_contract_id))}
              disableUpdate={flags.disableUpdate}
              disableDuplicate={flags.disableDuplicate}
              disableJsonExport={flags.disableJsonExport}
              disableDelete={flags.disableDelete}
            />
          );
        })}
      </Box>
    );
  };

  const renderListView = () => {
    return (
      <Box
        sx={{
          border: `1px solid ${theme.palette.divider}`,
          borderRadius: 1,
          overflow: 'hidden',
          backgroundColor: alpha(theme.palette.background.paper, 0.5),
        }}
      >
        <Box
          role="row"
          sx={{
            display: 'grid',
            gridTemplateColumns: '40px 44px minmax(0, 2fr) minmax(0, 1.2fr) 120px 130px 120px 160px 48px',
            alignItems: 'center',
            gap: 1.5,
            paddingBlock: 1,
            paddingInline: 1.5,
            borderBottom: `1px solid ${theme.palette.divider}`,
            backgroundColor: alpha(theme.palette.background.paper, 0.7),
          }}
        >
          <Box />
          <Box />
          {['Name', 'Domains', 'Platforms', 'Tags', 'Status', 'Updated'].map(label => (
            <Typography
              key={label}
              variant="overline"
              sx={{
                color: 'text.secondary',
                fontSize: 10.5,
                letterSpacing: '0.08em',
              }}
            >
              {t(label)}
            </Typography>
          ))}
          <Box />
        </Box>

        {(() => {
          if (loading) {
            return (
              <Box>
                {Array.from({ length: 10 }).map((_, idx) => (
                  <Box
                    key={idx}
                    sx={{
                      paddingBlock: 1.5,
                      paddingInline: 1.5,
                      borderBottom: `1px solid ${theme.palette.divider}`,
                    }}
                  >
                    <Skeleton variant="text" width="60%" height={20} animation="wave" />
                  </Box>
                ))}
              </Box>
            );
          }
          if (threatArsenalActions.length === 0) {
            return (
              <Box sx={{ padding: 4 }}>
                <ThreatArsenalEmptyState hasFilters={hasActiveFilters} onResetFilters={handleResetFilters} />
              </Box>
            );
          }
          return (
            <Box>
              {threatArsenalActions.map((action) => {
                const flags = computeRowFlags(action);
                return (
                  <Box
                    key={action.injector_contract_id}
                    sx={{ borderBottom: `1px solid ${theme.palette.divider}` }}
                  >
                    <ThreatArsenalListRow
                      action={action}
                      selected={selectedThreatArsenalAction?.injector_contract_id === action.injector_contract_id}
                      checked={isSelectedAction(action)}
                      onSelect={() => setSelectedThreatArsenalAction(action)}
                      onToggleEntity={event => onToggleEntity(action, event)}
                      onUpdate={(result: ThreatArsenalAction) =>
                        setThreatArsenalActions(prev => prev.map(a => (a.injector_contract_id === action.injector_contract_id ? result : a)))}
                      onDuplicate={(result: ThreatArsenalAction) => setThreatArsenalActions(prev => [result, ...prev])}
                      onDelete={() => setThreatArsenalActions(prev => prev.filter(a => a.injector_contract_id !== action.injector_contract_id))}
                      disableUpdate={flags.disableUpdate}
                      disableDuplicate={flags.disableDuplicate}
                      disableJsonExport={flags.disableJsonExport}
                      disableDelete={flags.disableDelete}
                    />
                  </Box>
                );
              })}
            </Box>
          );
        })()}
      </Box>
    );
  };

  const headerRightSlot = (
    <>
      <ToggleButtonGroup
        value={viewMode}
        exclusive
        size="small"
        onChange={handleViewModeChange}
        aria-label={t('View mode')}
        sx={{ '& .MuiToggleButton-root.Mui-selected .MuiSvgIcon-root': { color: 'primary.main' } }}
      >
        <ToggleButton value="grid" aria-label={t('Grid view')}>
          <Tooltip title={t('Grid view')}>
            <GridViewOutlined fontSize="small" />
          </Tooltip>
        </ToggleButton>
        <ToggleButton value="list" aria-label={t('List view')}>
          <Tooltip title={t('List view')}>
            <ViewListOutlined fontSize="small" />
          </Tooltip>
        </ToggleButton>
      </ToggleButtonGroup>

      <ToggleButtonGroup value="fake" exclusive>
        <ExportButton
          totalElements={totalElements}
          exportProps={exportProps}
          exportCsvMapperFunction={exportThreatArsenalCsvMapper}
        />
        <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
          <ImportUploaderThreatArsenal
            onImport={results => setThreatArsenalActions(prev => [...results, ...prev])}
          />
        </Can>
      </ToggleButtonGroup>
    </>
  );

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      paddingBottom: numberOfSelectedElements > 0 ? 12 : 4,
    }}
    >
      <Breadcrumbs
        variant="list"
        elements={[{
          label: t('Threat Arsenal'),
          current: true,
        }]}
      />

      <Box sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <ThreatArsenalHero
          totalElements={totalElements}
          domainElements={iconBarOrderedDomains}
          stats={[]}
          searchValue={searchPaginationInput.textSearch ?? ''}
          onSearchChange={value => queryableHelpers.textSearchHelpers.handleTextSearch(value)}
          rightSlot={headerRightSlot}
          bottomSlot={(
            <ThreatArsenalQuickFilters
              searchPaginationInput={searchPaginationInput}
              filterHelpers={queryableHelpers.filterHelpers}
            />
          )}
        />

        <Box sx={{ '& > div:first-of-type': { marginTop: 0 } }}>
          <PaginationComponentV2
            fetch={fetchActions}
            searchPaginationInput={searchPaginationInput}
            setContent={setThreatArsenalActions}
            entityPrefix="threat_arsenal"
            availableFilterNames={availableFilterNames}
            queryableHelpers={queryableHelpers}
            searchEnable={false}
            leftSlot={(
              <FormControlLabel
                label={(
                  <Typography
                    variant="body2"
                    sx={{
                      color: 'text.secondary',
                      fontWeight: 500,
                      fontSize: 12.5,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {(() => {
                      if (numberOfSelectedElements === 0) return t('Select all');
                      if (numberOfSelectedElements === 1) return t('1 action selected');
                      return t('{count} actions selected', { count: numberOfSelectedElements });
                    })()}
                  </Typography>
                )}
                control={(
                  <Checkbox
                    size="small"
                    checked={selectAll}
                    indeterminate={
                      (!selectAll && numberOfSelectedElements > 0)
                      || (selectAll && Object.keys(deSelectedElements ?? {}).length > 0)
                    }
                    onChange={handleToggleSelectAll}
                    disabled={threatArsenalActions.length === 0}
                  />
                )}
                sx={{
                  'marginLeft': -0.5,
                  'marginRight': 0,
                  '& .MuiFormControlLabel-label': { marginLeft: 0.5 },
                }}
              />
            )}
          />
        </Box>

        {viewMode === 'grid' ? renderGridView() : renderListView()}
      </Box>

      <Can I={ACTIONS.MANAGE} a={SUBJECTS.THREAT_ARSENALS}>
        <CreateThreatArsenalAction
          onCreate={(result: ThreatArsenalAction) => {
            setThreatArsenalActions(prev => [result, ...prev]);
          }}
        />
      </Can>

      {(selectedThreatArsenalAction !== null) && (
        <ThreatArsenalInformationDrawer
          open={true}
          onClose={() => setSelectedThreatArsenalAction(null)}
          threatArsenalAction={selectedThreatArsenalAction}
        />
      )}

      {isRunTestDrawerOpened && (
        <ThreatArsenalRunTestDrawer
          isExclusionMode={selectAll}
          isOnlyOneItemSelected={
            selectAll
              ? Object.keys(deSelectedElements).length === totalElements - 1
              : numberOfSelectedElements === 1
          }
          selectedElements={selectedElements}
          deSelectedElements={deSelectedElements}
          searchPaginationInput={searchPaginationInput}
          open={isRunTestDrawerOpened}
          onClose={() => setRunTestDrawerOpened(false)}
        />
      )}

      <ThreatArsenalSelectionBar
        count={numberOfSelectedElements}
        totalElements={totalElements}
        onClear={handleClearSelectedElements}
        onRunTest={() => setRunTestDrawerOpened(true)}
      />
    </Box>
  );
};

export default ThreatArsenal;

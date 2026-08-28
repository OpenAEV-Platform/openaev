import { ArrowBackOutlined, GridViewOutlined, ReorderOutlined } from '@mui/icons-material';
import { Box, IconButton, Skeleton, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { type FunctionComponent, type SyntheticEvent, useEffect, useMemo, useRef, useState } from 'react';

import { type AttackPatternHelper } from '../../../../../actions/attack_patterns/attackpattern-helper';
import { fetchAttackPatterns } from '../../../../../actions/AttackPattern';
import { fetchDomains } from '../../../../../actions/domains/domain-actions';
import { type DomainHelper } from '../../../../../actions/domains/domain-helper';
import { searchInjectorContracts } from '../../../../../actions/InjectorContracts';
import { type KillChainPhaseHelper } from '../../../../../actions/kill_chain_phases/killchainphase-helper';
import { fetchKillChainPhases } from '../../../../../actions/KillChainPhase';
import { generateFilterId } from '../../../../../components/common/queryable/filter/FilterUtils';
import { initSorting } from '../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { useQueryableWithLocalStorage } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import Empty from '../../../../../components/Empty';
import { useFormatter } from '../../../../../components/i18n';
import { useHelper } from '../../../../../store';
import {
  type AttackPattern,
  type Domain,
  type FilterGroup,
  type InjectorContractFullOutput,
  type InjectorContractSearchPaginationInput,
  type KillChainPhase,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../../../utils/hooks/useEntityToggle';
import computeAttackPatterns from '../../../../../utils/injector_contract/InjectorContractUtils';
import useDomainIconFilter from '../../domains/useDomainIconFilter';
import { MITRE_FILTER_KEY } from '../../filters/MitreFilter';
import InjectContractCard from './InjectContractCard';
import InjectContractListRow, { LIST_GRID_COLUMNS } from './InjectContractListRow';
import InjectContractSidebar from './InjectContractSidebar';
import InjectSelectionBar from './InjectSelectionBar';

const VIEW_MODE_STORAGE_KEY = 'inject-contract-picker:view-mode';

const availableFilterNames = [
  'injector_contract_attack_patterns',
  'injector_contract_injectors',
  'injector_contract_kill_chain_phases',
  'injector_contract_labels',
  'injector_contract_platforms',
  'injector_contract_players',
  'injector_contract_arch',
  'injector_contract_domains',
  'injector_contract_payload_status',
  'injector_contract_payload_author',
];

interface Props {
  title: string;
  /** Atomic testing creation: single-select, atomic-capable contracts only. */
  isAtomic?: boolean;
  /**
   * Deep-link scoping (e.g. the "Create an atomic testing" CTA of a TTP-scoped
   * dashboard drill-down): replaces the picker's attack pattern filter with
   * these ids on mount so only contracts covering the TTPs are listed.
   */
  initialAttackPatternIds?: string[];
  onSelectContract: (contract: InjectorContractFullOutput) => void;
  /** Basket bulk-add (absent in atomic mode). */
  onQuickAdd?: (contracts: InjectorContractFullOutput[]) => void;
  /** Navigates back to the caller's list (injects tab, atomic testings...). */
  onBack?: () => void;
}

// The inject-contract picker: a full page mirroring the Threat Arsenal library
// layout - compact back+title header, sticky facet sidebar (domains / platforms
// / kill chain), searchable card grid or list, and a floating selection basket
// for bulk add.
const InjectContractPicker: FunctionComponent<Props> = ({
  title,
  isAtomic = false,
  initialAttackPatternIds,
  onSelectContract,
  onQuickAdd,
  onBack,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  useDataLoader(() => {
    dispatch(fetchAttackPatterns());
    dispatch(fetchKillChainPhases());
    dispatch(fetchDomains());
  });

  const {
    attackPatterns,
    attackPatternsMap,
    killChainPhasesMap,
    domainOptions,
  } = useHelper((helper: AttackPatternHelper & KillChainPhaseHelper & DomainHelper) => ({
    attackPatterns: helper.getAttackPatterns(),
    attackPatternsMap: helper.getAttackPatternsMap(),
    killChainPhasesMap: helper.getKillChainPhasesMap(),
    domainOptions: helper.getDomains(),
  })) as {
    attackPatterns: AttackPattern[];
    attackPatternsMap: Record<string, AttackPattern>;
    killChainPhasesMap: Record<string, KillChainPhase>;
    domainOptions: Domain[];
  };

  // View mode (grid by default, persisted like the Threat Arsenal)
  const [viewMode, setViewMode] = useState<'grid' | 'list'>(() => {
    const stored = localStorage.getItem(VIEW_MODE_STORAGE_KEY);
    return stored === 'list' ? 'list' : 'grid';
  });
  const handleViewMode = (_: SyntheticEvent, mode: 'grid' | 'list' | null) => {
    if (mode) {
      setViewMode(mode);
      localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode);
    }
  };

  // Contracts search (atomic creation only surfaces atomic-capable contracts).
  // `loading` starts true so the first paint shows skeletons instead of a
  // "No data to display" flash while the initial search is in flight. Searches
  // can overlap (fast typing, filter changes): the sequence guard ensures only
  // the latest request clears the loading state, so a slow stale response
  // never hides the skeletons while a newer search is still in flight.
  const [contracts, setContracts] = useState<InjectorContractFullOutput[]>([]);
  const [loading, setLoading] = useState(true);
  const fetchSeqRef = useRef(0);
  const fetchContracts = (input: InjectorContractSearchPaginationInput) => {
    const seq = ++fetchSeqRef.current;
    setLoading(true);
    return searchInjectorContracts(input).finally(() => {
      if (seq === fetchSeqRef.current) {
        setLoading(false);
      }
    });
  };
  const initSearchPaginationInput = () => {
    const filterGroup: FilterGroup = {
      mode: 'and',
      filters: isAtomic
        ? [{
            id: generateFilterId(),
            key: 'injector_contract_atomic_testing',
            operator: 'eq',
            values: ['true'],
          }]
        : [],
    };
    return {
      sorts: initSorting('injector_contract_labels'),
      filterGroup,
      size: 50,
      page: 0,
    };
  };
  const {
    queryableHelpers,
    searchPaginationInput,
  } = useQueryableWithLocalStorage(isAtomic ? 'injector-contracts-picker-atomic' : 'injector-contracts-picker', initSearchPaginationInput());
  const totalElements = queryableHelpers.paginationHelpers.getTotalElements();

  // Deep-link TTP scoping: replace (not merge) any attack pattern filter left
  // over from a previous session so the picker opens exactly on the requested
  // techniques; the filter stays a regular editable chip afterwards.
  useEffect(() => {
    if (initialAttackPatternIds && initialAttackPatternIds.length > 0) {
      queryableHelpers.filterHelpers.handleRemoveFilterByKey(MITRE_FILTER_KEY);
      queryableHelpers.filterHelpers.handleAddMultipleValueFilter(MITRE_FILTER_KEY, initialAttackPatternIds);
    }
  }, []);

  // Domain facet (live counts + toggling of the injector_contract_domains filter)
  const { iconBarOrderedDomains } = useDomainIconFilter({
    domainOptions,
    searchPaginationInput,
    queryableHelpers,
  });

  // Selection basket
  const {
    selectedElements,
    handleClearSelectedElements,
    onToggleEntity,
    numberOfSelectedElements,
  } = useEntityToggle<InjectorContractFullOutput>('injector_contract', contracts, totalElements);

  const contractMeta = useMemo(() => contracts.map((contract) => {
    const contractAttackPatterns = computeAttackPatterns(
      contract.injector_contract_attack_patterns,
      attackPatternsMap,
    );
    const killChainPhaseId = contractAttackPatterns
      .flatMap((attackPattern: AttackPattern) => attackPattern.attack_pattern_kill_chain_phases ?? [])
      .at(0);
    const killChainPhaseName = killChainPhaseId && killChainPhasesMap[killChainPhaseId]
      ? killChainPhasesMap[killChainPhaseId].phase_name
      : undefined;
    return {
      contract,
      contractAttackPatterns,
      killChainPhaseName,
    };
  }), [contracts, attackPatternsMap, killChainPhasesMap]);

  const isChecked = (contract: InjectorContractFullOutput) =>
    contract.injector_contract_id in (selectedElements || {});

  const onQuickAddSelection = () => {
    onQuickAdd?.(Object.values(selectedElements));
    handleClearSelectedElements();
  };

  return (
    <Box sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 2,
      paddingBottom: numberOfSelectedElements > 0 ? 12 : 4,
    }}
    >
      {/* Compact header: back to the caller's list + page title (no hero band) */}
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        {onBack && (
          <Tooltip title={t('Back')}>
            <IconButton onClick={onBack} aria-label={t('Back')} size="small">
              <ArrowBackOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Typography variant="h1" sx={{ margin: 0 }}>
          {title}
        </Typography>
      </Box>

      {/* Two-column body: sticky facet sidebar + main content */}
      <Box sx={{
        display: 'flex',
        gap: 3,
        alignItems: 'flex-start',
      }}
      >
        <InjectContractSidebar
          domainElements={iconBarOrderedDomains}
          searchPaginationInput={searchPaginationInput}
          filterHelpers={queryableHelpers.filterHelpers}
        />
        <Box sx={{
          'flex': 1,
          'minWidth': 0,
          'display': 'flex',
          'flexDirection': 'column',
          'gap': 2,
          '& > div:first-of-type': { marginTop: 0 },
        }}
        >
          <PaginationComponentV2
            fetch={fetchContracts}
            searchPaginationInput={searchPaginationInput}
            setContent={setContracts}
            entityPrefix="injector_contract"
            availableFilterNames={availableFilterNames}
            queryableHelpers={queryableHelpers}
            attackPatterns={attackPatterns}
            topBarButtons={(
              <ToggleButtonGroup
                size="small"
                exclusive
                value={viewMode}
                onChange={handleViewMode}
                sx={{ marginLeft: 1.5 }}
              >
                <Tooltip title={t('Grid view')}>
                  <ToggleButton value="grid" aria-label={t('Grid view')}>
                    <GridViewOutlined fontSize="small" />
                  </ToggleButton>
                </Tooltip>
                <Tooltip title={t('List view')}>
                  <ToggleButton value="list" aria-label={t('List view')}>
                    <ReorderOutlined fontSize="small" />
                  </ToggleButton>
                </Tooltip>
              </ToggleButtonGroup>
            )}
          />

          {!loading && contracts.length === 0 && <Empty message={t('No data to display')} />}

          {/* Skeletons mirror the Threat Arsenal library loading state so the
              picker never flashes an empty state while the search is in flight. */}
          {viewMode === 'grid' && loading && (
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
                    borderRadius: 1,
                    overflow: 'hidden',
                    border: '1px solid',
                    borderColor: 'divider',
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
          )}

          {viewMode === 'grid' && !loading && contracts.length > 0 && (
            <Box sx={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
              gap: 2,
            }}
            >
              {contractMeta.map(({ contract, contractAttackPatterns, killChainPhaseName }) => (
                <InjectContractCard
                  key={contract.injector_contract_id}
                  contract={contract}
                  attackPatterns={contractAttackPatterns}
                  killChainPhaseName={killChainPhaseName}
                  checked={isChecked(contract)}
                  anySelected={numberOfSelectedElements > 0}
                  selectable={!isAtomic}
                  onSelect={() => onSelectContract(contract)}
                  onToggle={event => onToggleEntity(contract, event)}
                />
              ))}
            </Box>
          )}

          {viewMode === 'list' && (loading || contracts.length > 0) && (
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 0.25,
            }}
            >
              <Box sx={{
                display: 'grid',
                gridTemplateColumns: LIST_GRID_COLUMNS(!isAtomic),
                gap: 1.5,
                paddingInline: 1.5,
                paddingBlock: 0.5,
              }}
              >
                {!isAtomic && <span aria-hidden />}
                <span aria-hidden />
                <Typography variant="h4" sx={{ margin: 0 }}>{t('Name')}</Typography>
                <Typography variant="h4" sx={{ margin: 0 }}>{t('Domains')}</Typography>
                <Typography variant="h4" sx={{ margin: 0 }}>{t('Platform')}</Typography>
                <Typography variant="h4" sx={{ margin: 0 }}>{t('Attack patterns')}</Typography>
                <Typography variant="h4" sx={{ margin: 0 }}>{t('Kill chain phase')}</Typography>
                <span aria-hidden />
              </Box>
              {/* Skeleton rows share the exact grid template of the real rows
                  so the layout does not shift when the data lands. */}
              {loading && Array.from({ length: 10 }).map((_, idx) => (
                <Box
                  key={idx}
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: LIST_GRID_COLUMNS(!isAtomic),
                    alignItems: 'center',
                    gap: 1.5,
                    paddingBlock: 0.75,
                    paddingInline: 1.5,
                    borderLeft: '3px solid transparent',
                  }}
                >
                  {!isAtomic && <Skeleton variant="rounded" width={18} height={18} animation="wave" />}
                  <Skeleton variant="rounded" width={36} height={36} animation="wave" />
                  <Skeleton variant="text" width="70%" height={20} animation="wave" />
                  <Skeleton variant="text" width="60%" height={20} animation="wave" />
                  <Box sx={{
                    display: 'flex',
                    gap: 0.75,
                  }}
                  >
                    <Skeleton variant="circular" width={18} height={18} animation="wave" />
                    <Skeleton variant="circular" width={18} height={18} animation="wave" />
                  </Box>
                  <Skeleton variant="rounded" width={60} height={20} animation="wave" />
                  <Skeleton variant="text" width="60%" height={20} animation="wave" />
                  <span aria-hidden />
                </Box>
              ))}
              {!loading && contractMeta.map(({ contract, contractAttackPatterns, killChainPhaseName }) => (
                <InjectContractListRow
                  key={contract.injector_contract_id}
                  contract={contract}
                  attackPatterns={contractAttackPatterns}
                  killChainPhaseName={killChainPhaseName}
                  checked={isChecked(contract)}
                  selectable={!isAtomic}
                  onSelect={() => onSelectContract(contract)}
                  onToggle={event => onToggleEntity(contract, event)}
                />
              ))}
            </Box>
          )}
        </Box>
      </Box>

      {!isAtomic && onQuickAdd && (
        <InjectSelectionBar
          count={numberOfSelectedElements}
          totalElements={totalElements}
          onClear={handleClearSelectedElements}
          onAdd={onQuickAddSelection}
        />
      )}
    </Box>
  );
};

export default InjectContractPicker;

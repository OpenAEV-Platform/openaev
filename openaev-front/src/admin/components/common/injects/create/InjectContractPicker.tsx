import { GridViewOutlined, ReorderOutlined } from '@mui/icons-material';
import { Box, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useMemo, useState } from 'react';

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
  type KillChainPhase,
} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import useDataLoader from '../../../../../utils/hooks/useDataLoader';
import useEntityToggle from '../../../../../utils/hooks/useEntityToggle';
import computeAttackPatterns from '../../../../../utils/injector_contract/InjectorContractUtils';
import useDomainIconFilter from '../../domains/useDomainIconFilter';
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
];

interface Props {
  title: string;
  /** Atomic testing creation: single-select, atomic-capable contracts only. */
  isAtomic?: boolean;
  onSelectContract: (contract: InjectorContractFullOutput) => void;
  /** Basket bulk-add (absent in atomic mode). */
  onQuickAdd?: (contracts: InjectorContractFullOutput[]) => void;
}

// The inject-contract picker: a full page mirroring the Threat Arsenal library
// layout - hero band, sticky facet sidebar (domains / platforms / kill chain),
// searchable card grid or list, and a floating selection basket for bulk add.
const InjectContractPicker: FunctionComponent<Props> = ({
  title,
  isAtomic = false,
  onSelectContract,
  onQuickAdd,
}) => {
  const theme = useTheme();
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

  // Contracts search (atomic creation only surfaces atomic-capable contracts)
  const [contracts, setContracts] = useState<InjectorContractFullOutput[]>([]);
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
      {/* Hero band (same surface as the Threat Arsenal / integrations heroes) */}
      <Box
        component="section"
        aria-label={title}
        sx={{
          position: 'relative',
          borderRadius: 1,
          padding: 2,
          overflow: 'hidden',
          border: `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
          backgroundColor: theme.palette.background.paper,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 1.5,
          flexWrap: 'wrap',
        }}
      >
        <Box
          aria-hidden
          sx={{
            position: 'absolute',
            top: -100,
            right: -60,
            width: 260,
            height: 260,
            borderRadius: '50%',
            background: alpha(theme.palette.primary.main, 0.08),
            filter: 'blur(60px)',
            pointerEvents: 'none',
          }}
        />
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1.25,
          flexWrap: 'wrap',
          minWidth: 0,
        }}
        >
          <Typography
            variant="h1"
            sx={{
              fontWeight: 700,
              margin: 0,
              fontSize: 22,
              whiteSpace: 'nowrap',
            }}
          >
            {title}
          </Typography>
          <Box
            sx={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: 0.75,
              paddingBlock: 0.5,
              paddingInline: 1.25,
              borderRadius: 1,
              border: `1px solid ${alpha(theme.palette.text.primary, 0.1)}`,
              backgroundColor: alpha(theme.palette.text.primary, 0.04),
            }}
          >
            <Typography sx={{
              fontWeight: 600,
              fontSize: 13,
              fontVariantNumeric: 'tabular-nums',
            }}
            >
              {totalElements}
            </Typography>
            <Typography sx={{
              color: 'text.secondary',
              fontSize: 13,
            }}
            >
              {t('total actions')}
            </Typography>
          </Box>
        </Box>
        <ToggleButtonGroup size="small" exclusive value={viewMode} onChange={handleViewMode}>
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
            fetch={searchInjectorContracts}
            searchPaginationInput={searchPaginationInput}
            setContent={setContracts}
            entityPrefix="injector_contract"
            availableFilterNames={availableFilterNames}
            queryableHelpers={queryableHelpers}
            attackPatterns={attackPatterns}
          />

          {contracts.length === 0 && <Empty message={t('No data to display')} />}

          {viewMode === 'grid' && contracts.length > 0 && (
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

          {viewMode === 'list' && contracts.length > 0 && (
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
              {contractMeta.map(({ contract, contractAttackPatterns, killChainPhaseName }) => (
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

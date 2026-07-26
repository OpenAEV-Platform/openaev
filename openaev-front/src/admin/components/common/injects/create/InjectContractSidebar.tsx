import { useCallback, useEffect, useMemo, useState } from 'react';

import { fetchInjectorContractAuthorCounts, fetchInjectorContractFacetCounts } from '../../../../../actions/InjectorContracts';
import { type KillChainPhaseHelper } from '../../../../../actions/kill_chain_phases/killchainphase-helper';
import {
  buildAuthorRows,
  buildStatusRows,
  useAuthorFacetFilter,
  useAuthorFacetOptions,
} from '../../../../../components/common/facets/ContractFacets';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../../../components/common/queryable/filter/FilterUtils';
import { useFormatter } from '../../../../../components/i18n';
import PlatformIcon from '../../../../../components/PlatformIcon';
import { useHelper } from '../../../../../store';
import { type Filter, type KillChainPhase, type SearchPaginationInput } from '../../../../../utils/api-types';
import { sortKillChainPhase } from '../../../../../utils/kill_chain_phases/kill_chain_phases';
import { type IconBarElement } from '../../domains/IconBar-model';

const PLATFORM_FILTER_KEY = 'injector_contract_platforms';
const KILL_CHAIN_FILTER_KEY = 'injector_contract_kill_chain_phases';
const STATUS_FILTER_KEY = 'injector_contract_payload_status';
const AUTHOR_FILTER_KEY = 'injector_contract_payload_author';
const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

// Well-known kill chains get their official product name; custom ones fall back
// to their raw name (mirrors the home dashboard MITRE matrix labels).
const KILL_CHAIN_LABELS: Record<string, string> = {
  'mitre-attack': 'MITRE ATT&CK',
  'mitre-atlas': 'MITRE ATLAS',
};
const killChainLabel = (name: string) => KILL_CHAIN_LABELS[name.toLowerCase()] ?? name;

interface FacetCounts {
  platforms: Record<string, number>;
  kill_chain_phases: Record<string, number>;
  statuses: Record<string, number>;
}

/**
 * Platform + kill-chain-phase + status counts under the current filters
 * (backend aggregation), so the fixed-universe sidebar facets show live counts
 * like the domain facet. Returns `null` until the first response lands, so the
 * sidebar can skip the count badges instead of flashing everything at 0.
 */
const useFacetCounts = (searchPaginationInput: SearchPaginationInput): FacetCounts | null => {
  const [counts, setCounts] = useState<FacetCounts | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchInjectorContractFacetCounts(searchPaginationInput)
      .then((response) => {
        if (!cancelled) setCounts((response.data ?? null) as FacetCounts | null);
      })
      .catch(() => {
        if (!cancelled) setCounts(null);
      });
    return () => {
      cancelled = true;
    };
  }, [searchPaginationInput]);

  return counts;
};

// ATT&CK first (the most common), then the other kill chains alphabetically.
const sortKillChains = (a: string, b: string) => {
  const aAttack = a.toLowerCase().includes('attack');
  const bAttack = b.toLowerCase().includes('attack');
  if (aAttack !== bAttack) return aAttack ? -1 : 1;
  return a.localeCompare(b);
};

interface Props {
  /** Domain facet rows (with live counts + icons), already ordered. */
  domainElements: IconBarElement[];
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

// The inject-contract picker sidebar: same sticky faceted panel as the Threat
// Arsenal / integrations marketplace, whose rows toggle REAL backend filters.
// The generic "Add filter" bar still handles every other property.
const InjectContractSidebar = ({ domainElements, searchPaginationInput, filterHelpers }: Props) => {
  const { t } = useFormatter();

  const { killChainPhasesMap }: { killChainPhasesMap: Record<string, KillChainPhase> } = useHelper(
    (helper: KillChainPhaseHelper) => ({ killChainPhasesMap: helper.getKillChainPhasesMap() }),
  );

  const facetCounts = useFacetCounts(searchPaginationInput);

  // Full author universe + per-filter counts (backend aggregation), so the
  // sidebar keeps every author visible and greys out the zero-count ones.
  const authorOptions = useAuthorFacetOptions(fetchInjectorContractAuthorCounts, searchPaginationInput);

  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );

  const platformValues = useMemo(
    () => filters.find((f: Filter) => f.key === PLATFORM_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const killChainValues = useMemo(
    () => filters.find((f: Filter) => f.key === KILL_CHAIN_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const statusValues = useMemo(
    () => filters.find((f: Filter) => f.key === STATUS_FILTER_KEY)?.values ?? [],
    [filters],
  );
  const {
    authorValues,
    noAuthorActive,
    toggleAuthorValue,
    toggleNoAuthor,
  } = useAuthorFacetFilter(AUTHOR_FILTER_KEY, filters, filterHelpers);

  const sortedPhases = useMemo(
    () => Object.values(killChainPhasesMap).toSorted(sortKillChainPhase),
    [killChainPhasesMap],
  );
  // Distinct kill chains on the platform (e.g. MITRE ATT&CK + MITRE ATLAS).
  // Each one gets its own titled facet section so unrelated phases never
  // interleave; ATT&CK always comes first.
  const killChains = useMemo(
    () => [...new Set(sortedPhases.map(phase => phase.phase_kill_chain_name))].sort(sortKillChains),
    [sortedPhases],
  );

  const setFilterValues = useCallback(
    (key: string, values: string[]) => {
      const existing = filters.find((f: Filter) => f.key === key);
      if (values.length === 0) {
        if (existing?.id) {
          filterHelpers.handleRemoveFilterById(existing.id);
        } else {
          filterHelpers.handleRemoveFilterByKey(key);
        }
        return;
      }
      if (existing?.id) {
        filterHelpers.handleUpdateValuesById(existing.id, values);
        return;
      }
      filterHelpers.handleAddFilterWithEmptyValue({
        id: generateFilterId(),
        key,
        operator: 'eq',
        values,
        mode: 'and',
      });
    },
    [filterHelpers, filters],
  );

  const toggleValue = useCallback(
    (key: string, current: string[], value: string) => {
      const next = current.includes(value)
        ? current.filter(v => v !== value)
        : [...current, value];
      setFilterValues(key, next);
    },
    [setFilterValues],
  );

  const sections: FacetSection[] = useMemo(() => {
    const domainRows: FacetRow[] = domainElements.map(element => ({
      value: element.type ?? element.name,
      label: t(element.name),
      count: element.count ?? 0,
      icon: element.icon,
      checked: element.color === 'success',
      onToggle: element.function,
    }));

    const platformRows: FacetRow[] = PLATFORMS.map(platform => ({
      value: platform,
      label: t(platform),
      count: facetCounts ? (facetCounts.platforms[platform] ?? 0) : undefined,
      icon: () => <PlatformIcon platform={platform} width={16} />,
      checked: platformValues.includes(platform),
      onToggle: () => toggleValue(PLATFORM_FILTER_KEY, platformValues, platform),
    }));

    const statusRows = buildStatusRows({
      t,
      statusValues,
      statusCounts: facetCounts?.statuses,
      toggle: value => toggleValue(STATUS_FILTER_KEY, statusValues, value),
    });

    const authorRows = buildAuthorRows({
      authorOptions,
      authorValues,
      noAuthorActive,
      toggleAuthorValue,
      toggleNoAuthor,
      noAuthorLabel: t('No author'),
    });

    // One titled section per kill chain ("MITRE ATT&CK" phases, then
    // "MITRE ATLAS" phases, ...). The section is always titled with the kill
    // chain name (like the rest of the app) so the user knows which kill
    // chain the phases belong to, even when only one exists.
    const killChainSections: FacetSection[] = killChains.map(chain => ({
      id: `kill-chain-${chain}`,
      label: killChainLabel(chain),
      rows: sortedPhases
        .filter(phase => phase.phase_kill_chain_name === chain)
        .map(phase => ({
          value: phase.phase_id,
          label: phase.phase_name,
          count: facetCounts ? (facetCounts.kill_chain_phases[phase.phase_id] ?? 0) : undefined,
          checked: killChainValues.includes(phase.phase_id),
          onToggle: () => toggleValue(KILL_CHAIN_FILTER_KEY, killChainValues, phase.phase_id),
        })),
    }));

    return [
      {
        id: 'domains',
        label: t('Domains'),
        rows: domainRows,
      },
      {
        id: 'platforms',
        label: t('Platform'),
        rows: platformRows,
      },
      {
        id: 'status',
        label: t('Status'),
        rows: statusRows,
      },
      {
        id: 'author',
        label: t('Author'),
        rows: authorRows,
      },
      ...killChainSections,
    ].filter(section => section.rows.length > 0);
  }, [
    domainElements, authorOptions, platformValues, killChainValues, statusValues, noAuthorActive,
    authorValues, sortedPhases, killChains, facetCounts, toggleValue, toggleAuthorValue, toggleNoAuthor, t,
  ]);

  return <FacetSidebar sections={sections} />;
};

export default InjectContractSidebar;

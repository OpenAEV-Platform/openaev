import { useCallback, useMemo } from 'react';

import { type KillChainPhaseHelper } from '../../../../../actions/kill_chain_phases/killchainphase-helper';
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
const PLATFORMS = ['Windows', 'Linux', 'MacOS'];

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
      icon: () => <PlatformIcon platform={platform} width={16} />,
      checked: platformValues.includes(platform),
      onToggle: () => toggleValue(PLATFORM_FILTER_KEY, platformValues, platform),
    }));

    const killChainRows: FacetRow[] = Object.values(killChainPhasesMap)
      .toSorted(sortKillChainPhase)
      .map(phase => ({
        value: phase.phase_id,
        label: phase.phase_name,
        checked: killChainValues.includes(phase.phase_id),
        onToggle: () => toggleValue(KILL_CHAIN_FILTER_KEY, killChainValues, phase.phase_id),
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
        id: 'kill-chain',
        label: t('Kill chain phase'),
        rows: killChainRows,
      },
    ].filter(section => section.rows.length > 0);
  }, [domainElements, platformValues, killChainValues, killChainPhasesMap, toggleValue, t]);

  const anyActive = platformValues.length > 0
    || killChainValues.length > 0
    || domainElements.some(e => e.color === 'success');

  return (
    <FacetSidebar
      sections={sections}
      anyActive={anyActive}
      onClearAll={() => filterHelpers.handleClearAllFilters()}
    />
  );
};

export default InjectContractSidebar;

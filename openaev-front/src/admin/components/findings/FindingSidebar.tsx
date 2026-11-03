import { useCallback, useEffect, useMemo, useState } from 'react';

import { searchAssetGroupLinkedToFindingsAsOption } from '../../../actions/asset_groups/assetgroup-action';
import { searchEndpointLinkedToFindingsAsOption } from '../../../actions/assets/endpoint-actions';
import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import ContractOutputElementType, { CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS } from './ContractOutputElementType';

const TYPE_FILTER_KEY = 'finding_type';
const ASSET_FILTER_KEY = 'finding_assets';
const ASSET_GROUP_FILTER_KEY = 'finding_asset_groups';

interface Option {
  id: string;
  label: string;
}

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
  /** Narrows the asset facets to a context (simulation, scenario...), like the "Add filter" options. */
  contextId?: string;
}

// The findings "basket", same shell as the Threat Arsenal sidebar: each row
// toggles a REAL backend filter through `filterHelpers`, so the toolbar chips,
// "Clear filters" and the URL stay in sync. No counts: there is no findings
// aggregation endpoint, so the rows carry no badge.
const FindingSidebar = ({ searchPaginationInput, filterHelpers, contextId = '' }: Props) => {
  const { t } = useFormatter();
  const [assetOptions, setAssetOptions] = useState<Option[]>([]);
  const [assetGroupOptions, setAssetGroupOptions] = useState<Option[]>([]);

  // Only the assets and asset groups that carry findings (same options as the
  // "Add filter" autocompletes of these keys).
  useEffect(() => {
    searchEndpointLinkedToFindingsAsOption('', contextId)
      .then((res: { data: Option[] }) => setAssetOptions(res.data))
      .catch(() => setAssetOptions([]));
    searchAssetGroupLinkedToFindingsAsOption('', contextId)
      .then((res: { data: Option[] }) => setAssetGroupOptions(res.data))
      .catch(() => setAssetGroupOptions([]));
  }, [contextId]);

  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );
  const valuesOf = useCallback(
    (key: string) => filters.find((f: Filter) => f.key === key)?.values ?? [],
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
        mode: 'or',
      });
    },
    [filterHelpers, filters],
  );

  const sections: FacetSection[] = useMemo(() => {
    const buildRows = (key: string, options: Option[], icon?: (value: string) => FacetRow['icon']): FacetRow[] => {
      const current = valuesOf(key);
      return options.map(option => ({
        value: option.id,
        label: option.label,
        icon: icon?.(option.id),
        checked: current.includes(option.id),
        onToggle: () => setFilterValues(
          key,
          current.includes(option.id) ? current.filter(v => v !== option.id) : [...current, option.id],
        ),
      }));
    };

    const typeOptions = CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS
      .map(type => ({
        id: type,
        label: t(ContractOutputElementType[type]),
      }))
      .sort((a, b) => a.label.localeCompare(b.label));

    return [
      {
        id: 'types',
        label: t('Finding type'),
        rows: buildRows(TYPE_FILTER_KEY, typeOptions, (type) => {
          const TypeIcon = () => <FindingIcon findingType={type} />;
          return TypeIcon;
        }),
      },
      {
        id: 'assets',
        label: t('Assets'),
        rows: buildRows(ASSET_FILTER_KEY, assetOptions),
      },
      {
        id: 'asset_groups',
        label: t('Asset groups'),
        rows: buildRows(ASSET_GROUP_FILTER_KEY, assetGroupOptions),
      },
    ].filter(section => section.rows.length > 0);
  }, [assetOptions, assetGroupOptions, valuesOf, setFilterValues, t]);

  return <FacetSidebar sections={sections} />;
};

export default FindingSidebar;

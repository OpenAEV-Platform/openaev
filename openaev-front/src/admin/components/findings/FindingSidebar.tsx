import { ComputerOutlined } from '@mui/icons-material';
import { useCallback, useMemo } from 'react';

import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import getFindingTypeLabel, { getFindingTypeKey } from './FindingTypeLabel';
import { type FindingFacetCounts } from './useFindingFacetCounts';

const SEVERITY_FILTER_KEY = 'finding_severity';
const TYPE_FILTER_KEY = 'finding_type';
const ASSET_FILTER_KEY = 'finding_assets';
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
  facetCounts: FindingFacetCounts | null;
}

const FindingSidebar = ({ searchPaginationInput, filterHelpers, facetCounts }: Props) => {
  const { t } = useFormatter();
  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );

  const valuesFor = useCallback(
    (key: string) => filters.find((filter: Filter) => filter.key === key)?.values ?? [],
    [filters],
  );

  const setValues = useCallback((key: string, next: string[]) => {
    const existing = filters.find((filter: Filter) => filter.key === key);
    if (next.length === 0) {
      if (existing?.id) {
        filterHelpers.handleRemoveFilterById(existing.id);
      } else {
        filterHelpers.handleRemoveFilterByKey(key);
      }
      return;
    }
    if (existing?.id) {
      filterHelpers.handleUpdateFilterById(existing.id, {
        values: next,
        mode: 'or',
      });
      return;
    }
    filterHelpers.handleAddFilterWithEmptyValue({
      id: generateFilterId(),
      key,
      operator: 'eq',
      values: next,
      mode: 'or',
    });
  }, [filterHelpers, filters]);

  const toggle = useCallback((key: string, value: string) => {
    const current = valuesFor(key);
    setValues(
      key,
      current.includes(value)
        ? current.filter(currentValue => currentValue !== value)
        : [...current, value],
    );
  }, [setValues, valuesFor]);

  // US.1: the quick filters are Severity, Type and Asset, listed like the Threat Arsenal ones
  // (every value with its count). Source, Asset group and the dates stay in the filter bar.
  const sections: FacetSection[] = useMemo(() => {
    const severityValues = valuesFor(SEVERITY_FILTER_KEY);
    const typeValues = valuesFor(TYPE_FILTER_KEY);
    const assetValues = valuesFor(ASSET_FILTER_KEY);

    const severityRows: FacetRow[] = SEVERITIES.map(severity => ({
      value: severity,
      label: severity,
      count: facetCounts?.severities[severity] ?? 0,
      checked: severityValues.includes(severity),
      onToggle: () => toggle(SEVERITY_FILTER_KEY, severity),
    }));
    const typeRows: FacetRow[] = Object.keys(facetCounts?.types ?? {})
      .sort((left, right) => getFindingTypeLabel(t, left).localeCompare(getFindingTypeLabel(t, right)))
      .map(type => ({
        value: type,
        label: getFindingTypeLabel(t, type),
        count: facetCounts?.types[type],
        icon: () => <FindingIcon findingType={getFindingTypeKey(type)} />,
        checked: typeValues.includes(type),
        onToggle: () => toggle(TYPE_FILTER_KEY, type),
      }));
    const assetRows: FacetRow[] = (facetCounts?.assets ?? []).map(asset => ({
      value: asset.asset_id,
      label: asset.asset_name,
      count: asset.asset_count,
      icon: () => <ComputerOutlined />,
      checked: assetValues.includes(asset.asset_id),
      onToggle: () => toggle(ASSET_FILTER_KEY, asset.asset_id),
    }));

    return [
      {
        id: 'severity',
        label: t('Severity'),
        rows: severityRows,
      },
      {
        id: 'type',
        label: t('Type'),
        rows: typeRows,
      },
      {
        id: 'asset',
        label: t('Asset'),
        rows: assetRows,
      },
    ].filter(section => section.rows.length > 0);
  }, [facetCounts, t, toggle, valuesFor]);

  return <FacetSidebar sections={sections} />;
};

export default FindingSidebar;

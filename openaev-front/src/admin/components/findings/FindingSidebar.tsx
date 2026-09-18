import { CloudOutlined } from '@mui/icons-material';
import { useCallback, useMemo } from 'react';

import { type FacetRow, type FacetSection, FacetSidebar } from '../../../components/common/facets/FacetFilters';
import { type FilterHelpers } from '../../../components/common/queryable/filter/FilterHelpers';
import { generateFilterId } from '../../../components/common/queryable/filter/FilterUtils';
import FindingIcon from '../../../components/FindingIcon';
import { useFormatter } from '../../../components/i18n';
import { type Filter, type SearchPaginationInput } from '../../../utils/api-types';
import InjectIcon from '../common/injects/InjectIcon';
import getFindingTypeLabel from './FindingTypeLabel';
import { type FindingFacetCounts } from './useFindingFacetCounts';

const SEVERITY_FILTER_KEY = 'finding_severity';
const TYPE_FILTER_KEY = 'finding_type';
const PROVIDER_FILTER_KEY = 'finding_cloud_provider';
const SOURCE_FILTER_KEY = 'finding_source';
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];
const CLOUD_PROVIDERS = [
  {
    value: 'aws',
    label: 'AWS',
  },
  {
    value: 'gcp',
    label: 'GCP',
  },
  {
    value: 'azure',
    label: 'Azure',
  },
];

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

  const sections: FacetSection[] = useMemo(() => {
    const severityValues = valuesFor(SEVERITY_FILTER_KEY);
    const typeValues = valuesFor(TYPE_FILTER_KEY);
    const providerValues = valuesFor(PROVIDER_FILTER_KEY);
    const sourceValues = valuesFor(SOURCE_FILTER_KEY);

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
        icon: () => <FindingIcon findingType={type} />,
        checked: typeValues.includes(type),
        onToggle: () => toggle(TYPE_FILTER_KEY, type),
      }));
    const providerRows: FacetRow[] = CLOUD_PROVIDERS.map(provider => ({
      value: provider.value,
      label: provider.label,
      count: facetCounts?.cloud_providers[provider.value] ?? 0,
      icon: () => <CloudOutlined />,
      checked: providerValues.includes(provider.value),
      onToggle: () => toggle(PROVIDER_FILTER_KEY, provider.value),
    }));
    const sourceRows: FacetRow[] = (facetCounts?.sources ?? []).map(source => ({
      value: source.source_id,
      label: source.source_name,
      count: source.source_count,
      icon: () => <InjectIcon type={source.source_type} />,
      checked: sourceValues.includes(source.source_id),
      onToggle: () => toggle(SOURCE_FILTER_KEY, source.source_id),
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
        id: 'cloud-provider',
        label: t('Cloud provider'),
        rows: providerRows,
      },
      {
        id: 'source',
        label: t('Source'),
        rows: sourceRows,
      },
    ].filter(section => section.rows.length > 0);
  }, [facetCounts, t, toggle, valuesFor]);

  return <FacetSidebar sections={sections} />;
};

export default FindingSidebar;

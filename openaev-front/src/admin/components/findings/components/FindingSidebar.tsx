import { useEffect, useMemo, useState } from 'react';

import { searchEndpointLinkedToFindingsAsOption } from '../../../../actions/assets/endpoint-actions';
import { buildAssetRows, buildFindingTypeRows } from '../../../../components/common/facets/ContractFacets';
import { type FacetSection, FacetSidebar } from '../../../../components/common/facets/FacetFilters';
import useFacetFilterToggle from '../../../../components/common/facets/useFacetFilterToggle';
import type { FilterHelpers } from '../../../../components/common/queryable/filter/FilterHelpers';
import { useFormatter } from '../../../../components/i18n';
import type { Filter, Option, SearchPaginationInput } from '../../../../utils/api-types';

interface Props {
  searchPaginationInput: SearchPaginationInput;
  filterHelpers: FilterHelpers;
}

const TYPE_FILTER_KEY = 'finding_type';
const ASSET_FILTER_KEY = 'finding_assets';

const FindingSidebar = ({ searchPaginationInput, filterHelpers }: Props) => {
  const { t } = useFormatter();
  const { toggleValueWithCurrent } = useFacetFilterToggle(searchPaginationInput, filterHelpers);
  const [assetOptions, setAssetOptions] = useState<Option[]>([]);

  useEffect(() => {
    searchEndpointLinkedToFindingsAsOption()
      .then((res: { data: Option[] }) => setAssetOptions(res.data))
      .catch(() => setAssetOptions([]));
  }, []);

  const filters = useMemo(
    () => searchPaginationInput.filterGroup?.filters ?? [],
    [searchPaginationInput.filterGroup],
  );

  const findingTypeValues = useMemo(
    () => filters.find((f: Filter) => f.key === TYPE_FILTER_KEY)?.values ?? [],
    [filters],
  );
  // Kept as ids, as stored in the filter: matching on labels would merge assets sharing a name, and
  // toggling would drop the selected ids missing from the loaded options.
  const assetIds = useMemo(
    () => filters.find((f: Filter) => f.key === ASSET_FILTER_KEY)?.values ?? [],
    [filters],
  );

  const sections: FacetSection[] = useMemo(() => {
    const findingTypeRows = buildFindingTypeRows({
      t,
      statusValues: findingTypeValues,
      statusCounts: undefined,
      toggle: value => toggleValueWithCurrent(TYPE_FILTER_KEY, findingTypeValues, value),
    });
    const assetRows = buildAssetRows({
      assetOptions,
      selectedIds: assetIds,
      toggle: id => toggleValueWithCurrent(ASSET_FILTER_KEY, assetIds, id),
    });

    return [
      {
        id: 'type',
        label: t('Type'),
        rows: findingTypeRows,
      },
      {
        id: 'asset',
        label: t('Asset'),
        rows: assetRows,
      },
    ];
  }, [t, findingTypeValues, assetIds, assetOptions, toggleValueWithCurrent]);

  return <FacetSidebar sections={sections} />;
};

export default FindingSidebar;

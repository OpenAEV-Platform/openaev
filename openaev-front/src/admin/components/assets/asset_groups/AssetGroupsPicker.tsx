import { SelectGroup } from 'mdi-material-ui';
import { normalize } from 'normalizr';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findAssetGroups, searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { arrayOfAssetGroups } from '../../../../actions/asset_groups/assetgroup-schema';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import * as Constants from '../../../../constants/ActionTypes';
import { type AssetGroupOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (assetGroupIds: string[]) => void;
}

// Always rendered as an inline dialog: it is only opened from the inject form
// drawer, and the design system never stacks a drawer over a drawer.
const AssetGroupsPicker: FunctionComponent<Props> = ({
  initialState = [],
  open,
  onClose,
  onSubmit,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [assetGroupValues, setAssetGroupValues] = useState<AssetGroupOutput[]>([]);
  useEffect(() => {
    if (open) {
      findAssetGroups(initialState).then(result => setAssetGroupValues(result.data));
    }
  }, [open, initialState]);

  const selectedIds = useMemo(() => assetGroupValues.map(v => v.asset_group_id), [assetGroupValues]);

  const toggleAssetGroup = (assetGroupId: string, assetGroup: AssetGroupOutput) => {
    if (selectedIds.includes(assetGroupId)) {
      setAssetGroupValues(assetGroupValues.filter(v => v.asset_group_id !== assetGroupId));
    } else {
      setAssetGroupValues([...assetGroupValues, assetGroup]);
    }
  };

  // Drawer
  const handleClose = () => {
    setAssetGroupValues([]);
    onClose();
  };

  const handleSubmit = () => {
    dispatch({
      type: Constants.DATA_FETCH_SUCCESS,
      payload: normalize(assetGroupValues, arrayOfAssetGroups),
    });
    onSubmit(assetGroupValues.map(v => v.asset_group_id));
    handleClose();
  };

  // Headers
  const elements: SelectListPickerElements<AssetGroupOutput> = useMemo(() => ({
    icon: { value: () => <SelectGroup color="primary" /> },
    headers: [
      {
        field: 'asset_group_name',
        label: 'Name',
        isSortable: true,
        value: (assetGroup: AssetGroupOutput) => <>{assetGroup.asset_group_name}</>,
        width: 60,
      },
      {
        field: 'asset_group_tags',
        label: 'Tags',
        value: (assetGroup: AssetGroupOutput) => <ItemTags variant="list" limit={2} tags={assetGroup.asset_group_tags} />,
        width: 40,
      },
    ],
  }), []);

  // Pagination
  const [assetGroups, setAssetGroups] = useState<AssetGroupOutput[]>([]);

  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));

  const paginationComponent = (
    <PaginationComponentV2
      fetch={searchAssetGroups}
      searchPaginationInput={searchPaginationInput}
      setContent={setAssetGroups}
      setLoading={setIsLoading}
      entityPrefix="asset_group"
      availableFilterNames={['asset_group_tags']}
      queryableHelpers={queryableHelpers}
    />
  );

  return (
    <SelectListPicker<AssetGroupOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={t('Modify asset groups in this inject')}
      inline
      headerComponent={paginationComponent}
      values={assetGroups}
      elements={elements}
      sortHelpers={queryableHelpers.sortHelpers}
      selectedIds={selectedIds}
      onToggle={toggleAssetGroup}
      getId={element => element.asset_group_id}
      isLoading={isLoading}
    />
  );
};

export default AssetGroupsPicker;

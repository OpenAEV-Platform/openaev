import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle } from '@mui/material';
import { SelectGroup } from 'mdi-material-ui';
import { normalize } from 'normalizr';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findAssetGroups, searchAssetGroups } from '../../../../actions/asset_groups/assetgroup-action';
import { arrayOfAssetGroups } from '../../../../actions/asset_groups/assetgroup-schema';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../components/common/SelectList';
import Transition from '../../../../components/common/Transition';
import { useFormatter } from '../../../../components/i18n';
import * as Constants from '../../../../constants/ActionTypes';
import { type AssetGroupOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (assetGroupIds: string[]) => void;
}

const AssetGroupDialogAdding: FunctionComponent<Props> = ({
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

  const addAssetGroup = (_assetGroupId: string, assetGroup: AssetGroupOutput) => setAssetGroupValues([...assetGroupValues, assetGroup]);
  const removeAssetGroup = (assetGroupId: string) => setAssetGroupValues(assetGroupValues.filter(v => v.asset_group_id !== assetGroupId));

  // Dialog
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
  const elements: SelectListElements<AssetGroupOutput> = useMemo(() => ({
    icon: { value: () => <SelectGroup color="primary" /> },
    headers: [
      {
        field: 'asset_group_name',
        value: (assetGroup: AssetGroupOutput) => <>{assetGroup.asset_group_name}</>,
        width: 100,
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
    <Dialog
      open={open}
      TransitionComponent={Transition}
      onClose={handleClose}
      fullWidth
      maxWidth="lg"
      PaperProps={{
        elevation: 1,
        sx: {
          minHeight: 580,
          maxHeight: 580,
        },
      }}
    >
      <DialogTitle>{t('Modify asset groups in this inject')}</DialogTitle>
      <DialogContent>
        <Box sx={{ marginTop: 2 }}>
          <SelectList
            values={assetGroups}
            selectedValues={assetGroupValues}
            isLoadingValues={isLoading}
            elements={elements}
            onSelect={addAssetGroup}
            onDelete={removeAssetGroup}
            paginationComponent={paginationComponent}
            getId={element => element.asset_group_id}
            getName={element => element.asset_group_name}
          />
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('Cancel')}</Button>
        {!isLoading && (
          <Button color="secondary" onClick={handleSubmit}>
            {t('Update')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default AssetGroupDialogAdding;

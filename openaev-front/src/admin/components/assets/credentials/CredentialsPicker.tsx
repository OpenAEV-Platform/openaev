import { Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { normalize } from 'normalizr';
import { type FunctionComponent, useContext, useEffect, useMemo, useState } from 'react';

import { searchEndpoints } from '../../../../actions/assets/endpoint-actions';
import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import PlatformIcon from '../../../../components/PlatformIcon';
import * as Constants from '../../../../constants/ActionTypes';
import {CredentialOutput, type EndpointOutput, type FilterGroup} from '../../../../utils/api-types';
import { getActiveMsgTooltip, getExecutorsCount } from '../../../../utils/endpoints/utils';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { buildTenantApiPath } from '../../../../utils/url-helper';
import AssetCategoryIcon from '../AssetCategoryIcon';
import AssetStatus from '../AssetStatus';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (endpointIds: string[]) => void;
  title: string;
}

// Always rendered as an inline dialog: every context that picks endpoints
// (inject form, asset group management, payload drawers) is itself an overlay,
// and the design system never stacks a drawer over a drawer.
const CredentialsPicker: FunctionComponent<Props> = ({
  initialState = [],
  open,
  onClose,
  onSubmit,
  title,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [credentialValues, setCredentialValues] = useState<CredentialOutput[]>([]);

  useEffect(() => {
    if (open) {
      findCredentials(initialState).then(result => setCredentialValues(result.data));
    }
  }, [open, initialState]);

  const selectedIds = useMemo(() => credentialValues.map(v => v.credential_id!), [credentialValues]);

  const toggleEndpoint = (credentialId: string, credential: CredentialOutput) => {
    if (selectedIds.includes(credentialId)) {
      setCredentialValues(credentialValues.filter(v => v.credential_id !== credentialId));
    } else {
      setCredentialValues([...credentialValues, credential]);
    }
  };

  // Drawer
  const handleClose = () => {
    setCredentialValues([]);
    onClose();
  };

  const handleSubmit = () => {
    dispatch({
      type: Constants.DATA_FETCH_SUCCESS,
      payload: normalize(credentialValues, arrayOfCredentials),
    });
    onSubmit(credentialValues.map(v => v.credential_id));
    handleClose();
  };

  // Headers
  const elements: SelectListPickerElements<CredentialOutput> = useMemo(() => ({
    // Category-aware glyph (same as the assets inventory page) so non-host assets
    // (web applications, cloud resources, ...) don't show the generic device icon.
    icon: { value: (credential: CredentialOutput) =>
      <AssetCategoryIcon
        category={credential?.credential_type ?? null}
        scope="credential"
        color="primary"
      />
    },
    headers: [
      // Widths must total 100: each cell renders as `width: N%` in a flex row,
      // so any excess pushes the last column (tags) out of the row.
      {
        field: 'asset_name',
        label: 'Name',
        isSortable: true,
        value: (credential: CredentialOutput) => credential.credential_name,
        width: 30,
      },
      {
        field: 'asset_tags',
        label: 'Tags',
        // Single chip + "+N" counter so the fixed-height cell never wraps.
        value: (credential: CredentialOutput) => <ItemTags variant="list" limit={1} tags={credential.credential_tags_ids} />,
        width: 20,
      },
    ],
  }), []);

  // Pagination
  const [credentials, setCredentials] = useState<CredentialOutput[]>([]);

  const availableFilterNames = [
    'asset_tags',
  ];
  // endpoint_platform / endpoint_arch are single-value enums: their only valid
  // operator is 'eq' (the filter UI exposes eq/not_eq/empty/not_empty - 'contains'
  // is not selectable). Only add each scoping filter when the caller actually
  // restricts the selection (e.g. an inject payload); the asset-group flow passes
  // no platforms, so it must open with no predefined filter at all.
  const quickFilter: FilterGroup = {
    mode: 'and',
    filters: [],
  };
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({ filterGroup: quickFilter }));

  const paginationComponent = (
    <PaginationComponentV2
      fetch={searchEndpoints}
      searchPaginationInput={searchPaginationInput}
      setContent={setCredentials}
      setLoading={setIsLoading}
      entityPrefix="credential"
      availableFilterNames={availableFilterNames}
      queryableHelpers={queryableHelpers}
    />
  );

  return (
    <SelectListPicker<CredentialOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={title}
      inline
      headerComponent={paginationComponent}
      values={credentials}
      elements={elements}
      sortHelpers={queryableHelpers.sortHelpers}
      selectedIds={selectedIds}
      onToggle={toggleEndpoint}
      getId={element => element.credential_id!}
      isLoading={isLoading}
    />
  );
};

export default CredentialsPicker;

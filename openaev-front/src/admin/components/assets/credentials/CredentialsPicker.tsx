import { normalize } from 'normalizr';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findCredentialsByIds, searchCredentials } from '../../../../actions/assets/credential-actions';
import { arrayOfCredentials } from '../../../../actions/Schema';
import { buildFilter } from '../../../../components/common/queryable/filter/FilterUtils';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import * as Constants from '../../../../constants/ActionTypes';
import { type CredentialOutput, type FilterGroup } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { humanizeEnum } from '../asset-categories';
import AssetCategoryIcon from '../AssetCategoryIcon';
import CredentialStatusChip from './CredentialStatusChip';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (endpointIds: string[]) => void;
  title: string;
  multiple?: boolean;
  credentialType?: CredentialOutput['credential_type'];
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
  multiple = true,
  credentialType,
}) => {
  // Standard hooks
  const { t, fldt } = useFormatter();
  const dispatch = useAppDispatch();
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [credentialValues, setCredentialValues] = useState<CredentialOutput[]>([]);

  useEffect(() => {
    if (open) {
      findCredentialsByIds(initialState).then(result => setCredentialValues(multiple ? result.data : result.data.slice(0, 1)));
    }
  }, [open, initialState, multiple]);

  const selectedIds = useMemo(() => credentialValues.map(v => v.credential_id!), [credentialValues]);

  const toggleEndpoint = (credentialId: string, credential: CredentialOutput) => {
    if (selectedIds.includes(credentialId)) {
      setCredentialValues(credentialValues.filter(v => v.credential_id !== credentialId));
    } else if (multiple) {
      setCredentialValues([...credentialValues, credential]);
    } else {
      // Single selection: the new credential replaces the previous one.
      setCredentialValues([credential]);
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
    onSubmit(credentialValues.map(v => v.credential_id!));
    handleClose();
  };

  // Headers
  const elements: SelectListPickerElements<CredentialOutput> = useMemo(() => ({
    // Category-aware glyph (same as the assets inventory page) so non-host assets
    // (web applications, cloud resources, ...) don't show the generic device icon.
    icon: {
      value: (credential: CredentialOutput) => (
        <AssetCategoryIcon
          category={credential?.credential_type ?? null}
          scope="credential"
          color="primary"
        />
      ),
    },
    headers: [
      // Widths must total 100: each cell renders as `width: N%` in a flex row,
      // so any excess pushes the last column out of the row. Sized for the `lg`
      // dialog (~1060px of cells, 1% ~ 10px): Status fits its fixed 120px chip,
      // Tags one chip + "+N" counter, dates the compact format.
      // Labels are i18n keys: SortHeadersComponentV2 translates them.
      {
        field: 'credential_name',
        label: t('Name'),
        isSortable: true,
        value: (credential: CredentialOutput) => credential.credential_name ?? '',
        width: 16,
      },
      {
        field: 'credential_type',
        label: t('Type'),
        isSortable: true,
        value: (credential: CredentialOutput) => (credential.credential_type ? humanizeEnum(credential.credential_type) : '-'),
        width: 9,
      },
      {
        field: 'credential_auth_method',
        label: t('Auth Method'),
        value: (credential: CredentialOutput) => (credential.credential_auth_method ? humanizeEnum(credential.credential_auth_method) : '-'),
        width: 14,
      },
      {
        field: 'credential_status',
        label: t('Status'),
        value: (credential: CredentialOutput) => (
          <CredentialStatusChip status={credential.credential_status} variant="list" />
        ),
        width: 12,
      },
      {
        field: 'credential_last_verified_at',
        label: t('Last verified'),
        value: (credential: CredentialOutput) => (credential.credential_last_verified_at ? fldt(credential.credential_last_verified_at) : '-'),
        width: 13,
      },
      {
        field: 'credential_tags_ids',
        label: t('Tags'),
        // Single chip + "+N" counter so the fixed-height cell never wraps.
        value: (credential: CredentialOutput) => <ItemTags variant="list" limit={1} tags={credential.credential_tags_ids ?? []} />,
        width: 12,
      },
      {
        field: 'credential_created_at',
        label: t('Created'),
        value: (credential: CredentialOutput) => (credential.credential_created_at ? fldt(credential.credential_created_at) : '-'),
        width: 13,
      },
      {
        field: 'credential_created_by',
        label: t('Created by'),
        value: (credential: CredentialOutput) => credential.credential_created_by?.user_name || '-',
        width: 11,
      },
    ],
  }), [fldt]);

  // Pagination
  const [credentials, setCredentials] = useState<CredentialOutput[]>([]);

  const availableFilterNames = [
    'asset_tags',
    'secret_reference_credential_type',
  ];
  // The filter key is the entity property (secret_reference_credential_type), not the
  // output field (credential_type): the chip only renders - and can only be removed -
  // when its key resolves to a filterable property schema. Single-value enum, so 'eq'.
  // Only pre-apply it when the inject contract restricts the credential type.
  const quickFilter: FilterGroup = {
    mode: 'or',
    filters: [],
  };
  if (credentialType) {
    quickFilter.filters?.push(buildFilter('secret_reference_credential_type', [credentialType], 'eq'));
  }
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({ filterGroup: quickFilter }));

  const paginationComponent = (
    <PaginationComponentV2
      fetch={searchCredentials}
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
      showSelectedCount={multiple}
    />
  );
};

export default CredentialsPicker;

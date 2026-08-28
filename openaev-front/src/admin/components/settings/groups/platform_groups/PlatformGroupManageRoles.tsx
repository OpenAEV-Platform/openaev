import { SecurityOutlined } from '@mui/icons-material';
import { type FC, useEffect, useMemo, useState } from 'react';

import { fetchPlatformGroupRoleIds } from '../../../../../actions/platform/platform-group/platform-group-action';
import { findPlatformRoles, searchPlatformRoles } from '../../../../../actions/platform/platform-role/platform-role-action';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../../components/i18n';
import { type PlatformRoleOutput } from '../../../../../utils/api-types';
import { ENTITY_PLATFORM_ROLE_PREFIX, PLATFORM_ROLE_FILTERS } from '../../roles/platform_roles/platformRoles.queryable';

interface Props {
  platformGroupId: string;
  open: boolean;
  onClose: () => void;
  onSubmit: (roleIds: string[]) => void;
}

const PlatformGroupManageRoles: FC<Props> = ({
  platformGroupId,
  open,
  onClose,
  onSubmit,
}) => {
  const { t } = useFormatter();

  const [roleValues, setRoleValues] = useState<PlatformRoleOutput[]>([]);
  const [selectedRoleValues, setSelectedRoleValues] = useState<PlatformRoleOutput[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (open) {
      fetchPlatformGroupRoleIds(platformGroupId).then(
        (result: { data: string[] }) => {
          const ids = result.data ?? [];
          if (ids.length > 0) {
            findPlatformRoles(ids).then(
              (rolesResult: { data: PlatformRoleOutput[] }) => {
                setSelectedRoleValues(rolesResult.data ?? []);
              },
            );
          }
        },
      );
    }
  }, [open, platformGroupId]);

  const selectedIds = useMemo(() => selectedRoleValues.map(r => r.platform_role_id), [selectedRoleValues]);

  const toggleRole = (roleId: string, role: PlatformRoleOutput) => {
    if (selectedIds.includes(roleId)) {
      setSelectedRoleValues(prev => prev.filter(r => r.platform_role_id !== roleId));
    } else {
      setSelectedRoleValues(prev => [...prev, role]);
    }
  };

  // Headers
  const elements: SelectListPickerElements<PlatformRoleOutput> = useMemo(() => ({
    icon: { value: () => <SecurityOutlined /> },
    headers: [
      {
        // Backend Queryable sort field (the output DTO exposes it as
        // platform_role_name, see platformRoles.queryable.ts).
        field: 'role_name',
        label: 'Name',
        isSortable: true,
        value: (role: PlatformRoleOutput) => role.platform_role_name,
        width: 100,
      },
    ],
  }), []);

  // Pagination
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <PaginationComponentV2
      fetch={input => searchPlatformRoles(input)}
      searchPaginationInput={searchPaginationInput}
      setContent={setRoleValues}
      setLoading={setIsLoading}
      entityPrefix={ENTITY_PLATFORM_ROLE_PREFIX}
      availableFilterNames={PLATFORM_ROLE_FILTERS}
      queryableHelpers={queryableHelpers}
    />
  );

  const handleClose = () => {
    setRoleValues([]);
    setSelectedRoleValues([]);
    onClose();
  };

  const handleSubmit = () => {
    onSubmit(selectedRoleValues.map(r => r.platform_role_id));
    handleClose();
  };

  return (
    <SelectListPicker<PlatformRoleOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={t('Manage the platform roles of this group')}
      headerComponent={paginationComponent}
      values={roleValues}
      elements={elements}
      selectedIds={selectedIds}
      onToggle={toggleRole}
      getId={element => element.platform_role_id}
      isLoading={isLoading}
    />
  );
};

export default PlatformGroupManageRoles;

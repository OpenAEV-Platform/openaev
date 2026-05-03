import { SecurityOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FC, useEffect, useMemo, useState } from 'react';

import { fetchPlatformGroupRoleIds } from '../../../../../actions/platform/platform-group/platform-group-action';
import { findPlatformRoles, searchPlatformRoles } from '../../../../../actions/platform/platform-role/platform-role-action';
import ActionButtons from '../../../../../components/common/ActionButtons';
import Drawer from '../../../../../components/common/Drawer';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../../components/common/SelectList';
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

  // Headers
  const elements: SelectListElements<PlatformRoleOutput> = useMemo(() => ({
    icon: { value: () => <SecurityOutlined /> },
    headers: [
      {
        field: 'platform_role_name',
        value: (role: PlatformRoleOutput) => role.platform_role_name,
        width: 100,
      },
    ],
  }), []);

  const addRole = (_roleId: string, role: PlatformRoleOutput) => {
    setSelectedRoleValues(prev => [...prev, role]);
  };
  const removeRole = (roleId: string) => {
    setSelectedRoleValues(prev => prev.filter(r => r.platform_role_id !== roleId));
  };

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
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Manage the platform roles of this group')}
      variant="full"
    >
      <Box sx={{
        marginTop: 2,
        display: 'flex',
        flexDirection: 'column',
        gap: 2,
      }}
      >
        <SelectList
          values={roleValues}
          selectedValues={selectedRoleValues}
          isLoadingValues={isLoading}
          elements={elements}
          onSelect={addRole}
          onDelete={removeRole}
          paginationComponent={paginationComponent}
          getId={element => element.platform_role_id}
          getName={(element: PlatformRoleOutput) => element.platform_role_name}
        />
        <div style={{ alignSelf: 'flex-end' }}>
          <ActionButtons
            onSubmit={handleSubmit}
            onCancel={handleClose}
            cancelLabel={t('Cancel')}
            submitLabel={t('Update')}
          />
        </div>
      </Box>
    </Drawer>
  );
};

export default PlatformGroupManageRoles;

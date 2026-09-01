import { LockOutlined, SecurityOutlined } from '@mui/icons-material';
import { Box, Tooltip } from '@mui/material';
import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';

import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import { type RoleOutput } from '../../../../utils/api-types';
import useCapabilities from '../../../../utils/hooks/useCapabilities';
import useCapabilityGrants from '../../../../utils/hooks/useCapabilityGrants';
import { ENTITY_ROLE_PREFIX, ROLE_FILTERS } from '../roles/roles.queryable';
import { useRoleScope } from '../roles/RoleScopeContext';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (roleIds: string[]) => void;
  groupName?: string;
  title?: string;
}

const GroupManageRoles: FunctionComponent<Props> = ({
  initialState,
  open,
  onClose,
  onSubmit,
  groupName = '',
  title,
}) => {
  const { t } = useFormatter();
  const { scope, search, find } = useRoleScope();
  const { capabilities } = useCapabilities(scope);
  const { missingCapabilities } = useCapabilityGrants(capabilities);

  const [roleValues, setRoleValues] = useState<RoleOutput[]>([]);
  const [selectedRoleValues, setSelectedRoleValues] = useState<RoleOutput[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    if (!open) {
      return () => {};
    }
    let obsolete = false;
    find(initialState).then((roles: RoleOutput[]) => {
      if (!obsolete) {
        setSelectedRoleValues(roles);
      }
    }).catch(() => {
      if (!obsolete) {
        setSelectedRoleValues([]);
      }
    });
    return () => {
      obsolete = true;
    };
  }, [open, initialState, find]);

  const selectedIds = useMemo(() => selectedRoleValues.map(role => role.role_id), [selectedRoleValues]);

  // The API validates the resulting role set, so a restricted role can be detached but never added.
  const isRoleRestricted = useCallback(
    (role: RoleOutput) => missingCapabilities(role.role_capabilities ?? []).length > 0,
    [missingCapabilities],
  );

  const toggleRole = (roleId: string, role: RoleOutput) => {
    if (isRoleRestricted(role) && !selectedIds.includes(roleId)) {
      return;
    }
    if (selectedIds.includes(roleId)) {
      setSelectedRoleValues(prev => prev.filter(r => r.role_id !== roleId));
    } else {
      setSelectedRoleValues(prev => [...prev, role]);
    }
  };

  const restrictedTooltip = t(
    'The current user does not have all the capabilities of this role: it can only be removed, not added',
  );

  const elements: SelectListPickerElements<RoleOutput> = useMemo(() => ({
    icon: { value: () => <SecurityOutlined /> },
    headers: [
      {
        field: 'role_name',
        label: 'Name',
        isSortable: true,
        value: (role: RoleOutput) => (
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.5,
            }}
          >
            <span>{role.role_name}</span>
            {isRoleRestricted(role) && (
              <Tooltip title={restrictedTooltip}>
                <LockOutlined
                  sx={{
                    color: 'text.disabled',
                    fontSize: 16,
                  }}
                />
              </Tooltip>
            )}
          </Box>
        ),
        width: 100,
      },
    ],
  }), [isRoleRestricted, restrictedTooltip]);

  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <PaginationComponentV2
      fetch={search}
      searchPaginationInput={searchPaginationInput}
      setContent={setRoleValues}
      setLoading={setIsLoading}
      entityPrefix={ENTITY_ROLE_PREFIX}
      availableFilterNames={ROLE_FILTERS}
      queryableHelpers={queryableHelpers}
    />
  );

  const disabledIds = useMemo(
    () => roleValues
      .filter(role => isRoleRestricted(role) && !selectedIds.includes(role.role_id))
      .map(role => role.role_id),
    [roleValues, isRoleRestricted, selectedIds],
  );

  const restrictedSelectedRoles = useMemo(
    () => selectedRoleValues.filter(isRoleRestricted),
    [selectedRoleValues, isRoleRestricted],
  );

  const handleClose = () => {
    setRoleValues([]);
    setSelectedRoleValues([]);
    onClose();
  };

  const handleSubmit = () => {
    onSubmit(selectedRoleValues.map(role => role.role_id));
    handleClose();
  };

  let headerActions;
  if (restrictedSelectedRoles.length > 0) {
    headerActions = (
      <Tooltip
        title={t('The current user must remove the restricted roles before saving: {roles}',
          { roles: restrictedSelectedRoles.map(role => role.role_name).join(', ') })}
      >
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
          }}
        >
          <LockOutlined sx={{ color: 'text.disabled' }} />
        </Box>
      </Tooltip>
    );
  }

  return (
    <SelectListPicker<RoleOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={title ?? t('Manage roles for group: {groupName}', { groupName })}
      headerActions={headerActions}
      headerComponent={paginationComponent}
      values={roleValues}
      elements={elements}
      sortHelpers={queryableHelpers.sortHelpers}
      selectedIds={selectedIds}
      disabledIds={disabledIds}
      onToggle={toggleRole}
      getId={element => element.role_id}
      isLoading={isLoading}
      submitDisabled={restrictedSelectedRoles.length > 0}
    />
  );
};

export default GroupManageRoles;

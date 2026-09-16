import { PersonOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findUsers, searchUsers } from '../../../../actions/users/User';
import { type Page } from '../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { type SearchPaginationInput, type UserOutput } from '../../../../utils/api-types';
import useCapabilities from '../../../../utils/hooks/useCapabilities';
import useCapabilityGrants from '../../../../utils/hooks/useCapabilityGrants';
import { resolveUserName } from '../../../../utils/String';
import LockHint from '../../common/LockHint';
import { useRoleScope } from '../roles/RoleScopeContext';

interface Props {
  initialState: string[];
  groupName: string;
  open: boolean;
  onClose: () => void;
  onSubmit: (userIds: string[]) => void;
  groupRoleIds?: string[];
  searchUsersFn?: (input: SearchPaginationInput) => Promise<{ data: Page<UserOutput> }>;
  findUsersFn?: (userIds: string[]) => Promise<{ data: UserOutput[] }>;
}

const GroupManageUsers: FunctionComponent<Props> = ({
  initialState = [],
  groupName,
  open,
  onClose,
  onSubmit,
  groupRoleIds = [],
  searchUsersFn = searchUsers,
  findUsersFn = findUsers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const { scope, find } = useRoleScope();
  const { capabilities } = useCapabilities(scope);
  const { missingCapabilities } = useCapabilityGrants(capabilities);

  const [userValues, setUserValues] = useState<UserOutput[]>([]);
  const [selectedUserValues, setSelectedUserValues] = useState<UserOutput[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [missingRoleCapabilities, setMissingRoleCapabilities] = useState<string[]>([]);

  useEffect(() => {
    if (!open) {
      return () => {};
    }

    let obsolete = false;
    findUsersFn(initialState).then((result) => {
      if (!obsolete) {
        setSelectedUserValues(result.data);
      }
    });

    if (groupRoleIds.length === 0) {
      setMissingRoleCapabilities([]);
      return () => {
        obsolete = true;
      };
    }

    find(groupRoleIds).then((roles) => {
      if (obsolete) {
        return;
      }
      const missing = new Set(missingCapabilities(roles.flatMap(role => role.role_capabilities ?? [])));
      setMissingRoleCapabilities(Array.from(missing).sort((a, b) => a.localeCompare(b)));
    }).catch(() => {
      if (!obsolete) {
        setMissingRoleCapabilities([]);
      }
    });

    return () => {
      obsolete = true;
    };
  }, [open, initialState, groupRoleIds, missingCapabilities, find]);

  const isMembershipLocked = missingRoleCapabilities.length > 0;

  const selectedIds = useMemo(() => selectedUserValues.map(v => v.user_id), [selectedUserValues]);

  // A locked membership freezes every row: the ids rendered are exactly those of `userValues`.
  const disabledIds = useMemo(
    () => (isMembershipLocked ? userValues.map(user => user.user_id) : []),
    [isMembershipLocked, userValues],
  );

  const toggleUser = (userId: string, user: UserOutput) => {
    if (isMembershipLocked) {
      return;
    }

    if (selectedIds.includes(userId)) {
      setSelectedUserValues(selectedUserValues.filter(v => v.user_id !== userId));
    } else {
      setSelectedUserValues([...selectedUserValues, user]);
    }
  };

  // Headers
  const elements: SelectListPickerElements<UserOutput> = useMemo(() => ({
    icon: { value: () => <PersonOutlined /> },
    headers: [
      {
        field: 'user_email',
        label: 'Name',
        isSortable: true,
        value: (user: UserOutput) => resolveUserName(user),
        width: 50,
      },
      {
        field: 'user_organization_name',
        label: 'Organization',
        value: (user: UserOutput) => user.user_organization_name ?? '',
        width: 20,
      },
      {
        field: 'user_tags',
        label: 'Tags',
        value: (user: UserOutput) => <ItemTags variant="list" limit={1} tags={user.user_tags} />,
        width: 30,
      },
    ],
  }), []);

  // Pagination
  const lockTooltip = t(
    'The current user cannot add or remove users from this group because required role capabilities are missing: {capabilities}',
    { capabilities: missingRoleCapabilities.map(capability => t(capability)).join(', ') },
  );

  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <>
      {isMembershipLocked && <LockHint>{lockTooltip}</LockHint>}
      <PaginationComponentV2
        fetch={input => searchUsersFn(input)}
        searchPaginationInput={searchPaginationInput}
        setContent={setUserValues}
        setLoading={setIsLoading}
        entityPrefix="user"
        availableFilterNames={['user_tags']}
        queryableHelpers={queryableHelpers}
      />
    </>
  );

  const handleClose = () => {
    setUserValues([]);
    setMissingRoleCapabilities([]);
    onClose();
  };
  const handleSubmit = () => {
    if (isMembershipLocked) {
      return;
    }
    onSubmit(selectedUserValues.map(u => u.user_id));
    handleClose();
  };

  const title = t('Manage users for group: {groupName}', { groupName });

  return (
    <SelectListPicker<UserOutput>
      open={open}
      onClose={handleClose}
      onSubmit={handleSubmit}
      title={title}
      headerComponent={paginationComponent}
      values={userValues}
      elements={elements}
      sortHelpers={queryableHelpers.sortHelpers}
      selectedIds={selectedIds}
      disabledIds={disabledIds}
      submitDisabled={isMembershipLocked}
      onToggle={toggleUser}
      getId={element => element.user_id}
      isLoading={isLoading}
    />
  );
};

export default GroupManageUsers;

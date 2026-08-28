import { PersonOutlined } from '@mui/icons-material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findUsers, searchUsers } from '../../../../../actions/users/User';
import { type Page } from '../../../../../components/common/queryable/Page';
import PaginationComponentV2 from '../../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectListPicker, { type SelectListPickerElements } from '../../../../../components/common/SelectListPicker';
import { useFormatter } from '../../../../../components/i18n';
import ItemTags from '../../../../../components/ItemTags';
import { type SearchPaginationInput, type UserOutput } from '../../../../../utils/api-types';
import { resolveUserName } from '../../../../../utils/String';

interface Props {
  initialState: string[];
  groupName: string;
  open: boolean;
  onClose: () => void;
  onSubmit: (userIds: string[]) => void;
  searchUsersFn?: (input: SearchPaginationInput) => Promise<{ data: Page<UserOutput> }>;
  findUsersFn?: (userIds: string[]) => Promise<{ data: UserOutput[] }>;
}

const GroupManageUsers: FunctionComponent<Props> = ({
  initialState = [],
  groupName,
  open,
  onClose,
  onSubmit,
  searchUsersFn = searchUsers,
  findUsersFn = findUsers,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [userValues, setUserValues] = useState<UserOutput[]>([]);
  const [selectedUserValues, setSelectedUserValues] = useState<UserOutput[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  useEffect(() => {
    if (open) {
      findUsersFn(initialState).then(result => setSelectedUserValues(result.data));
    }
  }, [open, initialState]);

  const selectedIds = useMemo(() => selectedUserValues.map(v => v.user_id), [selectedUserValues]);

  const toggleUser = (userId: string, user: UserOutput) => {
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
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <PaginationComponentV2
      fetch={input => searchUsersFn(input)}
      searchPaginationInput={searchPaginationInput}
      setContent={setUserValues}
      setLoading={setIsLoading}
      entityPrefix="user"
      availableFilterNames={['user_tags']}
      queryableHelpers={queryableHelpers}
    />
  );

  const handleClose = () => {
    setUserValues([]);
    onClose();
  };
  const handleSubmit = () => {
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
      onToggle={toggleUser}
      getId={element => element.user_id}
      isLoading={isLoading}
    />
  );
};

export default GroupManageUsers;

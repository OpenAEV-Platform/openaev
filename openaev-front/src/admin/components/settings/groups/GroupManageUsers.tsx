import { PersonOutlined } from '@mui/icons-material';
import { Box, Button } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';

import { findUsers, searchUsers } from '../../../../actions/users/User';
import Drawer from '../../../../components/common/Drawer';
import PaginationComponentV2 from '../../../../components/common/queryable/pagination/PaginationComponentV2';
import { buildSearchPagination } from '../../../../components/common/queryable/QueryableUtils';
import { useQueryable } from '../../../../components/common/queryable/useQueryableWithLocalStorage';
import SelectList, { type SelectListElements } from '../../../../components/common/SelectList';
import { useFormatter } from '../../../../components/i18n';
import ItemTags from '../../../../components/ItemTags';
import { type UserOutput } from '../../../../utils/api-types';
import { resolveUserName } from '../../../../utils/String';

interface Props {
  initialState: string[];
  open: boolean;
  onClose: () => void;
  onSubmit: (userIds: string[]) => void;
}

const GroupManageUsers: FunctionComponent<Props> = ({
  initialState = [],
  open,
  onClose,
  onSubmit,
}) => {
  // Standard hooks
  const { t } = useFormatter();

  const [userValues, setUserValues] = useState<UserOutput[]>([]);
  const [selectedUserValues, setSelectedUserValues] = useState<UserOutput[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  useEffect(() => {
    if (open) {
      findUsers(initialState).then(result => setSelectedUserValues(result.data));
    }
  }, [open, initialState]);

  // Headers
  const elements: SelectListElements<UserOutput> = useMemo(() => ({
    icon: { value: () => <PersonOutlined /> },
    headers: [
      {
        field: 'user_name',
        value: (user: UserOutput) => resolveUserName(user),
        width: 50,
      },
      {
        field: 'user_organization_name',
        value: (user: UserOutput) => user.user_organization_name ?? '',
        width: 20,
      },
      {
        field: 'user_tags',
        value: (user: UserOutput) => <ItemTags variant="list" tags={user.user_tags} />,
        width: 30,
      },
    ],
  }), []);

  const addUser = (_userId: string, user: UserOutput) => setSelectedUserValues([...selectedUserValues, user]);
  const removeUser = (userId: string) => setSelectedUserValues(selectedUserValues.filter(v => v.user_id !== userId));

  // Pagination
  const { queryableHelpers, searchPaginationInput } = useQueryable(buildSearchPagination({}));
  const paginationComponent = (
    <PaginationComponentV2
      fetch={input => searchUsers(input)}
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

  return (
    <Drawer
      open={open}
      handleClose={handleClose}
      title={t('Manage the users of this group')}
      variant="full"
    >
      <Box sx={{ marginTop: 2 }}>
        <SelectList
          values={userValues}
          selectedValues={selectedUserValues}
          isLoadingValues={isLoading}
          elements={elements}
          onSelect={addUser}
          onDelete={removeUser}
          paginationComponent={paginationComponent}
          getId={element => element.user_id}
          getName={(element: UserOutput) => resolveUserName(element)}
        />
        <div style={{
          float: 'right',
          marginTop: 20,
        }}
        >
          <Button variant="contained" style={{ marginRight: 10 }} onClick={onClose}>
            {t('Cancel')}
          </Button>
          {!isLoading && (
            <Button variant="contained" color="secondary" onClick={handleSubmit}>
              {t('Update')}
            </Button>
          )}
        </div>
      </Box>
    </Drawer>
  );
};

export default GroupManageUsers;

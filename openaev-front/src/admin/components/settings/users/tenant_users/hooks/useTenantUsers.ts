import { useCallback, useState } from 'react';

import { deleteUser, searchUsers } from '../../../../../../actions/users/User';
import { type SearchPaginationInput, type UserOutput } from '../../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../../utils/hooks';

const useTenantUsers = () => {
  const dispatch = useAppDispatch();
  const [users, setUsers] = useState<UserOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setUserList = useCallback((data: UserOutput[]) => {
    setUsers(data);
  }, []);

  const fetchUsers = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchUsers(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addUser = useCallback((user: UserOutput) => {
    setUsers(prev => [user, ...prev]);
  }, []);

  const removeUser = useCallback((userId: string) => {
    dispatch(deleteUser(userId)).then(() => {
      setUsers(prev => prev.filter(u => u.user_id !== userId));
    });
  }, [dispatch]);

  return {
    users,
    setUserList,
    loading,
    fetchUsers,
    addUser,
    removeUser,
  };
};

export default useTenantUsers;

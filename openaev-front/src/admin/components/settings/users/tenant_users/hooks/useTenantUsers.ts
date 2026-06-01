import { useCallback, useState } from 'react';

import { deleteUser, searchUsers, updateUser } from '../../../../../../actions/users/User';
import { type SearchPaginationInput, type UserInput, type UserOutput } from '../../../../../../utils/api-types';
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

  const editUser = useCallback((userId: string, data: UserInput) => {
    const inputValues = { ...data };
    return dispatch(updateUser(userId, inputValues)).then((result: {
      entities: { users: Record<string, UserOutput> };
      result: string;
    }) => {
      if (result?.result) {
        const updated = result.entities.users[result.result];
        setUsers(prev => prev.map(u => (u.user_id === userId ? updated : u)));
      }
    });
  }, [dispatch]);

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
    editUser,
    removeUser,
  };
};

export default useTenantUsers;

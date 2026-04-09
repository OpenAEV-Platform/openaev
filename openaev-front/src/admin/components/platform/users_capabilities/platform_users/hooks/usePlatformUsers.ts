import { useCallback, useState } from 'react';

import { searchPlatformUsers } from '../../../../../../actions/platform/users/platform-user-action';
import type { SearchPaginationInput, UserOutput } from '../../../../../../utils/api-types';

const usePlatformUsers = () => {
  const [platformUsers, setPlatformUsers] = useState<UserOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setPlatformUserList = useCallback((users: UserOutput[]) => {
    setPlatformUsers(users);
  }, []);

  const fetchPlatformUsers = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchPlatformUsers(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addPlatformUser = useCallback((user: UserOutput) => {
    setPlatformUsers(prev => [user, ...prev]);
  }, []);

  const updatePlatformUserInList = useCallback((user: UserOutput) => {
    setPlatformUsers(prev =>
      prev.map(u =>
        u.user_id === user.user_id ? user : u,
      ),
    );
  }, []);

  const removePlatformUser = useCallback((userId: string) => {
    setPlatformUsers(prev => prev.filter(u => u.user_id !== userId));
  }, []);

  return {
    platformUsers,
    setPlatformUserList,
    loading,
    fetchPlatformUsers,
    addPlatformUser,
    updatePlatformUserInList,
    removePlatformUser,
  };
};

export default usePlatformUsers;

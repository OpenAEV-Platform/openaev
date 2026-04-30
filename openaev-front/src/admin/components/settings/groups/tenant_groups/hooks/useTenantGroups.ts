import { useCallback, useState } from 'react';

import { searchGroups } from '../../../../../../actions/Group';
import type { Group, SearchPaginationInput } from '../../../../../../utils/api-types';

const useTenantGroups = () => {
  const [groups, setGroups] = useState<Group[]>([]);
  const [loading, setLoading] = useState(true);

  const setGroupList = useCallback((data: Group[]) => {
    setGroups(data);
  }, []);

  const fetchGroups = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchGroups(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addGroup = useCallback((group: Group) => {
    setGroups(prev => [group, ...prev]);
  }, []);

  const updateGroupInList = useCallback((group: Group) => {
    setGroups(prev =>
      prev.map(g => g.group_id === group.group_id ? group : g),
    );
  }, []);

  const removeGroup = useCallback((groupId: string) => {
    setGroups(prev => prev.filter(g => g.group_id !== groupId));
  }, []);

  return {
    groups,
    setGroupList,
    loading,
    fetchGroups,
    addGroup,
    updateGroupInList,
    removeGroup,
  };
};

export default useTenantGroups;

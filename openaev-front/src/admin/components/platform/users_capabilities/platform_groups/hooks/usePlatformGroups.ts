import { useCallback, useState } from 'react';

import { searchPlatformGroups } from '../../../../../../actions/platform/platform-group/platform-group-action';
import type { PlatformGroupOutput, SearchPaginationInput } from '../../../../../../utils/api-types';

const usePlatformGroups = () => {
  const [platformGroups, setPlatformGroups] = useState<PlatformGroupOutput[]>([]);
  const [loading, setLoading] = useState(true);

  const setPlatformGroupList = useCallback((groups: PlatformGroupOutput[]) => {
    setPlatformGroups(groups);
  }, []);

  const fetchPlatformGroups = useCallback(
    async (input: SearchPaginationInput) => {
      setLoading(true);
      try {
        return await searchPlatformGroups(input);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  const addPlatformGroup = useCallback((group: PlatformGroupOutput) => {
    setPlatformGroups(prev => [group, ...prev]);
  }, []);

  const updatePlatformGroupInList = useCallback((group: PlatformGroupOutput) => {
    setPlatformGroups(prev =>
      prev.map(g =>
        g.platform_group_id === group.platform_group_id ? group : g,
      ),
    );
  }, []);

  const removePlatformGroup = useCallback((groupId: string) => {
    setPlatformGroups(prev => prev.filter(g => g.platform_group_id !== groupId));
  }, []);

  return {
    platformGroups,
    setPlatformGroupList,
    loading,
    fetchPlatformGroups,
    addPlatformGroup,
    updatePlatformGroupInList,
    removePlatformGroup,
  };
};

export default usePlatformGroups;

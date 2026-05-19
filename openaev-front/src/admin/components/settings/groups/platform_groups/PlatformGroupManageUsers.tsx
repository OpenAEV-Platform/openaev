import { type FC, useEffect, useState } from 'react';

import { fetchPlatformGroupUserIds } from '../../../../../actions/platform/platform-group/platform-group-action';
import { findPlatformUsers, searchPlatformUsers } from '../../../../../actions/platform/users/platform-user-action';
import GroupManageUsers from '../tenant_groups/GroupManageUsers';

interface Props {
  platformGroupId: string;
  groupName: string;
  open: boolean;
  onClose: () => void;
  onSubmit: (userIds: string[]) => void;
}

const PlatformGroupManageUsers: FC<Props> = ({
  platformGroupId,
  groupName,
  open,
  onClose,
  onSubmit,
}) => {
  const [userIds, setUserIds] = useState<string[]>([]);

  useEffect(() => {
    if (open) {
      fetchPlatformGroupUserIds(platformGroupId).then(
        (result: { data: string[] }) => {
          setUserIds(result.data ?? []);
        },
      );
    }
  }, [open, platformGroupId]);

  return (
    <GroupManageUsers
      initialState={userIds}
      groupName={groupName}
      open={open}
      onClose={onClose}
      onSubmit={onSubmit}
      searchUsersFn={searchPlatformUsers}
      findUsersFn={findPlatformUsers}
    />
  );
};

export default PlatformGroupManageUsers;

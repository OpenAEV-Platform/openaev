import { type FC, useEffect, useState } from 'react';

import { fetchPlatformGroupRoleIds, fetchPlatformGroupUserIds } from '../../../../../actions/platform/platform-group/platform-group-action';
import { findPlatformUsers, searchPlatformUsers } from '../../../../../actions/platform/users/platform-user-action';
import RoleScopeProvider from '../../roles/RoleScopeProvider';
import GroupManageUsers from '../GroupManageUsers';

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
  const [roleIds, setRoleIds] = useState<string[]>([]);

  useEffect(() => {
    if (open) {
      fetchPlatformGroupUserIds(platformGroupId).then((result: { data: string[] }) => {
        setUserIds(result.data ?? []);
      });
      fetchPlatformGroupRoleIds(platformGroupId).then((result: { data: string[] }) => {
        setRoleIds(result.data ?? []);
      });
    }
  }, [open, platformGroupId]);

  return (
    <RoleScopeProvider scope="PLATFORM">
      <GroupManageUsers
        initialState={userIds}
        groupRoleIds={roleIds}
        groupName={groupName}
        open={open}
        onClose={onClose}
        onSubmit={onSubmit}
        searchUsersFn={searchPlatformUsers}
        findUsersFn={findPlatformUsers}
      />
    </RoleScopeProvider>
  );
};

export default PlatformGroupManageUsers;

import { type FC, useEffect, useState } from 'react';

import { fetchPlatformGroupUserIds } from '../../../../../actions/platform/platform-group/platform-group-action';
import GroupManageUsers from '../../../settings/groups/GroupManageUsers';

interface Props {
  platformGroupId: string;
  open: boolean;
  onClose: () => void;
  onSubmit: (userIds: string[]) => void;
}

const PlatformGroupManageUsers: FC<Props> = ({
  platformGroupId,
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
      open={open}
      onClose={onClose}
      onSubmit={onSubmit}
    />
  );
};

export default PlatformGroupManageUsers;

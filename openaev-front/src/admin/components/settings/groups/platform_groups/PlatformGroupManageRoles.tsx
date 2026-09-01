import { type FC, useEffect, useState } from 'react';

import { fetchPlatformGroupRoleIds } from '../../../../../actions/platform/platform-group/platform-group-action';
import { useFormatter } from '../../../../../components/i18n';
import RoleScopeProvider from '../../roles/RoleScopeProvider';
import GroupManageRoles from '../GroupManageRoles';

interface Props {
  platformGroupId: string;
  open: boolean;
  onClose: () => void;
  onSubmit: (roleIds: string[]) => void;
}

const PlatformGroupManageRoles: FC<Props> = ({
  platformGroupId,
  open,
  onClose,
  onSubmit,
}) => {
  const { t } = useFormatter();

  const [roleIds, setRoleIds] = useState<string[]>([]);

  useEffect(() => {
    if (open) {
      fetchPlatformGroupRoleIds(platformGroupId).then(
        (result: { data: string[] }) => setRoleIds(result.data ?? []),
      );
    }
  }, [open, platformGroupId]);

  return (
    <RoleScopeProvider scope="PLATFORM">
      <GroupManageRoles
        initialState={roleIds}
        title={t('Manage the platform roles of this group')}
        open={open}
        onClose={onClose}
        onSubmit={onSubmit}
      />
    </RoleScopeProvider>
  );
};

export default PlatformGroupManageRoles;

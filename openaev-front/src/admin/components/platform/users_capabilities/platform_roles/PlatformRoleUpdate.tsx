import { type FunctionComponent, useCallback, useEffect, useMemo, useState } from 'react';

import { fetchPlatformRoleCapabilities, updatePlatformRole } from '../../../../../actions/platform/platform-role/platform-role-action';
import { PLATFORM_ROLE_SCHEMA_KEY } from '../../../../../actions/platform/platform-role/platform-role-schema';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import type { PlatformRoleInput, PlatformRoleOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import PlatformRoleForm from './PlatformRoleForm';

interface Props {
  platformRole: PlatformRoleOutput;
  open: boolean;
  onClose: () => void;
  onUpdate?: (result: PlatformRoleOutput) => void;
}

const PlatformRoleUpdate: FunctionComponent<Props> = ({
  platformRole,
  open,
  onClose,
  onUpdate,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  // Capabilities (loaded separately)
  const [capabilities, setCapabilities] = useState<string[]>([]);
  const [loadingCapabilities, setLoadingCapabilities] = useState(true);

  useEffect(() => {
    if (open) {
      setLoadingCapabilities(true);
      fetchPlatformRoleCapabilities(platformRole.platform_role_id).then(
        (result: { data: string[] }) => {
          setCapabilities(result.data ?? []);
          setLoadingCapabilities(false);
        },
      );
    }
  }, [open, platformRole.platform_role_id]);

  // Form

  const initialValues = useMemo<PlatformRoleInput>(
    () => ({
      platform_role_name: platformRole.platform_role_name,
      platform_role_description: platformRole.platform_role_description ?? '',
      platform_role_capabilities: capabilities as PlatformRoleInput['platform_role_capabilities'],
    }),
    [platformRole, capabilities],
  );

  const handleSubmit = useCallback(
    async (data: PlatformRoleInput) => {
      const result = await dispatch(updatePlatformRole(platformRole.platform_role_id, data));

      if (!result?.result) {
        return;
      }

      const updatedPlatformRole = result.entities[PLATFORM_ROLE_SCHEMA_KEY][result.result];
      onUpdate?.(updatedPlatformRole);
      onClose();
    },
    [dispatch, platformRole.platform_role_id, onUpdate, onClose],
  );

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Update platform role')}
    >
      {loadingCapabilities ? (
        <Loader />
      ) : (
        <PlatformRoleForm
          initialValues={initialValues}
          editing
          onSubmit={handleSubmit}
          onCancel={onClose}
        />
      )}
    </Drawer>
  );
};

export default PlatformRoleUpdate;

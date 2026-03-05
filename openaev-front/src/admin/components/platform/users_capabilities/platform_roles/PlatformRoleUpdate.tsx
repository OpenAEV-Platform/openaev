import { type FunctionComponent, useCallback, useMemo } from 'react';

import { updatePlatformRole } from '../../../../../actions/platform/platform-role/platform-role-action';
import type { PlatformRoleInput, PlatformRoleOutput } from '../../../../../utils/api-types';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import { useAppDispatch } from '../../../../../utils/hooks';
import PlatformRoleForm from './PlatformRoleForm';
import {PLATFORM_ROLE_SCHEMA_KEY} from "../../../../../actions/platform/platform-role/platform-role-schema";

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

  // Form

  const initialValues = useMemo<PlatformRoleInput>(
    () => ({
      platform_role_name: platformRole.platform_role_name,
      platform_role_description: platformRole.platform_role_description ?? '',
      platform_role_capabilities: platformRole.platform_role_capabilities ?? [],
    }),
    [platformRole],
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
      <PlatformRoleForm
        initialValues={initialValues}
        editing
        onSubmit={handleSubmit}
        onCancel={onClose}
      />
    </Drawer>
  );
};

export default PlatformRoleUpdate;



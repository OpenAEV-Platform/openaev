import { type FunctionComponent, useCallback, useMemo } from 'react';

import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { RoleInput, RoleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import RoleForm from './RoleForm';
import { useRoleScope } from './RoleScopeContext';

interface Props {
  role: RoleOutput;
  open: boolean;
  onClose: () => void;
  onUpdate?: (result: RoleOutput) => void;
}

const RoleUpdate: FunctionComponent<Props> = ({
  role,
  open,
  onClose,
  onUpdate,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { update, schemaKey } = useRoleScope();

  const initialValues = useMemo<RoleInput>(
    () => ({
      role_name: role.role_name,
      role_description: role.role_description ?? '',
      role_capabilities: role.role_capabilities ?? [],
    }),
    [role],
  );

  const handleSubmit = useCallback(
    async (data: RoleInput) => {
      const result = await dispatch(update(role.role_id, data));

      if (!result?.result) {
        return result;
      }

      onUpdate?.(result.entities[schemaKey][result.result]);
      onClose();

      return result;
    },
    [dispatch, update, schemaKey, role.role_id, onUpdate, onClose],
  );

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={`${t('Update')} ${role.role_name}`}
    >
      <RoleForm
        initialValues={initialValues}
        editing
        onSubmit={handleSubmit}
        onCancel={onClose}
      />
    </Drawer>
  );
};

export default RoleUpdate;

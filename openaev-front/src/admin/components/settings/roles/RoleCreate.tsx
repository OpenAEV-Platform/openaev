import { type FunctionComponent, useCallback, useContext } from 'react';

import ButtonCreate from '../../../../components/common/ButtonCreate';
import useDialog from '../../../../components/common/dialog/useDialog';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import type { RoleInput, RoleOutput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, PERMISSION_REQUIRED } from '../../../../utils/permissions/types';
import RoleForm from './RoleForm';
import { useRoleScope } from './RoleScopeContext';

interface Props {
  /** Drawer title, so the caller names the role it creates. */
  title: string;
  onCreate?: (result: RoleOutput) => void;
  /** Additional reason to grey out the button, on top of the MANAGE capability checked here. */
  disabled?: boolean;
  disabledMessage?: string;
}

const RoleCreate: FunctionComponent<Props> = ({
  title,
  onCreate,
  disabled,
  disabledMessage,
}) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { open, handleOpen, handleClose } = useDialog();
  const ability = useContext(AbilityContext);
  const { create, schemaKey, subject } = useRoleScope();

  const canManage = ability.can(ACTIONS.MANAGE, subject);

  const handleSubmit = useCallback(
    async (data: RoleInput) => {
      const result = await dispatch(create(data));

      if (!result?.result) {
        return result;
      }

      onCreate?.(result.entities[schemaKey][result.result]);
      handleClose();

      return result;
    },
    [dispatch, create, schemaKey, onCreate, handleClose],
  );

  return (
    <>
      <ButtonCreate
        onClick={handleOpen}
        disabled={disabled || !canManage}
        disabledMessage={disabledMessage ?? PERMISSION_REQUIRED}
      />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t(title)}
      >
        <RoleForm
          onSubmit={handleSubmit}
          onCancel={handleClose}
        />
      </Drawer>
    </>
  );
};

export default RoleCreate;

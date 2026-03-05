import { type FunctionComponent, useCallback } from 'react';

import { addPlatformRole } from '../../../../../actions/platform/platform-role/platform-role-action';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import Drawer from '../../../../../components/common/Drawer';
import useDialog from '../../../../../components/common/dialog/useDialog';
import { useFormatter } from '../../../../../components/i18n';
import type {PlatformRoleInput, PlatformRoleOutput, UserOutput} from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import PlatformRoleForm from './PlatformRoleForm';
import {PLATFORM_ROLE_SCHEMA_KEY} from "../../../../../actions/platform/platform-role/platform-role-schema";

interface Props { onCreate: (result: UserOutput) => void }

const PlatformRoleCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { open, handleOpen, handleClose } = useDialog();

  const handleSubmit = useCallback(
    async (data: PlatformRoleInput) => {
      const result = await dispatch(addPlatformRole(data));

      if (!result?.result) {
        return result;
      }

      const createdPlatformRole = result.entities[PLATFORM_ROLE_SCHEMA_KEY][result.result];
      onCreate(createdPlatformRole);
      handleClose();

      return result;
    },
    [dispatch, onCreate, handleClose],
  );

  return (
    <>
      <ButtonCreate onClick={handleOpen} variant={"rightMenu"}/>
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a platform role')}
      >
        <PlatformRoleForm
          onSubmit={handleSubmit}
          onCancel={handleClose}
        />
      </Drawer>
    </>
  );
};

export default PlatformRoleCreate;


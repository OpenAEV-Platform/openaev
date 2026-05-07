import { type FunctionComponent, useCallback } from 'react';

import { addPlatformGroup } from '../../../../../actions/platform/platform-group/platform-group-action';
import { PLATFORM_GROUP_SCHEMA_KEY } from '../../../../../actions/platform/platform-group/platform-group-schema';
import ButtonCreate from '../../../../../components/common/ButtonCreate';
import useDialog from '../../../../../components/common/dialog/useDialog';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type { PlatformGroupInput, PlatformGroupOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import PlatformGroupForm from './PlatformGroupForm';

interface Props { onCreate: (result: PlatformGroupOutput) => void }

const PlatformGroupCreate: FunctionComponent<Props> = ({ onCreate }) => {
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { open, handleOpen, handleClose } = useDialog();

  const handleSubmit = useCallback(
    async (data: PlatformGroupInput) => {
      const result = await dispatch(addPlatformGroup(data));

      if (!result?.result) {
        return result;
      }

      const createdPlatformGroup = result.entities[PLATFORM_GROUP_SCHEMA_KEY][result.result];
      onCreate(createdPlatformGroup);
      handleClose();

      return result;
    },
    [dispatch, onCreate, handleClose],
  );

  return (
    <>
      <ButtonCreate onClick={handleOpen} variant="rightMenu" />
      <Drawer
        open={open}
        handleClose={handleClose}
        title={t('Create a platform group')}
      >
        <PlatformGroupForm
          onSubmit={handleSubmit}
          onCancel={handleClose}
        />
      </Drawer>
    </>
  );
};

export default PlatformGroupCreate;

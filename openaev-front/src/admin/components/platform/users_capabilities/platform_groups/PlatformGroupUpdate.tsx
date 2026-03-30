import { type FunctionComponent, useCallback, useMemo } from 'react';

import { updatePlatformGroup } from '../../../../../actions/platform/platform-group/platform-group-action';
import { PLATFORM_GROUP_SCHEMA_KEY } from '../../../../../actions/platform/platform-group/platform-group-schema';
import Drawer from '../../../../../components/common/Drawer';
import { useFormatter } from '../../../../../components/i18n';
import type { PlatformGroupInput, PlatformGroupOutput } from '../../../../../utils/api-types';
import { useAppDispatch } from '../../../../../utils/hooks';
import PlatformGroupForm from './PlatformGroupForm';

interface Props {
  platformGroup: PlatformGroupOutput;
  open: boolean;
  onClose: () => void;
  onUpdate?: (result: PlatformGroupOutput) => void;
}

const PlatformGroupUpdate: FunctionComponent<Props> = ({
  platformGroup,
  open,
  onClose,
  onUpdate,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  // Form

  const initialValues = useMemo<PlatformGroupInput>(
    () => ({
      platform_group_name: platformGroup.platform_group_name,
      platform_group_description: platformGroup.platform_group_description ?? '',
    }),
    [platformGroup],
  );

  const handleSubmit = useCallback(
    async (data: PlatformGroupInput) => {
      const result = await dispatch(updatePlatformGroup(platformGroup.platform_group_id, data));

      if (!result?.result) {
        return;
      }

      const updatedPlatformGroup = result.entities[PLATFORM_GROUP_SCHEMA_KEY][result.result];
      onUpdate?.(updatedPlatformGroup);
      onClose();
    },
    [dispatch, platformGroup.platform_group_id, onUpdate, onClose],
  );

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={t('Update platform group')}
    >
      <PlatformGroupForm
        initialValues={initialValues}
        editing
        onSubmit={handleSubmit}
        onCancel={onClose}
      />
    </Drawer>
  );
};

export default PlatformGroupUpdate;

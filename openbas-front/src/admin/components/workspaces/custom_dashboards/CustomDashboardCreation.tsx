import { type FunctionComponent, useCallback, useState } from 'react';

import { updatePlatformParameters } from '../../../../actions/Application';
import { createCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import type { LoggedHelper } from '../../../../actions/helper';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import type { CustomDashboard, PlatformSettings } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import CustomDashboardForm, { type CustomDashboardFormType } from './CustomDashboardForm';
import { updateDefaultDashboardsInParameters } from './customDashboardUtils';

interface Props { onCreate: (result: CustomDashboard) => void }

const CustomDashboardCreation: FunctionComponent<Props> = ({ onCreate }) => {
  // Standard hooks
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));

  // Drawer
  const [open, setOpen] = useState(false);

  // Form
  const onSubmit = useCallback(
    async (data: CustomDashboardFormType) => {
      try {
        const response = await createCustomDashboard(data);
        if (response.data) {
          updateDefaultDashboardsInParameters(response.data.custom_dashboard_id, data, settings, updatedSettings => dispatch(updatePlatformParameters(updatedSettings)));
          onCreate(response.data);
        }
      } finally {
        setOpen(false);
      }
    },
    [onCreate],
  );

  return (
    <>
      <ButtonCreate onClick={() => setOpen(true)} />
      <Drawer
        open={open}
        handleClose={() => setOpen(false)}
        title={t('Create a custom dashboard')}
      >
        <CustomDashboardForm onSubmit={onSubmit} handleClose={() => setOpen(false)} />
      </Drawer>
    </>
  );
};

export default CustomDashboardCreation;

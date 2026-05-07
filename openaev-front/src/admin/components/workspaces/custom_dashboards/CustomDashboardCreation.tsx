import { type FunctionComponent, useCallback, useState } from 'react';
import { useNavigate } from 'react-router';

import { createCustomDashboard } from '../../../../actions/custom_dashboards/customdashboard-action';
import type { LoggedHelper } from '../../../../actions/helper';
import { updateTenantSettings } from '../../../../actions/settings/tenant-settings-action';
import ButtonCreate from '../../../../components/common/ButtonCreate';
import Drawer from '../../../../components/common/Drawer';
import { useFormatter } from '../../../../components/i18n';
import { DASHBOARD_BASE_URL } from '../../../../constants/BaseUrls';
import { useHelper } from '../../../../store';
import type { TenantSettingsOutput } from '../../../../utils/api-types';
import CustomDashboardForm, { type CustomDashboardFormType } from './CustomDashboardForm';
import updateDefaultDashboardsInParameters from './customDashboardUtils';

const CustomDashboardCreation: FunctionComponent = () => {
  // Standard hooks
  const { t } = useFormatter();
  const navigate = useNavigate();
  const { settings }: { settings: TenantSettingsOutput } = useHelper((helper: LoggedHelper) => ({ settings: helper.getTenantSettings() }));

  // Drawer
  const [open, setOpen] = useState(false);

  // Form
  const onSubmit = useCallback(
    async (data: CustomDashboardFormType) => {
      try {
        const response = await createCustomDashboard(data);
        if (response.data) {
          updateDefaultDashboardsInParameters(response.data.custom_dashboard_id, data, settings, updatedSettings => updateTenantSettings(updatedSettings));
          navigate(`${DASHBOARD_BASE_URL}/${response.data.custom_dashboard_id}`);
        }
      } finally {
        setOpen(false);
      }
    },
    [],
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

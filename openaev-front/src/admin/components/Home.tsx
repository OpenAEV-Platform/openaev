import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { fetchPlatformParameters } from '../../actions/Application';
import type { LoggedHelper } from '../../actions/helper';
import {
  fetchTenantHomeDashboard,
  fetchTenantSettings,
  tenantHomeDashboardAttackPaths,
  tenantHomeDashboardAverage,
  tenantHomeDashboardCount,
  tenantHomeDashboardEntities,
  tenantHomeDashboardSeries,
  tenantHomeWidgetToEntitiesRuntime,
  updateTenantSettings,
} from '../../actions/settings/tenant-settings-action';
import { useFormatter } from '../../components/i18n';
import { useHelper } from '../../store';
import { type TenantSettingsOutput } from '../../utils/api-types';
import { useAppDispatch } from '../../utils/hooks';
import useDataLoader from '../../utils/hooks/useDataLoader';
import { Can } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { XTM_HUB_PERMISSION_REQUIRED_QUERY_PARAM } from './RedirectByPath';
import CustomDashboardWrapper from './workspaces/custom_dashboards/CustomDashboardWrapper';
import NoDashboardComponent from './workspaces/custom_dashboards/NoDashboardComponent';
import SelectDashboardButton from './workspaces/custom_dashboards/SelectDashboardButton';

const Home = () => {
  const { t } = useFormatter();
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [isPermissionDialogOpen, setIsPermissionDialogOpen] = useState(false);
  const { tenantSettings }: { tenantSettings: TenantSettingsOutput } = useHelper((helper: LoggedHelper) => ({ tenantSettings: helper.getTenantSettings() }));

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
    dispatch(fetchTenantSettings());
  });

  useEffect(() => {
    const searchParams = new URLSearchParams(location.search);
    if (searchParams.get(XTM_HUB_PERMISSION_REQUIRED_QUERY_PARAM) !== 'true') {
      return;
    }
    setIsPermissionDialogOpen(true);
    searchParams.delete(XTM_HUB_PERMISSION_REQUIRED_QUERY_PARAM);
    const targetSearch = searchParams.toString();
    navigate(
      {
        pathname: location.pathname,
        search: targetSearch ? `?${targetSearch}` : '',
      },
      { replace: true },
    );
  }, [location.pathname, location.search, navigate]);

  const handleSelectNewDashboard = async (dashboardId: string) => {
    await updateTenantSettings({
      ...tenantSettings,
      platform_home_dashboard: dashboardId,
    });
    dispatch(fetchTenantSettings());
  };

  const configuration = {
    customDashboardId: tenantSettings.platform_home_dashboard,
    paramLocalStorageKey: 'custom-dashboard-home',
    fetchCustomDashboard: fetchTenantHomeDashboard,
    fetchCount: tenantHomeDashboardCount,
    fetchAverage: tenantHomeDashboardAverage,
    fetchSeries: tenantHomeDashboardSeries,
    fetchEntities: tenantHomeDashboardEntities,
    fetchEntitiesRuntime: tenantHomeWidgetToEntitiesRuntime,
    fetchAttackPaths: tenantHomeDashboardAttackPaths,
  };

  return (
    <>
      <Dialog
        open={isPermissionDialogOpen}
        onClose={() => setIsPermissionDialogOpen(false)}
        aria-labelledby="xtm-hub-permission-required-title"
      >
        <DialogTitle id="xtm-hub-permission-required-title">{t('Permission required')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('You do not have permission to connect this product. Please contact your product administrator to connect the product on your behalf.')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIsPermissionDialogOpen(false)}>{t('Close')}</Button>
        </DialogActions>
      </Dialog>
      <CustomDashboardWrapper
        configuration={configuration}
        noDashboardSlot={(
          <NoDashboardComponent
            actionComponent={(
              <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
                <SelectDashboardButton
                  variant="text"
                  handleApplyChange={handleSelectNewDashboard}
                />
              </Can>
            )}
          />
        )}
      />
    </>
  );
};

export default Home;

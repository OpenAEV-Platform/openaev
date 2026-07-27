import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';
import { useContext } from 'react';

import { fetchPlatformParameters } from '../../../../actions/Application';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { AbilityContext } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import EnterpriseEditionSettings from './EnterpriseEditionSettings';
import XtmHubSettings from './xtm_hub/XtmHubSettings';

const Experience: React.FC = () => {
  const theme = useTheme();
  const { t } = useFormatter();
  const dispatch = useAppDispatch();
  const ability = useContext(AbilityContext);
  const canAccessPlatformSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.PLATFORM_SETTINGS);
  const canAccessTenantSettings = ability.can(ACTIONS.ACCESS, SUBJECTS.TENANT_SETTINGS);

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
  });

  return (
    <div data-testid="experience-page">
      <Breadcrumbs
        style={{ marginBottom: theme.spacing(2.4) }}
        variant="list"
        elements={[{ label: t(SETTINGS_LABEL) }, {
          label: t('Filigran Experience'),
          current: true,
        }]}
      />

      <Grid container spacing={3} alignItems="stretch">
        {canAccessPlatformSettings && (
          <Grid size={6}>
            <EnterpriseEditionSettings />
          </Grid>
        )}

        {canAccessTenantSettings && (
          <Grid size={6}>
            <XtmHubSettings />
          </Grid>
        )}
      </Grid>
    </div>
  );
};

export default Experience;

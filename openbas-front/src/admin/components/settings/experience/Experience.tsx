import { Grid } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

import { fetchPlatformParameters } from '../../../../actions/Application';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import EnterpriseEditionSettings from './EnterpriseEditionSettings';
import XtmHubSettings from './xtm_hub/XtmHubSettings';

const Experience: React.FC = () => {
  const theme = useTheme();

  const { t } = useFormatter();
  const dispatch = useAppDispatch();

  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
  });

  return (
    <>
      <Breadcrumbs
        style={{ marginBottom: theme.spacing(2.4) }}
        variant="list"
        elements={[{ label: t('Settings') }, {
          label: t('Filigran Experience'),
          current: true,
        }]}
      />

      <Grid container spacing={3}>
        <EnterpriseEditionSettings />

        <Grid container flexDirection="column" gap="0" size={6}>
          <XtmHubSettings />
        </Grid>
      </Grid>
    </>
  );
};

export default Experience;

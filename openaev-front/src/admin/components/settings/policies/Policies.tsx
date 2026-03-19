import { GridLegacy, Paper, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { fetchPlatformParameters, updatePlatformPolicies } from '../../../../actions/Application';
import { type LoggedHelper } from '../../../../actions/helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type PlatformSettings, type PolicyInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import SecurityMenu from '../SecurityMenu';
import PolicyForm from './PolicyForm';

const useStyles = makeStyles()(theme => ({
  paper: {
    height: '100%',
    minHeight: '100%',
    margin: '10px 0 0 0',
    padding: 20,
    borderRadius: theme.borderRadius,
  },
}));

const Policies: FunctionComponent = () => {
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  const { t } = useFormatter();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  useDataLoader(() => {
    dispatch(fetchPlatformParameters());
  });

  const initialValues = {
    platform_login_message: settings.platform_policies?.platform_login_message || '',
    platform_consent_message: settings.platform_policies?.platform_consent_message || '',
    platform_consent_confirm_text: settings.platform_policies?.platform_consent_confirm_text || '',
  };

  const onUpdate = (data: PolicyInput) => {
    dispatch(updatePlatformPolicies(data));
  };

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }}>
        <Breadcrumbs
          variant="list"
          elements={[{ label: t('Settings') }, { label: t('Security') }, {
            label: t('Policies'),
            current: true,
          }]}
        />
        <GridLegacy item={true} xs={6} style={{ marginTop: 30 }}>
          <Typography variant="h4" gutterBottom={true}>
            {t('Login messages')}
          </Typography>
          <Paper classes={{ root: classes.paper }} variant="outlined">
            <PolicyForm onSubmit={onUpdate} initialValues={initialValues}></PolicyForm>
          </Paper>
        </GridLegacy>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Policies;

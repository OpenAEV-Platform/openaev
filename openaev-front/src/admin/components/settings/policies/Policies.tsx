import { Paper } from '@filigran/design-system';
import { GridLegacy } from '@mui/material';
import { type FunctionComponent } from 'react';

import { fetchPlatformParameters, updatePlatformPolicies } from '../../../../actions/Application';
import { type LoggedHelper } from '../../../../actions/helper';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { SectionLabel } from '../../../../components/common/detail/EntityDetailCommon';
import { useFormatter } from '../../../../components/i18n';
import { useHelper } from '../../../../store';
import { type PlatformSettings, type PolicyInput } from '../../../../utils/api-types';
import { useAppDispatch } from '../../../../utils/hooks';
import useDataLoader from '../../../../utils/hooks/useDataLoader';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import SecurityMenu from '../SecurityMenu';
import PolicyForm from './PolicyForm';

const Policies: FunctionComponent = () => {
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
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Security') }, {
            label: t('Policies'),
            current: true,
          }]}
        />
        <GridLegacy item={true} xs={6} style={{ marginTop: 30 }}>
          <SectionLabel>{t('Login messages')}</SectionLabel>
          <Paper
            padding={16}
            style={{
              height: '100%',
              minHeight: '100%',
              margin: '10px 0 0 0',
            }}
          >
            <PolicyForm onSubmit={onUpdate} initialValues={initialValues}></PolicyForm>
          </Paper>
        </GridLegacy>
      </div>
      <SecurityMenu />
    </div>
  );
};

export default Policies;

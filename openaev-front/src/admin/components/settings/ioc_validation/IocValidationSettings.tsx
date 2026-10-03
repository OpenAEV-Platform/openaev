import { Button, Checkbox, Input, Paper, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue, Text } from '@filigran/design-system';
import { Alert } from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import { searchAssetGroupAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { fetchIocValidationSettings, updateIocValidationSettings } from '../../../../actions/ioc_validations/ioc-validation-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import type { IocValidationSettingsInput, IocValidationSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import {
  IOC_VALIDATION_TEST_KIND_DESCRIPTIONS,
  IOC_VALIDATION_TEST_KINDS,
  type IocValidationTestKind,
  iocValidationTestKindLabel,
  validateIocValidationSettings,
} from '../../ioc_validations/iocValidationUtils';
import { SETTINGS_LABEL } from '../../nav/config/settings.config';
import CustomizationMenu from '../CustomizationMenu';

interface AssetGroupOption {
  id: string;
  label: string;
}

const NO_ASSET_GROUP = '__none__';

const toInput = (settings: IocValidationSettingsOutput): IocValidationSettingsInput => ({
  ioc_validation_allowed_test_kinds: settings.ioc_validation_allowed_test_kinds,
  ioc_validation_http_proxy_url: settings.ioc_validation_http_proxy_url ?? '',
  ioc_validation_sinkhole_address: settings.ioc_validation_sinkhole_address ?? '',
  ioc_validation_network_port: settings.ioc_validation_network_port,
  ioc_validation_asset_group_id: settings.ioc_validation_asset_group_id ?? '',
});

const IocValidationSettings = () => {
  const { t } = useFormatter();
  const [settings, setSettings] = useState<IocValidationSettingsOutput | null>(null);
  const [form, setForm] = useState<IocValidationSettingsInput | null>(null);
  const [portText, setPortText] = useState('');
  const [assetGroups, setAssetGroups] = useState<AssetGroupOption[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    fetchIocValidationSettings().then((result: { data: IocValidationSettingsOutput }) => {
      setSettings(result.data);
      setForm(toInput(result.data));
      setPortText(String(result.data.ioc_validation_network_port));
    });
    searchAssetGroupAsOption('').then((result: { data: AssetGroupOption[] }) => setAssetGroups(result.data ?? []));
  }, []);

  const errors = useMemo(() => (form ? validateIocValidationSettings(form, portText) : {}), [form, portText]);

  if (!settings || !form) {
    return <Loader />;
  }

  const update = (patch: Partial<IocValidationSettingsInput>) => setForm({
    ...form,
    ...patch,
  });

  const toggleKind = (kind: IocValidationTestKind) => {
    const kinds = form.ioc_validation_allowed_test_kinds;
    update({ ioc_validation_allowed_test_kinds: kinds.includes(kind) ? kinds.filter(k => k !== kind) : IOC_VALIDATION_TEST_KINDS.filter(k => k === kind || kinds.includes(k)) });
  };

  const save = () => {
    setSaving(true);
    updateIocValidationSettings({
      ...form,
      ioc_validation_network_port: Number(portText),
    })
      .then((result: { data: IocValidationSettingsOutput }) => {
        setSettings(result.data);
        setForm(toInput(result.data));
        setPortText(String(result.data.ioc_validation_network_port));
        MESSAGING$.notifySuccess(t('IOC validation settings saved'));
      })
      .finally(() => setSaving(false));
  };

  const contactsInfrastructure = form.ioc_validation_allowed_test_kinds.some(kind => kind !== 'DNS_RESOLUTION' && kind !== 'LOG_INJECTION' && kind !== 'FILE_DROP');
  const hasErrors = Object.keys(errors).length > 0;

  return (
    <div style={{ display: 'flex' }}>
      <div style={{ flexGrow: 1 }} data-testid="ioc-validation-settings">
        <Breadcrumbs
          variant="list"
          elements={[{ label: t(SETTINGS_LABEL) }, { label: t('Customization') }, {
            label: t('IOC validation'),
            current: true,
          }]}
        />
        {!settings.ioc_validation_opencti_enabled && (
          <Alert severity="info" variant="outlined" style={{ marginBottom: 16 }}>
            {t('No OpenCTI connection is configured for this tenant: OpenCTI cannot send IOC validation requests yet.')}
          </Alert>
        )}
        {settings.ioc_validation_opencti_enabled && !settings.ioc_validation_connector_registered && (
          <Alert severity="warning" variant="outlined" style={{ marginBottom: 16 }}>
            {t('The IOC validation connector is not registered in OpenCTI yet: requests and results wait until it is.')}
          </Alert>
        )}
        <div style={{
          display: 'grid',
          gap: 16,
        }}
        >
          <Paper padding={16} title={t('Allowed test kinds')}>
            <Text
              variant="content-compact"
              className="text-default-secondary"
              style={{
                display: 'block',
                marginBottom: 12,
              }}
            >
              {t('Every request needs an approval, and only the test kinds allowed here run. DNS resolution never connects to the indicator.')}
            </Text>
            <div style={{
              display: 'grid',
              gap: 8,
            }}
            >
              {IOC_VALIDATION_TEST_KINDS.map(kind => (
                <Checkbox
                  key={kind}
                  label={t(iocValidationTestKindLabel(kind))}
                  description={t(IOC_VALIDATION_TEST_KIND_DESCRIPTIONS[kind])}
                  checked={form.ioc_validation_allowed_test_kinds.includes(kind)}
                  onCheckedChange={() => toggleKind(kind)}
                  data-testid={`ioc-validation-kind-${kind}`}
                />
              ))}
            </div>
            {contactsInfrastructure && (
              <Alert severity="warning" variant="outlined" style={{ marginTop: 12 }}>
                {t('Network and HTTP tests reach the indicator infrastructure. Use a sinkhole for network tests and an egress proxy you control for HTTP tests.')}
              </Alert>
            )}
          </Paper>
          <Paper padding={16} title={t('Network safety')}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
              gap: 16,
            }}
            >
              <Input
                label={t('Egress proxy URL')}
                placeholder="http://proxy.internal:3128"
                value={form.ioc_validation_http_proxy_url ?? ''}
                onChange={event => update({ ioc_validation_http_proxy_url: event.target.value })}
                error={errors.proxy ? t(errors.proxy) : undefined}
                helperText={t('Required to allow HTTP HEAD tests.')}
              />
              <Input
                label={t('Sinkhole address')}
                placeholder="192.0.2.10"
                value={form.ioc_validation_sinkhole_address ?? ''}
                onChange={event => update({ ioc_validation_sinkhole_address: event.target.value })}
                error={errors.sinkhole ? t(errors.sinkhole) : undefined}
                helperText={t('When set, network tests connect to this address instead of the indicator.')}
              />
              <Input
                label={t('Network test port')}
                type="number"
                value={portText}
                onChange={event => setPortText(event.target.value)}
                error={errors.port ? t(errors.port) : undefined}
              />
            </div>
          </Paper>
          <Paper padding={16} title={t('Targets')}>
            <div style={{ maxWidth: 420 }}>
              <Select
                value={form.ioc_validation_asset_group_id || NO_ASSET_GROUP}
                onValueChange={(value: string) => update({ ioc_validation_asset_group_id: value === NO_ASSET_GROUP ? '' : value })}
              >
                <SelectLabel>{t('Asset group running the tests')}</SelectLabel>
                <SelectTrigger aria-label={t('Asset group running the tests')}>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent aria-label={t('Asset group running the tests')}>
                  <SelectItem value={NO_ASSET_GROUP}>{t('None')}</SelectItem>
                  {assetGroups.map(group => (
                    <SelectItem key={group.id} value={group.id}>{group.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <Text
              variant="content-compact"
              className="text-default-secondary"
              style={{
                display: 'block',
                marginTop: 8,
              }}
            >
              {t('Endpoints of this group run the benign tests; the security platforms of the request evaluate them.')}
            </Text>
          </Paper>
          <div style={{
            display: 'flex',
            justifyContent: 'flex-end',
          }}
          >
            <Can I={ACTIONS.MANAGE} a={SUBJECTS.TENANT_SETTINGS}>
              <Button onClick={save} disabled={saving || hasErrors} data-testid="ioc-validation-settings-save">
                {t('Save')}
              </Button>
            </Can>
          </div>
        </div>
      </div>
      <CustomizationMenu />
    </div>
  );
};

export default IocValidationSettings;

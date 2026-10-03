import { Button, Checkbox, Input, Paper, Select, SelectContent, SelectItem, SelectLabel, SelectTrigger, SelectValue, Text } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert } from '@mui/material';
import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import { searchAssetGroupAsOption } from '../../../../actions/asset_groups/assetgroup-action';
import { fetchIocValidationSettings, updateIocValidationSettings } from '../../../../actions/ioc_validations/ioc-validation-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import type { IocValidationSettingsInput, IocValidationSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { Can } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';
import { zodImplement } from '../../../../utils/Zod';
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

// The port is edited as text so a partial or invalid entry stays visible until it is fixed.
type IocValidationSettingsFormValues = Omit<IocValidationSettingsInput, 'ioc_validation_network_port'> & { ioc_validation_network_port: string };

const NO_ASSET_GROUP = '__none__';

const toFormValues = (settings: IocValidationSettingsOutput): IocValidationSettingsFormValues => ({
  ioc_validation_allowed_test_kinds: settings.ioc_validation_allowed_test_kinds,
  ioc_validation_http_proxy_url: settings.ioc_validation_http_proxy_url ?? '',
  ioc_validation_sinkhole_address: settings.ioc_validation_sinkhole_address ?? '',
  ioc_validation_network_port: String(settings.ioc_validation_network_port),
  ioc_validation_asset_group_id: settings.ioc_validation_asset_group_id ?? '',
});

const toInput = (values: IocValidationSettingsFormValues): IocValidationSettingsInput => ({
  ...values,
  ioc_validation_network_port: Number(values.ioc_validation_network_port),
});

// Same rules as the API, shared with validateIocValidationSettings; messages are i18n keys.
const settingsSchema = zodImplement<IocValidationSettingsFormValues>().with({
  ioc_validation_allowed_test_kinds: z.array(z.custom<IocValidationTestKind>(kind => IOC_VALIDATION_TEST_KINDS.includes(kind as IocValidationTestKind))),
  ioc_validation_asset_group_id: z.string().optional(),
  ioc_validation_http_proxy_url: z.string().optional(),
  ioc_validation_network_port: z.string(),
  ioc_validation_sinkhole_address: z.string().optional(),
}).superRefine((values, ctx) => {
  const errors = validateIocValidationSettings(toInput(values), values.ioc_validation_network_port);
  if (errors.proxy) ctx.addIssue({
    code: 'custom',
    path: ['ioc_validation_http_proxy_url'],
    message: errors.proxy,
  });
  if (errors.sinkhole) ctx.addIssue({
    code: 'custom',
    path: ['ioc_validation_sinkhole_address'],
    message: errors.sinkhole,
  });
  if (errors.port) ctx.addIssue({
    code: 'custom',
    path: ['ioc_validation_network_port'],
    message: errors.port,
  });
});

interface IocValidationSettingsFormProps {
  settings: IocValidationSettingsOutput;
  assetGroups: AssetGroupOption[];
  onSaved: (settings: IocValidationSettingsOutput) => void;
}

const IocValidationSettingsForm = ({ settings, assetGroups, onSaved }: IocValidationSettingsFormProps) => {
  const { t } = useFormatter();
  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isSubmitting, isValid },
  } = useForm<IocValidationSettingsFormValues>({
    mode: 'onChange',
    resolver: zodResolver(settingsSchema),
    defaultValues: toFormValues(settings),
  });

  const allowedKinds = watch('ioc_validation_allowed_test_kinds');
  const contactsInfrastructure = allowedKinds.some(kind => kind !== 'DNS_RESOLUTION' && kind !== 'LOG_INJECTION' && kind !== 'FILE_DROP');

  const save = (values: IocValidationSettingsFormValues) => updateIocValidationSettings(toInput(values))
    .then((result: { data: IocValidationSettingsOutput }) => {
      onSaved(result.data);
      reset(toFormValues(result.data));
      MESSAGING$.notifySuccess(t('IOC validation settings saved'));
    });

  const errorText = (message?: string) => (message ? t(message) : undefined);

  return (
    <form
      onSubmit={handleSubmit(save)}
      style={{
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
        <Controller
          control={control}
          name="ioc_validation_allowed_test_kinds"
          render={({ field }) => (
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
                  checked={field.value.includes(kind)}
                  onCheckedChange={() => field.onChange(field.value.includes(kind)
                    ? field.value.filter(k => k !== kind)
                    : IOC_VALIDATION_TEST_KINDS.filter(k => k === kind || field.value.includes(k)))}
                  data-testid={`ioc-validation-kind-${kind}`}
                />
              ))}
            </div>
          )}
        />
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
          <Controller
            control={control}
            name="ioc_validation_http_proxy_url"
            render={({ field }) => (
              <Input
                label={t('Egress proxy URL')}
                placeholder="http://proxy.internal:3128"
                value={field.value ?? ''}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errorText(errors.ioc_validation_http_proxy_url?.message)}
                helperText={t('Required to allow HTTP HEAD tests.')}
              />
            )}
          />
          <Controller
            control={control}
            name="ioc_validation_sinkhole_address"
            render={({ field }) => (
              <Input
                label={t('Sinkhole address')}
                placeholder="192.0.2.10"
                value={field.value ?? ''}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errorText(errors.ioc_validation_sinkhole_address?.message)}
                helperText={t('When set, network tests connect to this address instead of the indicator.')}
              />
            )}
          />
          <Controller
            control={control}
            name="ioc_validation_network_port"
            render={({ field }) => (
              <Input
                label={t('Network test port')}
                type="number"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                error={errorText(errors.ioc_validation_network_port?.message)}
              />
            )}
          />
        </div>
      </Paper>
      <Paper padding={16} title={t('Targets')}>
        <div style={{ maxWidth: 420 }}>
          <Controller
            control={control}
            name="ioc_validation_asset_group_id"
            render={({ field }) => (
              <Select
                value={field.value || NO_ASSET_GROUP}
                onValueChange={(value: string) => field.onChange(value === NO_ASSET_GROUP ? '' : value)}
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
            )}
          />
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
          <Button type="submit" disabled={isSubmitting || !isValid} data-testid="ioc-validation-settings-save">
            {t('Save')}
          </Button>
        </Can>
      </div>
    </form>
  );
};

const IocValidationSettings = () => {
  const { t } = useFormatter();
  const [settings, setSettings] = useState<IocValidationSettingsOutput | null>(null);
  const [assetGroups, setAssetGroups] = useState<AssetGroupOption[]>([]);

  useEffect(() => {
    fetchIocValidationSettings().then((result: { data: IocValidationSettingsOutput }) => setSettings(result.data));
    searchAssetGroupAsOption('').then((result: { data: AssetGroupOption[] }) => setAssetGroups(result.data ?? []));
  }, []);

  if (!settings) {
    return <Loader />;
  }

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
        <IocValidationSettingsForm settings={settings} assetGroups={assetGroups} onSaved={setSettings} />
      </div>
      <CustomizationMenu />
    </div>
  );
};

export default IocValidationSettings;

import {
  Alert,
  Button,
  Checkbox,
  Combobox,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
  Input,
  Paper,
  Text,
} from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useCallback, useContext, useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';

import {
  fetchIocValidationSettings,
  searchIocValidationAssetGroupOptions,
  updateIocValidationSettings,
} from '../../../../actions/ioc_validations/ioc-validation-actions';
import Breadcrumbs from '../../../../components/Breadcrumbs';
import { useFormatter } from '../../../../components/i18n';
import Loader from '../../../../components/Loader';
import type { IocValidationSettingsInput, IocValidationSettingsOutput } from '../../../../utils/api-types';
import { MESSAGING$ } from '../../../../utils/Environment';
import { AbilityContext, Can } from '../../../../utils/permissions/permissionsContext';
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

export const IOC_VALIDATION_DOCUMENTATION_URL = 'https://docs.openaev.io/latest/usage/build/scenario/ioc-validation/';
const IOC_VALIDATION_CONFIGURATION_URL = `${IOC_VALIDATION_DOCUMENTATION_URL}#configure-ioc-validation`;
const OPENCTI_CONNECTION_DOCUMENTATION_URL = 'https://docs.openaev.io/latest/usage/evaluate/xtm-suite-connector/#step-1-configure-openaev-to-connect-to-opencti';

interface AssetGroupOption {
  id: string;
  label: string;
}

// The port is edited as text so a partial or invalid entry stays visible until it is fixed.
type IocValidationSettingsFormValues = Omit<IocValidationSettingsInput, 'ioc_validation_network_port'> & { ioc_validation_network_port: string };

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

const ASSET_GROUP_SEARCH_DELAY_MS = 300;
const NETWORK_FIELD_MIN_WIDTH = 260;
const NETWORK_FIELD_GAP = 16;

interface AssetGroupFieldProps {
  value: string;
  onChange: (assetGroupId: string) => void;
  onBlur: () => void;
}

// Searched by name on the API (bounded results, configured group first), so every group of the tenant is reachable.
export const AssetGroupField = ({ value, onChange, onBlur }: AssetGroupFieldProps) => {
  const { t } = useFormatter();
  const [search, setSearch] = useState('');
  const [options, setOptions] = useState<AssetGroupOption[]>([]);
  const [selected, setSelected] = useState<AssetGroupOption | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    const timer = setTimeout(() => {
      searchIocValidationAssetGroupOptions(search)
        .then((result: { data: AssetGroupOption[] }) => {
          if (cancelled) return;
          const found = result.data ?? [];
          setFailed(false);
          setOptions(found);
          setSelected(current => current ?? found.find(option => option.id === value) ?? null);
        })
        .catch(() => {
          // An empty list would read as "no asset group" instead of a failed lookup
          if (cancelled) return;
          setFailed(true);
          setOptions([]);
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, search ? ASSET_GROUP_SEARCH_DELAY_MS : 0);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [search, attempt]);

  return (
    <Combobox<AssetGroupOption>
      error={failed}
      options={options}
      value={selected}
      onValueChange={(option) => {
        const next = (option as AssetGroupOption | null) ?? null;
        setSelected(next);
        onChange(next?.id ?? '');
      }}
      onInputChange={(input, meta) => {
        if (meta.cause === 'type') setSearch(input);
      }}
      getOptionLabel={option => option.label}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={found => found}
      loading={loading}
      clearable
    >
      <ComboboxLabel>{t('Asset group running the tests')}</ComboboxLabel>
      <ComboboxField>
        <ComboboxInput
          aria-label={t('Asset group running the tests')}
          placeholder={t('Search by name')}
          onBlur={onBlur}
          data-testid="ioc-validation-asset-group"
        />
        <ComboboxControls>
          <ComboboxClear />
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent />
      <ComboboxHelperText data-testid="ioc-validation-asset-group-helper">
        {failed
          ? (
              <span style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
              }}
              >
                {t('The asset groups could not be loaded.')}
                <Button type="button" priority="tertiary" size="sm" onClick={() => setAttempt(current => current + 1)}>
                  {t('Retry')}
                </Button>
              </span>
            )
          : t('Endpoints of this group run the benign tests.')}
      </ComboboxHelperText>
    </Combobox>
  );
};

interface IocValidationSettingsFormProps {
  settings: IocValidationSettingsOutput;
  onSaved: (settings: IocValidationSettingsOutput) => void;
}

const IocValidationSettingsForm = ({ settings, onSaved }: IocValidationSettingsFormProps) => {
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
      <Paper
        padding={16}
        title={t('Allowed test kinds')}
        action={(
          <Button priority="tertiary" size="sm" asChild>
            <a href={IOC_VALIDATION_DOCUMENTATION_URL} target="_blank" rel="noopener noreferrer" data-testid="ioc-validation-learn-more">
              {t('Learn more')}
            </a>
          </Button>
        )}
      >
        <Text
          variant="content-compact"
          className="text-default-secondary"
          style={{
            display: 'block',
            marginBottom: 12,
          }}
        >
          {t('Every request needs an approval, and only the test kinds allowed here run: none is allowed until you choose it.')}
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
          <Alert
            severity="warning"
            title={t('Network and HTTP tests reach the indicator infrastructure. Use a sinkhole for network tests and an egress proxy you control for HTTP tests, set to refuse internal addresses and the hosts of this platform: it resolves each URL again when the test runs.')}
            style={{ marginTop: 12 }}
          />
        )}
        {allowedKinds.includes('DNS_RESOLUTION') && (
          <Alert
            severity="warning"
            title={t('DNS resolution tests never connect to the indicator, but the resolver of the endpoints may query the name servers of its domain, which can tell their owner that the name was looked up. Point the endpoints at a resolver that does not forward to the internet to avoid it.')}
            style={{ marginTop: 12 }}
            data-testid="ioc-validation-dns-warning"
          />
        )}
      </Paper>
      <Paper padding={16} title={t('Network safety')}>
        <div style={{
          display: 'grid',
          gridTemplateColumns: `repeat(auto-fit, minmax(${NETWORK_FIELD_MIN_WIDTH}px, 1fr))`,
          gap: NETWORK_FIELD_GAP,
        }}
        >
          <Controller
            control={control}
            name="ioc_validation_http_proxy_url"
            render={({ field }) => (
              <Input
                label={t('Egress proxy URL')}
                placeholder="http://proxy.example.com:3128"
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
                helperText={t('Replaces the indicator in network tests.')}
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
                helperText={t('TCP port of the network tests, 443 by default.')}
              />
            )}
          />
        </div>
      </Paper>
      <Paper padding={16} title={t('Targets')}>
        {/* The width of one Network safety field while the three share a row (812 px and wider) */}
        <div style={{
          width: `max(${NETWORK_FIELD_MIN_WIDTH}px, calc((100% - ${2 * NETWORK_FIELD_GAP}px) / 3))`,
          maxWidth: '100%',
        }}
        >
          <Controller
            control={control}
            name="ioc_validation_asset_group_id"
            render={({ field }) => (
              <AssetGroupField value={field.value ?? ''} onChange={field.onChange} onBlur={field.onBlur} />
            )}
          />
        </div>
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

const DocumentationButton = ({ href, label }: {
  href: string;
  label: string;
}) => (
  <Button priority="secondary" size="sm" asChild>
    <a href={href} target="_blank" rel="noopener noreferrer">{label}</a>
  </Button>
);

/** What IOC validation still needs on this tenant, each with its next step. */
export const IocValidationReadiness = ({ openctiEnabled, connectorRegistered }: {
  openctiEnabled: boolean;
  connectorRegistered: boolean;
}) => {
  const { t } = useFormatter();
  const ability = useContext(AbilityContext);
  // The OpenCTI connection is part of the platform configuration
  const canConfigurePlatform = ability.can(ACTIONS.MANAGE, SUBJECTS.PLATFORM_SETTINGS);
  if (!openctiEnabled) {
    return (
      <Alert
        severity="info"
        data-testid="ioc-validation-opencti-missing"
        title={t('No OpenCTI connection is configured for this tenant: OpenCTI cannot send IOC validation requests yet.')}
        description={canConfigurePlatform
          ? t('Add the OpenCTI connection of this tenant to the platform configuration (XTM Suite connector), then restart OpenAEV.')
          : t('Ask your administrator to configure the OpenCTI connection of this tenant.')}
        action={canConfigurePlatform
          ? <DocumentationButton href={OPENCTI_CONNECTION_DOCUMENTATION_URL} label={t('Configure the OpenCTI connection')} />
          : undefined}
        style={{ marginBottom: 16 }}
      />
    );
  }
  if (!connectorRegistered) {
    return (
      <Alert
        severity="warning"
        data-testid="ioc-validation-connector-unregistered"
        title={t('The IOC validation connector is not registered in OpenCTI yet: requests and results wait until it is.')}
        description={t('OpenAEV registers it in OpenCTI with the OpenCTI account of the connection. In OpenCTI, give that account the Connector role, with the Update knowledge and Connectors API usage capabilities.')}
        action={<DocumentationButton href={IOC_VALIDATION_CONFIGURATION_URL} label={t('How to configure IOC validation')} />}
        style={{ marginBottom: 16 }}
      />
    );
  }
  return null;
};

const IocValidationSettings = () => {
  const { t } = useFormatter();
  const [settings, setSettings] = useState<IocValidationSettingsOutput | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);

  const load = useCallback(() => {
    setLoadFailed(false);
    fetchIocValidationSettings()
      .then((result: { data: IocValidationSettingsOutput }) => setSettings(result.data))
      .catch(() => setLoadFailed(true));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (loadFailed) {
    return (
      <Alert
        severity="error"
        data-testid="ioc-validation-settings-load-failed"
        title={t('The IOC validation settings could not be loaded.')}
        action={(
          <Button priority="secondary" size="sm" onClick={load}>
            {t('Retry')}
          </Button>
        )}
      />
    );
  }
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
        <IocValidationReadiness
          openctiEnabled={Boolean(settings.ioc_validation_opencti_enabled)}
          connectorRegistered={Boolean(settings.ioc_validation_connector_registered)}
        />
        <IocValidationSettingsForm settings={settings} onSaved={setSettings} />
      </div>
      <CustomizationMenu />
    </div>
  );
};

export default IocValidationSettings;

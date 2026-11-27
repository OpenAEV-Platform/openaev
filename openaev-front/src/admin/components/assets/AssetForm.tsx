import { Button, Combobox, ComboboxContent, ComboboxControls, ComboboxField, ComboboxHelperText, ComboboxInput, ComboboxLabel, ComboboxTrigger } from '@filigran/design-system';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect } from 'react';
import {
  Controller,
  FormProvider,
  type Resolver,
  type SubmitHandler,
  useForm,
  useFormContext,
  useWatch,
} from 'react-hook-form';
import { z } from 'zod';

import { resolveHostnameToIps } from '../../../actions/assets/endpoint-actions';
import AddressesFieldComponent from '../../../components/fields/AddressesFieldComponent';
import MarkingFieldController from '../../../components/fields/MarkingFieldController';
import PersonFieldController from '../../../components/fields/PersonFieldController';
import RequiredMark from '../../../components/fields/RequiredMark';
import SelectFieldController from '../../../components/fields/SelectFieldController';
import SwitchFieldController from '../../../components/fields/SwitchFieldController';
import TagFieldController from '../../../components/fields/TagFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { type EndpointInput, type MarkedEndpointInput } from '../../../utils/api-types';
import { formatMacAddress } from '../../../utils/String';
import {
  ARCH_OPTIONS,
  ASSET_EDITABLE_CATEGORIES,
  type AssetCategory,
  type AssetCategoryDef,
  type AssetSubCategory,
  CLOUD_PROVIDERS,
  criticalityItems,
  getCategoryDef,
  getCloudNativeTypeSuggestions,
  humanizeEnum,
  subcategoryBelongsTo,
} from './asset-categories';
import { withoutNullValues } from './asset-stream-values';
import useAssetMarkingsPicker from './useAssetMarkingsPicker';

type AssetFormValues = MarkedEndpointInput;

interface Props {
  category: AssetCategory;
  onSubmit: SubmitHandler<MarkedEndpointInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<MarkedEndpointInput>;
  /** For HOST endpoints, agent-managed fields (platform/arch/hostname) are only editable when agentless. */
  agentless?: boolean;
}

const regexMacAddress = /^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/;
const regexDomainName = /^((?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)*(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?))?$/;

const buildSchema = (def: AssetCategoryDef, t: (s: string) => string) => {
  const ipItem = z.union([
    z.ipv4({ message: t('Invalid IP addresses') }),
    z.ipv6({ message: t('Invalid IP addresses') }),
  ]);
  const requiredString = z.string().min(1, { message: t('Should not be empty') });
  return z.object({
    asset_name: requiredString,
    asset_description: z.string().optional(),
    asset_tags: z.string().array().optional(),
    asset_external_reference: z.string().optional(),
    asset_category: z.string().optional(),
    asset_subcategory: def.subcategoryRequired
      ? requiredString
      : z.string().optional().nullable(),
    asset_criticality: z.string().optional().nullable(),
    asset_internet_facing: z.boolean().optional().nullable(),
    endpoint_platform:
      def.fields.platform === 'required' ? requiredString : z.string().optional().nullable(),
    endpoint_arch:
      def.fields.arch === 'required' ? requiredString : z.string().optional().nullable(),
    asset_hostname: z.string().regex(regexDomainName, t('Invalid domain name')).optional().nullable(),
    asset_url: def.fields.url === 'required' ? requiredString : z.string().optional().nullable(),
    asset_ips:
      def.fields.ips === 'required'
        ? ipItem.array().min(1, { message: t('Should not be empty') })
        : ipItem.array().optional(),
    asset_mac_addresses: z
      .string()
      .regex(regexMacAddress, t('Invalid MAC addresses'))
      .array()
      .optional(),
    endpoint_is_eol: z.boolean().optional(),
    asset_cloud_provider: def.fields.cloud ? requiredString : z.string().optional().nullable(),
    asset_cloud_native_type: def.fields.cloud ? requiredString : z.string().optional().nullable(),
    asset_cloud_region: z.string().optional().nullable(),
    asset_linked_person: z.string().optional().nullable(),
    asset_metadata: z.record(z.string(), z.any()).optional(),
    // .nullable() matters here, unlike asset_tags: the backend's markingIds field (Asset.java) has
    // no default initializer, so an endpoint with no markings assigned comes back as literal
    // `null` rather than `[]` - .optional() alone rejects that and silently blocked every submit.
    asset_markings: z.string().array().optional(),
  }).superRefine((values, ctx) => {
    // Same rule as the API (issue #9653): a subcategory always belongs to the asset's category.
    const subcategory = values.asset_subcategory as AssetSubCategory | null | undefined;
    if (subcategory && !subcategoryBelongsTo(subcategory, def.value)) {
      ctx.addIssue({
        code: 'custom',
        path: ['asset_subcategory'],
        message: t('This subcategory does not belong to the category'),
      });
    }
  });
};

const CloudNativeTypeField: FunctionComponent = () => {
  const { control } = useFormContext();
  const { t } = useFormatter();
  const provider = useWatch({ name: 'asset_cloud_provider' });
  const subcategory = useWatch({ name: 'asset_subcategory' });
  const suggestions = getCloudNativeTypeSuggestions(provider, subcategory);
  return (
    <Controller
      name="asset_cloud_native_type"
      control={control}
      render={({ field, fieldState: { error } }) => (
        <Combobox<string>
          allowCustomValue
          createValueFromInput={input => input}
          options={suggestions}
          value={field.value ?? ''}
          onValueChange={value => field.onChange((value as string | null) ?? '')}
          onInputChange={(value, meta) => {
            // MUI fed every keystroke straight into the form; the cause keeps a
            // programmatic reset from doing the same.
            if (meta.cause === 'type') field.onChange(value ?? '');
          }}
          required
          error={!!error}
        >
          <ComboboxLabel>
            {t('Native type')}
            <RequiredMark />
          </ComboboxLabel>
          <ComboboxField>
            <ComboboxInput />
            <ComboboxControls>
              <ComboboxTrigger />
            </ComboboxControls>
          </ComboboxField>
          <ComboboxContent />
          <ComboboxHelperText>
            {error ? error.message : t('e.g. ec2_instance, s3_bucket, lambda_function')}
          </ComboboxHelperText>
        </Combobox>
      )}
    />
  );
};

/**
 * Category select of the edit form. A category change keeps every value the person typed, clears
 * a subcategory that does not belong to the new category (the API refuses the pair, issue #9653)
 * and gives a field the new category shows an empty value so its own validation applies.
 */
const CategoryField: FunctionComponent = () => {
  const { t } = useFormatter();
  const { getValues, setValue } = useFormContext<AssetFormValues>();
  const category = useWatch<AssetFormValues, 'asset_category'>({ name: 'asset_category' });
  useEffect(() => {
    const def = getCategoryDef(category as AssetCategory);
    const subcategory = getValues('asset_subcategory');
    if (subcategory && !subcategoryBelongsTo(subcategory, def.value)) {
      setValue('asset_subcategory', undefined, { shouldDirty: true });
    }
    if (def.fields.url !== 'hidden' && getValues('asset_url') == null) {
      setValue('asset_url', '');
    }
    if (def.fields.ips !== 'hidden' && getValues('asset_ips') == null) {
      setValue('asset_ips', []);
    }
  }, [category]);
  const items = ASSET_EDITABLE_CATEGORIES.map(c => ({
    value: c as string,
    label: t(getCategoryDef(c).label),
  }));
  return (
    <SelectFieldController
      name="asset_category"
      label={t('Category')}
      items={items}
      required
      helperText={t('Decides which fields this asset shows; a subcategory of another category is cleared.')}
    />
  );
};

const AssetForm: FunctionComponent<Props> = ({
  category,
  onSubmit,
  handleClose,
  editing,
  agentless,
  initialValues = {},
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const initialDef = getCategoryDef(category);

  const normalizedMacs = initialValues.asset_mac_addresses?.map(mac => formatMacAddress(mac));

  let defaultPlatform: EndpointInput['endpoint_platform'];
  if (initialDef.value === 'HOST') {
    defaultPlatform = 'Linux';
  } else if (initialDef.value === 'MOBILE_DEVICE') {
    defaultPlatform = 'iOS';
  }

  const defaultValues: AssetFormValues = {
    asset_name: '',
    asset_description: '',
    asset_tags: [],
    asset_hostname: initialDef.fields.hostname !== 'hidden' ? '' : undefined,
    asset_url: initialDef.fields.url !== 'hidden' ? '' : undefined,
    asset_ips: initialDef.fields.ips !== 'hidden' ? [] : undefined,
    endpoint_is_eol: false,
    asset_criticality: 'UNKNOWN',
    asset_internet_facing: initialDef.value === 'WEB_APPLICATION' ? true : undefined,
    endpoint_platform: defaultPlatform,
    endpoint_arch: initialDef.fields.arch !== 'hidden' ? 'x86_64' : undefined,
    asset_cloud_native_type: initialDef.fields.cloud ? '' : undefined,
    asset_cloud_region: initialDef.fields.cloud ? '' : undefined,
    asset_linked_person: null,
    asset_metadata: undefined,
    asset_markings: [],
    // Values read from the store can carry the stream's nulls: they fall back to these defaults.
    ...withoutNullValues(initialValues),
    // Except this one: null is "not set" (a switch keeps it until toggled), not the creation default.
    ...(initialValues.asset_internet_facing === null ? { asset_internet_facing: null } : {}),
    asset_mac_addresses: initialDef.fields.macAddresses !== 'hidden' ? (normalizedMacs ?? []) : undefined,
    asset_category: initialDef.value,
  };

  // The schema follows the category the form holds now: editing can move an asset to another
  // category, whose required fields then apply.
  const resolver: Resolver<AssetFormValues> = (values, context, options) => (
    zodResolver(buildSchema(getCategoryDef(values.asset_category as AssetCategory), t)) as Resolver<AssetFormValues>
  )(values, context, options);

  const methods = useForm<AssetFormValues>({
    mode: 'onTouched',
    resolver,
    defaultValues,
  });

  const watchedCategory = methods.watch('asset_category') as AssetCategory | undefined;
  const def = getCategoryDef(watchedCategory ?? category);

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  const watchedHostname = methods.watch('asset_hostname');

  const { showMarkings, withMarkings } = useAssetMarkingsPicker();

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(({ asset_markings, ...endpointData }) => onSubmit(withMarkings(endpointData, asset_markings)))(e);
  };

  // HOST platform/arch/hostname are managed by the agent: only editable for agentless hosts.
  const hostAgentManaged = def.value === 'HOST' && !agentless;
  const showPlatform = def.fields.platform !== 'hidden' && !hostAgentManaged;
  const showArch = def.fields.arch !== 'hidden' && !hostAgentManaged;
  const showHostname = def.fields.hostname !== 'hidden' && !hostAgentManaged;

  const platformItems = (def.platformOptions ?? []).map(p => ({
    value: p as string,
    label: t(p as string),
  }));
  const archItems = ARCH_OPTIONS.map(a => ({
    value: a,
    label: t(a),
  }));
  const subcategoryItems = def.subcategories.map(s => ({
    value: s,
    label: t(humanizeEnum(s)),
  }));
  const providerItems = CLOUD_PROVIDERS.map(p => ({
    value: p,
    label: t(humanizeEnum(p)),
  }));

  return (
    <FormProvider {...methods}>
      <form
        noValidate
        id="assetForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmitWithoutPropagation}
      >
        <TextFieldController required name="asset_name" label={t('Name')} />
        <TextFieldController name="asset_description" label={t('Description')} multiline rows={2} />

        {editing && <CategoryField />}

        {subcategoryItems.length > 0 && (
          <SelectFieldController
            name="asset_subcategory"
            label={t('Subcategory')}
            items={subcategoryItems}
            required={def.subcategoryRequired}
          />
        )}

        {(showPlatform || showArch) && (
          <div style={{
            display: 'grid',
            gridTemplateColumns: showPlatform && showArch ? '1fr 1fr' : '1fr',
            gap: theme.spacing(2),
          }}
          >
            {showPlatform && (
              <SelectFieldController name="endpoint_platform" label={t('Platform')} items={platformItems} required={def.fields.platform === 'required'} />
            )}
            {showArch && (
              <SelectFieldController name="endpoint_arch" label={t('Architecture')} items={archItems} required={def.fields.arch === 'required'} />
            )}
          </div>
        )}

        {showHostname && (
          <TextFieldController name="asset_hostname" label={t('Hostname')} />
        )}

        {def.fields.url !== 'hidden' && (
          <TextFieldController name="asset_url" label={t('URL')} required={def.fields.url === 'required'} />
        )}

        {def.fields.cloud && (
          <>
            <div style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: theme.spacing(2),
            }}
            >
              <SelectFieldController name="asset_cloud_provider" label={t('Cloud provider')} items={providerItems} required />
              <CloudNativeTypeField />
            </div>
            <div style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: theme.spacing(2),
            }}
            >
              <TextFieldController name="asset_cloud_region" label={t('Region')} />
              <TextFieldController name="asset_metadata.cloud_account_id" label={t('Account ID')} />
            </div>
            <TextFieldController name="asset_metadata.cloud_resource_id" label={t('Resource ID / ARN')} />
          </>
        )}

        {def.fields.person && (
          <PersonFieldController name="asset_linked_person" label={t('Person')} />
        )}

        {def.fields.ips !== 'hidden' && (
          <AddressesFieldComponent
            name="asset_ips"
            helperText="Please provide one IP address per line."
            label={t('IP Addresses')}
            required={def.fields.ips === 'required'}
            onResolve={def.fields.hostname !== 'hidden'
              ? async () => {
                const result = await resolveHostnameToIps(methods.getValues('asset_hostname') ?? '');
                return (result?.data ?? []) as string[];
              }
              : undefined}
            resolveDisabled={!watchedHostname}
            resolveTooltip={watchedHostname ? t('Resolve IP from hostname') : t('Set a hostname to resolve its IP')}
          />
        )}
        {def.fields.macAddresses !== 'hidden' && (
          <AddressesFieldComponent name="asset_mac_addresses" helperText="Please provide one MAC address per line." label={t('MAC Addresses')} />
        )}

        {def.fields.metadataFields.map(field => (
          <TextFieldController key={field.key} name={`asset_metadata.${field.key}`} label={t(field.label)} />
        ))}

        <SelectFieldController name="asset_criticality" label={t('Criticality')} items={criticalityItems(t)} />
        <TagFieldController name="asset_tags" label={t('Tags')} />
        {showMarkings && (
          <MarkingFieldController name="asset_markings" label={t('Markings')} />
        )}

        {def.fields.internetFacing && (
          <SwitchFieldController name="asset_internet_facing" label={t('Internet-facing')} />
        )}
        {def.fields.eol && (
          <SwitchFieldController name="endpoint_is_eol" label={t('End of Life')} />
        )}

        <div style={{ alignSelf: 'flex-end' }}>
          <Button type="button" priority="secondary" onClick={handleClose} disabled={isSubmitting} style={{ marginRight: theme.spacing(2) }}>
            {t('Cancel')}
          </Button>
          <Button type="submit" disabled={!isDirty || isSubmitting}>
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default AssetForm;

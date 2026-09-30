import { zodResolver } from '@hookform/resolvers/zod';
import { Autocomplete, Button, TextField } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent } from 'react';
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
import SelectFieldController from '../../../components/fields/SelectFieldController';
import SwitchFieldController from '../../../components/fields/SwitchFieldController';
import TagFieldController from '../../../components/fields/TagFieldController';
import TextFieldController from '../../../components/fields/TextFieldController';
import { useFormatter } from '../../../components/i18n';
import { type EndpointInput } from '../../../utils/api-types';
import markingIdSetsEqual from '../../../utils/markings';
import { formatMacAddress } from '../../../utils/String';
import { isFeatureEnabled } from '../../../utils/utils';
import {
  ARCH_OPTIONS,
  type AssetCategory,
  type AssetCategoryDef,
  CLOUD_PROVIDERS,
  CRITICALITY_OPTIONS,
  getCategoryDef,
  getCloudNativeTypeSuggestions,
  humanizeEnum,
} from './asset-categories';

// Markings are not part of EndpointInput: they live on a dedicated, more heavily-guarded endpoint
// (PUT /api/assets/{id}/markings, see AssetMarkingsApi) rather than the generic asset/endpoint
// update, so they cannot ride along in the same payload. This form still hosts the field (an
// asset already being edited has an id to assign markings against) but reports changes to it
// separately, through `onMarkingsChange`, instead of folding them into `onSubmit`'s EndpointInput.
type AssetFormValues = EndpointInput & { asset_markings?: string[] | null };

interface Props {
  category: AssetCategory;
  onSubmit: SubmitHandler<EndpointInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<EndpointInput> & { asset_markings?: string[] | null };
  /** For HOST endpoints, agent-managed fields (platform/arch/hostname) are only editable when agentless. */
  agentless?: boolean;
  /**
   * Called on submit with the full replacement marking id list, and awaited before `onSubmit`
   * runs (see the race explained where it's awaited). Only invoked while editing - assigning
   * markings requires an asset id, so there is nothing to call during creation. Resolves to
   * `false` on failure (the caller has already shown its own error notification for it) - a
   * `false` here cancels `onSubmit` too, so the form stays open on a half-applied save instead of
   * reporting success for the endpoint fields while the markings silently didn't take.
   */
  onMarkingsChange?: (markingIds: string[]) => boolean | Promise<boolean>;
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
    asset_markings: z.string().array().optional().nullable(),
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
        <Autocomplete
          freeSolo
          options={suggestions}
          value={field.value ?? ''}
          onChange={(_, value) => field.onChange(value ?? '')}
          onInputChange={(_, value) => field.onChange(value ?? '')}
          renderInput={params => (
            <TextField
              {...params}
              variant="standard"
              required
              label={t('Native type')}
              error={!!error}
              helperText={error ? error.message : t('e.g. ec2_instance, s3_bucket, lambda_function')}
            />
          )}
        />
      )}
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
  onMarkingsChange,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const def = getCategoryDef(category);

  const normalizedMacs = initialValues.asset_mac_addresses?.map(mac => formatMacAddress(mac));

  let defaultPlatform: EndpointInput['endpoint_platform'];
  if (def.value === 'HOST') {
    defaultPlatform = 'Linux';
  } else if (def.value === 'MOBILE_DEVICE') {
    defaultPlatform = 'iOS';
  }

  const defaultValues: AssetFormValues = {
    asset_name: '',
    asset_description: '',
    asset_tags: [],
    asset_hostname: def.fields.hostname !== 'hidden' ? '' : undefined,
    asset_url: def.fields.url !== 'hidden' ? '' : undefined,
    asset_ips: def.fields.ips !== 'hidden' ? [] : undefined,
    endpoint_is_eol: false,
    asset_criticality: 'UNKNOWN',
    asset_internet_facing: def.value === 'WEB_APPLICATION' ? true : undefined,
    endpoint_platform: defaultPlatform,
    endpoint_arch: def.fields.arch !== 'hidden' ? 'x86_64' : undefined,
    asset_cloud_native_type: def.fields.cloud ? '' : undefined,
    asset_cloud_region: def.fields.cloud ? '' : undefined,
    asset_linked_person: null,
    asset_metadata: undefined,
    asset_markings: [],
    ...initialValues,
    asset_mac_addresses: def.fields.macAddresses !== 'hidden' ? (normalizedMacs ?? []) : undefined,
    asset_category: def.value,
  };

  const methods = useForm<AssetFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(buildSchema(def, t)) as Resolver<AssetFormValues>,
    defaultValues,
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  const watchedHostname = methods.watch('asset_hostname');

  // Assigning markings requires an asset id, so the picker only makes sense once the asset exists
  // - gated on `editing` the same way the field itself is only reported through `onMarkingsChange`,
  // and on the flag the rest of the marking UI (Endpoints list, GroupManageMarkings) is gated on.
  const showMarkings = editing && isFeatureEnabled('MARKING');

  // Markings are split out of the submitted payload and reported through `onMarkingsChange`
  // instead: they don't belong to EndpointInput and are persisted through their own endpoint (see
  // the `AssetFormValues` comment above). Only sent when the picker was even available and its
  // selection actually moved from what the form opened with - there's nothing to replace
  // otherwise, and skipping avoids a pointless PUT (plus its own success/failure notification) on
  // every save of an asset whose markings were never touched.
  //
  // Awaited *before* onSubmit, not fired alongside it: both PUTs normalize into the same store
  // entity (see updateAssetMarkings's doc), and firing them concurrently is a race - whichever
  // response lands last wins the merge. The endpoint PUT's response carries a full snapshot of the
  // asset, markings included, read from the DB at the time it runs; if it lands after the markings
  // PUT it's fine (it already reflects the write), but if it lands first - or the two just
  // interleave - its (pre-write) asset_markings silently reverts what the markings PUT just saved.
  // Awaiting removes the race entirely: by the time the endpoint PUT is even sent, the marking
  // write has already committed, so its response is guaranteed consistent whichever order the
  // network delivers things in.
  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(async ({ asset_markings, ...endpointData }) => {
      if (showMarkings) {
        const currentIds = asset_markings ?? [];
        const initialIds = defaultValues.asset_markings ?? [];
        if (!markingIdSetsEqual(currentIds, initialIds)) {
          const markingsSaved = await onMarkingsChange?.(currentIds);
          // `false` means the markings PUT failed - its own error notification already fired (see
          // onMarkingsChange's doc), so don't also run the endpoint update: better to leave the
          // form open on a save the user can see failed than to report success for the fields that
          // did go through while the markings silently didn't.
          if (markingsSaved === false) {
            return;
          }
        }
      }
      onSubmit(endpointData);
    })(e);
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
  const criticalityItems = CRITICALITY_OPTIONS.map(c => ({
    value: c,
    label: t(humanizeEnum(c)),
  }));
  const providerItems = CLOUD_PROVIDERS.map(p => ({
    value: p,
    label: t(humanizeEnum(p)),
  }));

  return (
    <FormProvider {...methods}>
      <form
        id="assetForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmitWithoutPropagation}
      >
        <TextFieldController variant="standard" required name="asset_name" label={t('Name')} />
        <TextFieldController variant="standard" name="asset_description" label={t('Description')} multiline rows={2} />

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
          <TextFieldController variant="standard" name="asset_hostname" label={t('Hostname')} />
        )}

        {def.fields.url !== 'hidden' && (
          <TextFieldController variant="standard" name="asset_url" label={t('URL')} required={def.fields.url === 'required'} />
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
              <TextFieldController variant="standard" name="asset_cloud_region" label={t('Region')} />
              <TextFieldController variant="standard" name="asset_metadata.cloud_account_id" label={t('Account ID')} />
            </div>
            <TextFieldController variant="standard" name="asset_metadata.cloud_resource_id" label={t('Resource ID / ARN')} />
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
          <TextFieldController key={field.key} variant="standard" name={`asset_metadata.${field.key}`} label={t(field.label)} />
        ))}

        <SelectFieldController name="asset_criticality" label={t('Criticality')} items={criticalityItems} />
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
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            style={{ marginRight: theme.spacing(2) }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={!isDirty || isSubmitting}
          >
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default AssetForm;

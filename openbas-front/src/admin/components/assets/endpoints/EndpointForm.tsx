import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { z } from 'zod';

import AddressesFieldComponent from '../../../../components/fields/AddressesFieldComponent';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SwitchFieldController from '../../../../components/fields/SwitchFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { type EndpointInput } from '../../../../utils/api-types';
import { formatMacAddress } from '../../../../utils/String';
import { zodImplement } from '../../../../utils/Zod';

interface Props {
  onSubmit: SubmitHandler<EndpointInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: EndpointInput;
  agentless?: boolean;
}

const EndpointForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing,
  agentless,
  initialValues = {
    asset_name: '',
    asset_description: '',
    asset_tags: [],
    endpoint_hostname: '',
    endpoint_ips: [],
    endpoint_mac_addresses: [],
    endpoint_platform: 'Linux',
    endpoint_arch: 'x86_64',
  },
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const theme = useTheme();
  const regexMacAddress = /^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$/;
  if (initialValues.endpoint_mac_addresses) {
    initialValues.endpoint_mac_addresses = initialValues.endpoint_mac_addresses?.values().map((mac: string, _: number) => formatMacAddress(mac)).toArray();
  }

  const methods = useForm<EndpointInput>({
    mode: 'onTouched',
    resolver: zodResolver(
      zodImplement<EndpointInput>().with({
        asset_name: z.string().min(1, { message: t('Should not be empty') }),
        asset_description: z.string().optional(),
        asset_tags: z.string().array().optional(),
        endpoint_hostname: z.string().optional(),
        endpoint_ips: z.string().ip({ message: t('Invalid IP addresses') }).array().optional(),
        endpoint_mac_addresses: z
          .string()
          .regex(regexMacAddress,
            t('Invalid MAC addresses'),
          ).array().optional(),
        endpoint_platform: z.enum(['Linux', 'Windows', 'MacOS', 'Container', 'Service', 'Generic', 'Internal', 'Unknown']),
        endpoint_arch: z.enum(['x86_64', 'arm64', 'Unknown']),
        endpoint_agent_version: z.string().optional(),
        endpoint_is_eol: z.boolean().optional(),
        asset_external_reference: z.string().optional(),
      }),
    ),
    defaultValues: initialValues,
  });

  const {
    handleSubmit,
    formState: { isSubmitting, isDirty },
  } = methods;

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    handleSubmit(onSubmit)(e);
  };

  const architecturesItems = [
    {
      value: 'x86_64',
      label: t('x86_64'),
    },
    {
      value: 'arm64',
      label: t('arm64'),
    },
    {
      value: 'Unknown',
      label: t('Unknown'),
    },
  ];

  const platformItems = [
    {
      value: 'Linux',
      label: t('Linux'),
    },
    {
      value: 'Windows',
      label: t('Windows'),
    },
    {
      value: 'MacOS',
      label: t('MacOS'),
    },
    {
      value: 'Container',
      label: t('Container'),
    },
    {
      value: 'Service',
      label: t('Service'),
    },
    {
      value: 'Generic',
      label: t('Generic'),
    },
    {
      value: 'Internal',
      label: t('Internal'),
    },
    {
      value: 'Unknown',
      label: t('Unknown'),
    },
  ];

  return (
    <FormProvider {...methods}>
      <form
        id="endpointForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmitWithoutPropagation}
      >

        <TextFieldController
          variant="standard"
          required
          name="asset_name"
          label={t('Name')}
        />

        <TextFieldController variant="standard" name="asset_description" label={t('Description')} multiline={true} rows={2} />

        {
          agentless && (
            <>
              <div style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: theme.spacing(2),
              }}
              >
                <SelectFieldController name="endpoint_arch" label={t('Architecture')} items={architecturesItems} required />
                <SelectFieldController name="endpoint_platform" label={t('Platform')} items={platformItems} required />
              </div>

              <TextFieldController
                variant="standard"
                name="endpoint_hostname"
                label={t('Hostname')}
              />
            </>

          )
        }
        <AddressesFieldComponent name="endpoint_ips" helperText="Please provide one IP address per line." label={t('IP Addresses')} required />
        <AddressesFieldComponent name="endpoint_mac_addresses" helperText="Please provide one MAC address per line." label={t('MAC Addresses')} />
        <TagFieldController name="asset_tags" label={t('Tags')} />
        <SwitchFieldController name="endpoint_is_eol" label={t('End of Life')} />
        <div style={{ alignSelf: 'flex-end' }}>
          <Button
            variant="contained"
            onClick={handleClose}
            style={{ marginRight: theme.spacing(2) }}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
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

export default EndpointForm;

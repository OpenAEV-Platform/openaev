import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FormEvent, useEffect } from 'react';
import { type FieldValues, FormProvider, type SubmitHandler, useForm, useWatch } from 'react-hook-form';
import { z, type ZodTypeAny } from 'zod';

import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../components/i18n';
import { type DetectionRemediationInput } from '../../../utils/api-types';
import { type PayloadCreateInput } from '../../../utils/api-types-custom';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../common/entreprise_edition/EEChip';
import CommandsFormTab from './form/CommandsFormTab';
import GeneralFormTab from './form/GeneralFormTab';
import OutputFormTab from './form/OutputFormTab';
import RemediationFormTabs from './form/RemediationFormTabs';
import { hasSpecificDirtyFieldAI, trackedFields } from './utils/payloadFormToPayloadInput';
import { useSnapshotRemediation } from './utils/useSnapshotRemediation';

interface Props {
  onSubmit: SubmitHandler<PayloadCreateInput>;
  handleClose: () => void;
  editing: boolean;
  initialValues?: Partial<PayloadCreateInput> & { payload_id?: string };
}

const PayloadForm = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {
    payload_id: '',
    // @ts-expect-error set payload type to null to get a controlled component from the start
    payload_type: null,
    payload_name: '',
    payload_platforms: [],
    payload_expectations: ['PREVENTION', 'DETECTION'],
    payload_description: '',
    command_executor: '',
    command_content: '',
    payload_attack_patterns: [],
    payload_cleanup_command: '',
    payload_cleanup_executor: '',
    executable_file: '',
    file_drop_file: '',
    dns_resolution_hostname: '',
    payload_tags: [],
    payload_arguments: [],
    payload_prerequisites: [],
    payload_output_parsers: [],
    payload_execution_arch: 'ALL_ARCHITECTURES',
    remediations: new Map<string, DetectionRemediationInput>(),
    payload_domains: [],
  },
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const {
    isValidated: isValidatedEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();
  const { snapshot } = useSnapshotRemediation();

  const regexGroupObject = z.object({
    ...editing && { regex_group_id: z.string().optional() },
    regex_group_field: z.string().min(1, { message: t('Should not be empty') }),
    regex_group_index_values: z.string().min(1, { message: t('Should not be empty') }),
  });

  const contractOutputElementObject = z.object({
    ...editing && { contract_output_element_id: z.string().optional() },
    contract_output_element_is_finding: z.boolean(),
    contract_output_element_name: z.string().min(1, { message: t('Should not be empty') }),
    contract_output_element_key: z.string().min(1, { message: t('Should not be empty') }),
    contract_output_element_type: z.enum(['text', 'number', 'port', 'portscan', 'ipv4', 'ipv6', 'credentials', 'cve'], { message: t('Should not be empty') }),
    contract_output_element_tags: z.string().array().optional(),
    contract_output_element_rule: z.string().min(1, { message: t('Should not be empty') }),
    contract_output_element_regex_groups: z.array(regexGroupObject),
  });
  const outputParserObject = z.object({
    ...editing && { output_parser_id: z.string().optional() },
    output_parser_mode: z.enum(['STDOUT', 'STDERR', 'READ_FILE'], { message: t('Should not be empty') }),
    output_parser_type: z.enum(['REGEX'], { message: t('Should not be empty') }),
    output_parser_contract_output_elements: z.array(contractOutputElementObject),
  });

  const payloadPrerequisiteZodObject = z.object({
    executor: z.string().min(1, { message: t('Should not be empty') }),
    get_command: z.string().min(1, { message: t('Should not be empty') }),
    description: z.string().optional().nullable(),
    check_command: z.string().optional(),
  });

  const payloadArgumentZodObject = z.object({
    default_value: z.string().nonempty(t('Should not be empty')),
    key: z.string().nonempty(t('Should not be empty')),
    type: z.string().nonempty(t('Should not be empty')),
    description: z.string().optional().nullable(),
    separator: z.string().optional().nullable(),
  }).refine(
    data => data.type !== 'targeted-asset' || !!data.separator,
    {
      message: t('Should not be empty'),
      path: ['separator'],
    },
  );

  const payloadDomainZodObject = z.object({
    domain_id: z.string(),
    domain_name: z.string(),
    domain_color: z.string(),
  });

  const baseSchema = {
    payload_name: z.string().min(1, { message: t('Should not be empty') }).describe('General-tab'),
    payload_description: z.string().optional().describe('General-tab'),
    payload_attack_patterns: z.string().array().optional(),
    payload_tags: z.string().array().optional(),
    payload_domains: z.array(payloadDomainZodObject).refine(arr => arr.length > 0, t('Should not be empty')),
    payload_expectations: z.enum(['PREVENTION', 'DETECTION', 'VULNERABILITY', 'MANUAL', 'TEXT', 'CHALLENGE', 'DOCUMENT', 'ARTICLE']).array().optional(),
    payload_platforms: z.enum(['Linux', 'Windows', 'MacOS', 'Container', 'Service', 'Generic', 'Internal', 'Unknown']).array().min(1, { message: t('Should not be empty') }).describe('Commands-tab'),
    payload_execution_arch: z.enum(['x86_64', 'arm64', 'ALL_ARCHITECTURES'], { message: t('Should not be empty') }).describe('Commands-tab'),
    payload_cleanup_command: z.string().optional().describe('Commands-tab'),
    payload_cleanup_executor: z.string().optional(),
    payload_arguments: z.array(payloadArgumentZodObject).optional().describe('Commands-tab'),
    payload_prerequisites: z.array(payloadPrerequisiteZodObject).optional().describe('Commands-tab'),
    payload_output_parsers: z.array(outputParserObject).optional().describe('Output-tab'),
    remediations: z.any(),
  };

  const commandSchema = z.object({
    ...baseSchema,
    payload_type: z.literal('Command').describe('Commands-tab'),
    command_executor: z.string().min(1, { message: 'Should not be empty' }).describe('Commands-tab'),
    command_content: z.string().min(1, { message: 'Should not be empty' }).describe('Commands-tab'),
  });
  const executableSchema = z.object({
    ...baseSchema,
    payload_type: z.literal('Executable').describe('Commands-tab'),
    executable_file: z.string().min(1, { message: t('Should not be empty') }).describe('Commands-tab'),
  });
  const fileDropSchema = z.object({
    ...baseSchema,
    payload_type: z.literal('FileDrop').describe('Commands-tab'),
    file_drop_file: z.string().min(1, { message: t('Should not be empty') }).describe('Commands-tab'),
  });
  const dnsResolutionSchema = z.object({
    ...baseSchema,
    payload_type: z.literal('DnsResolution').describe('Commands-tab'),
    dns_resolution_hostname: z.string().min(1, { message: t('Should not be empty') }).describe('Commands-tab'),
  });

  const schema = z.discriminatedUnion('payload_type', [commandSchema, executableSchema, fileDropSchema, dnsResolutionSchema])
    .refine(data => !(data.payload_cleanup_command && !data.payload_cleanup_executor), {
      message: 'Should not be empty',
      path: ['payload_cleanup_executor'],
    });

  const methods = useForm<PayloadCreateInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });
  const {
    handleSubmit,
    control,
    formState: { errors, isDirty, isSubmitting, defaultValues, dirtyFields },
  } = methods;

  const getTabForField = (fieldName: string): string | undefined => {
    const commandShape = (commandSchema.shape as Record<string, ZodTypeAny>)[fieldName];
    const executableShape = (executableSchema.shape as Record<string, ZodTypeAny>)[fieldName];
    const fileDropShape = (fileDropSchema.shape as Record<string, ZodTypeAny>)[fieldName];
    const dnsResolutionShape = (dnsResolutionSchema.shape as Record<string, ZodTypeAny>)[fieldName];

    const fieldSchema: ZodTypeAny = commandShape || executableShape || fileDropShape || dnsResolutionShape;
    return fieldSchema?.description?.replace('-tab', '');
  };

  const tabEntries: TabsEntry[] = [{
    key: 'General',
    label: 'General',
  }, {
    key: 'Commands',
    label: 'Commands',
  }, {
    key: 'Output',
    label: 'Output',
  }, {
    key: 'Remediation',
    label: (
      <Box display="flex" alignItems="center">
        {t('Remediation')}
        {!isValidatedEnterpriseEdition && (
          <EEChip
            style={{ marginLeft: theme.spacing(1) }}
            clickable
            featureDetectedInfo={t('Remediation')}
          />
        )}
      </Box>
    ),
  }];
  const { currentTab, handleChangeTab } = useTabs(tabEntries[0].key);

  const handleSubmitWithoutDefault = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const isValid = await methods.trigger();
    if (!isValid) {
      const firstErrorField = Object.keys(errors)[0];
      const tabName = getTabForField(firstErrorField);
      if (tabName) handleChangeTab(tabName);
    } else {
      handleSubmit(onSubmit)(e);
    }
  };
  const trackedUseWatch = useWatch({
    control,
    name: trackedFields as unknown as keyof PayloadCreateInput,
  });

  useEffect(() => {
    const remediations = (methods.getValues('remediations') ?? {}) as Record<string, DetectionRemediationInput>;
    Object.entries(remediations).forEach(([key, value]) => {
      const currentDetection = value;
      const fieldName = `remediations.${key}` as const;

      if (hasSpecificDirtyFieldAI(defaultValues, snapshot?.get(key)?.trackedFields, trackedUseWatch as FieldValues)) {
        currentDetection.author_rule = currentDetection.author_rule !== 'HUMAN' ? 'AI_OUTDATED' : currentDetection.author_rule;

        methods.setValue(fieldName, { ...currentDetection }, { shouldDirty: true });
      } else if (Object.keys(dirtyFields).length !== 0 && currentDetection.author_rule === 'AI_OUTDATED') {
        currentDetection.author_rule = 'AI';

        methods.setValue(fieldName, { ...currentDetection }, { shouldDirty: true });
      }
    });
  }, [trackedUseWatch]);

  useEffect(() => {
    if (currentTab === 'Remediation' && !isValidatedEnterpriseEdition) {
      handleChangeTab('General');
      setEEFeatureDetectedInfo(t('Remediation'));
      openEnterpriseEditionDialog();
    }
  }, [currentTab, isValidatedEnterpriseEdition]);

  return (
    <>
      <FormProvider {...methods}>
        <form
          style={{
            display: 'flex',
            flexDirection: 'column',
            minHeight: '100%',
            gap: theme.spacing(2),
          }}
          id="payloadForm"
          noValidate // disabled tooltip
          onSubmit={handleSubmitWithoutDefault}
        >
          <Tabs
            entries={tabEntries}
            currentTab={currentTab}
            onChange={newValue => handleChangeTab(newValue)}
          />

          {currentTab === 'General' && (
            <GeneralFormTab />
          )}

          {currentTab === 'Commands' && (
            <CommandsFormTab disabledPayloadType={editing} />
          )}

          {currentTab === 'Output' && (
            <OutputFormTab />
          )}

          {currentTab === 'Remediation' && (
            <RemediationFormTabs payloadId={initialValues?.payload_id} />
          )}

          <div style={{
            marginTop: 'auto',
            display: 'flex',
            flexDirection: 'row-reverse',
            gap: theme.spacing(1),
          }}
          >
            <Button
              variant="contained"
              color="secondary"
              type="submit"
              disabled={isSubmitting || !isDirty}
            >
              {editing ? t('Update') : t('Create')}
            </Button>
            <Button
              variant="contained"
              onClick={handleClose}
              disabled={isSubmitting}
            >
              {t('Cancel')}
            </Button>
          </div>
        </form>
      </FormProvider>
    </>
  );
};

export default PayloadForm;

import { zodResolver } from '@hookform/resolvers/zod';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type SyntheticEvent, useEffect } from 'react';
import { type FieldValues, FormProvider, type SubmitHandler, useForm, useWatch } from 'react-hook-form';
import { z, type ZodTypeAny } from 'zod';

import Button from '../../../components/common/button/Button';
import Tabs, { type TabsEntry } from '../../../components/common/tabs/Tabs';
import useTabs from '../../../components/common/tabs/useTabs';
import { useFormatter } from '../../../components/i18n';
import { type DetectionRemediationInput } from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from '../common/entreprise_edition/EEChip';
import { CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS } from '../findings/ContractOutputElementType';
import { hasSpecificDirtyFieldAI, trackedFields } from '../payloads/utils/payloadFormToPayloadInput';
import CommandsFormTab from './form/CommandsFormTab';
import GeneralFormTab from './form/GeneralFormTab';
import OutputFormTab from './form/OutputFormTab';
import RemediationFormTabs from './form/RemediationFormTabs';
import { useSnapshotRemediation } from './utils/useSnapshotRemediation';

interface Props {
  onSubmit: SubmitHandler<ThreatArsenalActionCreateCustomInput>;
  handleClose: () => void;
  editing: boolean;
  initialValues?: Partial<ThreatArsenalActionCreateCustomInput> & { action_id?: string };
}

const ThreatArsenalActionForm = ({
  onSubmit,
  handleClose,
  editing,
  initialValues = {
    action_id: '',
    // @ts-expect-error set payload type to null to get a controlled component from the start
    action_type: '',
    action_name: '',
    action_platforms: [],
    action_expectations: ['PREVENTION', 'DETECTION'],
    action_description: '',
    command_executor: '',
    command_content: '',
    action_attack_patterns: [],
    action_cleanup_command: '',
    action_cleanup_executor: '',
    executable_file: '',
    file_drop_file: '',
    dns_resolution_hostname: '',
    action_tags: [],
    action_arguments: [],
    action_prerequisites: [],
    action_output_parsers: [],
    action_execution_arch: 'ALL_ARCHITECTURES',
    remediations: new Map<string, DetectionRemediationInput>(),
    action_domains: [],
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
  type ThreatArsenalActionCreateInput = z.infer<typeof schema>;

  const regexGroupObject = z.object({
    ...editing && { regex_group_id: z.string().optional() },
    regex_group_field: z.string().min(1, { error: t('Should not be empty') }),
    regex_group_index_values: z.string().min(1, { error: t('Should not be empty') }),
  });

  const contractOutputElementObject = z.object({
    ...editing && { contract_output_element_id: z.string().optional() },
    contract_output_element_is_finding: z.boolean(),
    contract_output_element_name: z.string().min(1, { error: t('Should not be empty') }),
    contract_output_element_key: z.string().min(1, { error: t('Should not be empty') }),
    contract_output_element_type: z.enum(CONTRACT_OUTPUT_ELEMENT_TYPE_KEYS, { error: t('Should not be empty') }),
    contract_output_element_tags: z.string().array().optional(),
    contract_output_element_rule: z.string().min(1, { error: t('Should not be empty') }),
    contract_output_element_regex_groups: z.array(regexGroupObject),
  });
  const outputParserObject = z.object({
    ...editing && { output_parser_id: z.string().optional() },
    output_parser_mode: z.enum(['STDOUT', 'STDERR', 'READ_FILE'], { error: t('Should not be empty') }),
    output_parser_type: z.enum(['REGEX'], { error: t('Should not be empty') }),
    output_parser_contract_output_elements: z.array(contractOutputElementObject),
  });

  const prerequisiteZodObject = z.object({
    executor: z.string().min(1, { error: t('Should not be empty') }),
    get_command: z.string().min(1, { error: t('Should not be empty') }),
    description: z.string().optional().nullable(),
    check_command: z.string().optional(),
  });

  const argumentZodObject = z.object({
    default_value: z.string().nonempty(t('Should not be empty')),
    key: z.string().nonempty(t('Should not be empty')),
    type: z.enum(['text', 'number', 'port', 'portscan', 'ipv4', 'ipv6', 'credentials', 'cve', 'document', 'targeted-asset', 'kerberoastable_account', 'asreproastable_account', 'account_with_password_not_required', 'vulnerability', 'sid', 'delegation', 'password_policy', 'computer', 'group', 'admin_username', 'share', 'username'], { error: t('Should not be empty') }),
    subtype: z.enum(['host', 'port', 'service', 'username', 'password', 'severity', 'domain']).optional(),
    description: z.string().optional().nullable(),
    separator: z.string().optional().nullable(),
  }).refine(
    data => data.type !== 'targeted-asset' || !!data.separator,
    {
      error: t('Should not be empty'),
      path: ['separator'],
    },
  );

  const baseSchema = {
    action_name: z.string().min(1, { error: t('Should not be empty') }).describe('General-tab'),
    action_description: z.string().optional().describe('General-tab'),
    action_attack_patterns: z.string().array().optional(),
    action_tags: z.string().array().optional(),
    // action_domains: z.array(domainZodObject).refine(arr => arr.length > 0, t('Should not be empty')).describe('General-tab'),
    action_domains: z.string().array().refine(arr => arr.length > 0, t('Should not be empty')).describe('General-tab'),
    action_expectations: z.enum(['PREVENTION', 'DETECTION', 'VULNERABILITY', 'MANUAL', 'TEXT', 'CHALLENGE', 'DOCUMENT', 'ARTICLE']).array(),
    action_platforms: z.enum(['Linux', 'Windows', 'MacOS', 'Container', 'Service', 'Generic', 'Internal', 'Unknown']).array().min(1, { error: t('Should not be empty') }).describe('Commands-tab'),
    action_execution_arch: z.enum(['x86_64', 'arm64', 'ALL_ARCHITECTURES'], { error: t('Should not be empty') }).describe('Commands-tab'),
    action_cleanup_command: z.string().optional().nullable().describe('Commands-tab'),
    action_cleanup_executor: z.string().optional().nullable().describe('Commands-tab'),
    action_arguments: z.array(argumentZodObject).optional().describe('Commands-tab'),
    action_prerequisites: z.array(prerequisiteZodObject).optional().describe('Commands-tab'),
    action_output_parsers: z.array(outputParserObject).optional().describe('Output-tab'),
    remediations: z.any().optional(),
  };

  const commandSchema = z.object({
    ...baseSchema,
    action_type: z.literal('Command').describe('Commands-tab'),
    command_executor: z.string().min(1, { error: 'Should not be empty' }).describe('Commands-tab'),
    command_content: z.string().min(1, { error: 'Should not be empty' }).describe('Commands-tab'),
  });
  const executableSchema = z.object({
    ...baseSchema,
    action_type: z.literal('Executable').describe('Commands-tab'),
    executable_file: z.string().min(1, { error: t('Should not be empty') }).describe('Commands-tab'),
  });
  const fileDropSchema = z.object({
    ...baseSchema,
    action_type: z.literal('FileDrop').describe('Commands-tab'),
    file_drop_file: z.string().min(1, { error: t('Should not be empty') }).describe('Commands-tab'),
  });
  const dnsResolutionSchema = z.object({
    ...baseSchema,
    action_type: z.literal('DnsResolution').describe('Commands-tab'),
    dns_resolution_hostname: z.string().min(1, { error: t('Should not be empty') }).describe('Commands-tab'),
  });

  const schema = z.discriminatedUnion('action_type', [commandSchema, executableSchema, fileDropSchema, dnsResolutionSchema])
    .refine(data => !(data.action_cleanup_command && !data.action_cleanup_executor), {
      path: ['action_cleanup_executor'],
      error: 'Should not be empty',
    });

  const methods = useForm<ThreatArsenalActionCreateCustomInput>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues,
  });
  const {
    handleSubmit,
    control,
    formState: { isDirty, isSubmitting, defaultValues, dirtyFields },
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

  const focusFirstErrorTab = () => {
    const fields = Object.keys(
      methods.getValues(),
    ) as (keyof ThreatArsenalActionCreateInput)[];

    const firstErrorField = fields.find(
      field => methods.getFieldState(field).error,
    );
    if (!firstErrorField) return;

    const rootField = String(firstErrorField).split('.')[0];
    const tabName = getTabForField(rootField);
    if (!tabName) return;

    handleChangeTab(tabName);
  };
  const handleSubmitWithoutDefault = async (e: SyntheticEvent) => {
    e.preventDefault();
    const isValid = await methods.trigger();
    if (!isValid) {
      focusFirstErrorTab();
      return;
    }
    await handleSubmit(onSubmit)(e);
  };

  const trackedUseWatch = useWatch({
    control,
    name: trackedFields as unknown as keyof ThreatArsenalActionCreateInput,
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
          id="actionForm"
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
            <CommandsFormTab disabledActionType={editing} />
          )}

          {currentTab === 'Output' && (
            <OutputFormTab />
          )}

          {currentTab === 'Remediation' && (
            <RemediationFormTabs actionId={initialValues?.action_id} />
          )}

          <div style={{
            marginTop: 'auto',
            display: 'flex',
            flexDirection: 'row-reverse',
            gap: theme.spacing(1),
          }}
          >
            <Button
              variant="primary"
              type="submit"
              disabled={isSubmitting || !isDirty}
            >
              {editing ? t('Update') : t('Create')}
            </Button>
            <Button
              variant="secondary"
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

export default ThreatArsenalActionForm;

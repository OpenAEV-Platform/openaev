import { zodResolver } from '@hookform/resolvers/zod';
import { RestartAlt } from '@mui/icons-material';
import { Box, Button, Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { FormProvider, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';

import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { ScopeAssetOutput, ScopeTeamOutput, ThreatArsenalAction } from '../../../../../utils/api-types';
import type { ContractElement, ContractType, EnhancedContractElement } from '../../../../../utils/api-types-custom';
import { zodImplement } from '../../../../../utils/Zod';
import InjectExpectations from '../../../common/injects/expectations/InjectExpectations';
import InjectDocumentsList from '../../../common/injects/form/documents/InjectDocumentsList';
import InjectContentFieldComponent from '../../../common/injects/form/InjectContentFieldComponent';
import InjectTeamsList from '../../../common/injects/form/teams/InjectTeamsList';
import { isInjectContentType, isRequiredField, isVisibleField } from '../../../common/injects/utils';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';
import { type ActionDetailData, type InjectDocumentInput } from '../types';
import ActionFormButtons from './ActionFormButtons';
import ActionScopeChips from './ActionScopeChips';
import {
  applyAutoLinks,
  applyPredefinedExpectations,
  buildContractDefaults,
  EXPECTATION_FIELD_TYPE,
  EXPECTATIONS_CONTENT_KEY,
  getAutoLinkedFieldKeys,
  getContractFieldDefaultValue,
  isExpectationInput,
  normalizeFieldLinks,
  parseContractFields,
  stripFrontendMetadataKeys,
} from './ConfigureActionDetail.utils';
import FieldOutputLink, { type FieldLink } from './FieldOutputLink';

interface ConfigureActionDetailProps {
  /** Whether the hosting drawer step is active (feeds the inject-data panel + contract fetch). */
  open?: boolean;
  action: ThreatArsenalAction | null;
  validAssets: ScopeAssetOutput[];
  validTeams?: ScopeTeamOutput[];
  initialData?: ActionDetailData;
  onClose: () => void;
  onSave: (data: ActionDetailData) => void;
}

// Targeting field types/keys owned by the scope definition - excluded from the generic form.
const PAYLOAD_HIDDEN_TYPES = new Set<ContractType>(['asset', 'asset-group']);
const INJECTOR_HIDDEN_TYPES = new Set<ContractType>(['asset', 'asset-group', 'targeted-asset']);
const INJECTOR_HIDDEN_KEYS = new Set([
  'target_selector', // type of targets
  'assets', // targeted assets
  'asset_groups', // targeted asset groups
  'target_property_selector', // targeted asset property
  'targets', // manual targets
]);
// Handled by dedicated widgets/sections rather than the dynamic-content loop.
const DEDICATED_TYPES = new Set<ContractType>([
  'team',
  'attachment',
  'asset',
  'asset-group',
  'article',
  'challenge',
  'expectation',
]);
// Field types whose value can be fed by a workflow output (the "Link an Output" adornment).
const LINKABLE_PRIMITIVE_TYPES = new Set<ContractType>(['text', 'number', 'select', 'choice']);

interface FormValues {
  inject_title: string;
  inject_content: Record<string, unknown>;
  inject_teams: string[];
  inject_all_teams: boolean;
  inject_documents: InjectDocumentInput[];
  inject_asset_groups: string[];
}

const emptyDefaults = (): FormValues => ({
  inject_title: '',
  inject_content: {},
  inject_teams: [],
  inject_all_teams: false,
  inject_documents: [],
  inject_asset_groups: [],
});

const buildEnhancedField = (
  field: ContractElement,
  fields: ContractElement[],
  values: FormValues,
): EnhancedContractElement => {
  const isInjectContent = isInjectContentType(field.type);
  return {
    ...field,
    key: isInjectContent ? `inject_content.${field.key}` : `inject_${field.key}`,
    originalKey: field.key,
    isInjectContentType: isInjectContent && field.type !== EXPECTATION_FIELD_TYPE,
    isVisible: isVisibleField(field, fields, values),
    isInMandatoryGroup: !!field.mandatoryGroups?.length,
    mandatoryGroupContractElementLabels: '',
    settings: { required: isRequiredField(field, fields, values) },
  };
};

const ConfigureActionDetail: FunctionComponent<ConfigureActionDetailProps> = ({
  open = true,
  action,
  validAssets,
  validTeams = [],
  initialData,
  onClose,
  onSave,
}) => {
  const { t, tPick } = useFormatter();

  const isPayload = !!action?.action_payload;

  const schema = useMemo(
    () => zodImplement<FormValues>().with({
      inject_title: z.string().min(1, { message: t('Title is required') }),
      inject_content: z.record(z.string(), z.unknown()),
      inject_teams: z.array(z.string()),
      inject_all_teams: z.boolean(),
      inject_documents: z.array(z.object({
        document_id: z.string(),
        document_attached: z.boolean(),
      })),
      inject_asset_groups: z.array(z.string()),
    }),
    [t],
  );

  const methods = useForm<FormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: emptyDefaults(),
  });

  const { handleSubmit, reset, setValue, getValues, control, formState: { isValid } } = methods;

  // Reactive snapshot of the form values, used to recompute field visibility/required-ness.
  const watchedValues = useWatch({ control }) as FormValues;

  const [contractFields, setContractFields] = useState<ContractElement[]>([]);
  const [loadingContract, setLoadingContract] = useState(false);

  // Output links per field (raw contract field key -> FieldLink); chaining-specific state.
  const [fieldLinks, setFieldLinks] = useState<Record<string, FieldLink>>({});

  // Reset the whole form + contract when the edited action changes.
  useEffect(() => {
    if (!action) return;
    const label = initialData?.inject_title ?? (action.action_labels ? tPick(action.action_labels) : '');
    const defaultTeams = initialData?.inject_teams
      ?? validTeams.map(team => team.team_id).filter((id): id is string => !!id);
    reset({
      ...emptyDefaults(),
      inject_title: label,
      // Never seed the editor with frontend-only metadata keys (see stripFrontendMetadataKeys).
      inject_content: stripFrontendMetadataKeys((initialData?.inject_content ?? {}) as Record<string, unknown>),
      inject_teams: defaultTeams,
      inject_all_teams: initialData?.inject_all_teams ?? false,
      inject_documents: initialData?.inject_documents ?? [],
      inject_asset_groups: initialData?.inject_asset_groups ?? [],
    });
    setFieldLinks(normalizeFieldLinks(initialData?.inject_field_links));
    setContractFields(initialData?.contract_fields ?? []);

    const contractId = initialData?.inject_injector_contract ?? action.injector_contract_id;
    setLoadingContract(true);
    directFetchInjectorContract(contractId)
      .then((res: { data: { injector_contract_content?: string } }) => {
        if (!res.data?.injector_contract_content) return;
        try {
          const parsed = JSON.parse(res.data.injector_contract_content);
          const fields = (parsed.fields ?? []) as ContractElement[];
          setContractFields(fields);
          // Use sanitized initial content when editing, otherwise contract defaults.
          const baseContent = initialData?.inject_content ?? buildContractDefaults(fields);
          const content = applyPredefinedExpectations(
            stripFrontendMetadataKeys(baseContent as Record<string, unknown>),
            fields,
          );
          setValue('inject_content', content);
        } catch {
          setContractFields([]);
        }
      })
      .catch(() => setContractFields([]))
      .finally(() => setLoadingContract(false));
  }, [action, initialData]);

  // Auto-link action input fields with their default primitive type when available.
  // Example: field argumentType "ipv4" -> outputTypes ["ipv4"].
  useEffect(() => {
    // In edit mode, preserve persisted links exactly as-is (no auto-link recomputation).
    if (initialData) return;
    if (contractFields.length === 0) return;
    setFieldLinks(prev => applyAutoLinks(contractFields, prev));
  }, [initialData, contractFields]);

  const handleResetDefaults = () => {
    const current = getValues('inject_content');
    setValue('inject_content', {
      ...buildContractDefaults(contractFields),
      [EXPECTATIONS_CONTENT_KEY]: current?.[EXPECTATIONS_CONTENT_KEY],
    });
    setFieldLinks({});
  };

  const handleLinkField = (fieldKey: string, link: FieldLink) => setFieldLinks(prev => ({
    ...prev,
    [fieldKey]: link,
  }));

  const handleUnlinkField = (fieldKey: string) => setFieldLinks((prev) => {
    const next = { ...prev };
    delete next[fieldKey];
    return next;
  });

  const handleToggleLocalScope = (fieldKey: string, localScope: boolean) => setFieldLinks(prev => ({
    ...prev,
    [fieldKey]: {
      ...prev[fieldKey],
      localScope,
    },
  }));

  const onSubmit = (formData: FormValues) => {
    if (!action) return;
    // Final safety: remove frontend-only keys before sending to backend.
    const contentWithExpectations = applyPredefinedExpectations(
      stripFrontendMetadataKeys(formData.inject_content),
      contractFields,
    );
    onSave({
      inject_title: formData.inject_title.trim(),
      inject_injector_contract: action.injector_contract_id,
      inject_assets: validAssets.map(a => a.asset_id).filter((id): id is string => !!id),
      inject_asset_groups: formData.inject_asset_groups ?? [],
      inject_teams: formData.inject_all_teams ? [] : (formData.inject_teams ?? []),
      inject_all_teams: formData.inject_all_teams ?? false,
      inject_documents: formData.inject_documents ?? [],
      inject_content: contentWithExpectations,
      inject_field_links: fieldLinks,
      contract_fields: contractFields,
    });
  };

  const enhancedFields = useMemo(
    () => contractFields.map(field => buildEnhancedField(field, contractFields, watchedValues)),
    [contractFields, watchedValues],
  );

  const hasTeamField = useMemo(() => !isPayload && contractFields.some(f => f.type === 'team'), [contractFields, isPayload]);
  const hasAttachmentField = useMemo(() => !isPayload && contractFields.some(f => f.type === 'attachment'), [contractFields, isPayload]);
  const teamFieldEnhanced = useMemo(() => enhancedFields.find(f => f.type === 'team'), [enhancedFields]);

  // Dynamic content fields (typed widgets), excluding scope-owned targeting + dedicated sections.
  const contentEnhancedFields = useMemo(() => {
    return enhancedFields.filter((ef) => {
      if (ef.readOnly || DEDICATED_TYPES.has(ef.type)) return false;
      if (!ef.isVisible) return false;
      if (!isPayload && (ef.originalKey.startsWith('targeted-property-') || ef.originalKey.startsWith('targeted-asset-separator-'))) return false;
      if (isPayload) return !PAYLOAD_HIDDEN_TYPES.has(ef.type);
      if (INJECTOR_HIDDEN_TYPES.has(ef.type)) return false;
      if (INJECTOR_HIDDEN_KEYS.has(ef.originalKey)) return false;
      return true;
    });
  }, [enhancedFields, isPayload]);

  const expectationField = useMemo(
    () => contractFields.find(field => field.type === EXPECTATION_FIELD_TYPE),
    [contractFields],
  );

  const autoLinkedFields = useMemo(
    () => (isPayload ? getAutoLinkedFieldKeys(contractFields) : new Set<string>()),
    [isPayload, contractFields],
  );

  const noLinkFields = useMemo(
    () =>
      isPayload
        ? new Set(
            contractFields
              .filter(f => f.key.startsWith('targeted-property-') || f.key.startsWith('targeted-asset-separator-'))
              .map(f => f.key),
          )
        : new Set<string>(),
    [isPayload, contractFields],
  );

  const expectations = useMemo(() => {
    if (!expectationField) return [];
    const value = watchedValues?.inject_content?.[EXPECTATIONS_CONTENT_KEY]
      ?? watchedValues?.inject_content?.[expectationField.key]
      ?? getContractFieldDefaultValue(expectationField);
    if (!Array.isArray(value)) return [];
    return value.filter(isExpectationInput);
  }, [expectationField, watchedValues]);

  const renderContentField = (ef: EnhancedContractElement) => {
    const rawKey = ef.originalKey;
    const link = fieldLinks[rawKey] ?? null;
    const isAutoLinked = autoLinkedFields.has(rawKey);
    const showLink = !noLinkFields.has(rawKey) && (LINKABLE_PRIMITIVE_TYPES.has(ef.type) || isAutoLinked);

    return (
      <Box key={ef.key}>
        {!link && <InjectContentFieldComponent field={ef} />}
        {showLink && (
          <FieldOutputLink
            panelOpen={open}
            fieldKey={rawKey}
            fieldLabel={t(ef.label) || rawKey}
            link={link}
            readOnly={isAutoLinked}
            onLink={handleLinkField}
            onUnlink={handleUnlinkField}
            onToggleLocalScope={handleToggleLocalScope}
          />
        )}
      </Box>
    );
  };

  return (
    <FormProvider {...methods}>
      <Box
        component="form"
        onSubmit={handleSubmit(onSubmit)}
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 3,
        }}
      >
        <TextFieldController name="inject_title" label={t('Title')} required />

        <ActionScopeChips isPayload={isPayload} validAssets={validAssets} validTeams={validTeams} />

        {hasTeamField && (
          <Box>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              mb: 1,
            }}
            >
              <Typography variant="subtitle2" fontWeight={600}>{t('Targeted teams')}</Typography>
              <SwitchFieldController
                name="inject_all_teams"
                label={<strong>{t('All teams')}</strong>}
                disabled={teamFieldEnhanced?.readOnly}
                size="small"
              />
            </Box>
            <InjectTeamsList />
          </Box>
        )}

        {(contentEnhancedFields.length > 0 || loadingContract) && (
          <Box>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              mb: 1,
            }}
            >
              <Typography variant="subtitle2" fontWeight={600}>{t('Inject data')}</Typography>
              <Button size="small" startIcon={<RestartAlt />} onClick={handleResetDefaults}>
                {t('Reset default value')}
              </Button>
            </Box>
            {loadingContract && (
              <Typography variant="body2" color="text.secondary">
                {t('Loading contract fields...')}
              </Typography>
            )}
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}
            >
              {contentEnhancedFields.map(renderContentField)}
            </Box>
          </Box>
        )}

        {hasAttachmentField && (
          <Box>
            <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>{t('Inject documents')}</Typography>
            <InjectDocumentsList hasAttachments />
          </Box>
        )}

        {expectationField && (
          <Box>
            <Typography variant="subtitle2" fontWeight={600} sx={{ mb: 1 }}>{t('Inject expectations')}</Typography>
            <InjectExpectations
              expectationDatas={expectations}
              handleExpectations={updatedExpectations => setValue('inject_content', {
                ...getValues('inject_content'),
                [EXPECTATIONS_CONTENT_KEY]: updatedExpectations,
              })}
              availableExpectations={expectationField.availableExpectations ?? []}
              inline
            />
            {expectations.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {t('No expectations for this action.')}
              </Typography>
            )}
          </Box>
        )}

        <ActionFormButtons disabled={!isValid} onCancel={onClose} />
      </Box>
    </FormProvider>
  );
};

export default ConfigureActionDetail;

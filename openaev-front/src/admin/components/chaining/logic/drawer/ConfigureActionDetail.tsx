import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Typography } from '@mui/material';
import { type FunctionComponent, useEffect, useMemo, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { z } from 'zod';

import { directFetchInjectorContract } from '../../../../../actions/InjectorContracts';
import Drawer from '../../../../../components/common/Drawer';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import type { ScopeAssetOutput, ThreatArsenalAction } from '../../../../../utils/api-types';
import type { ContractElement } from '../../../../../utils/api-types-custom';
import { zodImplement } from '../../../../../utils/Zod';
import DrawerBreadcrumb from '../../../common/DrawerBreadcrumb';
import InjectExpectations from '../../../common/injects/expectations/InjectExpectations';
import useArgumentTypes from '../../../threat_arsenal/form/useArgumentTypes';
import { type ActionDetailData } from '../types';
import ActionFormButtons from './ActionFormButtons';
import ActionInjectData from './ActionInjectData';
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
import { type FieldLink } from './InjectDataFieldItem';

interface ConfigureActionDetailProps {
  open: boolean;
  action: ThreatArsenalAction | null;
  validAssets: ScopeAssetOutput[];
  initialData?: ActionDetailData;
  onClose: () => void;
  onBack: () => void;
  onBackToRoot: () => void;
  onSave: (data: ActionDetailData) => void;
}

// Targeting field types/keys handled by the scope definition — excluded from the generic form.
const PAYLOAD_HIDDEN_TYPES = new Set(['asset', 'asset-group']);
const INJECTOR_HIDDEN_TYPES = new Set(['asset', 'asset-group', 'targeted-asset']);
const INJECTOR_HIDDEN_KEYS = new Set([
  'target_selector', // type of targets
  'assets', // targeted assets
  'asset_groups', // targeted asset groups
  'target_property_selector', // targeted asset property
  'targets', // manual targets
]);

interface FormValues { inject_title: string }

const ConfigureActionDetail: FunctionComponent<ConfigureActionDetailProps> = ({
  open,
  action,
  validAssets,
  initialData,
  onClose,
  onBack,
  onBackToRoot,
  onSave,
}) => {
  const { t, tPick } = useFormatter();
  const { argumentWithDefaultValueTypes } = useArgumentTypes();

  const isPayload = !!action?.action_payload;

  const schema = useMemo(
    () => zodImplement<FormValues>().with({ inject_title: z.string().min(1, { message: t('Title is required') }) }),
    [t],
  );

  const methods = useForm<FormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: { inject_title: '' },
  });

  const { handleSubmit, reset, formState: { isValid } } = methods;

  // Contract fields
  const [contractFields, setContractFields] = useState<ContractElement[]>([]);
  const [loadingContract, setLoadingContract] = useState(false);

  // Dynamic field values (inject_content)
  const [fieldValues, setFieldValues] = useState<Record<string, unknown>>({});

  // Output links per field (field key → FieldLink)
  const [fieldLinks, setFieldLinks] = useState<Record<string, FieldLink>>({});

  // Reset state when action changes
  useEffect(() => {
    if (action) {
      const label = initialData?.inject_title
        ?? (action.action_labels ? tPick(action.action_labels) : '');
      reset({ inject_title: label });
      // Never keep frontend-only metadata in runtime payload content.
      setFieldValues(stripFrontendMetadataKeys(initialData?.inject_content ?? {}));
      setFieldLinks(normalizeFieldLinks(initialData?.inject_field_links));
      setContractFields(initialData?.contract_fields ?? []);

      // Fetch injector contract content
      const contractId = initialData?.inject_injector_contract ?? action.injector_contract_id;
      setLoadingContract(true);
      directFetchInjectorContract(contractId)
        .then((res: { data: { injector_contract_content?: string } }) => {
          const fields = parseContractFields(res.data?.injector_contract_content);
          setContractFields(fields);
          // Use sanitized initial content when editing, otherwise contract defaults.
          const baseContent = initialData?.inject_content ?? buildContractDefaults(fields);
          setFieldValues(
            applyPredefinedExpectations(
              stripFrontendMetadataKeys(baseContent as Record<string, unknown>) as Record<
                string,
                unknown
              >,
              fields,
            ),
          );
        })
        .catch(() => setContractFields([]))
        .finally(() => setLoadingContract(false));
    }
  }, [action, initialData]);

  // Auto-link action input fields with their default primitive type when available.
  // Example: field argumentType "ipv4" -> outputTypes ["ipv4"].
  useEffect(() => {
    if (contractFields.length === 0) return;
    setFieldLinks(prev => applyAutoLinks(
      contractFields,
      prev,
      argumentWithDefaultValueTypes,
    ));
  }, [contractFields, argumentWithDefaultValueTypes]);

  // Resets all input argument fields to contract defaults.
  // Expectations are explicitly restored from current state because they are not part of this reset.
  const handleResetDefaults = () => {
    setFieldValues(prev => ({
      ...buildContractDefaults(contractFields),
      [EXPECTATIONS_CONTENT_KEY]: prev[EXPECTATIONS_CONTENT_KEY],
    }));
    setFieldLinks({});
  };

  // Updates a single input argument field value.
  const handleFieldValueChange = (fieldKey: string, value: string) => {
    setFieldValues(prev => ({
      ...prev,
      [fieldKey]: value,
    }));
  };

  // Links a field to a workflow scope variable.
  const handleLinkField = (fieldKey: string, link: FieldLink) => {
    setFieldLinks(prev => ({
      ...prev,
      [fieldKey]: link,
    }));
  };

  // Removes the scope variable link from a field, reverting it to a plain value.
  const handleUnlinkField = (fieldKey: string) => {
    setFieldLinks((prev) => {
      const next = { ...prev };
      delete next[fieldKey];
      return next;
    });
  };

  // Toggles whether a linked field reads from local (step) scope or global (workflow) scope.
  const handleToggleLocalScope = (fieldKey: string, localScope: boolean) => {
    setFieldLinks(prev => ({
      ...prev,
      [fieldKey]: {
        ...prev[fieldKey],
        localScope,
      },
    }));
  };

  const onSubmit = (formData: FormValues) => {
    if (!action) return;
    // Final safety: remove frontend-only keys before sending to backend.
    const contentWithExpectations = applyPredefinedExpectations(
      stripFrontendMetadataKeys(fieldValues),
      contractFields,
    );
    onSave({
      inject_title: formData.inject_title.trim(),
      inject_injector_contract: action.injector_contract_id,
      inject_assets: validAssets.map(a => a.asset_id).filter((id): id is string => !!id),
      inject_content: contentWithExpectations,
      inject_field_links: fieldLinks,
      contract_fields: contractFields,
    });
  };

  const actionLabel = useMemo(() => {
    if (!action?.action_labels) return '';
    return tPick(action.action_labels);
  }, [action, tPick]);

  // Expectations are not part of the generic input data because they are handled separately
  // in their own dedicated section via <InjectExpectations>.
  const inputArgumentFields = useMemo(() => {
    return contractFields.filter((f) => {
      if (f.readOnly || f.type === EXPECTATION_FIELD_TYPE) return false;
      if (!isPayload && (f.key.startsWith('targeted-property-') || f.key.startsWith('targeted-asset-separator-'))) return false;
      if (isPayload) return !PAYLOAD_HIDDEN_TYPES.has(f.type);
      if (INJECTOR_HIDDEN_TYPES.has(f.type)) return false;
      if (INJECTOR_HIDDEN_KEYS.has(f.key)) return false;
      return true;
    });
  }, [contractFields, isPayload]);

  const expectationField = useMemo(() => {
    return contractFields.find(field => field.type === EXPECTATION_FIELD_TYPE);
  }, [contractFields]);

  const autoLinkedFields = useMemo(
    () => (isPayload ? getAutoLinkedFieldKeys(contractFields) : new Set<string>()),
    [isPayload, contractFields],
  );

  const noLinkFields = useMemo(
    () =>
      isPayload
        ? new Set(
            contractFields
              .filter(
                f =>
                  f.key.startsWith('targeted-property-')
                  || f.key.startsWith('targeted-asset-separator-'),
              )
              .map(f => f.key),
          )
        : new Set<string>(),
    [isPayload, contractFields],
  );

  const expectations = useMemo(() => {
    if (!expectationField) return [];
    const value = fieldValues[EXPECTATIONS_CONTENT_KEY]
      ?? fieldValues[expectationField.key]
      ?? getContractFieldDefaultValue(expectationField);
    if (!Array.isArray(value)) return [];
    return value.filter(isExpectationInput);
  }, [expectationField, fieldValues]);

  return (
    <Drawer
      open={open}
      handleClose={onClose}
      title={actionLabel}
    >
      <FormProvider {...methods}>
        <Box
          component="form"
          onSubmit={handleSubmit(onSubmit)}
          sx={{
            display: 'flex',
            flexDirection: 'column',
            gap: 2,
          }}
        >
          <DrawerBreadcrumb
            grandParentLabel={t('Add component')}
            onBackToGrandParent={onBackToRoot}
            parentLabel={t('Add actions')}
            currentLabel={actionLabel}
            onBack={onBack}
          />

          <TextFieldController
            name="inject_title"
            label={t('Title')}
            required
          />

          <ActionScopeChips isPayload={isPayload} validAssets={validAssets} />

          <ActionInjectData
            panelOpen={open}
            loading={loadingContract}
            fields={inputArgumentFields}
            fieldValues={fieldValues}
            fieldLinks={fieldLinks}
            autoLinkedFields={autoLinkedFields}
            noLinkFields={noLinkFields}
            onResetDefaults={handleResetDefaults}
            onValueChange={handleFieldValueChange}
            onLink={handleLinkField}
            onUnlink={handleUnlinkField}
            onToggleLocalScope={handleToggleLocalScope}
          />

          {expectationField && (
            <Box>
              <InjectExpectations
                expectationDatas={expectations}
                handleExpectations={updatedExpectations => setFieldValues(prev => ({
                  ...prev,
                  [EXPECTATIONS_CONTENT_KEY]: updatedExpectations,
                }))}
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
    </Drawer>
  );
};

export default ConfigureActionDetail;

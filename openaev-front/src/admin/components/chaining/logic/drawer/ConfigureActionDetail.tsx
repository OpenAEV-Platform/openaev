import { zodResolver } from '@hookform/resolvers/zod';
import { InfoOutlined, RestartAlt } from '@mui/icons-material';
import {
  Box,
  Button,
  Chip,
  Tooltip,
  Typography,
} from '@mui/material';
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
import { type ActionDetailData } from '../types';
import {
  applyExpectationDefaults,
  buildContractDefaults,
  EXPECTATION_FIELD_TYPE,
  EXPECTATIONS_CONTENT_KEY,
  getContractFieldDefaultValue,
  isExpectationInput,
} from './ConfigureActionDetail.utils';
import InjectDataFieldItem, { type FieldLink } from './InjectDataFieldItem';

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
      setFieldValues(initialData?.inject_content ?? {});
      setFieldLinks(initialData?.inject_field_links ?? {});
      setContractFields(initialData?.contract_fields ?? []);

      // Fetch injector contract content
      const contractId = initialData?.inject_injector_contract ?? action.injector_contract_id;
      setLoadingContract(true);
      directFetchInjectorContract(contractId)
        .then((res: { data: { injector_contract_content?: string } }) => {
          if (res.data?.injector_contract_content) {
            try {
              const parsed = JSON.parse(res.data.injector_contract_content);
              const fields = (parsed.fields ?? []) as ContractElement[];
              setContractFields(fields);
              setFieldValues(
                applyExpectationDefaults(
                  (initialData?.inject_content ?? buildContractDefaults(fields)) as Record<
                    string,
                    unknown
                  >,
                  fields,
                ),
              );
            } catch {
              setContractFields([]);
            }
          }
        })
        .catch(() => setContractFields([]))
        .finally(() => setLoadingContract(false));
    }
  }, [action, initialData]);

  const handleResetDefaults = () => {
    setFieldValues(applyExpectationDefaults(buildContractDefaults(contractFields), contractFields));
    setFieldLinks({});
  };

  const handleFieldValueChange = (fieldKey: string, value: string) => {
    setFieldValues(prev => ({
      ...prev,
      [fieldKey]: value,
    }));
  };

  const handleLinkField = (fieldKey: string, link: FieldLink) => {
    setFieldLinks(prev => ({
      ...prev,
      [fieldKey]: link,
    }));
  };

  const handleUnlinkField = (fieldKey: string) => {
    setFieldLinks((prev) => {
      const next = { ...prev };
      delete next[fieldKey];
      return next;
    });
  };

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
    const normalizedInjectContent = applyExpectationDefaults(fieldValues, contractFields);
    onSave({
      inject_title: formData.inject_title.trim(),
      inject_injector_contract: action.injector_contract_id,
      inject_assets: validAssets.map(a => a.asset_id).filter((id): id is string => !!id),
      inject_content: normalizedInjectContent,
      inject_field_links: fieldLinks,
      contract_fields: contractFields,
    });
  };

  const actionLabel = useMemo(() => {
    if (!action?.action_labels) return '';
    return tPick(action.action_labels);
  }, [action, tPick]);

  const visibleFields = useMemo(() => {
    return contractFields.filter(f => !f.readOnly && f.type !== EXPECTATION_FIELD_TYPE);
  }, [contractFields]);

  const expectationField = useMemo(() => {
    return contractFields.find(field => field.type === EXPECTATION_FIELD_TYPE);
  }, [contractFields]);

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

          {/* Initial Target Assets */}
          <Box>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              mb: 1,
            }}
            >
              <Typography variant="subtitle2" fontWeight={600}>
                {t('Initial Target Assets')}
              </Typography>
              <Tooltip
                title={t('Additional endpoints may be included during simulation based on real decision logic.')}
              >
                <InfoOutlined fontSize="small" color="info" />
              </Tooltip>
            </Box>
            {validAssets.length > 0 ? (
              <Box sx={{
                display: 'flex',
                flexWrap: 'wrap',
                gap: 0.5,
              }}
              >
                {validAssets.map(asset => (
                  <Chip
                    key={asset.asset_id ?? ''}
                    label={asset.asset_name ?? ''}
                    size="small"
                    variant="filled"
                  />
                ))}
              </Box>
            ) : (
              <Typography variant="body2" color="text.secondary">
                {t('No assets in the allow list.')}
              </Typography>
            )}
          </Box>

          {/* Inject Data (dynamic contract fields) */}
          <Box>
            <Box sx={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              mb: 1,
            }}
            >
              <Typography variant="subtitle2" fontWeight={600}>
                {t('Inject Data')}
              </Typography>
              <Button
                size="small"
                startIcon={<RestartAlt />}
                onClick={handleResetDefaults}
              >
                {t('Reset default value')}
              </Button>
            </Box>
            {loadingContract && (
              <Typography variant="body2" color="text.secondary">
                {t('Loading contract fields...')}
              </Typography>
            )}
            {!loadingContract && visibleFields.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {t('No configuration fields for this action.')}
              </Typography>
            )}
            <Box sx={{
              display: 'flex',
              flexDirection: 'column',
              gap: 2,
            }}
            >
              {visibleFields.map((field) => {
                const fieldLabel = t(field.label) || field.key;
                const defaultVal = field.defaultValue != null ? String(field.defaultValue) : undefined;
                return (
                  <InjectDataFieldItem
                    key={field.key}
                    fieldKey={field.key}
                    fieldLabel={fieldLabel}
                    value={String(fieldValues[field.key] ?? '')}
                    defaultValue={defaultVal}
                    link={fieldLinks[field.key] ?? null}
                    onValueChange={handleFieldValueChange}
                    onLink={handleLinkField}
                    onUnlink={handleUnlinkField}
                    onToggleLocalScope={handleToggleLocalScope}
                  />
                );
              })}
            </Box>
          </Box>

          {/* Expectations */}
          {expectationField && (
            <Box>
              <InjectExpectations
                hasTitle={t('Inject Expectations')}
                expectationDatas={expectations}
                handleExpectations={updatedExpectations => setFieldValues(prev => ({
                  ...prev,
                  [EXPECTATIONS_CONTENT_KEY]: updatedExpectations,
                }))}
                predefinedExpectations={expectationField.predefinedExpectations ?? []}
                availableExpectations={expectationField.availableExpectations ?? []}
              />
            </Box>
          )}

          {/* Actions */}
          <Box sx={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: 1,
            mt: 1,
          }}
          >
            <Button variant="outlined" color="primary" onClick={onClose}>
              {t('Cancel')}
            </Button>
            <Button
              variant="contained"
              color="secondary"
              type="submit"
              disabled={!isValid}
            >
              {t('Save')}
            </Button>
          </Box>
        </Box>
      </FormProvider>
    </Drawer>
  );
};

export default ConfigureActionDetail;

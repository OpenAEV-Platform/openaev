import { zodResolver } from '@hookform/resolvers/zod';
import { Button, CircularProgress, FormHelperText, InputLabel } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type BaseSyntheticEvent, type FunctionComponent, type SyntheticEvent, useEffect, useMemo, useState } from 'react';
import {
  Controller,
  FormProvider,
  useForm,
  useFormContext,
  useWatch,
} from 'react-hook-form';
import { z } from 'zod/v4';

import { fetchCredentialContracts } from '../../../../actions/assets/credential-actions';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import TagFieldController from '../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import DOTS from '../../../../constants/Strings';
import { type CredentialContractField, type CredentialContractOutput, type CredentialInput } from '../../../../utils/api-types';
import type {
  ContractType,
  EnhancedContractElement,
} from '../../../../utils/api-types-custom';
import InjectContentFieldComponent from '../../common/injects/form/InjectContentFieldComponent';
import { humanizeEnum } from '../asset-categories';

interface Props {
  /** Receives the multipart payload: an `input` JSON part plus one part per uploaded file. */
  onSubmit: (formData: FormData, event?: BaseSyntheticEvent) => Promise<unknown> | void;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<CredentialInput>;
}

interface FileFieldProps {
  name: string;
  label: string;
  required?: boolean;
}

/**
 * Upload control for a contract field of type `file`.
 *
 * <p>The form value is either a freshly picked `File` — the only case producing a multipart part —
 * or the write-only placeholder set in edit mode, which means "keep the stored file".
 */
const FileFieldController: FunctionComponent<FileFieldProps> = ({
  name,
  label,
  required = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { onChange, value }, fieldState: { error } }) => {
        const storedPlaceholder = typeof value === 'string' && value.length > 0 ? value : '';
        const selectedName = value instanceof File ? value.name : storedPlaceholder;
        return (
          <div>
            <InputLabel required={required} error={!!error}>{label}</InputLabel>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: theme.spacing(1),
            }}
            >
              <Button variant="outlined" component="label" size="small">
                {t('Select a file')}
                <input
                  type="file"
                  accept="application/json,.json"
                  hidden
                  onChange={event => onChange(event.target.files?.[0] ?? null)}
                />
              </Button>
              <span>{selectedName || t('No file selected')}</span>
            </div>
            {error?.message && <FormHelperText error>{error.message}</FormHelperText>}
          </div>
        );
      }}
    />
  );
};

const CredentialForm: FunctionComponent<Props> = ({
  onSubmit,
  handleClose,
  editing = false,
  initialValues,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const [contracts, setContracts] = useState<CredentialContractOutput[]>([]);
  const [isLoadingContracts, setIsLoadingContracts] = useState(true);

  const dynamicFieldValueSchema = z.union([
    z.string(),
    z.number(),
    z.boolean(),
    z.array(z.string()),
    z.instanceof(File),
    z.null(),
    z.undefined(),
  ]);
  type CredentialDynamicValue = z.infer<typeof dynamicFieldValueSchema>;
  type CredentialFormValues = CredentialInput & Record<string, CredentialDynamicValue>;

  const matchesCondition = (
    values: CredentialFormValues,
    conditionField?: string,
    conditionValue?: string,
  ): boolean => {
    if (!conditionField || !conditionValue) {
      return false;
    }
    return values[conditionField] === conditionValue;
  };

  // Accepted enum values are derived from the fetched contracts: adding a provider backend-side
  // is enough, nothing has to be hardcoded here anymore.
  const acceptedTypes = useMemo(
    () => new Set(contracts.map(contract => contract.credential_type)),
    [contracts],
  );
  const acceptedAuthMethods = useMemo(
    () => new Set(contracts.map(contract => contract.credential_auth_method)),
    [contracts],
  );

  const schema = useMemo(
    () => z
      .object({
        credential_name: z.string().min(1, { message: t('Should not be empty') }),
        credential_description: z.string().optional(),
        credential_type: z.custom<CredentialInput['credential_type']>(
          value => acceptedTypes.size === 0
            || acceptedTypes.has(value as CredentialInput['credential_type']),
          { message: t('Should not be empty') },
        ),
        credential_auth_method: z.custom<CredentialInput['credential_auth_method']>(
          value => acceptedAuthMethods.size === 0
            || acceptedAuthMethods.has(value as CredentialInput['credential_auth_method']),
          { message: t('Should not be empty') },
        ),
        credential_tags: z.array(z.string()).optional(),
      })
      .catchall(dynamicFieldValueSchema)
      .check(({ value, issues }) => {
        const values = value;
        const contract = contracts.find(
          c => c.credential_type === values.credential_type
            && c.credential_auth_method === values.credential_auth_method,
        );

        if (!contract) {
          return;
        }

        (contract.fields || [])
          .filter((field: CredentialContractField) => {
            const isVisible = !field.visible_condition_field || matchesCondition(
              values,
              field.visible_condition_field,
              field.visible_condition_value,
            );
            const isRequired = field.required || matchesCondition(
              values,
              field.mandatory_condition_field,
              field.mandatory_condition_value,
            );
            return isVisible && isRequired;
          })
          .forEach((field: CredentialContractField) => {
            const fieldName = field.field_name;
            if (!fieldName || !(fieldName in values)) {
              return;
            }

            const fieldValue = values[fieldName as keyof typeof values];
            const isEmptyString = typeof fieldValue === 'string' && fieldValue.trim().length === 0;
            const isMissing = fieldValue === undefined || fieldValue === null || isEmptyString;

            if (isMissing) {
              issues.push({
                code: 'custom',
                input: values,
                path: [fieldName],
                message: t('Should not be empty'),
              });
            }
          });
      }),
    [acceptedAuthMethods, acceptedTypes, contracts, t],
  );

  const methods = useForm<CredentialFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues as CredentialFormValues | undefined,
  });

  const {
    handleSubmit,
    getValues,
    setValue,
    formState: { isDirty, isSubmitting },
  } = methods;

  const selectedType = useWatch({
    control: methods.control,
    name: 'credential_type',
  });
  const selectedAuthMethod = useWatch({
    control: methods.control,
    name: 'credential_auth_method',
  });

  useEffect(() => {
    setIsLoadingContracts(true);
    fetchCredentialContracts()
      .then((result: { data: CredentialContractOutput[] }) => {
        setContracts(result.data ?? []);
      })
      .finally(() => {
        setIsLoadingContracts(false);
      });
  }, []);

  const availableTypes = useMemo(
    () => Array.from(new Set(
      contracts
        .map(contract => contract.credential_type),
    )),
    [contracts],
  );

  const availableAuthMethods = useMemo(
    () => Array.from(new Set(
      contracts
        .filter(contract => contract.credential_type === selectedType)
        .map(contract => contract.credential_auth_method),
    )),
    [contracts, selectedType],
  );

  const selectedContract = useMemo(
    () => contracts.find(
      contract => contract.credential_type === selectedType
        && contract.credential_auth_method === selectedAuthMethod,
    ),
    [contracts, selectedAuthMethod, selectedType],
  );

  const fileFieldNames = useMemo(
    () => new Set(
      (selectedContract?.fields ?? [])
        .filter((field: CredentialContractField) => field.field_type === 'file')
        .map((field: CredentialContractField) => field.field_name)
        .filter((name): name is string => !!name),
    ),
    [selectedContract],
  );

  // Contract-declared defaults are applied once, and only on fields the user has not filled yet,
  // so switching auth method never overwrites a value that was typed in.
  useEffect(() => {
    (selectedContract?.fields ?? []).forEach((field: CredentialContractField) => {
      const fieldName = field.field_name;
      if (!fieldName || field.default_value === undefined || field.default_value === null) {
        return;
      }
      const currentValue = getValues()[fieldName];
      if (currentValue === undefined || currentValue === null || currentValue === '') {
        setValue(fieldName, field.default_value, { shouldDirty: false });
      }
    });
  }, [getValues, selectedContract, setValue]);

  const fieldsToSubscribe = useMemo(() => {
    const names = new Set<string>();
    (selectedContract?.fields ?? []).forEach((field: CredentialContractField) => {
      if (field.mandatory_condition_field) {
        names.add(field.mandatory_condition_field);
      }
      if (field.visible_condition_field) {
        names.add(field.visible_condition_field);
      }
    });
    return Array.from(names);
  }, [selectedContract]);

  const watchedConditionValues = useWatch({
    control: methods.control,
    name: fieldsToSubscribe as (keyof CredentialInput)[],
  });

  const currentFormValues = useMemo(
    () => getValues(),
    [getValues, watchedConditionValues],
  );

  const formatFieldType = (type: CredentialContractField['field_type'],
  ): ContractType => {
    const supportedType = ['select', 'text', 'number', 'checkbox', 'password'];
    if (type == 'select') {
      return 'choice';
    }
    if (type && supportedType.includes(type)) {
      return type as ContractType;
    }
    return 'text';
  };

  const formatField = (field: CredentialContractField): EnhancedContractElement => {
    const isRequired = !!field.required
      || matchesCondition(
        currentFormValues,
        field.mandatory_condition_field,
        field.mandatory_condition_value,
      );

    return {
      originalKey: `${field.field_name}`,
      isInjectContentType: false,
      isInMandatoryGroup: false,
      mandatoryGroupContractElementLabels: '',
      isVisible: true,
      readOnly: false,
      key: `${field.field_name}`,
      type: formatFieldType(field.field_type),
      mandatory: isRequired,
      label: t(`${field.field_name}`) ?? '',
      choices: field.choices?.map((value: string) => ({
        label: t(`${value}`),
        value,
      })),
      cardinality: '1',
      defaultValue: field.default_value ?? undefined,
      settings: { required: isRequired },
      // A stored secret is never echoed back: passwords and uploaded key files are both rendered
      // as a placeholder in edit mode and only sent when the user provides a new value.
      writeOnly: editing && (field.field_type === 'password' || field.field_type === 'file'),
    };
  };

  const handleSubmitSanitized = async (
    values: CredentialFormValues,
    event?: BaseSyntheticEvent,
  ) => {
    const allowedKeys = new Set<string>([
      'credential_name',
      'credential_type',
      'credential_auth_method',
      'credential_tags',
      'credential_description',
      ...((selectedContract?.fields ?? [])
        .map(field => field.field_name)
        .filter((name): name is string => name.length > 0)),
    ]);

    // File fields never travel inside the JSON part: each one gets its own multipart part.
    const sanitizedEntries = Object.entries(values)
      .filter(([key, value]) => allowedKeys.has(key) && value != DOTS && !fileFieldNames.has(key));

    const sanitizedValues = Object.fromEntries(sanitizedEntries) as Partial<CredentialInput>;
    const payload: CredentialInput = {
      ...sanitizedValues,
      credential_name: values.credential_name,
      credential_type: values.credential_type,
      credential_auth_method: values.credential_auth_method,
    };

    const formData = new FormData();
    formData.append('input', new Blob([JSON.stringify(payload)], { type: 'application/json' }));
    fileFieldNames.forEach((fieldName) => {
      const fileValue = values[fieldName];
      if (fileValue instanceof File) {
        formData.append(fieldName, fileValue);
      }
    });

    await onSubmit(formData, event);
  };

  const handleSubmitWithoutPropagation = (e: SyntheticEvent) => {
    e.preventDefault();
    e.stopPropagation();
    void handleSubmit(handleSubmitSanitized)(e);
  };

  return (
    <FormProvider {...methods}>
      <form
        id="credentialForm"
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: theme.spacing(2),
        }}
        onSubmit={handleSubmitWithoutPropagation}
      >
        <TextFieldController
          variant="standard"
          name="credential_name"
          label={t('Name')}
          required
          disabled={isSubmitting}
        />

        <TextFieldController
          variant="standard"
          name="credential_description"
          label={t('Description')}
          multiline
          disabled={isSubmitting}
        />

        <TagFieldController name="credential_tags" label={t('Tags')} disabled={isSubmitting} />

        <SelectFieldController
          name="credential_type"
          label={t('Type')}
          required
          items={availableTypes.map(type => ({
            value: type,
            label: t(`${type}`),
          }))}
          disabled={isSubmitting || isLoadingContracts || availableTypes.length < 2}
        />

        <SelectFieldController
          name="credential_auth_method"
          label={t('Auth Method')}
          required
          items={availableAuthMethods.map(method => ({
            value: method,
            label: t(`${humanizeEnum(method)}`),
          }))}
          disabled={isSubmitting || isLoadingContracts || availableAuthMethods.length === 0}
        />

        {(selectedContract?.fields ?? [])
          .filter((field: CredentialContractField) => field.visible_condition_field
            ? matchesCondition(
                currentFormValues,
                field.visible_condition_field,
                field.visible_condition_value,
              )
            : true)
          .map(field => (field.field_type === 'file'
            ? (
                <FileFieldController
                  key={field.field_name}
                  name={field.field_name}
                  label={t(`${field.field_name}`)}
                  required={!!field.required}
                />
              )
            : (
                <InjectContentFieldComponent
                  key={field.field_name}
                  field={formatField(field)}
                />
              )))}

        <div
          style={{
            display: 'flex',
            justifyContent: 'flex-end',
            gap: theme.spacing(1),
            marginTop: theme.spacing(1),
          }}
        >
          <Button
            variant="outlined"
            color="primary"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            type="submit"
            disabled={isSubmitting || !isDirty}
          >
            {isSubmitting && (
              <CircularProgress
                size={16}
                color="inherit"
                sx={{ marginRight: theme.spacing(1) }}
              />
            )}
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default CredentialForm;

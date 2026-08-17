import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useEffect, useMemo, useState } from 'react';
import {
  FormProvider,
  type SubmitHandler,
  useForm,
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

interface Props {
  onSubmit: SubmitHandler<CredentialInput>;
  handleClose: () => void;
  editing?: boolean;
  initialValues?: Partial<CredentialInput>;
}

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
    z.null(),
    z.undefined(),
  ]);
  type CredentialDynamicValue = z.infer<typeof dynamicFieldValueSchema>;
  type CredentialFormValues = CredentialInput & Record<string, CredentialDynamicValue>;

  const matchesCondition = (
    values: CredentialFormValues,
    conditionField?: keyof CredentialInput,
    conditionValue?: string,
  ): boolean => {
    if (!conditionField || !conditionValue) {
      return false;
    }
    return values[conditionField] === conditionValue;
  };

  const schema = useMemo(
    () => z
      .object({
        credential_name: z.string().min(1, { message: t('Should not be empty') }),
        credential_description: z.string().optional(),
        credential_type: z.enum(['IDENTITY', 'CLOUD_AWS', 'CLOUD_AZURE']),
        credential_auth_method: z.enum(['USERNAME_PASSWORD', 'HASH', 'AWS_ACCESS_KEY', 'AWS_ASSUME_ROLE', 'AZURE_SERVICE_PRINCIPAL', 'AZURE_MANAGED_IDENTITY']),
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
              field.visible_condition_field as keyof CredentialInput,
              field.visible_condition_value,
            );
            const isRequired = field.required || matchesCondition(
              values,
              field.mandatory_condition_field as keyof CredentialInput,
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
    [contracts, t],
  );

  const methods = useForm<CredentialFormValues>({
    mode: 'onTouched',
    resolver: zodResolver(schema),
    defaultValues: initialValues as CredentialFormValues | undefined,
  });

  const {
    handleSubmit,
    getValues,
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
    () => getValues() as Record<string, unknown>,
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
      key: `${field.field_name}`,
      type: formatFieldType(field.field_type),
      mandatory: isRequired,
      label: t(`${field.field_name}`) ?? '',
      readOnly: false,
      choices: field.choices?.map((value: string) => ({
        label: t(`${value}`),
        value,
      })),
      cardinality: '1',
      defaultValue: undefined,
      settings: { required: isRequired },
      writeOnly: editing && field.field_type === 'password',
    };
  };

  const handleSubmitSanitized: SubmitHandler<CredentialFormValues> = async (values, event) => {
    const allowedKeys = new Set<string>([
      'credential_name',
      'credential_type',
      'credential_auth_method',
      'credential_tags',
      'credential_description',
      ...((selectedContract?.fields ?? [])
        .map(field => field.field_name)
        .filter((name): name is string => typeof name === 'string' && name.length > 0)),
    ]);

    const sanitizedEntries = Object.entries(values)
      .filter(([key, value]) => allowedKeys.has(key) && value != DOTS);

    const sanitizedValues = Object.fromEntries(sanitizedEntries) as Partial<CredentialInput>;
    const payload: CredentialInput = {
      ...sanitizedValues,
      credential_name: values.credential_name,
      credential_type: values.credential_type,
      credential_auth_method: values.credential_auth_method,
    };

    await onSubmit(payload, event);
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
        />

        <TextFieldController
          variant="standard"
          name="credential_description"
          label={t('Description')}
          multiline
        />

        <TagFieldController name="credential_tags" label={t('Tags')} />

        <SelectFieldController
          name="credential_type"
          label={t('Type')}
          required
          items={availableTypes.map(type => ({
            value: type,
            label: t(`${type}`),
          }))}
          disabled={isLoadingContracts || availableTypes.length < 2}
        />

        <SelectFieldController
          name="credential_auth_method"
          label={t('Auth Method')}
          required
          items={availableAuthMethods.map(method => ({
            value: method,
            label: t(`${humanizeEnum(method)}`),
          }))}
          disabled={isLoadingContracts || availableAuthMethods.length === 0}
        />

        {(selectedContract?.fields ?? [])
          .filter((field: CredentialContractField) => field.visible_condition_field
            ? matchesCondition(
                currentFormValues,
                field.visible_condition_field,
                field.visible_condition_value,
              )
            : true)
          .map(field => (
            <InjectContentFieldComponent
              key={field.field_name}
              field={formatField(field)}
            />
          ))}

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
            {editing ? t('Update') : t('Create')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default CredentialForm;

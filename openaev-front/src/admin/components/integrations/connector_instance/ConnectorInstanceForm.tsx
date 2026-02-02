import { zodResolver } from '@hookform/resolvers/zod';
import { InfoOutlined } from '@mui/icons-material';
import { AccordionDetails, Button, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FormEvent, useMemo } from 'react';
import { FormProvider, type SubmitHandler, useFieldArray, useForm } from 'react-hook-form';
import { z } from 'zod';

import { Accordion, AccordionSummary } from '../../../../components/common/Accordion';
import TextField from '../../../../components/fields/TextField';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import DOTS from '../../../../constants/Strings';
import {
  type CatalogConnectorConfiguration, type ConfigurationInput,
  type CreateConnectorInstanceInput,
} from '../../../../utils/api-types';
import { type ContractType, type EnhancedContractElement } from '../../../../utils/api-types-custom';
import InjectContentFieldComponent from '../../common/injects/form/InjectContentFieldComponent';

interface Props {
  catalogConnectorSlug: string;
  initialConfigurationValues: ConfigurationInput[];
  configurationsDefinitionMap: Record<string, CatalogConnectorConfiguration>;
  onSubmit: SubmitHandler<Omit<CreateConnectorInstanceInput, 'catalog_connector_id'>>;
  onClose: () => void;
  isEditing?: boolean;
  disabled?: boolean;
}

const ConnectorInstanceForm = ({
  initialConfigurationValues,
  configurationsDefinitionMap,
  catalogConnectorSlug,
  onClose,
  isEditing = false,
  onSubmit,
  disabled = false,
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  const createDynamicSchema = (configurationsDefMap: Record<string, CatalogConnectorConfiguration>) => {
    const getZodType = (typeFromConf: CatalogConnectorConfiguration['connector_configuration_type'], formatFromConf: CatalogConnectorConfiguration['connector_configuration_format']) => {
      if (formatFromConf === 'URI') {
        return z.string().url(t('Must be a valid URI'));
      }
      if (formatFromConf === 'EMAIL') {
        return z.string().email(t('Must be a valid email'));
      }

      switch (typeFromConf) {
        case 'ARRAY':
          return z.string();
        case 'BOOLEAN':
          return z.boolean();
        case 'OBJECT':
          return z.object({});
        default:
          return z.string();
      }
    };
    const configurationsSchema = z.array(z.object({
      configuration_key: z.string().nonempty(t('Should not be empty')),
      configuration_value: z.unknown().or(z.undefined()).optional(), // This allows undefined but not null
    })).superRefine((confValues, ctx) => {
      confValues.forEach((confValue, index) => {
        const { configuration_value: value, configuration_key: key } = confValue;
        const matchingConf = configurationsDefMap[key];
        if (!matchingConf) {
          return;
        }
        const expectedSchema = getZodType(matchingConf.connector_configuration_type, matchingConf.connector_configuration_format);
        const result = expectedSchema.safeParse(value);
        if (!result.success && value) {
          result.error.issues.forEach((issue) => {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: issue.message,
              path: [index, 'configuration_value'],
            });
          });
          return;
        }

        if (matchingConf.connector_configuration_required
        // should only require password inputs ("writeOnly") during creation, NOT modification
          && (!isEditing || !matchingConf.connector_configuration_writeonly)
          && (!value || value === '')) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t('Should not be empty'),
            path: [index, 'configuration_value'],
          });
        }
      });
    });

    return z.object({ connector_instance_configurations: configurationsSchema });
  };

  const validationSchema = useMemo(() => {
    return createDynamicSchema(configurationsDefinitionMap);
  }, [configurationsDefinitionMap]);

  const methods = useForm<{ connector_instance_configurations: ConfigurationInput[] }>({
    mode: 'onTouched',
    // eslint-disable-next-line @typescript-eslint/ban-ts-comment
    // @ts-ignore
    resolver: zodResolver(validationSchema),
    defaultValues: { connector_instance_configurations: initialConfigurationValues },
  });

  const {
    handleSubmit,
    control,
    formState: { isSubmitting },
  } = methods;

  const { fields: configurationFields } = useFieldArray({
    control,
    name: 'connector_instance_configurations',
  });

  const handleSubmitWithoutDefault = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    handleSubmit(onSubmit)();
  };

  const formatKeyToLabel = (key: string): string => {
    return key
      .replace(/_/g, ' ')
      .replace(/\b\w/g, char => char.toUpperCase());
  };

  const formatFieldType = (configurationType: CatalogConnectorConfiguration['connector_configuration_type'], configurationFormat: CatalogConnectorConfiguration['connector_configuration_format'], isEnum: boolean): ContractType => {
    if (isEnum) {
      return 'choice';
    }
    if (configurationFormat == 'PASSWORD') {
      return 'password';
    }
    if (configurationType == 'BOOLEAN') {
      return 'checkbox';
    }
    if (configurationType == 'INTEGER') {
      return 'number';
    }
    return 'text';
  };

  const formatCatalogConnectorConfigurationToObject = (definition: CatalogConnectorConfiguration, index: number, required: boolean): EnhancedContractElement => {
    return {
      originalKey: `connector_instance_configurations.${index}.configuration_value`,
      isInjectContentType: false,
      isVisible: true,
      isInMandatoryGroup: false,
      mandatoryGroupContractElementLabels: '',
      key: `connector_instance_configurations.${index}.configuration_value`,
      type: formatFieldType(definition.connector_configuration_type, definition.connector_configuration_format, !!definition.connector_configuration_enum?.length),
      mandatory: required,
      label: formatKeyToLabel(definition.connector_configuration_key || ''), // TODO should be not null
      readOnly: false,
      choices: definition.connector_configuration_enum?.map(value => ({
        label: value,
        value,
      })),
      cardinality: definition.connector_configuration_type == 'ARRAY' ? 'n' : '1',
      defaultValue: (isEditing && definition.connector_configuration_writeonly) ? DOTS : (definition.connector_configuration_default || ''),
      settings: { required },
      writeOnly: isEditing && definition.connector_configuration_writeonly,
    };
  };

  const { nameFieldIndex, requiredFields, optionalFields } = useMemo(() => {
    let nameFieldIndex;
    const requiredFields: Array<{
      index: number;
      field: typeof configurationFields[0];
      definition: CatalogConnectorConfiguration;
    }> = [];

    const optionalFields: Array<{
      index: number;
      field: typeof configurationFields[0];
      definition: CatalogConnectorConfiguration;
    }> = [];

    configurationFields.forEach((field, index) => {
      if (['COLLECTOR_NAME', 'INJECTOR_NAME', 'EXECUTOR_NAME'].includes(field.configuration_key)) {
        nameFieldIndex = index;
        return;
      }

      const configDef = configurationsDefinitionMap[field.configuration_key];

      if (configDef?.connector_configuration_required) {
        requiredFields.push({
          index,
          field,
          definition: configDef,
        });
      } else if (configDef) {
        optionalFields.push({
          index,
          field,
          definition: configDef,
        });
      }
    });

    return {
      nameFieldIndex,
      requiredFields,
      optionalFields,
    };
  }, [configurationFields, configurationsDefinitionMap]);

  return (
    <FormProvider {...methods}>
      <form
        noValidate // disabled tooltip
        style={{
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100%',
          gap: theme.spacing(2),
        }}
        id="connectorInstanceForm"
        onSubmit={handleSubmitWithoutDefault}
      >
        <TextFieldController
          name={`connector_instance_configurations.${nameFieldIndex}.configuration_value`}
          label={t('Display name')}
          required
          disabled={disabled || isEditing}
        />
        <TextField id="catalog-connector-slug" label={t('Instance name')} disabled defaultValue={catalogConnectorSlug} />
        {requiredFields.map(({ index, field, definition }) => (
          <div
            key={field.id}
            style={{
              display: 'grid',
              gridTemplateColumns: '1fr auto',
              alignItems: 'start',
              gap: theme.spacing(2),
            }}
          >
            <InjectContentFieldComponent
              key={field.id}
              field={formatCatalogConnectorConfigurationToObject(definition, index, true)}
              readOnly={disabled}
            />
            <Tooltip title={definition.connector_configuration_description}>
              <InfoOutlined
                color="primary"
                fontSize="small"
                sx={{ mt: '25px' }}
              />
            </Tooltip>
          </div>
        ))}
        {optionalFields.length > 0 && (
          <>
            <Typography variant="h5" marginTop={theme.spacing(3)}>{t('Configuration')}</Typography>
            <Accordion>
              <AccordionSummary
                aria-controls="panel1-content"
                id="advanced-options-header"
              >
                <Typography component="span">{t('Advanced options')}</Typography>
              </AccordionSummary>
              <AccordionDetails style={{
                display: 'flex',
                flexDirection: 'column',
                minHeight: '100%',
                gap: theme.spacing(2),
              }}
              >
                {optionalFields.map(({ index, field, definition }) => (
                  <div
                    key={field.id}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '1fr auto',
                      alignItems: 'end',
                      gap: theme.spacing(2),
                    }}
                  >
                    <InjectContentFieldComponent
                      key={field.id}
                      field={formatCatalogConnectorConfigurationToObject(definition, index, false)}
                      readOnly={disabled}
                    />
                    <Tooltip title={definition.connector_configuration_description}>
                      <InfoOutlined
                        fontSize="small"
                        color="primary"
                      />
                    </Tooltip>
                  </div>
                ))}
              </AccordionDetails>
            </Accordion>
          </>
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
            disabled={isSubmitting || disabled}
          >
            {isEditing ? t('Update') : t('Create')}
          </Button>
          <Button
            variant="contained"
            onClick={onClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default ConnectorInstanceForm;

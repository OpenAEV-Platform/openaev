import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { FormProvider, type SubmitHandler, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z, type ZodIssue, type ZodObject } from 'zod/v4';

import TagFieldController from '../../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import Loader from '../../../../../components/Loader';
import {
  type Article,
  type AttackPattern,
  type Inject,
  type InjectInput,
  type Variable,
} from '../../../../../utils/api-types';
import { type ContractElement, type EnhancedContractElement, type InjectorContractConverted } from '../../../../../utils/api-types-custom';
import { splitDuration } from '../../../../../utils/Time';
import { PermissionsContext } from '../../Context';
import { getValidatingRule, isInjectContentType, isRequiredField, isVisibleField } from '../utils';
import InjectContentForm from './InjectContentForm';

const useStyles = makeStyles()(theme => ({
  injectFormContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
    marginTop: theme.spacing(2),
  },
  injectFormButtonsContainer: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: theme.spacing(1),
    marginTop: theme.spacing(1),
  },
  injectContentButton: {
    width: '100%',
    height: theme.spacing(5),
  },
  triggerBox: {
    borderRadius: 4,
    display: 'flex',
    alignItems: 'center',
    padding: theme.spacing(1),
    textWrap: 'nowrap',
    gap: theme.spacing(3),
  },
  triggerBoxColor: { border: `1px solid ${theme.palette.primary.main}` },
  triggerBoxColorDisabled: { border: `1px solid ${theme.palette.action.disabled}` },
  triggerText: {
    fontFamily: 'Consolas, monaco, monospace',
    fontSize: 12,
  },
  triggerTextColor: { color: theme.palette.primary.main },
  triggerTextColorDisabled: { color: theme.palette.action.disabled },
}));

type FieldValue = string | number | boolean | string[] | AttackPattern[] | object | {
  key: string;
  value: string;
  type?: string;
}
| {
  key: string;
  value: string;
  type?: string;
}[];

type InjectInputForm = Omit<InjectInput, 'inject_depends_duration'> & {
  inject_depends_duration_days?: string;
  inject_depends_duration_hours?: string;
  inject_depends_duration_minutes?: string;
};

interface Props {
  handleClose: () => void;
  disabled?: boolean;
  isAtomic: boolean;
  isCreation?: boolean;
  defaultInject: Inject | Omit<Inject, 'inject_created_at' | 'inject_updated_at'>;
  onSubmitInject: (data: InjectInput) => Promise<void>;
  injectorContractContent?: InjectorContractConverted['convertedContent'];
  articlesFromExerciseOrScenario: Article[];
  uriVariable: string;
  variablesFromExerciseOrScenario: Variable[];
}

const initialZodSchema = z.object({ inject_content: z.object({}) });

const InjectForm = ({
  handleClose,
  disabled = false,
  isAtomic,
  isCreation = false,
  defaultInject = {} as Inject,
  injectorContractContent,
  onSubmitInject,
  articlesFromExerciseOrScenario,
  uriVariable,
  variablesFromExerciseOrScenario,
}: Props) => {
  const { classes } = useStyles();
  const { t } = useFormatter();
  const { permissions } = useContext(PermissionsContext);
  const [fieldsMapByKey, setFieldsMapByKey] = useState<Record<ContractElement['key'], ContractElement>>({});
  const [enhancedFields, setEnhancedFields] = useState<EnhancedContractElement[]>([]);
  const [enhancedFieldsMapByType, setEnhancedFieldsMapByType] = useState<Map<ContractElement['type'], EnhancedContractElement>>(new Map());
  const [defaultValues, setDefaultValues] = useState<Partial<InjectInputForm>>({});
  const [mandatoryKeys, setMandatoryKeys] = useState<ZodObject>(initialZodSchema);
  const [mandatoryGroupKeys, setMandatoryGroupKeys] = useState<ZodObject>(initialZodSchema);
  const notDynamicFields = [
    'teams',
    'assets',
    'asset_groups',
    'articles',
    'challenges',
    'attachments',
    'expectations',
  ];

  const getInitialValues = (): Record<string, FieldValue> => {
    const duration = splitDuration(defaultInject?.inject_depends_duration || 0);
    const initialValues = {
      ...defaultInject,
      inject_content: (defaultInject?.inject_content ?? {}) as Record<string, FieldValue>,
      inject_tags: defaultInject?.inject_tags || [],
      inject_depends_duration_days: duration.days,
      inject_depends_duration_hours: duration.hours,
      inject_depends_duration_minutes: duration.minutes,
      inject_all_teams: defaultInject?.inject_all_teams ?? false,
      inject_teams: defaultInject?.inject_teams ?? [],
    };

    // Enrich initialValues with default contract value
    if (injectorContractContent) {
      injectorContractContent.fields
        .filter(f => !notDynamicFields.includes(f.key))
        .forEach((field: ContractElement) => {
          if (!initialValues.inject_content[field.key]) {
            initialValues.inject_content = {
              ...initialValues.inject_content,
              [field.key]: (field.cardinality === '1' ? field.defaultValue?.[0] : field.defaultValue) || '',
            };
          }

          // Specific richText type field
          if (
            field.type === 'textarea'
            && field.richText
          ) {
            initialValues.inject_content[field.key] = (initialValues.inject_content[field.key] as string)
              .replaceAll('<#list challenges as challenge>', '&lt;#list challenges as challenge&gt;')
              .replaceAll('<#list articles as article>', '&lt;#list articles as article&gt;')
              .replaceAll('</#list>', '&lt;/#list&gt;');
          }
        });
    }
    return initialValues;
  };

  const formatInjectContentData = (content: Record<string, FieldValue>): object | null => {
    const formattedContent = { ...content };
    injectorContractContent?.fields
      .filter(f => !notDynamicFields.includes(f.key))
      .forEach((field) => {
        if (field.type === 'number' && typeof formattedContent[field.key] === 'string') {
          formattedContent[field.key] = parseInt(formattedContent[field.key].toString(), 10);

        // Specific richText type field
        } else if (
          field.type === 'textarea'
          && field.richText
          && (String(formattedContent[field.key]))?.length > 0
        ) {
          const regex = /&lt;#list\s+(\w+)\s+as\s+(\w+)&gt;/g;
          formattedContent[field.key] = (formattedContent[field.key] as string)
            .replace(regex, (_, listName, identifier) => `<#list ${listName} as ${identifier}>`)
            .replaceAll('&lt;/#list&gt;', '</#list>');
        }
      });
    return formattedContent;
  };

  const strictKeys = {
    inject_title: z.string().min(1, { message: t('This field is required.') }),
    inject_depends_duration_days: z.string().min(1, { message: t('This field is required.') }).optional(),
    inject_depends_duration_hours: z.string().min(1, { message: t('This field is required.') }).optional(),
    inject_depends_duration_minutes: z.string().min(1, { message: t('This field is required.') }).optional(),
  };

  const methods = useForm<InjectInputForm>({
    mode: isCreation ? 'onSubmit' : 'all',
    reValidateMode: isCreation ? 'onSubmit' : 'onChange',
    resolver: zodResolver(z.object({
      ...strictKeys,
      ...mandatoryKeys.shape,
    }).check(({ value, issues }) => {
      if (isCreation) return;
      const parsed = mandatoryGroupKeys.safeParse(value);
      if (!parsed?.error?.issues) return;
      injectorContractContent?.fields.forEach((field) => {
        if (field.mandatoryGroups) {
          const newIssues: (ZodIssue & { currentField?: boolean })[] = [];
          field.mandatoryGroups.forEach((mandatoryField) => {
            const issue = parsed.error.issues.find(err => isInjectContentType(fieldsMapByKey[mandatoryField].type) ? err.path[1] === mandatoryField : err.path[0] === `inject_${mandatoryField}`);
            if (issue) {
              newIssues.push({
                ...issue,
                message: t('At least one of these fields is required.'),
                ...(mandatoryField === field.key && { currentField: true }),
              });
            }
          });
          if (newIssues.length === field.mandatoryGroups.length) {
            newIssues.filter(i => !i.currentField).forEach(i => issues.push(i));
          }
        }
      });
    })),
    defaultValues: defaultValues,
  });

  const { handleSubmit, reset, subscribe, getValues, clearErrors, trigger, formState: { isSubmitting } } = methods;

  const onSubmit: SubmitHandler<InjectInputForm> = async (data) => {
    // we cannot save, even in draft, without title
    if (!data.inject_title?.length) {
      return;
    }
    if (injectorContractContent) {
      const inject_depends_duration = Number(data.inject_depends_duration_days) * 3600 * 24
        + Number(data.inject_depends_duration_hours) * 3600
        + Number(data.inject_depends_duration_minutes) * 60;
      const values = {
        inject_title: data.inject_title,
        inject_injector_contract: injectorContractContent.contract_id,
        inject_description: data.inject_description as string,
        inject_tags: data.inject_tags,
        inject_content: formatInjectContentData(data.inject_content as Record<string, FieldValue>),
        inject_all_teams: data.inject_all_teams,
        inject_teams: data.inject_all_teams ? [] : data.inject_teams,
        inject_assets: data.inject_assets,
        inject_asset_groups: data.inject_asset_groups,
        inject_documents: data.inject_documents,
        inject_depends_duration,
        inject_depends_on: data.inject_depends_on ? data.inject_depends_on : [],
      } as InjectInput;
      await onSubmitInject(values);
    }
    handleClose();
  };

  useEffect(() => {
    const fieldsToSubscribe: (keyof InjectInputForm)[] = [];
    injectorContractContent?.fields.forEach((field) => {
      if (field.mandatoryConditionFields?.length) {
        field.mandatoryConditionFields.forEach((mandatoryConditionField) => {
          const mandatoryConditionFieldType = injectorContractContent?.fields.find(f => f.key === mandatoryConditionField)?.type;
          const fieldToSubscribe = ((mandatoryConditionFieldType && isInjectContentType(mandatoryConditionFieldType)) ? `inject_content.${mandatoryConditionField}` : `inject_${mandatoryConditionField}`) as (keyof InjectInputForm);
          if (fieldsToSubscribe.indexOf(fieldToSubscribe) === -1) {
            fieldsToSubscribe.push(fieldToSubscribe);
          }
        });
      } else if (field.visibleConditionFields?.length) {
        field.visibleConditionFields.forEach((visibleConditionField) => {
          const visibleConditionFieldType = injectorContractContent?.fields.find(f => f.key === visibleConditionField)?.type;
          const fieldToSubscribe = ((visibleConditionFieldType && isInjectContentType(visibleConditionFieldType)) ? `inject_content.${visibleConditionField}` : `inject_${visibleConditionField}`) as (keyof InjectInputForm);
          if (fieldsToSubscribe.indexOf(fieldToSubscribe) === -1) {
            fieldsToSubscribe.push(fieldToSubscribe);
          }
        });
      }
    });

    const unsubscribe = subscribe({
      name: fieldsToSubscribe,
      exact: true,
      formState: { values: true },
      callback: ({ values }) => {
        const newEnhancedFields: EnhancedContractElement[] = [];
        const newEnhancedFieldsMapByType: Map<ContractElement['type'], EnhancedContractElement> = new Map();

        let manda: ZodObject = initialZodSchema;
        let mandaGroup: ZodObject = initialZodSchema;

        injectorContractContent?.fields.forEach((field) => {
          const isInjectContent = isInjectContentType(field.type);
          const isRequired = isRequiredField(field, injectorContractContent?.fields, values);
          const isVisible = isVisibleField(field, injectorContractContent?.fields, values);
          const enhancedField = {
            ...field,
            key: isInjectContent ? `inject_content.${field.key}` : `inject_${field.key}`,
            originalKey: field.key,
            isInjectContentType: isInjectContent && field.type !== 'expectation',
            isVisible,
            isInMandatoryGroup: !!field.mandatoryGroups?.length,
            mandatoryGroupContractElementLabels: injectorContractContent?.fields.filter(f => field.mandatoryGroups?.includes(f.key)).reduce((acc, f, index) => {
              let newAcc = acc;
              if (index !== 0) newAcc += ', ';
              newAcc += t(f.label);
              return newAcc;
            }, ''),
            settings: { required: isRequired },
          };

          newEnhancedFields.push(enhancedField);
          newEnhancedFieldsMapByType.set(field.type, enhancedField);

          if (!isCreation) {
            if (isRequired) {
              const validatingRule = getValidatingRule(field, t);
              if (isInjectContent) {
                clearErrors(`inject_content.${field.key}` as (keyof InjectInputForm));
                manda = z.object({
                  ...manda.shape,
                  inject_content: z.object({
                    ...manda.shape.inject_content.shape,
                    [field.key]: validatingRule,
                  }),
                });
              } else {
                clearErrors(`inject_${field.key}` as (keyof InjectInputForm));
                manda = z.object({
                  ...manda.shape,
                  [`inject_${field.key}`]: validatingRule,
                });
              }
            } else if (field.mandatoryGroups) {
              const validatingRule = getValidatingRule(field, t);

              if (isInjectContent) {
                mandaGroup = z.object({
                  ...mandaGroup.shape,
                  inject_content: z.object({
                    ...mandaGroup.shape.inject_content.shape,
                    [field.key]: validatingRule,
                  }),
                });
                manda = z.object({
                  ...manda.shape,
                  inject_content: z.object({
                    ...manda.shape.inject_content.shape,
                    [field.key]: z.any(),
                  }),
                });
              } else {
                mandaGroup = z.object({
                  ...mandaGroup.shape,
                  [`inject_${field.key}`]: validatingRule,
                });
                manda = z.object({
                  ...manda.shape,
                  [`inject_${field.key}`]: z.any(),
                });
              }
            }
          }
        });
        if (!isCreation) {
          setMandatoryKeys(manda);
          setMandatoryGroupKeys(mandaGroup);
        }
        setEnhancedFields(newEnhancedFields);
        setEnhancedFieldsMapByType(newEnhancedFieldsMapByType);
      },
    });
    return unsubscribe;
  }, [subscribe, injectorContractContent]);

  useEffect(() => {
    let unsubscribe;
    if (!isCreation) {
      const mandatoryGroupFields = (injectorContractContent?.fields.filter(field => field.mandatoryGroups?.length).map(field => isInjectContentType(field.type) ? `inject_content.${field.key}` : `inject_${field.key}`) || []) as (keyof InjectInputForm)[];
      unsubscribe = subscribe({
        name: mandatoryGroupFields,
        exact: true,
        formState: { values: true },
        callback: () => {
          trigger(mandatoryGroupFields as (keyof InjectInputForm)[]);
        },
      });
    }
    return unsubscribe;
  }, [subscribe, injectorContractContent]);

  useEffect(() => {
    if (!isCreation) {
      trigger();
    }
  }, [isCreation, mandatoryKeys]);

  useEffect(() => {
    if (injectorContractContent?.fields) {
      setFieldsMapByKey(injectorContractContent.fields.reduce<Record<ContractElement['key'], ContractElement>>((acc, field) => {
        acc[field.key] = field;
        return acc;
      }, {}));
    }
  }, [injectorContractContent]);

  // Update mode:
  // In update mode, we want to initialize the form with the current inject values,
  // but we do NOT want to reset again if injectorContractContent changes later,
  // otherwise user edits would be lost.
  useEffect(() => {
    if (!isCreation) {
      const initialValues = getInitialValues();
      reset(initialValues);
      setDefaultValues(initialValues);
    }
  }, []);

  // Create mode:
  // In create mode, when the user selects a new contract, we need to reset
  // the form with the new default values provided by this contract.
  // This ensures the form is pre-filled according to the selected contract.
  useEffect(() => {
    if (isCreation) {
      const initialValues = getInitialValues();
      reset(initialValues);
      setDefaultValues(initialValues);
    }
  }, [injectorContractContent, isCreation]);

  if (Object.keys(defaultValues).length === 0) {
    return <Loader />;
  }

  return (
    <FormProvider {...methods}>
      <form
        id="injectForm"
        noValidate // disabled tooltip
        className={classes.injectFormContainer}
        onSubmit={handleSubmit(onSubmit)}
      >
        <TextFieldController name="inject_title" label={t('Title')} required disabled={isSubmitting || disabled || permissions.readOnly} />
        <TextFieldController name="inject_description" label={t('Description')} multiline rows={2} disabled={isSubmitting || disabled || permissions.readOnly} />
        <TagFieldController name="inject_tags" label={t('Tags')} disabled={isSubmitting || disabled || permissions.readOnly} />

        {!isAtomic && (
          <div className={`${classes.triggerBox} ${isSubmitting || disabled || permissions.readOnly ? classes.triggerBoxColorDisabled : classes.triggerBoxColor}`}>
            <div className={`${classes.triggerText} ${isSubmitting || disabled || permissions.readOnly ? classes.triggerTextColorDisabled : classes.triggerTextColor}`}>{t('Trigger after')}</div>
            <TextFieldController name="inject_depends_duration_days" label={t('Days')} type="number" disabled={permissions.readOnly} />
            <TextFieldController name="inject_depends_duration_hours" label={t('Hours')} type="number" disabled={permissions.readOnly} />
            <TextFieldController name="inject_depends_duration_minutes" label={t('Minutes')} type="number" disabled={permissions.readOnly} />
          </div>
        )}

        {injectorContractContent && (
          <InjectContentForm
            enhancedFields={enhancedFields}
            enhancedFieldsMapByType={enhancedFieldsMapByType}
            injectorContractVariables={injectorContractContent.variables || []}
            readOnly={isSubmitting || disabled || permissions.readOnly}
            injectId={defaultInject.inject_id}
            isAtomic={isAtomic}
            isCreation={isCreation}
            uriVariable={uriVariable}
            variables={variablesFromExerciseOrScenario}
            articles={articlesFromExerciseOrScenario}
          />
        )}

        <div className={classes.injectFormButtonsContainer}>
          <Button
            variant="contained"
            onClick={handleClose}
            disabled={isSubmitting}
          >
            {t('Cancel')}
          </Button>
          <Button
            variant="contained"
            color="secondary"
            onClick={() => {
              onSubmit(getValues());
            }}
            disabled={isSubmitting || disabled || permissions.readOnly}
          >
            {isCreation ? t('Create') : t('Update')}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
};

export default InjectForm;

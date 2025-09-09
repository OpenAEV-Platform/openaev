import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowDropDownOutlined, ArrowDropUpOutlined } from '@mui/icons-material';
import { Button } from '@mui/material';
import { type RefObject, useContext, useEffect, useState } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';
import { z } from 'zod';

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
import { type ContractElement, type InjectorContractConverted } from '../../../../../utils/api-types-custom';
import { splitDuration } from '../../../../../utils/Time';
import { PermissionsContext } from '../../Context';
import InjectContentForm from './InjectContentForm';

const useStyles = makeStyles()(theme => ({
  injectFormContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(2),
  },
  injectFormButtonsContainer: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: theme.spacing(1),
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
  openDetails?: boolean;
  disabled?: boolean;
  isAtomic: boolean;
  isCreation?: boolean;
  drawerRef: RefObject<HTMLDivElement | null>;
  defaultInject: Inject | Omit<Inject, 'inject_created_at' | 'inject_updated_at'>;
  onSubmitInject: (data: InjectInput) => Promise<void>;
  injectorContractContent?: InjectorContractConverted['convertedContent'];
  articlesFromExerciseOrScenario: Article[];
  uriVariable: string;
  variablesFromExerciseOrScenario: Variable[];
}

const InjectForm = ({
  handleClose,
  openDetails = false,
  disabled = false,
  isAtomic,
  isCreation = false,
  drawerRef,
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
  const [defaultValues, setDefaultValues] = useState({});
  const [openDetailContent, setOpenDetailContent] = useState(openDetails);
  const notDynamicFields = [
    'teams',
    'assets',
    'assetgroups',
    'articles',
    'challenges',
    'attachments',
    'expectations',
  ];
  const toggleInjectContent = () => {
    if (openDetailContent && drawerRef.current) {
      drawerRef.current.scrollTop = 0;
    }
    setOpenDetailContent(!openDetailContent);
  };

  const getInitialValues = (): Record<string, FieldValue> => {
    const duration = splitDuration(defaultInject?.inject_depends_duration || 0);
    const initialValues = {
      ...defaultInject,
      inject_content: (defaultInject?.inject_content ?? {}) as Record<string, FieldValue>,
      inject_tags: defaultInject?.inject_tags || [],
      inject_depends_duration_days: duration.days,
      inject_depends_duration_hours: duration.hours,
      inject_depends_duration_minutes: duration.minutes,
    };

    // Enrich initialValues with default contract value
    if (injectorContractContent) {
      injectorContractContent.fields
        .filter(f => !notDynamicFields.includes(f.key))
        .forEach((field: ContractElement) => {
          if (!initialValues.inject_content[field.key]) {
            initialValues.inject_content = {
              ...initialValues.inject_content,
              [field.key]: field.cardinality === '1' ? field.defaultValue?.[0] : field.defaultValue,
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

  const onSubmit = async (data: Record<string, FieldValue>) => {
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

  const strictKeys = {
    inject_title: z.string().min(1, { message: t('This field is required.') }),
    inject_depends_duration_days: z.string().min(1, { message: t('This field is required.') }).optional(),
    inject_depends_duration_hours: z.string().min(1, { message: t('This field is required.') }).optional(),
    inject_depends_duration_minutes: z.string().min(1, { message: t('This field is required.') }).optional(),
  };

  const methods = useForm<InjectInputForm>({
    mode: 'all',
    resolver: zodResolver(z.object({
      ...strictKeys,
      ...Object.keys(defaultValues).reduce<Record<string, z.ZodTypeAny>>((acc, key) => {
        if (!(key in strictKeys)) acc[key] = z.any();
        return acc;
      }, {}),
    })),
    defaultValues: defaultValues,
  });

  const { handleSubmit, reset, formState: { isSubmitting } } = methods;

  useEffect(() => {
    const initialValues = getInitialValues();
    setDefaultValues(initialValues);
    setOpenDetailContent(openDetails);
    reset(initialValues);
  }, [injectorContractContent]);

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

        {injectorContractContent && openDetailContent && (
          <InjectContentForm
            injectorContractContent={injectorContractContent}
            readOnly={isSubmitting || disabled || permissions.readOnly}
            injectId={defaultInject.inject_id}
            isAtomic={isAtomic}
            uriVariable={uriVariable}
            variables={variablesFromExerciseOrScenario}
            articles={articlesFromExerciseOrScenario}
          />
        )}
        { injectorContractContent && (
          <Button
            variant="outlined"
            onClick={toggleInjectContent}
            className={classes.injectContentButton}
          >
            {openDetailContent ? <ArrowDropUpOutlined fontSize="large" /> : <ArrowDropDownOutlined fontSize="large" />}
            {t('Inject content')}
          </Button>
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
            type="submit"
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

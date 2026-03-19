import { Alert, InputLabel } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useState } from 'react';
import {
  type FieldError,
  useFormContext,
  useWatch,
} from 'react-hook-form';

import RichTextField from '../../../../../components/fields/RichTextField';
import SelectFieldController from '../../../../../components/fields/SelectFieldController';
import SeparatorFieldController from '../../../../../components/fields/SeparatorFieldController';
import SwitchFieldController from '../../../../../components/fields/SwitchFieldController';
import TagFieldController from '../../../../../components/fields/TagFieldController';
import TextFieldController from '../../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../../components/i18n';
import { type ChoiceItem, type EnhancedContractElement } from '../../../../../utils/api-types-custom';
import InjectEndpointsList from './endpoints/InjectEndpointsList';

interface Props {
  field: EnhancedContractElement;
  readOnly?: boolean;
}

const InjectContentFieldComponent = ({
  field,
  readOnly = false,
}: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control, getValues, formState: { errors } } = useFormContext();
  const values = getValues();

  const selectedValue = useWatch({
    control,
    name: field.key,
  });
  const [informationToDisplay, setInformationToDisplay] = useState<string>('');
  let fieldType: EnhancedContractElement['type'] | 'richText' = field.type;
  if (field.type === 'textarea' && field.richText) {
    fieldType = 'richText';
  }

  useEffect(() => {
    const findInformation = (value: string) => (field.choices as ChoiceItem[]).find(c => c.value === value)?.information ?? '';
    if (!selectedValue && field.type === 'choice') {
      setInformationToDisplay(findInformation(values[field.key]) ?? findInformation((field.defaultValue || [])[0]));
    }
    if (selectedValue && field.type === 'choice') {
      setInformationToDisplay(findInformation(selectedValue));
    }
  }, [selectedValue]);

  const label = field.linkedFields?.length && field.linkedFields?.length > 0
    ? `${t(field.label)} - ${field.linkedFields.map(f => f.key).join(', ')}`
    : t(field.label);

  const error = (errors.inject_content as Record<string, FieldError>)?.[field.originalKey];

  const fieldComponent = () => {
    switch (fieldType) {
      case 'tags':
        return (
          <TagFieldController
            name={field.key}
            label={t(label)}
            disabled={readOnly}
            required={field.settings?.required}
          />
        );
      case 'richText':
        return (
          <RichTextField
            key={field.key}
            name={field.key}
            label={t(label)}
            style={{ height: 250 }}
            disabled={readOnly}
            askAi={true}
            inInject={true}
            control={control}
            required={field.settings?.required}
          />
        );
      case 'checkbox':
        return (
          <SwitchFieldController
            name={field.key}
            label={t(label)}
            disabled={readOnly}
            required={field.settings?.required}
          />
        );
      case 'select':
      case 'choice':
      case 'dependency-select': {
        let choices = [];

        const makeChoiceItems = (entries: [string, string][]) =>
          entries.toSorted((a, b) => a[1].localeCompare(b[1]))
            .map(([k, v]) => ({
              value: k,
              label: t(v || 'Unknown'),
            }));

        if (fieldType === 'dependency-select') {
          const depValue = values[field.dependencyField ?? ''];
          const depChoices = ((field.choices as Record<string, string>)[depValue]) as unknown as Record<string, string> ?? {};
          choices = makeChoiceItems(Object.entries(depChoices));
        } else if (fieldType === 'select') {
          choices = makeChoiceItems(Object.entries(field.choices as Record<string, string>));
        } else {
          choices = field.choices as ChoiceItem[];
        }
        return (
          <SelectFieldController
            name={field.key}
            label={t(label)}
            items={choices as ChoiceItem[]}
            multiple={field.cardinality === 'n'}
            disabled={readOnly}
            required={field.settings?.required}
          />
        );
      }
      case 'targeted-asset':
        return (
          <>
            <InputLabel required={field?.settings?.required} error={!!error}>{`${t('Targeted assets')} - ${field.key.split('.').at(-1)}`}</InputLabel>
            <InjectEndpointsList
              name={field.key}
              errorLabel={error?.message}
            />
          </>
        );
      default: {
        if (field.key.includes('targeted-asset-separator')) {
          return (
            <SeparatorFieldController
              name={field.key}
              label={`${label}${field.mandatory ? ' *' : ''}`}
              disabled={readOnly}
              defaultValue={field.defaultValue as string}
            />
          );
        }
        return (
          <TextFieldController
            name={field.key}
            label={t(label)}
            disabled={readOnly}
            multiline={field.type === 'textarea'}
            rows={field.type === 'textarea' ? (field.settings?.rows ?? 10) : 1}
            required={field.settings?.required}
            type={field.type as ('number' | 'text' | 'password')}
            writeOnly={field.writeOnly}
          />
        );
      }
    }
  };

  return (
    <div>
      {fieldComponent()}
      {informationToDisplay && <Alert sx={{ marginTop: theme.spacing(1) }} severity="warning">{informationToDisplay}</Alert>}
    </div>
  );
};

export default InjectContentFieldComponent;

import { DeleteOutlined } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import DocumentField from '../../../../components/fields/DocumentField';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SeparatorFieldController from '../../../../components/fields/SeparatorFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import { simpleCall } from '../../../../utils/Action';
import type { OutputTypeDescriptor } from '../../../../utils/api-types-custom';

interface Props {
  argumentName: string;
  canSelectTargetAsset: boolean;
  onArgumentRemoveClick: () => void;
}

// Singleton cache for output types catalog
let outputTypesCachePromise: Promise<OutputTypeDescriptor[]> | null = null;
const fetchOutputTypesCatalog = (): Promise<OutputTypeDescriptor[]> => {
  if (!outputTypesCachePromise) {
    outputTypesCachePromise = simpleCall('/api/output_types')
      .then((res: { data: OutputTypeDescriptor[] }) => res.data)
      .catch(() => [] as OutputTypeDescriptor[]);
  }
  return outputTypesCachePromise;
};

// Classic argument types (not output types)
const CLASSIC_TYPES = new Set(['text', 'document', 'targeted-asset']);

const PayloadArgumentsField = ({ argumentName, canSelectTargetAsset, onArgumentRemoveClick }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { watch, control, setValue } = useFormContext();
  const argumentType = watch(`${argumentName}.type`);

  const [outputTypes, setOutputTypes] = useState<OutputTypeDescriptor[]>([]);

  useEffect(() => {
    fetchOutputTypesCatalog().then(setOutputTypes);
  }, []);

  const isOutputType = !CLASSIC_TYPES.has(argumentType) && argumentType !== undefined;

  // Build the type dropdown: classic types + all output types
  const argumentTypeItems = useMemo(() => [
    { value: 'text', label: t('Text') },
    { value: 'document', label: t('Document') },
    ...canSelectTargetAsset
      ? [{ value: 'targeted-asset', label: t('Targeted assets') }]
      : [],
    ...outputTypes.map(ot => ({
      value: ot.outputType,
      label: ot.outputType,
    })),
  ], [t, canSelectTargetAsset, outputTypes]);

  const targetPropertyItems = [
    { value: 'hostname', label: t('Hostname') },
    { value: 'local_ip', label: t('Local IP (first)') },
    { value: 'seen_ip', label: t('Seen IP') },
  ];

  // Sub-field items for the selected output type
  const selectedOutputType = outputTypes.find(ot => ot.outputType === argumentType);
  const outputFieldItems = selectedOutputType && selectedOutputType.fields.length > 0
    ? selectedOutputType.fields.map(f => ({
      value: f.key,
      label: f.key,
    }))
    : [];

  // Sync input_source when type changes
  useEffect(() => {
    if (isOutputType) {
      setValue(`${argumentName}.input_source.input_type`, argumentType);
      // Clear sub-field when switching input types
      setValue(`${argumentName}.input_source.input_field`, null);
    } else {
      setValue(`${argumentName}.input_source`, null);
    }
  }, [argumentType, isOutputType, argumentName, setValue]);

  const hasSubFields = outputFieldItems.length > 0;
  const columnCount = argumentType === 'targeted-asset'
    ? 5
    : isOutputType && hasSubFields
      ? 5 // type + key + default_value + sub-field + delete
      : 4; // type + key + default_value + delete

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${columnCount - 1}, 1fr) auto`,
        gap: theme.spacing(1),
      }}
    >
      <SelectFieldController
        name={`${argumentName}.type` as const}
        label={t('Type')}
        items={argumentTypeItems}
        required
      />
      <TextFieldController name={`${argumentName}.key` as const} label={t('Key')} required />
      {argumentType === 'text' && (
        <TextFieldController
          name={`${argumentName}.default_value` as const}
          label={t('Default Value')}
          required
        />
      )}
      {argumentType === 'document' && (
        <Controller
          control={control}
          name={`${argumentName}.default_value` as const}
          render={({ field: { onChange, value }, fieldState: { error } }) => (
            <DocumentField
              fieldValue={value ?? []}
              fieldOnChange={onChange}
              label={t('Default Value')}
              error={error}
              style={{ marginTop: 3 }}
            />
          )}
        />
      )}
      {argumentType === 'targeted-asset' && (
        <>
          <SelectFieldController
            name={`${argumentName}.default_value` as const}
            label={t('Targeted property')}
            items={targetPropertyItems}
            required
          />
          <SeparatorFieldController
            name={`${argumentName}.separator` as const}
            label={t('Separator')}
            defaultValue=","
            required
          />
        </>
      )}
      {isOutputType && (
        <TextFieldController
          name={`${argumentName}.default_value` as const}
          label={t('Default Value')}
        />
      )}
      {isOutputType && hasSubFields && (
        <SelectFieldController
          name={`${argumentName}.input_source.input_field` as const}
          label={t('Sub-field')}
          items={outputFieldItems}
          required
        />
      )}
      <IconButton
        onClick={onArgumentRemoveClick}
        size="small"
        color="primary"
      >
        <DeleteOutlined />
      </IconButton>
    </div>
  );
};

export default PayloadArgumentsField;

import { DeleteOutlined } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Controller, useFormContext } from 'react-hook-form';

import DocumentField from '../../../../components/fields/DocumentField';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SeparatorFieldController from '../../../../components/fields/SeparatorFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';

interface Props {
  argumentName: string;
  canSelectTargetAsset: boolean;
  onArgumentRemoveClick: () => void;
}

const PayloadArgumentsField = ({ argumentName, canSelectTargetAsset, onArgumentRemoveClick }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { watch, control } = useFormContext();
  const argumentType = watch(`${argumentName}.type`);

  const argumentTypeItems = [{
    value: 'text',
    label: t('Text'),
  },
  {
    value: 'document',
    label: t('Document'),
  },
  ...canSelectTargetAsset
    ? [{
        value: 'targeted-asset',
        label: t('Targeted assets'),
      }]
    : [],
  ];
  const targetPropertyItems = [
    {
      value: 'hostname',
      label: t('Hostname'),
    },
    {
      value: 'local_ip',
      label: t('Local IP (first)'),
    },
    {
      value: 'seen_ip',
      label: t('Seen IP'),
    },
  ];

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: argumentType == 'targeted-asset' ? 'repeat(4, 1fr) auto' : 'repeat(3, 1fr) auto',
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
      {argumentType == 'text' && (
        <TextFieldController
          name={`${argumentName}.default_value` as const}
          label={t('Default Value')}
          required
        />
      )}
      {argumentType == 'document' && (
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
      {argumentType == 'targeted-asset' && (
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

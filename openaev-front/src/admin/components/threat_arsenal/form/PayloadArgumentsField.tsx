import { DeleteOutlined } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Controller, useFormContext } from 'react-hook-form';

import DocumentField from '../../../../components/fields/DocumentField';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SeparatorFieldController from '../../../../components/fields/SeparatorFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import type { PayloadArgument } from '../../../../utils/api-types';
import { formatPrimitiveTypeLabel } from '../../../../utils/String';
import { isFeatureEnabled } from '../../../../utils/utils';
import useArgumentTypes from './useArgumentTypes';

interface Props {
  argumentName: string;
  canSelectTargetAsset: boolean;
  onArgumentRemoveClick: () => void;
}

const PayloadArgumentsField = ({ argumentName, canSelectTargetAsset, onArgumentRemoveClick }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { watch, control } = useFormContext();
  const argumentType: PayloadArgument['type'] = watch(`${argumentName}.type`);
  /** Types that require the INJECT_CHAINING feature flag to be selectable. */
  const isChainingEnabled = isFeatureEnabled('INJECT_CHAINING');
  const { argumentTypes, argumentWithDefaultValueTypes } = useArgumentTypes();

  /** Always-available types */
  const alwaysAvailableTypes = new Set(['text', 'document']);
  const alwaysAvailableItems = [
    {
      value: 'text',
      label: t('Text'),
    },
    {
      value: 'document',
      label: t('Document'),
    },
  ];

  const toItem = (at: string) => ({
    value: at,
    label: t(formatPrimitiveTypeLabel(at)),
  });

  const argumentTypeItems: {
    value: string;
    label: string;
  }[] = [
    ...alwaysAvailableItems,
    ...canSelectTargetAsset
      ? [{
          value: 'targeted-asset',
          label: t('Targeted assets'),
        }]
      : [],
    ...(isChainingEnabled
      ? argumentTypes
          .filter(at => !alwaysAvailableTypes.has(at)
            && at !== 'targeted-asset')
          .map(at => toItem(at))
      : []),
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
  const columnCount = (() => {
    if (argumentType === 'targeted-asset') return 4;
    return 3;
  })();

  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${columnCount}, 1fr) auto`,
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
      {argumentWithDefaultValueTypes.has(argumentType) && argumentType !== 'document' && argumentType !== 'targeted-asset' && (
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
      <IconButton
        onClick={onArgumentRemoveClick}
        size="small"
        color="primary"
        data-testid={`${argumentName}.delete-btn`}
      >
        <DeleteOutlined />
      </IconButton>
    </div>
  );
};
export default PayloadArgumentsField;

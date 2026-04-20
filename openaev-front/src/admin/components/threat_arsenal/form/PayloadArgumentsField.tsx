import { DeleteOutlined } from '@mui/icons-material';
import { IconButton } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect, useRef } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import DocumentField from '../../../../components/fields/DocumentField';
import SelectFieldController from '../../../../components/fields/SelectFieldController';
import SeparatorFieldController from '../../../../components/fields/SeparatorFieldController';
import TextFieldController from '../../../../components/fields/TextFieldController';
import { useFormatter } from '../../../../components/i18n';
import type { ArgumentTypeOutput, PayloadArgument } from '../../../../utils/api-types';
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
  const { watch, control, setValue } = useFormContext();
  const argumentType: PayloadArgument['type'] = watch(`${argumentName}.type`);
  /** Types that require the INJECT_CHAINING feature flag to be selectable. */
  const isChainingEnabled = isFeatureEnabled('INJECT_CHAINING');
  const { argumentTypes, subtypesByType, structuredTypes, argumentWithDefaultValueTypes } = useArgumentTypes();

  const previousTypeRef = useRef<PayloadArgument['type']>(argumentType);
  useEffect(() => {
    if (previousTypeRef.current !== argumentType) {
      setValue(`${argumentName}.subtype`, undefined);
    }
    previousTypeRef.current = argumentType;
  }, [argumentType, argumentName, setValue]);

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

  const toItem = (at: ArgumentTypeOutput) => ({
    value: at.argument_type,
    label: t(at.argument_type.charAt(0).toUpperCase() + at.argument_type.slice(1)),
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
          .filter((at: ArgumentTypeOutput) => !alwaysAvailableTypes.has(at.argument_type)
            && at.argument_type !== 'targeted-asset')
          .map((at: ArgumentTypeOutput) => toItem(at))
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
  const isStructured = structuredTypes.has(argumentType);
  const subtypeItems = isStructured
    ? (subtypesByType[argumentType] ?? []).map((sub: string) => ({
        value: sub,
        label: t(sub.charAt(0).toUpperCase() + sub.slice(1)),
      }))
    : [];
  const columnCount = (() => {
    if (argumentType === 'targeted-asset') return 4;
    if (isStructured) return 4;
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
      {isChainingEnabled && isStructured && (
        <SelectFieldController
          name={`${argumentName}.subtype` as const}
          label={t('Sub-type')}
          items={subtypeItems}
        />
      )}
      {argumentWithDefaultValueTypes.has(argumentType) && argumentType !== 'document' && (
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

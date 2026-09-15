import { SvgIcon } from '@mui/material';
import { LogoXtmOneIcon } from 'filigran-icon';
import { useState } from 'react';
import { type Control, type FieldValues, type UseFormSetValue, useWatch } from 'react-hook-form';

import TextFieldAskAI from '../../admin/components/common/form/TextFieldAskAI';
import useAskAiAvailability from '../../admin/components/common/form/useAskAiAvailability';
import { useFormatter } from '../i18n';
import TextFieldFds, { type TextFieldFdsProps } from './TextFieldFds';

export type TextFieldProps<TFieldValues extends FieldValues = FieldValues>
  = TextFieldFdsProps & {
    /** Show the AskAI action that lets the user transform the field via XTM One. */
    askAi?: boolean;
    /** react-hook-form `control` — required to watch the field value when `askAi` is enabled. */
    control?: Control<TFieldValues>;
    /** react-hook-form `setValue` — required to write the AI-generated value back into the form. */
    setValue?: UseFormSetValue<TFieldValues>;
  };

const TextField = <TFieldValues extends FieldValues = FieldValues>({
  askAi,
  control,
  setValue,
  ...props
}: TextFieldProps<TFieldValues>) => {
  const { t } = useFormatter();
  const { hidden: askAiHidden, isAvailable: askAiAvailable } = useAskAiAvailability();
  const [askAiAnchor, setAskAiAnchor] = useState<HTMLElement | null>(null);
  const fieldName = props.name;
  const watchedValue = useWatch({
    // `name` is keyed off the underlying form so we widen here; runtime safety is enforced by the
    // `disabled` flag below (we only subscribe when both a control and a name are available).
    control: control as Control<FieldValues> | undefined,
    name: fieldName ?? '',
    disabled: !control || !fieldName,
  });

  const currentValue: unknown = fieldName ? watchedValue : undefined;
  const withAskAi = Boolean(askAi && fieldName && setValue && !askAiHidden);

  return (
    <>
      <TextFieldFds
        {...props}
        value={control && fieldName ? (currentValue as string | undefined) ?? '' : props.value}
        endIcon={withAskAi
          ? {
              type: 'iconButton',
              icon: <SvgIcon component={LogoXtmOneIcon} fontSize="small" inheritViewBox />,
              label: t('Ask AI'),
              disabled: !askAiAvailable,
              onClick: event => setAskAiAnchor(event.currentTarget),
            }
          : props.endIcon}
      />
      {withAskAi && (
        <TextFieldAskAI
          variant="text"
          currentValue={typeof currentValue === 'string' ? currentValue : ''}
          setFieldValue={(val: string) => (setValue as UseFormSetValue<FieldValues>)(
            fieldName as string,
            val,
            {
              shouldDirty: true,
              shouldValidate: true,
            },
          )}
          format="text"
          disabled={props.disabled}
          triggerAnchor={askAiAnchor}
          onTriggerClose={() => setAskAiAnchor(null)}
        />
      )}
    </>
  );
};

export default TextField;

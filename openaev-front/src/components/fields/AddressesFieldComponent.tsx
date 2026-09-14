import { Icon, IconButton, Spinner, Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@filigran/design-system';
import { type CSSProperties, type FormEventHandler, useState } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { useFormatter } from '../i18n';
import TextFieldFds from './TextFieldFds';

interface Props {
  name: string;
  label: string;
  style?: CSSProperties;
  helperText: string;
  disabled?: boolean;
  required?: boolean;
  /** When provided, renders a button that resolves values (e.g. IP from hostname) and merges them in. */
  onResolve?: () => Promise<string[]>;
  resolveDisabled?: boolean;
  resolveTooltip?: string;
}

const AddressesFieldComponent = ({ name, label, style = {}, disabled = false, required = false, helperText, onResolve, resolveDisabled = false, resolveTooltip }: Props) => {
  const { control } = useFormContext();
  const { t } = useFormatter();
  const [resolving, setResolving] = useState(false);

  return (
    <Controller
      control={control}
      name={name}
      render={({ field: { onChange, onBlur, value }, fieldState: { error } }) => {
        const value2 = value?.reduce((accumulator: string, current: string) => (accumulator === '' ? current : `${accumulator}\n${current}`), '');
        const onChange2: FormEventHandler<HTMLTextAreaElement | HTMLInputElement> = (event) => {
          if (event.currentTarget.value === '') {
            onChange([]);
          } else {
            onChange(event.currentTarget.value.split('\n'));
          }
        };
        const handleResolve = async () => {
          if (!onResolve) {
            return;
          }
          setResolving(true);
          try {
            const resolved = await onResolve();
            if (resolved && resolved.length > 0) {
              const current: string[] = Array.isArray(value) ? value.filter((v: string) => v !== '') : [];
              onChange(Array.from(new Set([...current, ...resolved])));
            }
          } finally {
            setResolving(false);
          }
        };
        const resolveLabel = resolveTooltip ?? t('Resolve');
        // The library Textarea has no end slot; the action sits in the label row's slot instead.
        const resolveAction = onResolve
          ? (
              <TooltipProvider delayDuration={200}>
                <Tooltip>
                  <TooltipTrigger asChild>
                    <IconButton
                      aria-label={resolveLabel}
                      icon={resolving ? <Spinner size="sm" tone="inherit" /> : <Icon name="locate" size={16} aria-hidden />}
                      variant="default"
                      priority="tertiary"
                      size="sm"
                      disabled={disabled || resolveDisabled || resolving}
                      onClick={handleResolve}
                    />
                  </TooltipTrigger>
                  <TooltipContent>{resolveLabel}</TooltipContent>
                </Tooltip>
              </TooltipProvider>
            )
          : undefined;
        return (
          <TextFieldFds
            multiline
            rows={3}
            label={label}
            style={style}
            error={error?.message}
            helperText={t(helperText)}
            disabled={disabled}
            onChange={onChange2}
            onBlur={onBlur}
            value={value2}
            required={required}
            infoTooltip={resolveAction}
          />
        );
      }}
    />
  );
};

export default AddressesFieldComponent;

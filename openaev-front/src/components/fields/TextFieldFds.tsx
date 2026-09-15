import { Input, type InputProps, Textarea } from '@filigran/design-system';
import { type ChangeEvent, type CSSProperties, type FocusEvent, forwardRef, type KeyboardEvent, type ReactNode } from 'react';

type Control = HTMLInputElement | HTMLTextAreaElement;

export interface TextFieldFdsProps {
  'label'?: string;
  'required'?: boolean;
  /** The message, or the MUI-style flag paired with a string `helperText`. */
  'error'?: boolean | string;
  'helperText'?: ReactNode;
  'disabled'?: boolean;
  'placeholder'?: string;
  'name'?: string;
  'id'?: string;
  'value'?: string | number | null;
  'defaultValue'?: string | number;
  'onChange'?: (event: ChangeEvent<Control>) => void;
  'onBlur'?: (event: FocusEvent<Control>) => void;
  'onKeyDown'?: (event: KeyboardEvent<Control>) => void;
  'autoFocus'?: boolean;
  'autoComplete'?: string;
  'readOnly'?: boolean;
  /** Both land on the wrapper root: the library puts everything but `className` on the control itself. */
  'style'?: CSSProperties;
  'className'?: string;
  'fullWidth'?: boolean;
  'data-testid'?: string;
  'multiline'?: boolean;
  'rows'?: number;
  'minRows'?: number;
  'maxRows'?: number;
  'type'?: InputProps['type'];
  'min'?: number | string;
  'max'?: number | string;
  'step'?: number | string;
  'maxLength'?: number;
  'startIcon'?: InputProps['startIcon'];
  'endIcon'?: InputProps['endIcon'];
  'infoTooltip'?: ReactNode;
  /** Unit or suffix drawn inside the field, read as its description. */
  'endText'?: string;
}

const resolveErrorMessage = (error: boolean | string | undefined, helperText: ReactNode) => {
  if (typeof error === 'string') return error;
  return error && typeof helperText === 'string' ? helperText : undefined;
};

const TextFieldFds = forwardRef<Control, TextFieldFdsProps>(({
  error,
  helperText,
  style,
  className,
  fullWidth = true,
  value,
  defaultValue,
  multiline = false,
  rows,
  minRows,
  maxRows,
  type = 'text',
  min,
  max,
  step,
  maxLength,
  startIcon,
  endIcon,
  infoTooltip,
  endText,
  ...control
}, ref) => {
  const errorMessage = resolveErrorMessage(error, helperText);
  const stringValue = value === undefined ? undefined : String(value ?? '');
  const stringDefault = defaultValue === undefined ? undefined : String(defaultValue);
  // MUI's `fullWidth` made the field take its row's width; a block wrapper alone lets a flex row shrink it.
  const rootStyle: CSSProperties = fullWidth
    ? {
        width: '100%',
        ...style,
      }
    : {
        display: 'inline-flex',
        ...style,
      };

  if (multiline) {
    // MUI's `multiline` without `rows` grows from one line; the library needs `minRows` to do the same.
    const autosize = rows === undefined;
    return (
      <div style={rootStyle} className={className}>
        <Textarea
          ref={ref as React.Ref<HTMLTextAreaElement>}
          {...control}
          value={stringValue}
          defaultValue={stringDefault}
          error={errorMessage}
          helperText={errorMessage ? undefined : helperText}
          rows={autosize ? undefined : rows}
          minRows={autosize ? minRows ?? 1 : undefined}
          maxRows={autosize ? maxRows : undefined}
          maxLength={maxLength}
          infoTooltip={infoTooltip}
          resize="none"
        />
      </div>
    );
  }

  return (
    <div style={rootStyle} className={className}>
      <Input
        ref={ref as React.Ref<HTMLInputElement>}
        {...control}
        value={stringValue}
        defaultValue={stringDefault}
        error={errorMessage}
        helperText={errorMessage ? undefined : helperText}
        type={type === 'number' ? undefined : type}
        isTypeNumber={type === 'number'}
        min={min}
        max={max}
        step={step}
        maxLength={maxLength}
        startIcon={startIcon}
        endIcon={endIcon}
        infoTooltip={infoTooltip}
        endText={endText}
      />
    </div>
  );
});
TextFieldFds.displayName = 'TextFieldFds';

export default TextFieldFds;

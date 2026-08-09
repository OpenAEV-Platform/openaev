import { type FunctionComponent, type ReactNode } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import CodeEditor from './CodeEditor';

interface Props {
  name: string;
  language: string;
  label?: string;
  badge?: string;
  placeholder?: string;
  disabled?: boolean;
  minHeight?: number;
  headerAction?: ReactNode;
}

/** react-hook-form wrapper around the syntax-highlighting CodeEditor. */
const CodeFieldController: FunctionComponent<Props> = ({
  name,
  language,
  label,
  badge,
  placeholder,
  disabled = false,
  minHeight,
  headerAction,
}) => {
  const { control } = useFormContext();
  return (
    <Controller
      name={name}
      control={control}
      render={({ field, fieldState: { error } }) => (
        <CodeEditor
          value={field.value ?? ''}
          onChange={field.onChange}
          language={language}
          label={label}
          badge={badge}
          placeholder={placeholder}
          disabled={disabled}
          error={!!error}
          helperText={error?.message}
          minHeight={minHeight}
          headerAction={headerAction}
        />
      )}
    />
  );
};

export default CodeFieldController;

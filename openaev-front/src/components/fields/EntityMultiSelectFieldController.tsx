import { type CSSProperties, type FunctionComponent, type ReactNode } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import { type Option } from '../../utils/Option';
import EntityMultiSelectField from './EntityMultiSelectField';

export interface EntityMultiSelectFieldControllerProps {
  name: string;
  label: string;
  options: Option[];
  icon?: ReactNode;
  placeholder?: string;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

/**
 * Binds a multiple entity selection to the surrounding react-hook-form: the field
 * value is a list of entity ids. Options are provided by the caller, so that each
 * entity keeps its own facade in charge of loading them (see ScenarioFieldController).
 */
const EntityMultiSelectFieldController: FunctionComponent<EntityMultiSelectFieldControllerProps> = ({
  name,
  label,
  options,
  icon,
  placeholder,
  style,
  disabled = false,
  required = false,
}) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({ field: { value, onChange }, fieldState: { error } }) => (
        <EntityMultiSelectField
          label={label}
          options={options}
          fieldValue={value ?? []}
          fieldOnChange={onChange}
          icon={icon}
          error={error}
          placeholder={placeholder}
          style={style}
          disabled={disabled}
          required={required}
        />
      )}
    />
  );
};

export default EntityMultiSelectFieldController;

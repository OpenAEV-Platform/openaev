import type { CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import type { Domain } from '../../utils/api-types';
import { buildDomainAutocompleteState, TO_CLASSIFY } from '../../utils/domains/domainUtils';
import AutocompleteField from './AutocompleteField';

interface DomainFieldControllerProps {
  name: string;
  label: string;
  domains: Domain[];
  style?: CSSProperties;
  required?: boolean;
  disabled?: boolean;
}

interface DomainFieldControllerProps {
  name: string;
  label: string;
  domains: Domain[];
  style?: CSSProperties;
  required?: boolean;
  disabled?: boolean;
}

const DomainFieldController = ({
  name,
  label,
  domains,
  required,
  disabled,
  style,
}: DomainFieldControllerProps) => {
  const { control } = useFormContext();

  return (
    <Controller
      name={name}
      control={control}
      render={({
        field: { onChange, value },
        fieldState: { error },
      }) => {
        const { currentIds, options }
          = buildDomainAutocompleteState(domains, value);

        return (
          <AutocompleteField
            style={style}
            label={label}
            multiple
            required={required}
            disabled={disabled}
            options={options}
            value={currentIds}
            error={!!error}
            onInputChange={() => {}}
            onChange={onChange}
            hideOption={option => option.label === TO_CLASSIFY}
          />
        );
      }}
    />
  );
};

export default DomainFieldController;

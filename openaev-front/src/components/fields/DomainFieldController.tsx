import type { CSSProperties } from 'react';
import { Controller, useFormContext } from 'react-hook-form';

import type { Domain } from '../../utils/api-types';
import { buildDomainAutocompleteState, TO_CLASSIFY } from '../../utils/domains/domainUtils';
import { useFormatter } from '../i18n';
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
  const { t } = useFormatter();
  const toClassifyId = domains.find(d => d.domain_name === TO_CLASSIFY)?.domain_id;

  return (
    <Controller
      name={name}
      control={control}
      render={({
        field: { onChange, value },
        fieldState: { error },
      }) => {
        const { currentIds, options: rawOptions }
          = buildDomainAutocompleteState(domains, value);
        const options = rawOptions.map(option => ({
          ...option,
          label: t(option.label),
        }));

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
            hideOption={option => option.id === toClassifyId}
          />
        );
      }}
    />
  );
};

export default DomainFieldController;

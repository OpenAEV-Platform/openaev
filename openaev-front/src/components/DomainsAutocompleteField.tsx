import { useTheme } from '@mui/material/styles';
import { type FieldRenderProps } from 'react-final-form';

import { type Domain } from '../utils/api-types';
import { TO_CLASSIFY } from '../utils/domains/domainUtils';
import AutocompleteField from './fields/AutocompleteField';

type DomainValue = Domain | string;
interface Props {
  input: FieldRenderProps<DomainValue[]>['input'];
  meta: FieldRenderProps<DomainValue[]>['meta'];
  domainOptions: Domain[];
  label: string;
  disabled?: boolean;
}

// TODO To refacto when injectorContractForm will be in TS using react hook form
const DomainsAutocompleteField = ({
  input,
  meta,
  domainOptions,
  label,
  disabled,
}: Props) => {
  const theme = useTheme();

  const domains = domainOptions ?? [];

  const toClassifyDomain = domains.find(
    d => d.domain_name === TO_CLASSIFY,
  );

  const selectableDomains = domains.filter(
    d => d.domain_name !== TO_CLASSIFY,
  );

  const currentIds: string[] = Array.isArray(input.value)
    ? input.value.map(v =>
        typeof v === 'string' ? v : v.domain_id,
      )
    : [];

  const hasToClassifySelected
    = !!toClassifyDomain
      && currentIds.includes(toClassifyDomain.domain_id);

  const optionsForAutocomplete
    = hasToClassifySelected && toClassifyDomain
      ? [...selectableDomains, toClassifyDomain]
      : selectableDomains;

  const mappedOptions = optionsForAutocomplete.map(d => ({
    id: d.domain_id,
    label: d.domain_name,
  }));

  const handleChange = (ids: string[]) => {
    const selectedDomains = selectableDomains.filter(d =>
      ids.includes(d.domain_id),
    );

    input.onChange(selectedDomains);
  };

  return (
    <AutocompleteField
      style={{ marginTop: theme.spacing(2) }}
      label={label}
      variant="standard"
      multiple
      disabled={disabled}
      options={mappedOptions}
      value={currentIds}
      error={meta.touched && meta.error}
      onInputChange={() => {}}
      onChange={handleChange}
      renderOption={(props, option) => {
        if (option.label === TO_CLASSIFY) {
          return null;
        }
        return undefined;
      }}
    />
  );
};

export default DomainsAutocompleteField;

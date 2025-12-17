import { type FunctionComponent, useEffect } from 'react';

import { SIMULATIONS } from '../common/queryable/filter/constants';
import useSearchOptions, { type SearchOptionsConfig } from '../common/queryable/filter/useSearchOptions';
import AutocompleteField from './AutocompleteField';

interface Props {
  label: string;
  className?: string;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  required?: boolean;
  error?: boolean;
  searchOptionsConfig?: SearchOptionsConfig;
}

const SimulationField: FunctionComponent<Props> = ({ label, value, onChange, className = '', required = false, error = false, searchOptionsConfig }) => {
  const { options, searchOptions } = useSearchOptions();
  const finalSearchOptionsConfig = {
    filterKey: searchOptionsConfig?.filterKey ?? SIMULATIONS,
    contextId: searchOptionsConfig?.contextId,
    defaultValues: searchOptionsConfig?.defaultValues,
  };
  useEffect(() => {
    searchOptions(finalSearchOptionsConfig, '');
  }, []);

  return (
    <AutocompleteField
      label={label}
      className={className}
      value={value}
      onChange={onChange}
      required={required}
      error={error}
      options={options}
      onInputChange={(search: string) => searchOptions(finalSearchOptionsConfig, search)}
    />
  );
};

export default SimulationField;

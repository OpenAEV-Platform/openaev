import { type FunctionComponent, useEffect } from 'react';

import type { GroupOption } from '../../utils/Option';
import { SCENARIOS } from '../common/queryable/filter/constants';
import useSearchOptions from '../common/queryable/filter/useSearchOptions';
import AutocompleteField from './AutocompleteField';

interface Props {
  label: string;
  className?: string;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
  required?: boolean;
  error?: boolean;
  defaultOptions?: GroupOption[];
}

const ScenarioField: FunctionComponent<Props> = ({ label, value, onChange, className = '', required = false, error = false, defaultOptions = [] }) => {
  const { options, searchOptions } = useSearchOptions();
  const searchOptionsConfig = {
    filterKey: SCENARIOS,
    defaultValues: defaultOptions,
  };
  useEffect(() => {
    searchOptions(searchOptionsConfig, '');
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
      onInputChange={(search: string) => searchOptions(searchOptionsConfig, search)}
    />
  );
};

export default ScenarioField;

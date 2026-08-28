import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxTrigger,
} from '@filigran/design-system';
import { DateTimePicker } from '@mui/x-date-pickers';
import { type FunctionComponent, useCallback, useContext, useEffect, useState } from 'react';

import { type Filter, type PropertySchemaDTO } from '../../../../utils/api-types';
import { type GroupOption, type Option } from '../../../../utils/Option';
import { debounce } from '../../../../utils/utils';
import { useFormatter } from '../../../i18n';
import { FilterContext } from './context';
import { type FilterHelpers } from './FilterHelpers';
import { getSelectedOptions } from './FilterUtils';
import useRetrieveOptions from './useRetrieveOptions';
import useSearchOptions, { type SearchOptionsConfig } from './useSearchOptions';
import wordsToExcludeFromTranslation from './WordsToExcludeFromTranslation';

interface Props {
  filter: Filter;
  helpers: FilterHelpers;
  contextId?: string; // used to give contextual information to the searchOptions function
}

export const BasicTextInput: FunctionComponent<Props> = ({
  filter,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');
  const values = filter.values ?? [];
  // Free-text filters accept several values (chips), like select-based filters:
  // "Value != 443 and 80" reads as NOT IN (443, 80) on the backend.
  const commit = (newValues: string[]) => {
    helpers.handleUpdateValuesById(
      filter.id,
      Array.from(new Set(newValues.map(v => v.trim()).filter(v => v.length > 0))),
    );
  };
  return (
    <Combobox<string>
      multiple
      allowCustomValue
      createValueFromInput={input => input}
      options={[]}
      value={values}
      inputValue={inputValue}
      onInputChange={(search, meta) => {
        if (meta.cause === 'type') setInputValue(search);
      }}
      onValueChange={(newValues) => {
        commit(newValues as string[]);
        setInputValue('');
      }}
      keepInputOnBlur
    >
      <ComboboxField>
        <ComboboxChips />
        <ComboboxInput
          placeholder={t(filter.key)}
          autoFocus
          onBlur={() => {
            // Clicking away with pending text must still register the value
            // (historical single-value behavior of this input).
            if (inputValue.trim().length > 0) {
              commit([...values, inputValue]);
              setInputValue('');
            }
          }}
        />
        <ComboboxControls>
          <ComboboxClear />
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent />
    </Combobox>
  );
};

export const BasicSelectInput: FunctionComponent<Props & { propertySchema: PropertySchemaDTO }> = ({
  filter,
  helpers,
  propertySchema,
  contextId,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const [inputValue, setInputValue] = useState('');
  const { options, setOptions, searchOptions, loading } = useSearchOptions();
  // Resolve the already-selected values (ids) to their labels the same way the
  // filter chip does: a text search may not return an entity that is already
  // selected (e.g. an author outside the first page), which would otherwise
  // render as a raw id in the "Selected" group.
  const { options: resolvedSelectedOptions, searchOptions: retrieveSelectedOptions } = useRetrieveOptions();
  const { defaultValues } = useContext(FilterContext);
  // The selected values resolve against both the live search results and the
  // by-id resolution, so a selected entity always shows its human-readable label.
  const optionPool = [
    ...options,
    ...resolvedSelectedOptions.filter(resolved => !options.some(option => option.id === resolved.id)),
  ];
  const selectedOptions = getSelectedOptions(optionPool, filter.values ?? [], t);
  const mergedOptions = [
    ...selectedOptions,
    ...options.filter(option => !selectedOptions.some(selectedOption => selectedOption.id === option.id)),
  ];
  const handleSearchOptions = (search: string) => {
    const searchOptionsConfig: SearchOptionsConfig = {
      filterKey: filter.key,
      contextId: contextId,
      defaultValues: defaultValues?.get(filter.key),
    };
    searchOptions(searchOptionsConfig, search);
  };
  // Dynamic options hit the backend: debounce keystrokes so large inventories
  // (e.g. thousands of assets) trigger one search per pause, not one per character.
  const debouncedSearchOptions = useCallback(
    debounce((search?: string) => handleSearchOptions(search ?? ''), 300),
    [filter.key, contextId],
  );
  useEffect(() => {
    // Resolve the labels of the values selected before the popover opened; new
    // selections come from the search results and already carry their label.
    if (filter.values && filter.values.length > 0) {
      retrieveSelectedOptions(filter.values, {
        filterKey: filter.key,
        contextId,
        defaultValues: defaultValues?.get(filter.key),
      });
    }
  }, []);
  useEffect(() => {
    if (propertySchema.schema_property_values && propertySchema.schema_property_values?.length > 0) {
      setOptions(
        propertySchema.schema_property_values
          .map((value) => {
            const label = wordsToExcludeFromTranslation.includes(value) ? value : t(value.charAt(0).toUpperCase() + value.slice(1).toLowerCase());
            return ({
              id: value,
              label,
            });
          })
          .sort((a, b) => a.label.localeCompare(b.label)),
      );
    } else {
      handleSearchOptions('');
    }
  }, []);

  const onClick = (optionId: string) => {
    const isIncluded = filter.values?.includes(optionId);
    const newValues = isIncluded
      ? (filter.values?.filter(v => v !== optionId) ?? [])
      : [...(filter.values ?? []), optionId];
    helpers.handleUpdateValuesById(filter.id, newValues);
  };

  return (
    <Combobox<GroupOption | Option>
      multiple
      selectOnFocus
      openOnFocus
      options={mergedOptions}
      value={selectedOptions}
      inputValue={inputValue}
      loading={loading}
      isOptionEqualToValue={(option, value) => option.id === value.id}
      groupBy={(option: GroupOption | Option) => 'group' in option ? option.group : ''}
      getOptionLabel={option => option.label ?? ''}
      onInputChange={(search, meta) => {
        if (meta.cause !== 'type') {
          return;
        }
        setInputValue(search);
        debouncedSearchOptions(search);
      }}
      onValueChange={(next) => {
        // Was: every row owned its own click and called `onClick(option.id)`. The
        // library owns the row, so the new selection is diffed against the old one
        // and the same helper is replayed for whatever moved.
        const before = new Set(filter.values ?? []);
        const after = new Set((next as (GroupOption | Option)[]).map(o => o.id));
        for (const id of after) {
          if (!before.has(id)) {
            onClick(id);
          }
        }
        for (const id of before) {
          if (!after.has(id)) {
            onClick(id);
          }
        }
      }}
    >
      <ComboboxField>
        <ComboboxInput placeholder={t(propertySchema.schema_property_name)} />
        <ComboboxControls>
          <ComboboxClear />
          <ComboboxTrigger />
        </ComboboxControls>
      </ComboboxField>
      <ComboboxContent emptyMessage={t('No available options')} />
    </Combobox>
  );
};

export const BasicFilterDate: FunctionComponent<Props> = ({
  filter,
  helpers,
}) => {
  // Standard hooks
  const { t } = useFormatter();
  const handleValueChange = (date: Date) => {
    helpers.handleUpdateValuesById(filter.id, [date.toISOString()]);
  };
  return (
    <DateTimePicker
      label={t(filter.key)}
      onChange={(date) => {
        if (date) {
          handleValueChange(date);
        }
      }}
      slotProps={{
        textField: {
          variant: 'outlined',
          fullWidth: true,
        },
      }}
    />
  );
};

export const FilterChipPopoverInput: FunctionComponent<Props & { propertySchema: PropertySchemaDTO }> = ({
  propertySchema,
  filter,
  helpers,
  contextId,
}) => {
  const choice = () => {
    // Date field
    if (propertySchema.schema_property_type.includes('instant')) {
      return (<BasicFilterDate filter={filter} helpers={helpers} contextId={contextId} />);
    }
    // Emptiness
    if (filter?.operator && ['empty', 'not_empty'].includes(filter.operator)) {
      return null;
    }
    // Select field
    if (propertySchema.schema_property_values || propertySchema.schema_property_has_dynamic_value) {
      return (<BasicSelectInput propertySchema={propertySchema} filter={filter} helpers={helpers} contextId={contextId} />);
    }
    // Simple text field
    return (<BasicTextInput filter={filter} helpers={helpers} contextId={contextId} />);
  };
  return (choice());
};

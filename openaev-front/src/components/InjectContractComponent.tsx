import {
  Combobox,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { type FunctionComponent, useEffect, useState } from 'react';
import { type FieldError } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { searchInjectorContracts } from '../actions/InjectorContracts';
import InjectIcon from '../admin/components/common/injects/InjectIcon';
import { type FilterGroup, type InjectorContract } from '../utils/api-types';
import { isNotEmptyField } from '../utils/utils';
import { generateFilterId } from './common/queryable/filter/FilterUtils';
import { initSorting, type Page } from './common/queryable/Page';
import { useFormatter } from './i18n';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
}));

interface Props {
  label: string;
  onChange: (data: string | null | undefined) => void;
  error: FieldError | undefined;
  fieldValue: string | undefined;
}

const InjectContractComponent: FunctionComponent<Props> = ({
  label,
  onChange,
  error,
  fieldValue,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const { t, tPick } = useFormatter();

  // Pagination
  const [contracts, setContracts] = useState<InjectorContract[]>([]);
  const searchContract = (val: string) => {
    return contracts.filter(
      type => type.injector_contract_id.includes(val)
        || tPick(type.injector_contract_labels).includes(val),
    );
  };

  const importFilter: FilterGroup = {
    mode: 'and',
    filters: [
      {
        id: generateFilterId(),
        key: 'injector_contract_import_available',
        operator: 'eq',
        mode: 'and',
        values: ['true'],
      }],
  };
  const searchPaginationInput = {
    sorts: initSorting('injector_contract_labels'),
    filterGroup: importFilter,
  };

  useEffect(() => {
    const finalSearchPaginationInput = {
      ...searchPaginationInput,
      textSearch: '',
      page: 0,
      size: 100,
    };

    searchInjectorContracts(finalSearchPaginationInput).then((result: { data: Page<InjectorContract> }) => {
      const { data } = result;
      setContracts(data.content);
    });
  }, []);

  const [value, setValue] = useState<string | null | undefined>(fieldValue ?? '');

  return (
    <div style={{ marginTop: 20 }}>
      <Combobox
        openOnFocus
        required
        error={!!error}
        options={contracts}
        value={contracts.find(i => i.injector_contract_id === value) ?? null}
        onValueChange={(injectorContract) => {
          const next = injectorContract as typeof contracts[number] | null;
          setValue(next?.injector_contract_id);
          onChange(next?.injector_contract_id);
        }}
        onInputChange={inputValue => searchContract(inputValue)}
        getOptionLabel={option => tPick(option.injector_contract_labels)}
        renderOption={option => (
          <>
            <div className={classes.icon}>
              <InjectIcon
                type={
                  option.injector_contract_payload
                    ? (option.injector_contract_payload?.payload_collector_type ?? option.injector_contract_payload?.payload_type)
                    : option.injector_contract_injector_type
                }
                isPayload={isNotEmptyField(option.injector_contract_payload)}
              />
            </div>
            <div className={classes.text}>
              {tPick(option.injector_contract_labels)}
            </div>
          </>
        )}
      >
        <ComboboxLabel>{t(label)}</ComboboxLabel>
        <ComboboxField>
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent emptyMessage={t('No available options')} />
        {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
      </Combobox>
    </div>
  );
};

export default InjectContractComponent;

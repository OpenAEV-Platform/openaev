import {
  Combobox,
  type ComboboxChangeMeta,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { Checkbox, Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useMemo } from 'react';

import { type GroupOption, type Option } from '../../utils/Option';
import { useFormatter } from '../i18n';

type AutocompleteOption = GroupOption | Option;

interface BaseProps {
  label: string;
  options: AutocompleteOption[];
  onInputChange: (search: string) => void;
  disableCloseOnSelect?: boolean;
  disableOptionTooltip?: boolean;
  open?: boolean;
  placeholder?: string;
  onOpenChange?: (open: boolean, meta: ComboboxChangeMeta) => void;
  required?: boolean;
  error?: boolean;
  className?: string;
  disabled?: boolean;
  style?: CSSProperties;
  /**
   * Hides an option outright. Was expressed as a `renderOption` returning
   * `null`: MUI let the row vanish that way, but the wrapper never used the
   * node a caller returned — only whether it was `null`. So the contract has
   * always been "hide this option", and it is now stated as one.
   */
  hideOption?: (option: AutocompleteOption) => boolean;
  openOnFocus?: boolean;
  selectOnFocus?: boolean;
  autoFocus?: boolean;
}

interface SingleProps extends BaseProps {
  multiple?: false;
  value: string | undefined;
  onChange: (value: string | undefined) => void;
}

interface MultipleProps extends BaseProps {
  multiple: true;
  value: string[];
  onChange: (value: string[]) => void;
}

type Props = SingleProps | MultipleProps;

const AutocompleteField: FunctionComponent<Props> = (props) => {
  const {
    label,
    options = [],
    onInputChange,
    required = false,
    error = false,
    className = '',
    disabled,
    openOnFocus = true,
    selectOnFocus = true,
    disableOptionTooltip = false,
    autoFocus = false,
    hideOption,
    placeholder,
  } = props;

  const multiple = props.multiple === true;
  const value = props.value;
  const { t } = useFormatter();

  // Hiding is done on the list itself rather than through `filterOptions`, so
  // the library keeps owning the text search it applies on top.
  const visibleOptions = useMemo(
    () => (hideOption ? options.filter(o => !hideOption(o)) : options),
    [options, hideOption],
  );

  const selectedOption = useMemo(() => {
    if (!options.length) {
      return multiple ? [] : null;
    }

    if (props.multiple) {
      return options.filter(o => props.value.includes(o.id));
    }

    return options.find(o => o.id === props.value) ?? null;
  }, [props.value, options, props.multiple]);

  const handleValue = (
    newValue: AutocompleteOption | AutocompleteOption[] | null,
  ) => {
    if (props.multiple) {
      const ids = (newValue as AutocompleteOption[]).map(v => v.id);
      props.onChange(ids);
    } else {
      const id = (newValue as AutocompleteOption | null)?.id;
      props.onChange(id);
    }
  };

  const renderRow = (option: AutocompleteOption) => {
    const checked = multiple
      ? (value as string[] | undefined)?.includes(option.id)
      : value === option.id;

    const body = (
      <>
        {/* The row already carries `aria-selected`, so the box is a visual echo
            and is taken out of the accessibility tree rather than named twice. */}
        {multiple && (
          <Checkbox
            checked={!!checked}
            size="small"
            sx={{ padding: 0 }}
            inputProps={{
              'aria-hidden': true,
              'tabIndex': -1,
            }}
          />
        )}
        <span
          style={{
            flexGrow: 1,
            minWidth: 0,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            fontStyle: option.italic ? 'italic' : 'normal',
          }}
        >
          {option.label}
        </span>
      </>
    );

    if (disableOptionTooltip) {
      return body;
    }

    // The tooltip now wraps the row's CONTENT, not the row: the library owns the
    // row element and its accessibility contract. The anchor is given a real box
    // filling the row, since `display: contents` leaves nothing to hover.
    return (
      <Tooltip title={option.label}>
        <span
          style={{
            display: 'flex',
            flex: 1,
            minWidth: 0,
            alignItems: 'center',
          }}
        >
          {body}
        </span>
      </Tooltip>
    );
  };

  return (
    <div className={className} style={props.style}>
      <Combobox<AutocompleteOption>
        disabled={disabled}
        multiple={multiple}
        open={props.open}
        onOpenChange={props.onOpenChange}
        openOnFocus={openOnFocus}
        selectOnFocus={selectOnFocus}
        closeOnSelect={props.disableCloseOnSelect !== true}
        options={visibleOptions}
        value={selectedOption}
        groupBy={option => ('group' in option ? option.group : '')}
        getOptionLabel={option => option.label ?? ''}
        isOptionEqualToValue={(option, val) => option.id === val.id}
        error={error}
        onInputChange={(search, meta) => {
          if (meta.cause === 'type') {
            onInputChange(search);
          }
        }}
        onValueChange={newValue => handleValue(newValue)}
        renderOption={renderRow}
      >
        <ComboboxLabel>
          {label}
          {required ? ' *' : ''}
        </ComboboxLabel>
        <ComboboxField>
          {multiple && <ComboboxChips />}
          <ComboboxInput autoFocus={autoFocus} placeholder={placeholder} />
          <ComboboxControls>
            <ComboboxClear />
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent emptyMessage={t('No available options')} />
      </Combobox>
    </div>
  );
};

export default AutocompleteField;

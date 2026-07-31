import { Autocomplete, Box, Checkbox, TextField, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, type HTMLAttributes, type Key, type ReactNode, useMemo } from 'react';

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
  required?: boolean;
  error?: boolean;
  className?: string;
  variant?: 'standard' | 'outlined' | 'filled';
  disabled?: boolean;
  style?: CSSProperties;
  renderOption?: (
    props: HTMLAttributes<HTMLLIElement>,
    option: AutocompleteOption,
  ) => ReactNode;
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
    variant = 'outlined',
    disabled,
    openOnFocus = true,
    selectOnFocus = true,
    disableOptionTooltip = false,
    autoFocus = false,
  } = props;

  const multiple = props.multiple === true;
  const value = props.value;
  const { t } = useFormatter();
  const theme = useTheme();

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

  const defaultRenderOption = (
    liProps: HTMLAttributes<HTMLLIElement> & { key?: Key },
    option: AutocompleteOption,
  ) => {
    const checked = multiple
      ? value?.includes(option.id)
      : value === option.id;

    // React ignores a `key` arriving through a props spread, so extract it and
    // set it explicitly on the outermost element of the returned node.
    const { key, ...itemProps } = liProps;
    const optionKey = key ?? option.id;

    const listItem = (itemKey?: Key) => (
      <Box
        component="li"
        key={itemKey}
        {...itemProps}
        sx={{
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          padding: 0,
          margin: 0,
          display: 'flex',
          alignItems: 'center',
        }}
      >
        {multiple && <Checkbox checked={checked} />}

        <Box
          sx={{
            display: 'inline-block',
            flexGrow: 1,
            marginLeft: multiple ? theme.spacing(1) : 0,
            fontStyle: option.italic ? 'italic' : 'normal',
          }}
        >
          {option.label}
        </Box>
      </Box>
    );

    if (disableOptionTooltip) {
      return listItem(optionKey);
    }

    return (
      <Tooltip key={optionKey} title={option.label}>
        {listItem()}
      </Tooltip>
    );
  };

  return (
    <Autocomplete<AutocompleteOption, boolean>
      style={props.style}
      disabled={disabled}
      className={className}
      size="small"
      open={props.open}
      selectOnFocus={selectOnFocus}
      openOnFocus={openOnFocus}
      autoHighlight
      disableCloseOnSelect={props.disableCloseOnSelect ?? false}
      noOptionsText={t('No available options')}
      multiple={multiple}
      options={options}
      value={selectedOption}
      groupBy={option => ('group' in option ? option.group : '')}
      getOptionLabel={option => option.label ?? ''}
      isOptionEqualToValue={(option, val) => option.id === val.id}
      onInputChange={(_, search, reason) => {
        if (reason === 'input') {
          onInputChange(search);
        }
      }}
      onChange={(_, newValue) => handleValue(newValue)}
      renderInput={params => (
        <TextField
          {...params}
          label={label}
          variant={variant}
          size="small"
          required={required}
          error={error}
          // autoFocus must live on the TextField: on the Autocomplete root it
          // would land on a plain div and never focus the input.
          autoFocus={autoFocus}
        />
      )}
      renderOption={(liProps, option) => {
        if (props.renderOption) {
          const custom = props.renderOption(liProps, option);
          if (custom === null) {
            return null;
          }
        }

        return defaultRenderOption(liProps, option);
      }}
    />
  );
};

export default AutocompleteField;

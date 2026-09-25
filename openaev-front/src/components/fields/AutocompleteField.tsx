import { Combobox, type ComboboxChangeMeta, ComboboxChips, ComboboxClear, ComboboxContent, ComboboxControls, ComboboxField, ComboboxInput, ComboboxLabel, ComboboxTrigger, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent, type ReactNode, useMemo } from 'react';

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
  /**
   * Draws the BODY of an option row — not the row. The row element, its
   * selection box, its tooltip and its truncation stay owned here; this only
   * replaces what sits inside, so a caller cannot break the row's contract.
   *
   * Restored from main (Camille Roux, #5274): before that fix this prop was
   * called and its result thrown away, so a caller's node never rendered. It is
   * the counterpart of `hideOption` — one says what a row looks like, the other
   * says whether the option is in the list at all.
   *
   * WCAG 1.1.1 / 4.1.2: the row's accessible name is the TEXT this returns, so
   * any glyph must be decorative. MUI icons are (`aria-hidden` unless given
   * `titleAccess`); a glyph carrying its own name would be announced twice.
   */
  renderOption?: (option: AutocompleteOption) => ReactNode;
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
    renderOption,
    placeholder,
  } = props;

  const multiple = props.multiple === true;
  const { t } = useFormatter();

  // Hiding is done on the list itself rather than through `filterOptions`, so
  // the library keeps owning the text search it applies on top.
  const hasGroups = useMemo(
    () => options.some(option => 'group' in option && !!option.group),
    [options],
  );

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
    const custom = renderOption?.(option);

    const body = (
      <>
        {/* No box drawn here: in `multiple` the library's own option row already
            renders one, presentational, before this content (Combobox.tsx). Ours
            was a second box on every row — measured, 2 per row. */}
        <span
          style={{
            flexGrow: 1,
            minWidth: 0,
            overflow: 'hidden',
            // A caller-drawn body lays out its own children (a glyph beside a
            // label), so it gets a flex box. Plain text keeps the single-line
            // ellipsis, which only means anything on a text node.
            ...(custom !== undefined
              ? {
                  display: 'flex',
                  alignItems: 'center',
                }
              : {
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }),
            fontStyle: option.italic ? 'italic' : 'normal',
          }}
        >
          {custom !== undefined ? custom : option.label}
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
      <Tooltip>
        <TooltipTrigger asChild>
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
        </TooltipTrigger>
        {option.label && <TooltipContent>{option.label}</TooltipContent>}
      </Tooltip>
    );
  };

  return (
    <div className={className} style={props.style}>
      <Combobox<AutocompleteOption>
        required={required}
        disabled={disabled}
        multiple={multiple}
        open={props.open}
        onOpenChange={props.onOpenChange}
        openOnFocus={openOnFocus}
        selectOnFocus={selectOnFocus}
        closeOnSelect={props.disableCloseOnSelect !== true}
        options={visibleOptions}
        value={selectedOption}
        // Only when the list ACTUALLY groups. Supplying `groupBy` unconditionally
        // made every ungrouped list one group whose label is "", and the library
        // draws a full 32px header row per group — an empty band above the first
        // option. MUI drew nothing for an empty label, which is why it survived
        // the conversion unnoticed.
        groupBy={hasGroups ? option => ('group' in option ? option.group : '') : undefined}
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
        <ComboboxLabel required={required}>{label}</ComboboxLabel>
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

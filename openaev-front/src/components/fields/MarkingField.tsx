import { Lens } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Chip, TextField } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useMemo } from 'react';
import { type GlobalError } from 'react-hook-form';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { hexToRGB } from '../../utils/Colors';
import useMarkingDefinitions from '../../utils/hooks/useMarkingDefinitions';
import { markingLabel } from '../ItemMarkings';

interface Props {
  label?: string;
  fieldValue: string[];
  fieldOnChange: (values: string[]) => void;
  error?: GlobalError;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

// Most restrictive first within a type (TLP:RED before TLP:AMBER before ...) - same ordering as
// GroupManageMarkings and ItemMarkings, so a marking reads the same way in every picker/display.
const sortMarkings = (definitions: MarkingDefinitionOutput[]): MarkingDefinitionOutput[] =>
  [...definitions].sort((a, b) => {
    if (a.marking_definition_type !== b.marking_definition_type) {
      return a.marking_definition_type.localeCompare(b.marking_definition_type);
    }
    return b.marking_definition_order - a.marking_definition_order;
  });

// This never creates a marking definition inline: definitions are managed from Settings > Marking
// definitions, and are few enough (nine seeded TLP/PAP levels per tenant) that there is no product
// need for ad-hoc creation from a form. This field only assigns/removes the definitions that
// already exist.
const MarkingField: FunctionComponent<Props> = ({
  label,
  fieldValue,
  fieldOnChange,
  error,
  style = {},
  disabled = false,
  required = false,
}) => {
  const definitions = useMarkingDefinitions();

  const options = useMemo(() => sortMarkings(Object.values(definitions)), [definitions]);

  // An id with no matching definition (e.g. a marking deleted after being assigned) is dropped
  // rather than rendered raw - same convention as ItemMarkings.
  const value = useMemo(
    () => fieldValue
      .map(id => definitions[id])
      .filter((marking): marking is MarkingDefinitionOutput => !!marking),
    [fieldValue, definitions],
  );

  return (
    <div style={{
      position: 'relative',
      ...style,
    }}
    >
      <MuiAutocomplete
        multiple
        size="small"
        selectOnFocus
        autoHighlight
        clearOnBlur={false}
        clearOnEscape={false}
        disabled={disabled}
        options={options}
        value={value}
        onChange={(_, newValue) => {
          fieldOnChange(newValue.map(v => v.marking_definition_id));
        }}
        isOptionEqualToValue={(option, val) => option.marking_definition_id === val.marking_definition_id}
        getOptionLabel={option => markingLabel(option)}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.marking_definition_id}>
            <Lens sx={{
              color: option.marking_definition_color,
              marginRight: 1,
              fontSize: 'small',
            }}
            />
            <span>{markingLabel(option)}</span>
          </Box>
        )}
        renderTags={(markingValue, getMarkingProps) => markingValue.map((option, index) => {
          const { key, ...markingProps } = getMarkingProps({ index });
          return (
            <Chip
              key={key}
              {...markingProps}
              variant="outlined"
              size="small"
              label={markingLabel(option)}
              sx={{
                color: option.marking_definition_color,
                borderColor: option.marking_definition_color,
                backgroundColor: option.marking_definition_color ? hexToRGB(option.marking_definition_color) : undefined,
              }}
            />
          );
        })}
        renderInput={params => (
          <TextField
            {...params}
            label={label}
            variant="standard"
            fullWidth
            error={!!error}
            required={required}
          />
        )}
      />
    </div>
  );
};

export default MarkingField;

import { Lens } from '@mui/icons-material';
import { Autocomplete as MuiAutocomplete, Box, Chip, TextField } from '@mui/material';
import { type CSSProperties, type FunctionComponent, useContext, useMemo } from 'react';
import { type GlobalError } from 'react-hook-form';

import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { hexToRGB } from '../../utils/Colors';
import useMarkingDefinitions from '../../utils/hooks/useMarkingDefinitions';
import { AbilityContext } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
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
  // The backend's GET /api/marking_definitions/assignable requires ACCESS_MARKING_DEFINITION
  // (Action.SEARCH/READ + ResourceType.MARKING_DEFINITION - see Capability.java); a caller without
  // it would just get a 403. Checked here too, client-side, so the picker never fires that request
  // in the first place - per the platform's own convention (see PERMISSION_REQUIRED's doc),
  // reading rights hide the affordance entirely rather than showing it disabled.
  const ability = useContext(AbilityContext);
  const canAccessMarkingDefinitions = ability.can(ACTIONS.ACCESS, SUBJECTS.MARKING_DEFINITION);

  // Only the markings the current user is cleared to assign - offering one they don't hold would
  // just fail server-side at submit time (see MarkingEscalationValidator). Whatever is already
  // assigned is guaranteed to already be within their clearance too: a row is only visible at all
  // when its markings are a subset of the viewer's own, so there is no case here where the current
  // value would fall outside this same filtered set.
  const definitions = useMarkingDefinitions({
    assignableOnly: true,
    skip: !canAccessMarkingDefinitions,
  });

  const options = useMemo(() => sortMarkings(Object.values(definitions)), [definitions]);

  // An id with no matching definition (e.g. a marking deleted after being assigned) is dropped
  // rather than rendered raw - same convention as ItemMarkings.
  const value = useMemo(
    () => fieldValue
      .map(id => definitions[id])
      .filter((marking): marking is MarkingDefinitionOutput => !!marking),
    [fieldValue, definitions],
  );

  if (!canAccessMarkingDefinitions) {
    return null;
  }

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

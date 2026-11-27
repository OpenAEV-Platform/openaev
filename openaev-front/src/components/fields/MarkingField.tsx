import {
  Combobox,
  ComboboxChips,
  ComboboxClear,
  ComboboxContent,
  ComboboxControls,
  ComboboxField,
  ComboboxHelperText,
  ComboboxInput,
  ComboboxLabel,
  ComboboxTrigger,
} from '@filigran/design-system';
import { Lens } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, useMemo } from 'react';
import { type GlobalError } from 'react-hook-form';

import { type MarkingDefinitionHelper } from '../../actions/marking_definitions/marking-definition-helper';
import { useHelper } from '../../store';
import { type MarkingDefinitionOutput } from '../../utils/api-types';
import { MESSAGING$ } from '../../utils/Environment';
import { collapseToHighestPerType, markingLabel, sortMarkings } from '../../utils/markings';
import { useFormatter } from '../i18n';

interface Props {
  label?: string;
  fieldValue: string[];
  fieldOnChange: (values: string[]) => void;
  error?: GlobalError;
  style?: CSSProperties;
  disabled?: boolean;
  required?: boolean;
}

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
  const { t } = useFormatter();
  const theme = useTheme();

  // Only the markings the current user is cleared to assign - offering one they don't hold would
  // just fail server-side at submit time (see MarkingEscalationValidator). Whatever is already
  // assigned is guaranteed to already be within their clearance too: a row is only visible at all
  // when its markings are a subset of the viewer's own, so there is no case here where the current
  // value would fall outside this same filtered set.
  const { definitions } = useHelper((helper: MarkingDefinitionHelper) => ({ definitions: helper.getMarkingDefinitionsMap() }));

  const options = useMemo(() => sortMarkings(Object.values(definitions)), [definitions]);

  // An id with no matching definition (e.g. a marking deleted after being assigned) is dropped
  // rather than rendered raw - same convention as ItemMarkings. Also collapsed to the highest
  // level per type: a level implies every less restrictive level of the same type, so there is
  // never a reason to hold more than one per type - this is a defensive fallback for a value that
  // predates that rule (or was written some other way), since handleChange below prevents a new
  // one from ever entering through this field.
  const value = useMemo(
    () => collapseToHighestPerType(
      fieldValue
        .map(id => definitions[id])
        .filter((marking): marking is MarkingDefinitionOutput => !!marking),
    ),
    [fieldValue, definitions],
  );

  // Enforces "at most one marking per type" on the selection itself, not just its display:
  // adding a level of a type already selected either replaces the lower one (silently - picking a
  // stronger level is an unsurprising upgrade) or, if it is the weaker one, is rejected outright
  // with an explanation - a type can only ever be covered by one level at a time, so there is
  // nothing to add otherwise.
  const handleChange = (newValue: MarkingDefinitionOutput[]) => {
    const removed = value.filter(
      current => !newValue.some(n => n.marking_definition_id === current.marking_definition_id),
    );
    const added = newValue.filter(
      candidate => !value.some(current => current.marking_definition_id === candidate.marking_definition_id),
    );

    let next = value.filter(
      current => !removed.some(r => r.marking_definition_id === current.marking_definition_id),
    );
    added.forEach((candidate) => {
      const existing = next.find(
        current => current.marking_definition_type === candidate.marking_definition_type,
      );
      if (!existing) {
        next = [...next, candidate];
        return;
      }
      if (candidate.marking_definition_order > existing.marking_definition_order) {
        next = next.filter(current => current !== existing).concat(candidate);
        return;
      }
      MESSAGING$.notifyError(
        t('{rejected} was not added: {kept} is already selected and takes precedence for this marking type.', {
          rejected: markingLabel(candidate),
          kept: markingLabel(existing),
        }),
      );
    });

    fieldOnChange(next.map(marking => marking.marking_definition_id));
  };

  return (
    <div style={{
      position: 'relative',
      ...style,
    }}
    >
      <Combobox<MarkingDefinitionOutput>
        required={required}
        multiple
        value={value}
        options={options}
        disabled={disabled}
        selectOnFocus
        keepInputOnBlur
        onValueChange={newValue => handleChange(newValue as MarkingDefinitionOutput[])}
        getOptionLabel={markingLabel}
        isOptionEqualToValue={(option, val) => option.marking_definition_id === val.marking_definition_id}
        getChipColor={option => option.marking_definition_color}
        error={!!error}
        renderOption={option => (
          <span style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: theme.spacing(1),
          }}
          >
            <Lens fontSize="small" style={{ color: option.marking_definition_color }} />
            {markingLabel(option)}
          </span>
        )}
      >
        <ComboboxLabel required={required}>{label}</ComboboxLabel>
        <ComboboxField>
          <ComboboxChips />
          <ComboboxInput />
          <ComboboxControls>
            <ComboboxClear />
            <ComboboxTrigger />
          </ComboboxControls>
        </ComboboxField>
        <ComboboxContent />
        {error?.message ? <ComboboxHelperText>{error.message}</ComboboxHelperText> : null}
      </Combobox>
    </div>
  );
};

export default MarkingField;

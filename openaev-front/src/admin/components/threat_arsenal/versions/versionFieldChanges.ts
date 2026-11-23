import { type ChangesTableRow } from '../../../../components/common/update/ChangesTable';
import { type ThreatArsenalActionFullOutput } from '../../../../utils/api-types';
import { CUSTOMIZABLE_FIELD_LABELS, CUSTOMIZABLE_FORM_FIELDS, type CustomizableField, fieldText } from '../customization/fieldUpdates';
import sameValue from '../customization/sameValue';

type Details = ThreatArsenalActionFullOutput;

// What a version runs, in the order the action form shows it.
const RUN_FIELDS: CustomizableField[] = ['PLATFORMS', 'COMMAND_EXECUTOR', 'COMMAND_CONTENT', 'ARGUMENTS', 'PREREQUISITES', 'CLEANUP_EXECUTOR', 'CLEANUP_COMMAND'];

const formValue = (details: Details, field: CustomizableField) => (details as unknown as Record<string, unknown>)[CUSTOMIZABLE_FORM_FIELDS[field]];

/**
 * What changed from one version of a payload to the next, field by field, as the before / after rows of
 * the ChangesTable: the name and description, then what the payload runs. Unchanged fields are left out.
 */
const versionFieldChanges = (before: Details, after: Details, name: (details: Details) => string): ChangesTableRow[] => {
  const rows: ChangesTableRow[] = [];
  if (name(before) !== name(after)) {
    rows.push({
      id: 'NAME',
      field: 'Name',
      before: name(before),
      after: name(after),
    });
  }
  if (!sameValue(before.action_description, after.action_description)) {
    rows.push({
      id: 'DESCRIPTION',
      field: 'Description',
      before: before.action_description ?? '',
      after: after.action_description ?? '',
    });
  }
  RUN_FIELDS.forEach((field) => {
    const left = formValue(before, field);
    const right = formValue(after, field);
    if (!sameValue(left, right)) {
      rows.push({
        id: field,
        field: CUSTOMIZABLE_FIELD_LABELS[field],
        before: fieldText(field, left),
        after: fieldText(field, right),
      });
    }
  });
  return rows;
};

export default versionFieldChanges;

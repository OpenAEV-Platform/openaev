import { type ChangesTableRow } from '../../../../components/common/update/ChangesTable';
import { type PayloadVersionChangeOutput } from '../../../../utils/api-types';
import { CUSTOMIZABLE_FIELD_LABELS } from '../customization/fieldUpdates';

// The payload's own name of each field the action form shows; any other field reads from its name.
const FIELD_LABELS: Record<string, string> = {
  payload_name: 'Name',
  payload_description: 'Description',
  payload_platforms: CUSTOMIZABLE_FIELD_LABELS.PLATFORMS,
  command_executor: CUSTOMIZABLE_FIELD_LABELS.COMMAND_EXECUTOR,
  command_content: CUSTOMIZABLE_FIELD_LABELS.COMMAND_CONTENT,
  payload_arguments: CUSTOMIZABLE_FIELD_LABELS.ARGUMENTS,
  payload_prerequisites: CUSTOMIZABLE_FIELD_LABELS.PREREQUISITES,
  payload_cleanup_executor: CUSTOMIZABLE_FIELD_LABELS.CLEANUP_EXECUTOR,
  payload_cleanup_command: CUSTOMIZABLE_FIELD_LABELS.CLEANUP_COMMAND,
};

const fieldLabel = (field: string) => FIELD_LABELS[field] ?? field
  .replace(/^(payload|command)_/, '')
  .replace(/_/g, ' ')
  .replace(/^./, first => first.toUpperCase());

const itemText = (item: unknown) => (typeof item === 'string' ? item : JSON.stringify(item));

/** A value as text: a list one item per line, nothing when empty. */
const valueText = (value: unknown): string => {
  if (value === null || value === undefined) return '';
  if (Array.isArray(value)) return value.map(itemText).join('\n');
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

/** What a version changed, as the before / after rows of the ChangesTable. */
const versionFieldChanges = (changes: PayloadVersionChangeOutput[]): ChangesTableRow[] => changes.map(change => ({
  id: change.change_field,
  field: fieldLabel(change.change_field),
  before: valueText(change.change_before),
  after: valueText(change.change_after),
}));

export default versionFieldChanges;

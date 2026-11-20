import { type PayloadArgument, type PayloadFieldUpdateOutput, type PayloadPrerequisite } from '../../../../utils/api-types';

export type CustomizableField = PayloadFieldUpdateOutput['field_name'];

export type CustomizableBlock = PayloadFieldUpdateOutput['field_block'];

export const CUSTOMIZABLE_FIELD_LABELS: Record<CustomizableField, string> = {
  COMMAND_EXECUTOR: 'Executor',
  COMMAND_CONTENT: 'Command',
  CLEANUP_EXECUTOR: 'Cleanup executor',
  CLEANUP_COMMAND: 'Cleanup command',
  PLATFORMS: 'Platforms',
  ARGUMENTS: 'Arguments',
  PREREQUISITES: 'Prerequisites',
};

/** The action form field of each field a customized copy may change. */
export const CUSTOMIZABLE_FORM_FIELDS: Record<CustomizableField, string> = {
  COMMAND_EXECUTOR: 'command_executor',
  COMMAND_CONTENT: 'command_content',
  CLEANUP_EXECUTOR: 'action_cleanup_executor',
  CLEANUP_COMMAND: 'action_cleanup_command',
  PLATFORMS: 'action_platforms',
  ARGUMENTS: 'action_arguments',
  PREREQUISITES: 'action_prerequisites',
};

const asList = (value: unknown): unknown[] => {
  if (value === null || value === undefined || value === '') {
    return [];
  }
  return Array.isArray(value) ? value : [value];
};

const ITEM_TEXT: Partial<Record<CustomizableField, (item: unknown) => string>> = {
  ARGUMENTS: (item) => {
    const argument = item as PayloadArgument;
    return `${argument.key} = ${argument.default_value ?? ''}`;
  },
  PREREQUISITES: (item) => {
    const prerequisite = item as PayloadPrerequisite;
    return `${prerequisite.executor}: ${prerequisite.get_command}`;
  },
};

/** A field value as text: platforms, arguments and prerequisites one per line. */
export const fieldText = (field: CustomizableField, value: unknown) => asList(value)
  .map(ITEM_TEXT[field] ?? String)
  .join('\n');

/** One side of a conflicting block as text: each field under its label when the block has several. */
export const blockText = (
  fields: PayloadFieldUpdateOutput[],
  valueOf: (field: PayloadFieldUpdateOutput) => unknown,
  labelOf: (field: PayloadFieldUpdateOutput) => string,
) => (fields.length === 1
  ? fieldText(fields[0].field_name, valueOf(fields[0]))
  : fields.map(field => `${labelOf(field)}\n${fieldText(field.field_name, valueOf(field))}`).join('\n\n'));

// Keys of the payload's hashed content (the backend's canonical form); an unknown key shows as is.
const OTHER_CHANGE_LABELS: Record<string, string> = {
  payload_name: 'Name',
  payload_description: 'Description',
  payload_attack_patterns: 'Attack patterns',
  payload_expectations: 'Expectations',
  payload_expected_security_platforms: 'Expected security platforms',
  payload_execution_arch: 'Architecture',
  payload_detection_remediations: 'Remediation',
  payload_output_parsers: 'Output',
  executable_file: 'Executable file',
  file_drop_file: 'File to drop',
  dns_resolution_hostname: 'Hostname',
};

export const otherChangeLabel = (key: string) => OTHER_CHANGE_LABELS[key] ?? key;

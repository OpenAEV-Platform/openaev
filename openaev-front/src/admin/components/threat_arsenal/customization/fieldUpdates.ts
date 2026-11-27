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

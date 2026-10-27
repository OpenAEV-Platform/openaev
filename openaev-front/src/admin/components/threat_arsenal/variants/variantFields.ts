// Fields a variant may change, in display order, with their label and how they are edited.
export const VARIANT_FIELD_LABELS: Record<string, string> = {
  command_executor: 'Executor',
  command_content: 'Command',
  payload_cleanup_executor: 'Cleanup executor',
  payload_cleanup_command: 'Cleanup command',
  payload_platforms: 'Platforms',
  payload_prerequisites: 'Prerequisites',
  payload_arguments: 'Arguments',
};

const LIST_FIELDS = new Set(['payload_platforms', 'payload_prerequisites', 'payload_arguments']);

export const isListField = (field: string) => LIST_FIELDS.has(field);

export const toText = (field: string, value: unknown): string => {
  if (value === null || value === undefined) {
    return '';
  }
  return isListField(field) ? JSON.stringify(value, null, 2) : String(value);
};

// Throws on invalid JSON: the caller tells the user which field.
export const fromText = (field: string, text: string): unknown => {
  if (isListField(field)) {
    return text.trim() === '' ? [] : JSON.parse(text);
  }
  return text === '' ? null : text;
};

export const sameValue = (left: unknown, right: unknown) => JSON.stringify(left ?? null) === JSON.stringify(right ?? null);

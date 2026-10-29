import { type VariantBlockPreview, type VariantRebasePreview } from '../../../../actions/payloads/payload-variant-actions';
import { handleCleanupCommandValue, handleCleanupExecutorValue, type ThreatArsenalActionFormValues } from '../actionFormValues';

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

const canonical = (value: unknown): unknown => {
  if (value === null || value === undefined || value === '') {
    return undefined;
  }
  if (Array.isArray(value)) {
    return value.map(canonical);
  }
  if (typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .map(([key, v]) => [key, canonical(v)])
      .filter(([, v]) => v !== undefined)
      .sort(([a], [b]) => String(a).localeCompare(String(b))));
  }
  return value;
};

// Empty values and key order do not count as a change.
export const sameValue = (left: unknown, right: unknown) => JSON.stringify(canonical(left) ?? null) === JSON.stringify(canonical(right) ?? null);

// The catalogue values in the shape of the action form, so a field can go back to them.
export const toFormValues = (values: Record<string, unknown>): ThreatArsenalActionFormValues => ({
  command_executor: (values.command_executor as string | null) ?? '',
  command_content: (values.command_content as string | null) ?? '',
  action_cleanup_executor: (values.payload_cleanup_executor as string | null) ?? '',
  action_cleanup_command: (values.payload_cleanup_command as string | null) ?? '',
  action_platforms: (values.payload_platforms ?? []) as ThreatArsenalActionFormValues['action_platforms'],
  action_prerequisites: (values.payload_prerequisites ?? []) as ThreatArsenalActionFormValues['action_prerequisites'],
  action_arguments: ((values.payload_arguments ?? []) as NonNullable<ThreatArsenalActionFormValues['action_arguments']>).map(arg => ({
    ...arg,
    description: arg.description ?? undefined,
    separator: arg.separator ?? undefined,
  })),
});

// The form values in the shape of the overrides.
export const fromFormValues = (form: ThreatArsenalActionFormValues): Record<string, unknown> => ({
  command_executor: form.command_executor,
  command_content: form.command_content,
  payload_cleanup_executor: handleCleanupExecutorValue(form.action_cleanup_executor as string ?? '', form.action_cleanup_command as string ?? ''),
  payload_cleanup_command: handleCleanupCommandValue(form.action_cleanup_command as string ?? ''),
  payload_platforms: form.action_platforms,
  payload_prerequisites: form.action_prerequisites,
  payload_arguments: form.action_arguments,
});

export const changedFields = (block: VariantBlockPreview, side: 'catalog' | 'mine') => Object.keys(block.base)
  .filter(field => !sameValue(block.base[field], block[side][field]));

// The fields the user changed that an update keeps without asking (no conflict on them).
export const keptChanges = (preview: VariantRebasePreview) => preview.blocks
  .filter(block => !block.conflict)
  .flatMap(block => changedFields(block, 'mine'))
  .map(field => VARIANT_FIELD_LABELS[field] ?? field);

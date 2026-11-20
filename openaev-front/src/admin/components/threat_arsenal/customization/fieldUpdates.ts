import { type PayloadFieldUpdateOutput } from '../../../../utils/api-types';
import sameValue from './sameValue';

export type CustomizableField = PayloadFieldUpdateOutput['field_name'];

export const CUSTOMIZABLE_FIELD_LABELS: Record<CustomizableField, string> = {
  COMMAND_EXECUTOR: 'Executor',
  COMMAND_CONTENT: 'Command',
  CLEANUP_EXECUTOR: 'Cleanup executor',
  CLEANUP_COMMAND: 'Cleanup command',
  PLATFORMS: 'Platforms',
  ARGUMENTS: 'Arguments',
  PREREQUISITES: 'Prerequisites',
};

export const changedByFiligran = (field: PayloadFieldUpdateOutput) => !sameValue(field.field_base, field.field_filigran);

export const changedByYou = (field: PayloadFieldUpdateOutput) => !sameValue(field.field_base, field.field_yours);

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

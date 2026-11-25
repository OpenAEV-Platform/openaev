import { type Translate } from '../../i18n';

/**
 * A field that differs between two versions of a payload, as the API gives it (version changes,
 * draft changes and the History tab): the fields of the payload's content hash, so every change
 * that makes a new version has one.
 */
export interface PayloadFieldChange {
  field: string;
  before?: unknown;
  after?: unknown;
}

/** i18n keys of the payload content fields. */
export const PAYLOAD_FIELD_LABELS: Record<string, string> = {
  payload_type: 'Type',
  payload_name: 'Name',
  payload_description: 'Description',
  payload_platforms: 'Platforms',
  payload_execution_arch: 'Architecture',
  payload_elevation_required: 'Elevation required',
  payload_expectations: 'Expectations',
  payload_expected_security_platforms: 'Expected security platforms',
  payload_arguments: 'Arguments',
  payload_prerequisites: 'Prerequisites',
  payload_cleanup_executor: 'Cleanup executor',
  payload_cleanup_command: 'Cleanup command',
  command_executor: 'Executor',
  command_content: 'Command',
  dns_resolution_hostname: 'Hostname',
  network_traffic_ip_src: 'Source IP',
  network_traffic_ip_dst: 'Destination IP',
  network_traffic_port_src: 'Source port',
  network_traffic_port_dst: 'Destination port',
  network_traffic_protocol: 'Protocol',
  ai_attack_engine: 'Engine',
  ai_attack_category: 'Category',
  ai_attack_content: 'Content',
  ai_attack_multi_turn: 'Multi-turn',
  ai_attack_converters: 'Converters',
  ai_attack_success_detector: 'Success detector',
  executable_file: 'Executable file',
  file_drop_file: 'File to drop',
  payload_attack_patterns: 'Attack patterns',
  payload_detection_remediations: 'Remediations',
  payload_output_parsers: 'Outputs',
};

/** Compared line by line like code: commands, and every field shown as one item per line. */
export const PAYLOAD_CODE_FIELDS = new Set([
  'command_content',
  'payload_cleanup_command',
  'payload_arguments',
  'payload_prerequisites',
  'payload_expectations',
  'payload_expected_security_platforms',
  'payload_attack_patterns',
  'payload_detection_remediations',
  'payload_output_parsers',
  'ai_attack_content',
  'ai_attack_multi_turn',
  'ai_attack_converters',
  'ai_attack_success_detector',
]);

export const payloadFieldLabel = (t: Translate, field: string) => t(PAYLOAD_FIELD_LABELS[field] ?? field);

interface ArgumentValue {
  key?: string;
  type?: string;
  default_value?: unknown;
  separator?: string;
}

interface PrerequisiteValue {
  executor?: string;
  get_command?: string;
  check_command?: string;
}

interface RemediationValue {
  security_platform_type?: string;
  detection_remediation_values?: string;
}

const scalar = (value: unknown) => (typeof value === 'string' ? value : JSON.stringify(value));

const FORMATS: Record<string, (value: unknown, t: Translate) => string> = {
  payload_elevation_required: (value, t) => (value === true ? t('Yes') : t('No')),
  payload_arguments: value => (value as ArgumentValue[])
    .map(arg => `${arg.key ?? ''} (${arg.type ?? ''}) = ${arg.default_value === undefined ? '' : scalar(arg.default_value)}${arg.separator ? ` [${arg.separator}]` : ''}`)
    .join('\n'),
  payload_prerequisites: value => (value as PrerequisiteValue[])
    .map(prerequisite => [
      `${prerequisite.executor ?? ''}: ${prerequisite.get_command ?? ''}`,
      prerequisite.check_command ? `  check: ${prerequisite.check_command}` : '',
    ].filter(Boolean).join('\n'))
    .join('\n'),
  payload_expected_security_platforms: value => Object.entries(value as Record<string, string[]>)
    .map(([expectation, platforms]) => `${expectation}: ${platforms.join(', ')}`)
    .join('\n'),
  payload_detection_remediations: value => (value as RemediationValue[])
    .map(remediation => `${remediation.security_platform_type ?? ''}: ${remediation.detection_remediation_values ?? ''}`)
    .join('\n'),
};

/**
 * A field's value as text for the Before / After block: lists one item per line, so the diff
 * shows what was added and removed. Empty stays empty, so the block shows "(empty)".
 */
export const payloadFieldText = (t: Translate, field: string, value: unknown): string => {
  if (value === null || value === undefined || value === '') {
    return '';
  }
  const format = FORMATS[field];
  if (format) {
    return format(value, t);
  }
  if (Array.isArray(value) && value.every(item => typeof item !== 'object' || item === null)) {
    return value.map(item => String(item)).join('\n');
  }
  return typeof value === 'string' ? value : JSON.stringify(value, null, 2);
};

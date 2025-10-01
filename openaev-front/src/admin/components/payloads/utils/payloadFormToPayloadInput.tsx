import { type FieldValues } from 'react-hook-form';

import { type DetectionRemediation, type PayloadInput } from '../../../../utils/api-types';

export interface DetectionRemediationForm {
  remediationId: string;
  content: string;
  author_rule: DetectionRemediation['author_rule'];
}

// eslint-disable-next-line import/prefer-default-export
export const payloadFormToPayloadInputForAI = (data: FieldValues): Partial<PayloadInput> => {
  return {
    payload_type: data.payload_type,
    payload_name: data.payload_name,
    payload_platforms: data.payload_platforms,
    payload_description: data.payload_description,
    command_content: data.command_content,
    payload_execution_arch: data.payload_execution_arch,
    payload_expectations: data.payload_expectations ?? ['PREVENTION', 'DETECTION'],
    executable_file: data.executable_file,
    file_drop_file: data.file_drop_file,
    dns_resolution_hostname: data.dns_resolution_hostname,
    payload_arguments: data.payload_arguments,
    payload_prerequisites: data.payload_prerequisites,
    payload_cleanup_executor: data.payload_cleanup_executor === null ? '' : data.payload_cleanup_executor,
    payload_cleanup_command: data.payload_cleanup_command === null ? '' : data.payload_cleanup_command,
    payload_tags: data.payload_tags,
    payload_attack_patterns: data.payload_attack_patterns,
    payload_output_parsers: data.payload_output_parsers,
    payload_detection_remediations: (Object.entries(data.remediations) as [string, DetectionRemediationForm][]).filter(value => value[1])
      .map(([key, remediation]) => ({
        detection_remediation_collector: key,
        detection_remediation_values: remediation.content,
        detection_remediation_id: remediation.remediationId,
        author_rule: remediation.author_rule,
      })),
  };
};

export const trackedFields = [
  'payload_name',
  'payload_type',
  'dns_resolution_hostname',
  'command_executor',
  'command_content',
  'payload_description',
  'payload_platforms',
  'payload_execution_arch',
  'payload_arguments',
  'payload_attack_patterns',
];

export const mapSpecificField = (
  fields: FieldValues,
) => {
  return new Map(trackedFields.map((key, index) => [key, fields[index]]));
};

function remediationOutdated(defaultValue: FieldValues, newValue: FieldValues): boolean {
  return !(JSON.stringify(defaultValue) === JSON.stringify(newValue));
}

export const hasSpecificDirtyFieldAI = (
  defaultFieldValue: FieldValues | undefined,
  fieldsLastAIGeneration: FieldValues[] | undefined | null,
  currentFieldValue?: FieldValues | undefined,
): boolean => {
  if (!currentFieldValue) return false;

  const current = mapSpecificField(currentFieldValue);
  const source = fieldsLastAIGeneration?.length ? fieldsLastAIGeneration : defaultFieldValue;

  return source
    ? trackedFields.some((field, i) =>
        remediationOutdated(
          Array.isArray(source) ? source[i] : source[field],
          current.get(field),
        ),
      )
    : false;
};

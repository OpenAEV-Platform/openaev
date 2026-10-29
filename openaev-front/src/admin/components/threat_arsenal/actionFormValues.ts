import { type ThreatArsenalActionFullOutput } from '../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../utils/api-types-custom';
import { type DetectionRemediationForm } from '../payloads/utils/payloadFormToPayloadInput';

export type ThreatArsenalActionFormValues = Partial<ThreatArsenalActionCreateCustomInput> & {
  action_id?: string;
  payload_id?: string;
  command_executor?: string;
  command_content?: string;
};

export const buildInitialValues
  = (action: ThreatArsenalActionFullOutput, actionName: string): ThreatArsenalActionFormValues => {
    const remediations: Record<string, DetectionRemediationForm> = {};
    action.action_detection_remediations?.forEach((remediation) => {
      remediations[remediation.detection_remediation_security_platform ?? ''] = {
        content: remediation.detection_remediation_values ?? '',
        remediationId: remediation.detection_remediation_id ?? '',
        author_rule: remediation.author_rule,
      };
    });

    return {
      action_id: action.action_id,
      action_name: actionName,
      action_description: action.action_description,
      action_type: action.action_type as ThreatArsenalActionCreateCustomInput['action_type'],
      command_executor: action.command_executor as string | undefined,
      command_content: action.command_content as string | undefined,
      dns_resolution_hostname: action.dns_resolution_hostname as string | undefined,
      action_arguments: action.action_arguments?.map(arg => ({
        ...arg,
        type: String(arg.type) === 'targeted_asset' ? 'targeted-asset' : arg.type,
        description: arg.description ?? undefined,
        separator: arg.separator ?? undefined,
      })),
      action_prerequisites: action.action_prerequisites,
      file_drop_file: action.file_drop_file as string | undefined,
      action_attack_patterns: action.action_attack_patterns,
      action_tags: action.action_tags as string[] | undefined,
      action_expectations: action.action_expectations ?? ['PREVENTION', 'DETECTION'],
      action_expected_security_platforms: action.action_expected_security_platforms ?? {},
      action_execution_arch: action.action_execution_arch,
      action_output_parsers: action.action_output_parsers as ThreatArsenalActionCreateCustomInput['action_output_parsers'],
      action_platforms: action.action_platforms,
      executable_file: action.executable_file as string | undefined,
      action_cleanup_executor: action.action_cleanup_executor ?? '',
      action_cleanup_command: action.action_cleanup_command ?? '',
      remediations: remediations as ThreatArsenalActionCreateCustomInput['remediations'],
      action_domains: action.action_domains,
    } as ThreatArsenalActionFormValues;
  };

export const handleCleanupCommandValue = (value: string): string | null => (value === '' ? null : value);

export const handleCleanupExecutorValue = (executor: string, command: string): string | null => {
  if (executor !== '' && handleCleanupCommandValue(command) !== null) return executor;
  return null;
};

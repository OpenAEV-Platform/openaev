import { describe, expect, it } from 'vitest';

import {
  buildInitialValues,
  remediationsInput,
  toActionInput,
} from '../../../../../admin/components/threat_arsenal/utils/actionFormValues';
import { type DetectionRemediation, type ThreatArsenalActionFullOutput } from '../../../../../utils/api-types';
import { type ThreatArsenalActionCreateCustomInput } from '../../../../../utils/api-types-custom';

const aRemediation = (platform: string, values: string): DetectionRemediation => ({
  author_rule: 'HUMAN',
  detection_remediation_id: `remediation-${platform}`,
  detection_remediation_payload_id: 'payload-1',
  detection_remediation_security_platform: platform,
  detection_remediation_values: values,
});

const anAction = (overrides: Partial<ThreatArsenalActionFullOutput> = {}) => ({
  action_id: 'action-1',
  action_type: 'Command',
  command_executor: 'bash',
  command_content: 'whoami',
  ...overrides,
}) as ThreatArsenalActionFullOutput;

const roundTrip = (action: ThreatArsenalActionFullOutput) => toActionInput(
  buildInitialValues(action, 'An action') as ThreatArsenalActionCreateCustomInput,
);

describe('actionFormValues', () => {
  it('given an action with remediations should send them back unchanged', () => {
    // Arrange
    const remediations = [aRemediation('splunk', 'index=main'), aRemediation('sentinel', 'SecurityEvent')];

    // Act
    const input = roundTrip(anAction({ action_detection_remediations: remediations }));

    // Assert
    expect(input.action_detection_remediations).toEqual(remediations.map(remediation => ({
      detection_remediation_security_platform: remediation.detection_remediation_security_platform,
      detection_remediation_values: remediation.detection_remediation_values,
      detection_remediation_id: remediation.detection_remediation_id,
      author_rule: remediation.author_rule,
    })));
  });

  it('given a remediation left empty in the form should not send it', () => {
    // Arrange
    const remediations = {
      splunk: {
        content: 'index=main',
        remediationId: 'remediation-splunk',
        author_rule: 'AI',
      },
      sentinel: undefined,
    } as unknown as ThreatArsenalActionCreateCustomInput['remediations'];

    // Act
    const inputs = remediationsInput(remediations);

    // Assert
    expect(inputs.map(input => input.detection_remediation_security_platform)).toEqual(['splunk']);
  });

  describe.each([
    {
      cleanup: 'an executor and a command',
      executor: 'sh',
      command: 'rm /tmp/x',
      expectedExecutor: 'sh',
      expectedCommand: 'rm /tmp/x',
    },
    {
      cleanup: 'no executor and no command',
      executor: undefined,
      command: undefined,
      expectedExecutor: null,
      expectedCommand: null,
    },
    {
      cleanup: 'an executor without command',
      executor: 'sh',
      command: undefined,
      expectedExecutor: null,
      expectedCommand: null,
    },
  ])('given a cleanup with $cleanup', ({ executor, command, expectedExecutor, expectedCommand }) => {
    it('should send the cleanup the API expects', () => {
      // Arrange
      const action = anAction({
        action_cleanup_executor: executor,
        action_cleanup_command: command,
      });

      // Act
      const input = roundTrip(action);

      // Assert
      expect(input.action_cleanup_executor).toBe(expectedExecutor);
      expect(input.action_cleanup_command).toBe(expectedCommand);
    });
  });
});

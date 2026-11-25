import { describe, expect, it } from 'vitest';

import { PAYLOAD_FIELD_LABELS, payloadFieldLabel, payloadFieldText } from '../../../../components/common/content_versions/payloadFieldChanges';
import { type Translate } from '../../../../components/i18n';

const t = ((value: string) => value) as Translate;

// The fields of the backend content hash (PayloadContentHashService.canonicalTree): each change
// that makes a new version has a label here, so no change is ever shown under a raw key.
const CONTENT_HASH_FIELDS = [
  'payload_type',
  'payload_name',
  'payload_description',
  'payload_platforms',
  'payload_execution_arch',
  'payload_elevation_required',
  'payload_expectations',
  'payload_expected_security_platforms',
  'payload_arguments',
  'payload_prerequisites',
  'payload_cleanup_executor',
  'payload_cleanup_command',
  'command_executor',
  'command_content',
  'dns_resolution_hostname',
  'network_traffic_ip_src',
  'network_traffic_ip_dst',
  'network_traffic_port_src',
  'network_traffic_port_dst',
  'network_traffic_protocol',
  'ai_attack_engine',
  'ai_attack_category',
  'ai_attack_content',
  'ai_attack_multi_turn',
  'ai_attack_converters',
  'ai_attack_success_detector',
  'executable_file',
  'file_drop_file',
  'payload_attack_patterns',
  'payload_detection_remediations',
  'payload_output_parsers',
];

describe('payloadFieldChanges', () => {
  it('given every field that makes a version should have a label', () => {
    // Assert
    expect(Object.keys(PAYLOAD_FIELD_LABELS).sort()).toEqual([...CONTENT_HASH_FIELDS].sort());
    expect(payloadFieldLabel(t, 'payload_name')).toBe('Name');
    expect(payloadFieldLabel(t, 'payload_description')).toBe('Description');
  });

  it('given a list should show one item per line', () => {
    // Act
    const text = payloadFieldText(t, 'payload_attack_patterns', ['T1033 Discovery', 'T1059 Interpreter']);

    // Assert
    expect(text).toBe('T1033 Discovery\nT1059 Interpreter');
  });

  it('given arguments, prerequisites, scopes and remediations should show readable lines', () => {
    // Act / Assert
    expect(payloadFieldText(t, 'payload_arguments', [{
      key: 'user',
      type: 'text',
      default_value: 'root',
    }])).toBe('user (text) = root');
    expect(payloadFieldText(t, 'payload_prerequisites', [{
      executor: 'sh',
      get_command: 'apt install nmap',
      check_command: 'which nmap',
    }])).toBe('sh: apt install nmap\n  check: which nmap');
    expect(payloadFieldText(t, 'payload_expected_security_platforms', { DETECTION: ['EDR', 'SIEM'] })).toBe('DETECTION: EDR, SIEM');
    expect(payloadFieldText(t, 'payload_detection_remediations', [{
      security_platform_type: 'EDR',
      detection_remediation_values: 'Block it',
    }])).toBe('EDR: Block it');
    expect(payloadFieldText(t, 'payload_elevation_required', true)).toBe('Yes');
  });

  it('given an empty value should stay empty', () => {
    // Act / Assert
    expect(payloadFieldText(t, 'payload_description', null)).toBe('');
    expect(payloadFieldText(t, 'payload_name', undefined)).toBe('');
  });
});

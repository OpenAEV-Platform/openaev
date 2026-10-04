import { describe, expect, it } from 'vitest';

import {
  countIocValidationOutcomes,
  iocValidationObservableTypeLabel,
  iocValidationOutcomeLabel,
  iocValidationOutcomeSeverity,
  iocValidationStatusLabel,
  iocValidationStatusSeverity,
  iocValidationTestKindLabel,
  isAwaitingApproval,
  isIpAddress,
  isPollingStatus,
  isWebLink,
  validateIocValidationSettings,
} from '../../../../admin/components/ioc_validations/iocValidationUtils';
import type { IocValidationPairOutput, IocValidationSettingsInput } from '../../../../utils/api-types';

const settings = (overrides: Partial<IocValidationSettingsInput> = {}): IocValidationSettingsInput => ({
  ioc_validation_allowed_test_kinds: ['DNS_RESOLUTION'],
  ...overrides,
} as IocValidationSettingsInput);

describe('iocValidationUtils', () => {
  it('labels the OpenCTI observable types instead of printing their keys', () => {
    expect(iocValidationObservableTypeLabel('Domain-Name')).toBe('Domain name');
    expect(iocValidationObservableTypeLabel('IPv4-Addr')).toBe('IPv4 address');
    expect(iocValidationObservableTypeLabel('Url')).toBe('URL');
    expect(iocValidationObservableTypeLabel('StixFile')).toBe('File');
    expect(iocValidationObservableTypeLabel('Email-Addr')).toBe('Email-Addr');
    expect(iocValidationObservableTypeLabel(null)).toBe('');
  });

  it('labels statuses, outcomes and test kinds', () => {
    expect(iocValidationStatusLabel('AWAITING_APPROVAL')).toBe('Awaiting approval');
    expect(iocValidationStatusSeverity('FAILED')).toBe('critical');
    expect(iocValidationOutcomeLabel('PREVENTED')).toBe('Prevented');
    expect(iocValidationOutcomeLabel(null)).toBe('Pending');
    expect(iocValidationOutcomeSeverity('MISSED')).toBe('critical');
    expect(iocValidationOutcomeSeverity(undefined)).toBe('neutral');
    expect(iocValidationTestKindLabel('HTTP_HEAD')).toBe('HTTP HEAD request');
    expect(iocValidationTestKindLabel(null)).toBe('-');
  });

  it('polls only while a decision or results are expected', () => {
    expect(isAwaitingApproval('AWAITING_APPROVAL')).toBe(true);
    expect(isPollingStatus('AWAITING_APPROVAL')).toBe(true);
    expect(isPollingStatus('RUNNING')).toBe(true);
    expect(isPollingStatus('COMPLETED')).toBe(false);
    expect(isPollingStatus('REJECTED')).toBe(false);
    expect(isPollingStatus(undefined)).toBe(false);
  });

  it('accepts only http and https links', () => {
    expect(isWebLink('https://opencti.example.com/dashboard')).toBe(true);
    expect(isWebLink('http://localhost:4000')).toBe(true);
    expect(isWebLink('javascript:alert(1)')).toBe(false);
    expect(isWebLink('not a url')).toBe(false);
    expect(isWebLink('')).toBe(false);
    expect(isWebLink(null)).toBe(false);
  });

  it('accepts valid settings', () => {
    expect(validateIocValidationSettings(settings(), '443')).toEqual({});
    expect(validateIocValidationSettings(settings({
      ioc_validation_allowed_test_kinds: ['HTTP_HEAD'],
      ioc_validation_http_proxy_url: 'http://proxy.internal:3128',
      ioc_validation_sinkhole_address: '2001:db8::1',
    }), '8080')).toEqual({});
  });

  it('requires an egress proxy for HTTP HEAD tests', () => {
    expect(validateIocValidationSettings(settings({ ioc_validation_allowed_test_kinds: ['HTTP_HEAD'] }), '443').proxy)
      .toBe('HTTP HEAD tests can only be allowed once an egress proxy is configured');
    expect(validateIocValidationSettings(settings({ ioc_validation_http_proxy_url: 'ftp://proxy' }), '443').proxy)
      .toBe('The egress proxy must be an absolute http or https URL');
  });

  it('rejects an invalid sinkhole and port', () => {
    expect(validateIocValidationSettings(settings({ ioc_validation_sinkhole_address: 'sinkhole.local' }), '443').sinkhole)
      .toBe('The sinkhole must be an IPv4 or IPv6 address');
    expect(validateIocValidationSettings(settings({ ioc_validation_sinkhole_address: '256.1.1.1' }), '443').sinkhole).toBeDefined();
    expect(validateIocValidationSettings(settings({ ioc_validation_sinkhole_address: '10.0.0.53' }), '443').sinkhole).toBeUndefined();
    ['0', '65536', 'abc', '', '12.5'].forEach((port) => {
      expect(validateIocValidationSettings(settings(), port).port).toBe('The network port must be between 1 and 65535');
    });
  });

  it('accepts well-formed IPv6 literals only', () => {
    ['2001:db8::1', '::1', '::', 'fe80::1:2:3:4', '::ffff:192.0.2.1', '2001:0db8:0000:0000:0000:ff00:0042:8329'].forEach((address) => {
      expect(isIpAddress(address)).toBe(true);
    });
    [':::', '1:2:3:4:5:6:7:8:9', '2001::db8::1', '12345::1', '::ffff:256.1.1.1', '[::1]', '2001:db8::g', '.:'].forEach((address) => {
      expect(isIpAddress(address)).toBe(false);
    });
  });

  it('counts pair outcomes, pending included', () => {
    const pairs = [
      { pair_outcome: 'PREVENTED' },
      { pair_outcome: 'DETECTED' },
      { pair_outcome: 'DETECTED' },
      { pair_outcome: 'MISSED' },
      { pair_outcome: 'ERROR' },
      {},
    ] as IocValidationPairOutput[];
    expect(countIocValidationOutcomes(pairs)).toEqual({
      prevented: 1,
      detected: 2,
      missed: 1,
      error: 1,
      pending: 1,
    });
  });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  actionOfVersion,
  isRunnableVersion,
  resolveRunSelection,
  runTestSearchInput,
} from '../../../../../admin/components/threat_arsenal/bulk/runTestVersions';
import { type PayloadVersionOutput, type ThreatArsenalAction } from '../../../../../utils/api-types';

const mockFetchVersions = vi.fn();
vi.mock('../../../../../actions/threat_arsenals/threatArsenal-version-actions', () => ({ fetchThreatArsenalVersions: (...args: unknown[]) => mockFetchVersions(...args) }));

const version = (overrides: Partial<PayloadVersionOutput>): PayloadVersionOutput => ({
  payload_id: 'payload',
  payload_action_id: 'contract',
  payload_version: '1.0.0',
  payload_installed: true,
  payload_revoked: false,
  payload_approval_status: 'APPROVED',
  payload_usage_count: 0,
  ...overrides,
});

const action = (id: string, approval?: 'PENDING' | 'APPROVED'): ThreatArsenalAction => ({
  injector_contract_id: id,
  injector_contract_updated_at: '',
  action_attack_patterns_ids: [],
  action_domains_ids: [],
  action_labels: { en: id },
  action_payload: approval
    ? {
        payload_id: `payload-${id}`,
        payload_version: '2.0.0',
        payload_approval_status: approval,
      }
    : undefined,
});

describe('runTestVersions', () => {
  beforeEach(() => mockFetchVersions.mockReset());

  describe('isRunnableVersion', () => {
    it('given an approved, installed, live version should allow it', () => {
      expect(isRunnableVersion(version({}))).toBe(true);
    });

    it.each([
      ['pending', { payload_approval_status: 'PENDING' as const }],
      ['rejected', { payload_approval_status: 'REJECTED' as const }],
      ['superseded', { payload_approval_status: 'SUPERSEDED' as const }],
      ['revoked', { payload_revoked: true }],
      ['not installed', { payload_installed: false }],
    ])('given a %s version should refuse it', (_, overrides) => {
      expect(isRunnableVersion(version(overrides))).toBe(false);
    });
  });

  describe('actionOfVersion', () => {
    it('given a version should point the action at that version', () => {
      // Arrange / Act
      const result = actionOfVersion(action('row', 'PENDING'), version({
        payload_id: 'payload-old',
        payload_action_id: 'contract-old',
        payload_version: '1.0.0',
      }));

      // Assert
      expect(result.injector_contract_id).toBe('contract-old');
      expect(result.action_payload?.payload_id).toBe('payload-old');
      expect(result.action_payload?.payload_version).toBe('1.0.0');
      expect(result.action_payload?.payload_approval_status).toBe('APPROVED');
    });
  });

  describe('runTestSearchInput', () => {
    it('should search every version, so an older chosen version is found', () => {
      expect(runTestSearchInput().filterGroup?.filters?.map(filter => filter.key)).toEqual(['action_all_versions']);
    });
  });

  describe('resolveRunSelection', () => {
    it('given approved, pending and never-approved actions should keep, swap and block them', async () => {
      // Arrange
      mockFetchVersions.mockImplementation((id: string) => Promise.resolve({
        data: id === 'pending'
          ? [
              version({
                payload_action_id: 'pending',
                payload_version: '2.0.0',
                payload_approval_status: 'PENDING',
              }),
              version({
                payload_action_id: 'pending-approved',
                payload_version: '1.0.0',
              }),
            ]
          : [version({
              payload_action_id: 'new',
              payload_approval_status: 'PENDING',
            })],
      }));

      // Act
      const result = await resolveRunSelection([action('approved', 'APPROVED'), action('pending', 'PENDING'), action('new', 'PENDING'), action('injector')]);

      // Assert
      expect(Object.keys(result.runnable).sort()).toEqual(['approved', 'injector', 'pending-approved']);
      expect(result.blocked.map(blocked => blocked.injector_contract_id)).toEqual(['new']);
      expect(mockFetchVersions).toHaveBeenCalledTimes(2);
    });
  });
});

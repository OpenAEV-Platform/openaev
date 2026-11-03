import { describe, expect, it } from 'vitest';

import { type User } from '../../../utils/api-types';
import { rightsKey, rightsOf, rightsShrank, sessionCacheKey, type SessionRights } from '../../../utils/query/sessionRights';

const rights = (overrides: Partial<SessionRights> = {}): SessionRights => ({
  admin: false,
  capabilities: ['ACCESS_ASSETS', 'MANAGE_ASSETS'],
  grants: {
    'scenario-1': 'OBSERVER',
    'scenario-2': 'PLANNER',
  },
  ...overrides,
});

describe('sessionRights', () => {
  describe('rightsOf', () => {
    it('given_aUser_should_copyItsAdminFlagCapabilitiesAndGrants', () => {
      // Arrange
      const capabilities: User['user_capabilities'] = ['ACCESS_ASSETS'];
      const grants = { 'scenario-1': 'OBSERVER' };

      // Act
      const result = rightsOf({
        user_admin: true,
        user_capabilities: capabilities,
        user_grants: grants,
      });

      // Assert: copies, so that a later change of the user object cannot change these rights
      expect(result).toEqual({
        admin: true,
        capabilities: ['ACCESS_ASSETS'],
        grants: { 'scenario-1': 'OBSERVER' },
      });
      expect(result.capabilities).not.toBe(capabilities);
      expect(result.grants).not.toBe(grants);
    });

    it('given_aUserWithoutRights_should_returnEmptyRights', () => {
      // Act
      const result = rightsOf({});

      // Assert
      expect(result).toEqual({
        admin: false,
        capabilities: [],
        grants: {},
      });
    });
  });

  describe('rightsKey', () => {
    it('given_theSameRightsInAnotherOrder_should_beEqual', () => {
      // Arrange
      const reordered = rights({
        capabilities: ['MANAGE_ASSETS', 'ACCESS_ASSETS'],
        grants: {
          'scenario-2': 'PLANNER',
          'scenario-1': 'OBSERVER',
        },
      });

      // Act
      const key = rightsKey(reordered);

      // Assert
      expect(key).toBe(rightsKey(rights()));
    });

    it.each([
      ['a changed grant', rights({
        grants: {
          'scenario-1': 'PLANNER',
          'scenario-2': 'PLANNER',
        },
      })],
      ['a changed capability', rights({ capabilities: ['ACCESS_ASSETS', 'MANAGE_TAGS'] })],
      ['a changed admin flag', rights({ admin: true })],
    ])('given_%s_should_differ', (_label, changed) => {
      // Act
      const key = rightsKey(changed);

      // Assert
      expect(key).not.toBe(rightsKey(rights()));
    });
  });

  describe('rightsShrank', () => {
    it.each([
      ['a removed capability', rights({ capabilities: ['ACCESS_ASSETS'] })],
      ['a removed grant', rights({ grants: { 'scenario-1': 'OBSERVER' } })],
      ['a changed grant', rights({
        grants: {
          'scenario-1': 'PLANNER',
          'scenario-2': 'PLANNER',
        },
      })],
    ])('given_%s_should_beTrue', (_label, next) => {
      // Act
      const shrank = rightsShrank(rights(), next);

      // Assert
      expect(shrank).toBe(true);
    });

    it('given_aLostAdminFlag_should_beTrue', () => {
      // Act
      const shrank = rightsShrank(rights({ admin: true }), rights());

      // Assert
      expect(shrank).toBe(true);
    });

    it.each([
      ['an added capability', rights({ capabilities: ['ACCESS_ASSETS', 'MANAGE_ASSETS', 'ACCESS_TAGS'] })],
      ['an added grant', rights({
        grants: {
          'scenario-1': 'OBSERVER',
          'scenario-2': 'PLANNER',
          'scenario-3': 'OBSERVER',
        },
      })],
      ['a gained admin flag', rights({ admin: true })],
      ['the same rights in another order', rights({
        capabilities: ['MANAGE_ASSETS', 'ACCESS_ASSETS'],
        grants: {
          'scenario-2': 'PLANNER',
          'scenario-1': 'OBSERVER',
        },
      })],
    ])('given_%s_should_beFalse', (_label, next) => {
      // Act
      const shrank = rightsShrank(rights(), next);

      // Assert
      expect(shrank).toBe(false);
    });
  });

  describe('sessionCacheKey', () => {
    it('given_anotherUser_should_differ', () => {
      // Act
      const key = sessionCacheKey('user-2', 'tenant-1');

      // Assert
      expect(key).not.toBe(sessionCacheKey('user-1', 'tenant-1'));
    });

    it('given_anotherTenant_should_differ', () => {
      // Act
      const key = sessionCacheKey('user-1', 'tenant-2');

      // Assert
      expect(key).not.toBe(sessionCacheKey('user-1', 'tenant-1'));
    });

    it.each([null, undefined])('given_noTenant_%s_should_differFromATenant', (tenant) => {
      // Act
      const key = sessionCacheKey('user-1', tenant);

      // Assert
      expect(key).not.toBe(sessionCacheKey('user-1', 'tenant-1'));
      expect(key).toBe(sessionCacheKey('user-1', null));
    });
  });
});

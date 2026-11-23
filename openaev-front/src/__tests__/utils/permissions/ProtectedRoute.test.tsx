import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ASSESSMENT_ACCESS_CHECKS } from '../../../admin/components/nav/config/assessment.config';
import { type UserGrant } from '../../../utils/api-types';
import { defineAbility } from '../../../utils/permissions/ability';
import { AbilityProvider } from '../../../utils/permissions/permissionsContext';
import ProtectedRoute from '../../../utils/permissions/ProtectedRoute';

vi.mock('../../../components/i18n', () => ({ useFormatter: () => ({ t: (value: string) => value }) }));
vi.mock('../../../utils/hooks/useEnterpriseEdition', () => ({ default: () => ({ isValidated: true }) }));

describe('ProtectedRoute with the scenario, simulation and atomic testing list checks', () => {
  afterEach(cleanup);

  describe.each<{
    name: string;
    capabilities: string[];
    grantType?: UserGrant['type'];
    allowed: boolean;
  }>([
    {
      name: 'a SCENARIO grant',
      capabilities: [],
      grantType: 'SCENARIO',
      allowed: true,
    },
    {
      name: 'a SIMULATION grant',
      capabilities: [],
      grantType: 'SIMULATION',
      allowed: true,
    },
    {
      name: 'an ATOMIC_TESTING grant',
      capabilities: [],
      grantType: 'ATOMIC_TESTING',
      allowed: true,
    },
    {
      name: 'the assessment capability',
      capabilities: ['ACCESS_ASSESSMENT'],
      allowed: true,
    },
    {
      name: 'a THREAT_ARSENAL grant',
      capabilities: [],
      grantType: 'THREAT_ARSENAL',
      allowed: false,
    },
    {
      name: 'no permissions',
      capabilities: [],
      allowed: false,
    },
  ])('with $name', ({ capabilities, grantType, allowed }) => {
    it(`renders ${allowed ? 'the list' : 'the no access message'}`, () => {
      // Arrange
      const grants: Record<string, UserGrant> = grantType
        ? {
            'granted-resource': {
              grant: 'OBSERVER',
              type: grantType,
            },
          }
        : {};
      const ability = defineAbility(capabilities, grants, false);

      // Act
      render(
        <AbilityProvider value={ability}>
          <MemoryRouter>
            <ProtectedRoute checks={ASSESSMENT_ACCESS_CHECKS} Component={<div data-testid="assessment-list" />} />
          </MemoryRouter>
        </AbilityProvider>,
      );

      // Assert
      expect(screen.queryByTestId('assessment-list') !== null).toBe(allowed);
      expect(screen.queryByText('You dont have the right access. Please contact your administrator.') !== null).toBe(!allowed);
    });
  });
});

import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import LeftBar from '../../../../admin/components/nav/LeftBar';
import { type NavMenuEntries } from '../../../../components/common/menu/navbar/nav-menu-model';
import { type UserGrant } from '../../../../utils/api-types';
import { defineAbility } from '../../../../utils/permissions/ability';
import { AbilityProvider } from '../../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../../utils/permissions/types';

interface NavbarProps { entries: NavMenuEntries[] }

// Keep the production menu and permission checks; only simplify its presentation.
vi.mock('../../../../components/common/menu/navbar/AppNavbar', () => ({
  default: ({ entries }: NavbarProps) => (
    <nav>
      {entries.filter(entry => entry.userRight).flatMap(entry => entry.items)
        .filter(item => item.userRight)
        .map(item => <a key={item.path} href={item.path}>{item.label}</a>)}
    </nav>
  ),
}));
vi.mock('../../../../admin/components/nav/LeftBarHeader', () => ({ default: () => null }));
vi.mock('../../../../admin/components/nav/LeftBarTenantSwitcher', () => ({ default: () => null }));
vi.mock('../../../../utils/hooks/useAuth', () => ({ default: () => ({ userTenants: [] }) }));
vi.mock('../../../../utils/utils', () => ({ isFeatureEnabled: () => false }));

describe('LeftBar Threat Arsenal navigation', () => {
  afterEach(cleanup);

  describe.each(
    (['OBSERVER', 'PLANNER', 'LAUNCHER'] as const).flatMap(grant => (['THREAT_ARSENAL', 'PAYLOAD'] as const).map(type => ({
      grant,
      type,
    }))),
  )('with a direct $grant grant on a $type', ({ grant, type }) => {
    it('shows the link without granting global manage or delete permissions', () => {
      // Arrange
      const ability = defineAbility([], {
        'granted-resource': {
          grant,
          type,
        },
      }, false);

      // Act
      render(<AbilityProvider value={ability}><LeftBar /></AbilityProvider>);

      // Assert
      expect(screen.getByRole('link', { name: 'Threat Arsenal' }).getAttribute('href')).toBe('/admin/threat-arsenal');
      expect(ability.can(ACTIONS.MANAGE, SUBJECTS.THREAT_ARSENALS)).toBe(false);
      expect(ability.can(ACTIONS.DELETE, SUBJECTS.THREAT_ARSENALS)).toBe(false);
    });
  });

  describe.each(['SCENARIO', 'SIMULATION', 'ATOMIC_TESTING', 'UNKNOWN'] as const)('with a direct grant on a %s only', (type) => {
    it('hides the link but keeps the grant on the resource', () => {
      // Arrange
      const ability = defineAbility([], {
        'granted-resource': {
          grant: 'OBSERVER',
          type,
        },
      }, false);

      // Act
      render(<AbilityProvider value={ability}><LeftBar /></AbilityProvider>);

      // Assert
      expect(screen.queryByRole('link', { name: 'Threat Arsenal' })).toBeNull();
      expect(ability.can(ACTIONS.ACCESS, SUBJECTS.RESOURCE, 'granted-resource')).toBe(true);
    });
  });

  describe.each([
    {
      name: 'no permissions',
      capabilities: [],
      isAdmin: false,
      visible: false,
    },
    {
      name: 'an unrelated capability',
      capabilities: ['ACCESS_ASSETS'],
      isAdmin: false,
      visible: false,
    },
    {
      name: 'Threat Arsenal access',
      capabilities: ['ACCESS_THREAT_ARSENALS'],
      isAdmin: false,
      visible: true,
    },
    {
      name: 'Security Platform access',
      capabilities: ['ACCESS_SECURITY_PLATFORMS'],
      isAdmin: false,
      visible: true,
    },
    {
      name: 'administrator access',
      capabilities: [],
      isAdmin: true,
      visible: true,
    },
  ])('with $name and no grants', ({ capabilities, isAdmin, visible }) => {
    it('preserves the existing navigation access', () => {
      // Arrange
      const ability = defineAbility(capabilities, {}, isAdmin);

      // Act
      render(<AbilityProvider value={ability}><LeftBar /></AbilityProvider>);

      // Assert
      const link = screen.queryByRole('link', { name: 'Threat Arsenal' });
      expect(link?.getAttribute('href') ?? null).toBe(visible ? '/admin/threat-arsenal' : null);
    });
  });
});

describe('LeftBar assessment navigation', () => {
  afterEach(cleanup);

  const ASSESSMENT_LINKS = [
    {
      name: 'Scenarios',
      href: '/admin/scenarios',
    },
    {
      name: 'Simulations',
      href: '/admin/simulations',
    },
    {
      name: 'Atomic testings',
      href: '/admin/atomic_testings',
    },
  ];

  describe.each<{
    name: string;
    capabilities: string[];
    grantType?: UserGrant['type'];
    visible: boolean;
  }>([
    {
      name: 'a SCENARIO grant',
      capabilities: [],
      grantType: 'SCENARIO',
      visible: true,
    },
    {
      name: 'a SIMULATION grant',
      capabilities: [],
      grantType: 'SIMULATION',
      visible: true,
    },
    {
      name: 'an ATOMIC_TESTING grant',
      capabilities: [],
      grantType: 'ATOMIC_TESTING',
      visible: true,
    },
    {
      name: 'the assessment capability',
      capabilities: ['ACCESS_ASSESSMENT'],
      visible: true,
    },
    {
      name: 'a THREAT_ARSENAL grant',
      capabilities: [],
      grantType: 'THREAT_ARSENAL',
      visible: false,
    },
    {
      name: 'no permissions',
      capabilities: [],
      visible: false,
    },
  ])('with $name', ({ capabilities, grantType, visible }) => {
    it(`${visible ? 'shows' : 'hides'} the scenario, simulation and atomic testing links`, () => {
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
      render(<AbilityProvider value={ability}><LeftBar /></AbilityProvider>);

      // Assert
      ASSESSMENT_LINKS.forEach(({ name, href }) => {
        expect(screen.queryByRole('link', { name })?.getAttribute('href') ?? null).toBe(visible ? href : null);
      });
    });
  });
});

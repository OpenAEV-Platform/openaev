/**
 * Users the E2E suite can run as. `admin` is the platform admin seeded from the
 * OPENAEV_ADMIN_* settings; every other persona is created by auth.setup.ts in the
 * default tenant and joined to `groupName`, a group the backend seeds there.
 */
export const PERSONAS = {
  admin: {
    email: 'admin@openaev.io',
    password: 'admin',
  },
  manager: {
    email: 'e2e-manager@openaev.io',
    password: 'e2e-manager',
    firstname: 'E2E Manager',
    groupName: 'Manager',
  },
  observer: {
    email: 'e2e-observer@openaev.io',
    password: 'e2e-observer',
    firstname: 'E2E Observer',
    groupName: 'Observer',
  },
} as const;

export type Persona = keyof typeof PERSONAS;

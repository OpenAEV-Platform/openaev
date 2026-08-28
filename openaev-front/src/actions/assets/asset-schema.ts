import { schema } from 'normalizr';

// Endpoint

export const endpoint = new schema.Entity(
  'endpoints',
  {},
  { idAttribute: 'asset_id' },
);
export const arrayOfEndpoints = new schema.Array(endpoint);

// Security Platforms

export const securityPlatform = new schema.Entity(
  'securityplatforms',
  {},
  { idAttribute: 'asset_id' },
);
export const arrayOfSecurityPlatforms = new schema.Array(securityPlatform);

// AI Targets

export const aiTarget = new schema.Entity(
  'aitargets',
  {},
  { idAttribute: 'asset_id' },
);
export const arrayOfAiTargets = new schema.Array(aiTarget);

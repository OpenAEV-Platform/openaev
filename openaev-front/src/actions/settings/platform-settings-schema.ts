import { schema } from 'normalizr';

const publicPlatformParameters = new schema.Entity(
  'publicPlatformParameters',
  {},
  { idAttribute: () => 'parameters' },
);

export default publicPlatformParameters;

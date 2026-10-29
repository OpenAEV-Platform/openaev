import { describe, expect, it } from 'vitest';

import { resolveInitialConfigurationValue } from '../../../../../admin/components/integrations/connector_instance/useConnectorInstance';
import DOTS from '../../../../../constants/Strings';
import { type CatalogConnectorConfiguration } from '../../../../../utils/api-types';

// -- TEST DATA --

const buildDefinition = (overrides: Partial<CatalogConnectorConfiguration>): CatalogConnectorConfiguration => ({
  connector_configuration_key: 'SOME_KEY',
  connector_configuration_type: 'STRING',
  ...overrides,
});

const BOOLEAN_DEFAULT_TRUE = buildDefinition({
  connector_configuration_key: 'ELASTIC_VERIFY_SSL',
  connector_configuration_type: 'BOOLEAN',
  connector_configuration_default: true,
});

const INTEGER_DEFAULT_3 = buildDefinition({
  connector_configuration_key: 'ELASTIC_MAX_RETRY',
  connector_configuration_type: 'INTEGER',
  connector_configuration_default: 3,
});

const STRING_WITH_DEFAULT = buildDefinition({
  connector_configuration_key: 'COLLECTOR_LOG_LEVEL',
  connector_configuration_default: 'error',
});

const SECRET = buildDefinition({
  connector_configuration_key: 'ELASTIC_PASSWORD',
  connector_configuration_writeonly: true,
});

// -- TESTS --

describe('resolveInitialConfigurationValue', () => {
  it.each([
    ['false boolean', BOOLEAN_DEFAULT_TRUE, false, false],
    ['0 integer', INTEGER_DEFAULT_3, 0, '0'],
    ['empty string', STRING_WITH_DEFAULT, '', ''],
  ])('keeps a stored %s instead of the catalog default', (_label, definition, stored, expected) => {
    expect(resolveInitialConfigurationValue(definition, stored, true)).toBe(expected);
  });

  it.each([undefined, null])('falls back to the catalog default when nothing is stored (%s)', (stored) => {
    expect(resolveInitialConfigurationValue(BOOLEAN_DEFAULT_TRUE, stored, true)).toBe(true);
  });

  it('masks a write-only secret when editing', () => {
    expect(resolveInitialConfigurationValue(SECRET, undefined, true)).toBe(DOTS);
  });

  it('does not mask a write-only secret when creating', () => {
    expect(resolveInitialConfigurationValue(SECRET, undefined, false)).toBe('');
  });
});

import { type APIRequestContext } from '@playwright/test';

import { type Endpoint } from '../../src/utils/api-types';
import EndpointsApiHelpers from '../api-helpers/EndpointsApiHelpers';
import { type Persona } from './personas';

/**
 * Three assets, one per TLP marking, to check what each persona's marking clearance lets it see.
 */
export const MARKINGS = {
  green: 'TLP:GREEN',
  amber: 'TLP:AMBER',
  red: 'TLP:RED',
} as const;

export type MarkedAssetKey = keyof typeof MARKINGS;
export type MarkedAssets = Record<MarkedAssetKey, Endpoint>;

const MARKED_ASSET_KEYS = Object.keys(MARKINGS) as MarkedAssetKey[];

export interface ClearanceCase {
  persona: Persona;
  visibleAssets: MarkedAssetKey[];
  invisibleAssets: MarkedAssetKey[];
}

const clearanceCase = (persona: Persona, visibleAssets: MarkedAssetKey[]): ClearanceCase => ({
  persona,
  visibleAssets,
  invisibleAssets: MARKED_ASSET_KEYS.filter(key => !visibleAssets.includes(key)),
});

// Default group clearances (PresetTenantData.DEFAULT_GROUP_MARKINGS): Admin TLP:RED, Manager TLP:AMBER,
// Observer TLP:GREEN. A clearance also covers every lower marking of the same type.
export const CLEARANCE_CASES: ClearanceCase[] = [
  clearanceCase('admin', ['green', 'amber', 'red']),
  clearanceCase('manager', ['green', 'amber']),
  clearanceCase('observer', ['green']),
];

export const visibleAssetsOf = (persona: Persona): MarkedAssetKey[] => {
  const found = CLEARANCE_CASES.find(c => c.persona === persona);
  if (!found) {
    throw new Error(`No marking clearance case for persona ${persona}`);
  }
  return found.visibleAssets;
};

/**
 * Creates the three marked assets, named after `suffix` so a search on it finds all of them. Call it as admin:
 * a read-only persona cannot create them, and the data does not depend on who reads it.
 */
export const createMarkedAssets = async (
  request: APIRequestContext,
  getMarkingDefinitionId: (definition: string) => string,
  suffix: string,
): Promise<MarkedAssets> => {
  const endpointApiHelpers = new EndpointsApiHelpers(request);
  const [green, amber, red] = await Promise.all([
    endpointApiHelpers.createEndpoint(`Asset Green ${suffix}`, [getMarkingDefinitionId(MARKINGS.green)]),
    endpointApiHelpers.createEndpoint(`Asset Amber ${suffix}`, [getMarkingDefinitionId(MARKINGS.amber)]),
    endpointApiHelpers.createEndpoint(`Asset Red ${suffix}`, [getMarkingDefinitionId(MARKINGS.red)]),
  ]);
  return {
    green,
    amber,
    red,
  };
};

export const deleteMarkedAssets = async (request: APIRequestContext, assets: MarkedAssets) => {
  const endpointApiHelpers = new EndpointsApiHelpers(request);
  await Promise.all(Object.values(assets).map(asset => endpointApiHelpers.deleteEndpoint(asset.asset_id)));
};

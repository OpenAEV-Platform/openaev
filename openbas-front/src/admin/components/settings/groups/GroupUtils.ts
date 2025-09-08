import * as R from 'ramda';

import { type DefaultGrant } from '../../../../utils/api-types';

export const isDefaultGrantPresent = (grantsToCheck: DefaultGrant[], defaultGrant: DefaultGrant) => {
  if (!grantsToCheck) {
    return false;
  }
  return grantsToCheck.some(e => R.equals(e, defaultGrant));
};

// Temporary const, before grant refactoring following AT and payloads
export const defaultGrantScenarioObserver: DefaultGrant = {
  grant_type: 'OBSERVER',
  grant_resource_type: 'SCENARIO',
};

export const defaultGrantScenarioPlanner: DefaultGrant = {
  grant_type: 'PLANNER',
  grant_resource_type: 'SCENARIO',
};

export const defaultGrantSimulationObserver: DefaultGrant = {
  grant_type: 'OBSERVER',
  grant_resource_type: 'SIMULATION',
};

export const defaultGrantSimulationPlanner: DefaultGrant = {
  grant_type: 'PLANNER',
  grant_resource_type: 'SIMULATION',
};
export const defaultGrantPayloadObserver: DefaultGrant = {
  grant_type: 'OBSERVER',
  grant_resource_type: 'PAYLOAD',
};

export const defaultGrantPayloadPlanner: DefaultGrant = {
  grant_type: 'PLANNER',
  grant_resource_type: 'PAYLOAD',
};

export const defaultGrantAtomicTestingObserver: DefaultGrant = {
  grant_type: 'OBSERVER',
  grant_resource_type: 'ATOMIC_TESTING',
};

export const defaultGrantAtomicTestingPlanner: DefaultGrant = {
  grant_type: 'PLANNER',
  grant_resource_type: 'ATOMIC_TESTING',
};

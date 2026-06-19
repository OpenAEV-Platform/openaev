import { useMemo } from 'react';

import { findSimulationAssetGroupsByIds } from '../../../actions/asset_groups/assetgroup-action';
import { findSimulationEndpointsByIds } from '../../../actions/assets/endpoint-actions';
import type { Exercise } from '../../api-types';
import { type EndpointContextType } from './EndpointContext';

const endpointContextForExercise = (exerciseId: Exercise['exercise_id']): EndpointContextType => {
  // Stable identity: used as a context provider value on hot screens
  return useMemo(() => ({
    async fetchEndpointsByIds(endpointIds: string[]) {
      return findSimulationEndpointsByIds(exerciseId, endpointIds);
    },
    async fetchAssetGroupsByIds(assetGroupIds: string[]) {
      return findSimulationAssetGroupsByIds(exerciseId, assetGroupIds);
    },
  }), [exerciseId]);
};

export default endpointContextForExercise;

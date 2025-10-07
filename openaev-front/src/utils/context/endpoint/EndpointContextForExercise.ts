import { findSimulationAssetGroupsByIds } from '../../../actions/asset_groups/assetgroup-action';
import { findSimulationEndpointsByIds } from '../../../actions/assets/endpoint-actions';
import type { Exercise } from '../../api-types';
import { type EndpointContextType } from './EndpointContext';

const endpointContextForExercise = (exerciseId: Exercise['exercise_id']): EndpointContextType => {
  return {
    async fetchEndpointsByIds(endpointIds: string[]) {
      return findSimulationEndpointsByIds(exerciseId, endpointIds);
    },
    async fetchAssetGroupsByIds(assetGroupIds: string[]) {
      return findSimulationAssetGroupsByIds(exerciseId, assetGroupIds);
    },
  };
};

export default endpointContextForExercise;

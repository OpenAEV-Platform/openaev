import { type Communication } from '../../utils/api-types';

export interface CommunicationHelper {
  getExerciseCommunications: (exerciseId: string) => Communication[];
  getInjectCommunications: (injectId: string) => Communication[];
}

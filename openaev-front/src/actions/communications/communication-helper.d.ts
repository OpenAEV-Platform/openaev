import { type Communication } from '../../utils/api-types';

export interface CommunicationHelper { getExerciseCommunications: (exerciseId: string) => Communication[] }

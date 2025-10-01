import { type Comcheck } from '../../utils/api-types';

export interface ComCheckHelper { getExerciseComchecks: (exerciseId: string) => Comcheck[] }

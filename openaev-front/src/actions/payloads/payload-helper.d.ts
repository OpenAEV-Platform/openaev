import { type Payload } from '../../utils/api-types';

export interface PayloadHelper {
  getPayloads: () => Payload[];
  getPayloadsMap: () => Record<string, Payload>;
}

import { isFeatureEnabled } from '../../utils/utils';

/**
 * The cross-product counterparts preview feature (XTM_COUNTERPARTS, off by default; Sam, 23 Nov
 * 2026): the single XTM One emblem of every header, its surface and the list-row marks. Separate
 * from EXPLORATION_GRAPH. Off, no emblem is drawn, no lookup leaves the browser and the backend
 * answers 404 on every /xtm route, so no call goes out to a connected product.
 */
const useXtmCounterparts = (): boolean => isFeatureEnabled('XTM_COUNTERPARTS');

export default useXtmCounterparts;

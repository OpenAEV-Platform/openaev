import { type InjectResultOutput } from '../../../utils/api-types';

// Shared `goTo` for the cross-scope "Injects played" lists of the detail pages (asset, asset
// group, team, person, organization): simulation injects open in their simulation, standalone
// injects open the atomic testing page. The exercise id is declared locally until the API types
// are regenerated.
const injectResultDetailPath = (injectId: string, inject: InjectResultOutput): string => {
  const exerciseId = (inject as InjectResultOutput & { inject_exercise?: string }).inject_exercise;
  return exerciseId
    ? `/admin/simulations/${exerciseId}/injects/${injectId}`
    : `/admin/atomic_testings/${injectId}`;
};

export default injectResultDetailPath;

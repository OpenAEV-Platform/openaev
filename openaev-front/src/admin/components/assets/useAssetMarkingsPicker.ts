import { useAbility } from '../../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../../utils/permissions/types';
import { isFeatureEnabled } from '../../../utils/utils';

const useAssetMarkingsPicker = () => {
  const ability = useAbility();
  const showMarkings = isFeatureEnabled('MARKING') && ability.can(ACTIONS.MANAGE, SUBJECTS.MARKING_ASSIGNMENT);
  // Without the picker, the markings are left out: the asset keeps the ones it has.
  const withMarkings = <T extends object>(values: T, markingIds?: string[] | null) => (showMarkings
    ? {
        ...values,
        asset_markings: markingIds ?? [],
      }
    : values);
  return {
    showMarkings,
    withMarkings,
  };
};

export default useAssetMarkingsPicker;

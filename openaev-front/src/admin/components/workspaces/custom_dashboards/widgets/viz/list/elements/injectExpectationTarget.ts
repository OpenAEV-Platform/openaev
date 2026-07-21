import { type EsInjectExpectation } from '../../../../../../../../utils/api-types';

const getTargetTypeFromInjectExpectation = (expectation: EsInjectExpectation): {
  label: string;
  type: string;
} => {
  let label = '';
  let type = '';
  if (expectation.base_user_side != null) {
    label = 'player';
    type = 'PLAYERS';
  } else if (expectation.base_team_side != null) {
    label = 'team';
    type = 'TEAMS';
  } else if (expectation.base_asset_side != null) {
    label = 'endpoint';
    type = 'ASSETS';
  } else if (expectation.base_asset_group_side != null) {
    label = 'asset group';
    type = 'ASSETS_GROUPS';
  }
  return {
    label,
    type,
  };
};

export default getTargetTypeFromInjectExpectation;

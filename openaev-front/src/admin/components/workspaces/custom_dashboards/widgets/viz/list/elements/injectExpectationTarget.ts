import { type EsInjectExpectation } from '../../../../../../../../utils/api-types';

const getTargetTypeFromInjectExpectation = (expectation: EsInjectExpectation): {
  label: string;
  type: string;
  id?: string;
} => {
  let label = '';
  let type = '';
  let id: string | undefined;
  if (expectation.base_user_side != null) {
    label = 'player';
    type = 'PLAYERS';
    id = expectation.base_user_side;
  } else if (expectation.base_team_side != null) {
    label = 'team';
    type = 'TEAMS';
    id = expectation.base_team_side;
  } else if (expectation.base_asset_side != null) {
    label = 'endpoint';
    type = 'ASSETS';
    id = expectation.base_asset_side;
  } else if (expectation.base_asset_group_side != null) {
    label = 'asset group';
    type = 'ASSETS_GROUPS';
    id = expectation.base_asset_group_side;
  }
  return {
    label,
    type,
    id,
  };
};

export default getTargetTypeFromInjectExpectation;

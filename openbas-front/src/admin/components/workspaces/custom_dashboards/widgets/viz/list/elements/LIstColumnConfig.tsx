import { Tooltip } from '@mui/material';

import AssetPlatformFragment from '../../../../../../../../components/common/list/fragments/AssetPlatformFragment';
import AttackPatternFragment from '../../../../../../../../components/common/list/fragments/AttackPatternFragment';
import DateFragment from '../../../../../../../../components/common/list/fragments/DateFragment';
import EndpointActiveFragment from '../../../../../../../../components/common/list/fragments/EndpointActiveFragment';
import EndpointAgentsPrivilegeFragment
  from '../../../../../../../../components/common/list/fragments/EndpointAgentsPrivilegeFragment';
import EndpointArchFragment from '../../../../../../../../components/common/list/fragments/EndpointArchFragment';
import InverseBooleanFragment from '../../../../../../../../components/common/list/fragments/InverseBooleanFragment';
import VulnerableEndpointActionFragment
  from '../../../../../../../../components/common/list/fragments/VulnerableEndpointActionFragment';
import ItemStatus from '../../../../../../../../components/ItemStatus';
import ItemTags from '../../../../../../../../components/ItemTags';
import {
  type AttackPattern,
  type EsBase,
  type EsInjectExpectation, type Tag,
} from '../../../../../../../../utils/api-types';
import EndpointListItemFragments from '../../../../../../common/endpoints/EndpointListItemFragments';

export type ColumnRenderer = (value: string | string[] | boolean | boolean[], opts: {
  element: EsBase;
  attackPatterns: AttackPattern[];
}) => React.ReactElement;
export type RendererMap = Record<string, ColumnRenderer>;

const commonColumnsRenderers: RendererMap = {
  ['base_tags_side']: tags => <ItemTags variant="list" tags={tags ?? []} />,
  ['base_attack_patterns_side']: (attackPatternIds, opts) =>
    <AttackPatternFragment attackPatterns={opts.attackPatterns} attackPatternIds={(attackPatternIds as string[]) ?? []} />,
  ['base_created_at']: value => <DateFragment value={value as string} />,
  ['base_updated_at']: value => <DateFragment value={value as string} />,
};

const endpointColumnsRenderers: RendererMap = {
  [EndpointListItemFragments.ENDPOINT_PLATFORM]: platform => <AssetPlatformFragment platform={platform as string} />,
  [EndpointListItemFragments.ENDPOINT_ARCH]: arch => <EndpointArchFragment arch={arch as string} />,
  [EndpointListItemFragments.ENDPOINT_IS_EOL]: isEol => <InverseBooleanFragment bool={isEol as boolean} />,
};

const vulnerableEndpointColumnsRenderers: RendererMap = {
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_PLATFORM]: platform => <AssetPlatformFragment platform={platform as string} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_ARCHITECTURE]: arch => <EndpointArchFragment arch={arch as string} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_AGENTS_ACTIVE_STATUS]: status => <EndpointActiveFragment activity_map={status as boolean[]} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_AGENTS_PRIVILEGES]: privileges => <EndpointAgentsPrivilegeFragment privileges={privileges as string[]} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_ACTION]: action => <VulnerableEndpointActionFragment action={action as string} />,
};

const injectColumnsRenderers: RendererMap = {
  ['inject_status']: status => <ItemStatus isInject status={status as string} label={status as string} variant="inList" />,
  ['base_platforms_side_denormalized']: platform => <AssetPlatformFragment platform={platform as string} />,
  ['inject_execution_date']: value => <DateFragment value={value as string} />,
};

export const getTargetTypeFromInjectExpectation = (expectation: EsInjectExpectation): string => {
  let target = '';
  if (expectation.base_user_side != null) {
    target = 'PLAYERS';
  } else if (expectation.base_team_side != null) {
    target = 'TEAMS';
  } else if (expectation.base_agent_side != null) {
    target = 'AGENT';
  } else if (expectation.base_asset_side != null) {
    target = 'ASSETS';
  } else if (expectation.base_asset_group_side != null) {
    target = 'ASSETS_GROUPS';
  }
  return target;
};

const injectExpectationRenderers: RendererMap = {
  ['inject_expectation_source']: (_, { element }) => {
    const targetType = getTargetTypeFromInjectExpectation(element as EsInjectExpectation);
    return (
      <Tooltip title={targetType} placement="bottom-start">
        <span>{targetType}</span>
      </Tooltip>
    );
  },
};

const listConfigRenderer = {
  ...commonColumnsRenderers,
  ...endpointColumnsRenderers,
  ...vulnerableEndpointColumnsRenderers,
  ...injectColumnsRenderers,
  ...injectExpectationRenderers,
};

export default listConfigRenderer;

import { Tooltip } from '@mui/material';

import AssetPlatformFragment from '../../../../../../../../components/common/list/fragments/AssetPlatformFragment';
import AttackPatternFragment from '../../../../../../../../components/common/list/fragments/AttackPatternFragment';
import DateFragment from '../../../../../../../../components/common/list/fragments/DateFragment';
import EndpointActiveFragment from '../../../../../../../../components/common/list/fragments/EndpointActiveFragment';
import EndpointAgentsPrivilegeFragment
  from '../../../../../../../../components/common/list/fragments/EndpointAgentsPrivilegeFragment';
import EndpointArchFragment from '../../../../../../../../components/common/list/fragments/EndpointArchFragment';
import InverseBooleanFragment from '../../../../../../../../components/common/list/fragments/InverseBooleanFragment';
import TagsFragment from '../../../../../../../../components/common/list/fragments/TagsFragment';
import VulnerableEndpointActionFragment
  from '../../../../../../../../components/common/list/fragments/VulnerableEndpointActionFragment';
import ItemStatus from '../../../../../../../../components/ItemStatus';
import {
  type EsBase,
  type EsInjectExpectation,
} from '../../../../../../../../utils/api-types';
import EndpointListItemFragments from '../../../../../../common/endpoints/EndpointListItemFragments';

export type ColumnRenderer = (value: any, element: EsBase) => React.ReactElement;
export type RendererMap = Record<string, ColumnRenderer>;

const commonColumnsRenderers: RendererMap = {
  ['base_tags_side']: (tags: string[]) => <TagsFragment tags={tags ?? []} />,
  ['base_attack_patterns_side']: (attackPatternIds: string[]) => <AttackPatternFragment attackPatternIds={attackPatternIds ?? []} />,
  ['base_created_at']: (value: string) => <DateFragment value={value} />,
  ['base_updated_at']: (value: string) => <DateFragment value={value} />,
};

const endpointColumnsRenderers: RendererMap = {
  [EndpointListItemFragments.ENDPOINT_PLATFORM]: (platform: string) => <AssetPlatformFragment platform={platform} />,
  [EndpointListItemFragments.ENDPOINT_ARCH]: (arch: string) => <EndpointArchFragment arch={arch} />,
  [EndpointListItemFragments.ENDPOINT_IS_EOL]: (isEol: boolean) => <InverseBooleanFragment bool={isEol} />,
};

const vulnerableEndpointColumnsRenderers: RendererMap = {
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_PLATFORM]: platform => <AssetPlatformFragment platform={platform} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_ARCHITECTURE]: arch => <EndpointArchFragment arch={arch} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_AGENTS_ACTIVE_STATUS]: status => <EndpointActiveFragment activity_map={status} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_AGENTS_PRIVILEGES]: privileges => <EndpointAgentsPrivilegeFragment privileges={privileges} />,
  [EndpointListItemFragments.VULNERABLE_ENDPOINT_ACTION]: action => <VulnerableEndpointActionFragment action={action} />,
};

const injectColumnsRenderers: RendererMap = {
  ['inject_status']: (status: string) => <ItemStatus isInject status={status} label={status} variant="inList" />,
  ['base_platforms_side_denormalized']: platform => <AssetPlatformFragment platform={platform} />,
  ['inject_execution_date']: (value: string) => <DateFragment value={value} />,
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
  ['inject_expectation_source']: (_, element) => {
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

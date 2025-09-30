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
import { useFormatter } from '../../../../../../../../components/i18n';
import ItemStatus from '../../../../../../../../components/ItemStatus';
import ItemTags from '../../../../../../../../components/ItemTags';
import {
  type AttackPattern,
  type EsBase,
  type EsInjectExpectation,
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
  ['execution_date']: value => <DateFragment value={value as string} />,

};

export const getTargetTypeFromInjectExpectation = (expectation: EsInjectExpectation): {
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

const injectExpectationRenderers: RendererMap = {
  ['inject_expectation_source']: (_, { element }) => {
    const { t } = useFormatter();
    const target = getTargetTypeFromInjectExpectation(element as EsInjectExpectation);
    return (
      <Tooltip title={target.label} placement="bottom-start">
        <span>{(t(target.label)).toUpperCase()}</span>
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

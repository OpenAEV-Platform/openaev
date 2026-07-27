import { Chip, Tooltip } from '@mui/material';

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
  type EsInjectExpectation,
  type EsSimulation,
  type Exercise,
  type InjectStatus as InjectStatusType,
} from '../../../../../../../../utils/api-types';
import { computeInjectExpectationLabel } from '../../../../../../../../utils/statusUtils';
import EndpointListItemFragments from '../../../../../../common/endpoints/EndpointListItemFragments';
import expectationIconByType from '../../../../../../common/ExpectationIconByType';
import InjectStatus from '../../../../../../common/injects/status/InjectStatus';
import ScenarioStatus from '../../../../../../scenarios/scenario/ScenarioStatus';
import ExerciseStatus from '../../../../../../simulations/simulation/ExerciseStatus';
import ExpectationTypeChip from './ExpectationTypeChip';
import InjectExpectationSourceFragment from './InjectExpectationSourceFragment';

export type ColumnRenderer = (value: string | string[] | boolean | boolean[], opts: {
  element: EsBase;
  attackPatterns: AttackPattern[];
}) => React.ReactElement;
export type RendererMap = Record<string, ColumnRenderer>;

// Stable-ish palette for finding types so each type reads as a distinct chip.
const FINDING_TYPE_COLORS: Record<string, string> = {
  CVE: '#f44336',
  PortsScan: '#0fbcff',
  IPAddress: '#00bcd4',
  Hostname: '#9575cd',
  Text: '#78909c',
};

const commonColumnsRenderers: RendererMap = {
  ['base_tags_side']: tags => <ItemTags variant="list" tags={tags ?? []} />,
  ['finding_type']: (value) => {
    const label = (value as string) ?? '';
    const color = FINDING_TYPE_COLORS[label] ?? '#607d8b';
    return (
      <Chip
        label={label}
        size="small"
        variant="outlined"
        sx={{
          height: 20,
          fontSize: 11,
          fontWeight: 600,
          color,
          borderColor: `${color}66`,
          backgroundColor: `${color}14`,
        }}
      />
    );
  },
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
  ['inject_status']: status => <InjectStatus status={status as InjectStatusType['status_name']} />,
  ['base_platforms_side_denormalized']: platform => <AssetPlatformFragment platform={platform as string} />,
  ['execution_date']: value => <DateFragment value={value as string} />,

};

export { default as getTargetTypeFromInjectExpectation } from './injectExpectationTarget';

const injectExpectationRenderers: RendererMap = {
  ['inject_expectation_type']: value => <ExpectationTypeChip type={value as string} />,
  ['inject_expectation_status']: (_, { element }) => {
    const expectation = element as EsInjectExpectation;
    const label = computeInjectExpectationLabel(
      expectation.inject_expectation_status,
      expectation.inject_expectation_type,
    ) ?? '';
    return (
      <ItemStatus
        label={label}
        variant="inList"
        status={label}
        icon={expectationIconByType(expectation.inject_expectation_type, { fontSize: 14 })}
      />
    );
  },
  ['inject_expectation_source']: (_, { element }) => <InjectExpectationSourceFragment element={element} />,
};

export const defaultRenderer: ColumnRenderer = (value) => {
  const text = value?.toString() ?? '';
  return (
    <Tooltip title={text} placement="bottom-start">
      <span>{text}</span>
    </Tooltip>
  );
};

// The bare `status` column is shared by the simulation and scenario ES models:
// render the same status chips as their own list pages (design system) instead
// of the raw enum text.
const entityStatusRenderers: RendererMap = {
  ['status']: (value, opts) => {
    const { element } = opts;
    if (element.base_entity === 'simulation') {
      const simulation = element as EsSimulation;
      return (
        <ExerciseStatus
          variant="list"
          exerciseStatus={simulation.status as Exercise['exercise_status']}
          exerciseStartDate={simulation.execution_date}
        />
      );
    }
    if (element.base_entity === 'scenario') {
      return <ScenarioStatus variant="list" scheduled={value === 'SCHEDULED'} />;
    }
    return defaultRenderer(value, opts);
  },
};

const listConfigRenderer = {
  ...commonColumnsRenderers,
  ...endpointColumnsRenderers,
  ...vulnerableEndpointColumnsRenderers,
  ...injectColumnsRenderers,
  ...injectExpectationRenderers,
  ...entityStatusRenderers,
};

export default listConfigRenderer;

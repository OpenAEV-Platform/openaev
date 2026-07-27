import { DevicesOtherOutlined, GroupsOutlined, HelpOutlineOutlined, PlayCircleOutlineOutlined, RouteOutlined } from '@mui/icons-material';
import { type SvgIconProps, Tooltip } from '@mui/material';
import { Binoculars, SelectGroup } from 'mdi-material-ui';
import { type ComponentType, type CSSProperties, type ReactElement } from 'react';

import AssetPlatformFragment from '../../../../../components/common/list/fragments/AssetPlatformFragment';
import AttackPatternFragment from '../../../../../components/common/list/fragments/AttackPatternFragment';
import DateFragment from '../../../../../components/common/list/fragments/DateFragment';
import EndpointActiveFragment from '../../../../../components/common/list/fragments/EndpointActiveFragment';
import EndpointAgentsPrivilegeFragment from '../../../../../components/common/list/fragments/EndpointAgentsPrivilegeFragment';
import EndpointArchFragment from '../../../../../components/common/list/fragments/EndpointArchFragment';
import VulnerableEndpointActionFragment from '../../../../../components/common/list/fragments/VulnerableEndpointActionFragment';
import FindingIcon from '../../../../../components/FindingIcon';
import ItemStatus from '../../../../../components/ItemStatus';
import ItemTags from '../../../../../components/ItemTags';
import PlatformIconGroup from '../../../../../components/PlatformIconGroup';
import {
  type EsAsset,
  type EsBase,
  type EsFinding,
  type EsInject,
  type EsInjectExpectation,
  type EsSimulation,
  type Exercise,
  type InjectStatus as InjectStatusType,
} from '../../../../../utils/api-types';
import { computeInjectExpectationLabel } from '../../../../../utils/statusUtils';
import { type AssetCategory } from '../../../assets/asset-categories';
import AssetCategoryIcon from '../../../assets/AssetCategoryIcon';
import expectationIconByType, { expectationTypeIcon } from '../../../common/ExpectationIconByType';
import InjectStatus from '../../../common/injects/status/InjectStatus';
import ContractOutputElementType from '../../../findings/ContractOutputElementType';
import ExerciseStatus from '../../../simulations/simulation/ExerciseStatus';
import ExpectationTypeChip from '../widgets/viz/list/elements/ExpectationTypeChip';
import InjectExpectationSourceFragment from '../widgets/viz/list/elements/InjectExpectationSourceFragment';
import { type ColumnRenderer, defaultRenderer } from '../widgets/viz/list/elements/ListColumnConfig';
import { AssetCategoryCell, FindingTypeCell, InjectRowIcon, ScenarioStatusCell } from './ResultsListCells';

/**
 * Canonical per-entity list configuration for the dashboard results explorer.
 *
 * Each drilled entity renders with the exact same columns, labels, fragments
 * and leading icon as its own left-menu list page (Endpoints, Findings,
 * Scenarios, Simulations, Injects...), restricted to the fields actually
 * available on the Elasticsearch documents. No invented icons or chips: every
 * cell reuses the fragment components of the canonical pages.
 */

export interface ResultsColumn {
  field: string;
  label: string;
  /** ES sorting is only safe on date fields (text fields have no sortable mapping). */
  isSortable: boolean;
  /** Percentage width, mirroring the canonical page's inline styles. */
  width: number;
  /** Extra cell styles (e.g. flex centering for platform icons). */
  style?: CSSProperties;
}

export interface EntityListConfig {
  columns: ResultsColumn[];
  renderers: Record<string, ColumnRenderer>;
  rowIcon: (element: EsBase) => ReactElement;
  loaderIcon: ComponentType<SvgIconProps>;
}

export { defaultRenderer as entityDefaultRenderer };

// -- Shared cell components -------------------------------------------------

// ES stores the display form of the finding type ("IPv4", "PortsScan"...):
// map it back to the lowercase contract key used by FindingIcon and the
// canonical Findings list.
const FINDING_TYPE_KEY_BY_LABEL: Record<string, string> = Object.fromEntries(
  Object.entries(ContractOutputElementType).map(([key, label]) => [label, key]),
);
const findingTypeKey = (value: string): string => FINDING_TYPE_KEY_BY_LABEL[value] ?? value;

// -- Shared renderers --------------------------------------------------------

const tagsRenderer: ColumnRenderer = tags => <ItemTags variant="list" tags={(tags as string[]) ?? []} />;
const dateRenderer: ColumnRenderer = value => <DateFragment value={value as string} />;
const platformsRenderer: ColumnRenderer = platforms => <PlatformIconGroup platforms={(platforms as string[]) ?? []} />;
const attackPatternsRenderer: ColumnRenderer = (attackPatternIds, opts) => (
  <AttackPatternFragment attackPatterns={opts.attackPatterns} attackPatternIds={(attackPatternIds as string[]) ?? []} />
);
const textRenderer: ColumnRenderer = (value) => {
  const text = value?.toString() ?? '';
  return (
    <Tooltip title={text} placement="bottom-start">
      <span>{text}</span>
    </Tooltip>
  );
};

// -- Per-entity configuration ------------------------------------------------

const ENTITY_LIST_CONFIGS: Record<string, EntityListConfig> = {
  // Mirrors the Assets > Endpoints page (agent/executor/posture columns are
  // JPA-only and not available on the ES document).
  'asset': {
    columns: [
      {
        field: 'asset_name',
        label: 'Name',
        isSortable: false,
        width: 25,
      },
      {
        field: 'asset_category',
        label: 'Category',
        isSortable: false,
        width: 17,
      },
      {
        field: 'endpoint_platform',
        label: 'Platform',
        isSortable: false,
        width: 10,
        style: {
          display: 'flex',
          alignItems: 'center',
        },
      },
      {
        field: 'endpoint_arch',
        label: 'Architecture',
        isSortable: false,
        width: 10,
      },
      {
        field: 'base_tags_side',
        label: 'Tags',
        isSortable: false,
        width: 23,
      },
      {
        field: 'base_created_at',
        label: 'base_created_at',
        isSortable: true,
        width: 15,
      },
    ],
    renderers: {
      asset_category: value => <AssetCategoryCell category={value as string} />,
      endpoint_platform: platform => <AssetPlatformFragment platform={platform as string} />,
      endpoint_arch: arch => <EndpointArchFragment arch={arch as string} />,
      base_tags_side: tagsRenderer,
      base_created_at: dateRenderer,
    },
    rowIcon: element => <AssetCategoryIcon category={(element as EsAsset).asset_category as AssetCategory} color="primary" />,
    loaderIcon: HelpOutlineOutlined,
  },

  // Mirrors the Findings page (assets / asset-group pivots are aggregated
  // JPA-side and not available on the ES document).
  'finding': {
    columns: [
      {
        field: 'finding_type',
        label: 'Type',
        isSortable: false,
        width: 12,
      },
      {
        field: 'finding_value',
        label: 'Value',
        isSortable: false,
        width: 44,
      },
      {
        field: 'base_created_at',
        label: 'First seen',
        isSortable: true,
        width: 22,
      },
      {
        field: 'base_updated_at',
        label: 'Last seen',
        isSortable: true,
        width: 22,
      },
    ],
    renderers: {
      finding_type: value => <FindingTypeCell value={(value as string) ?? ''} />,
      finding_value: textRenderer,
      base_created_at: dateRenderer,
      base_updated_at: dateRenderer,
    },
    rowIcon: element => <FindingIcon findingType={findingTypeKey((element as EsFinding).finding_type ?? '')} tooltip />,
    loaderIcon: Binoculars,
  },

  // Mirrors the injects lists (simulation injects / atomic testings): contract
  // icon on the left, then title, status, attack patterns, platforms, tags.
  'inject': {
    columns: [
      {
        field: 'inject_title',
        label: 'Title',
        isSortable: false,
        width: 24,
      },
      {
        field: 'inject_status',
        label: 'Status',
        isSortable: false,
        width: 12,
      },
      {
        field: 'base_attack_patterns_side',
        label: 'Attack patterns',
        isSortable: false,
        width: 19,
      },
      {
        field: 'base_platforms_side_denormalized',
        label: 'Platforms',
        isSortable: false,
        width: 10,
        style: {
          display: 'flex',
          alignItems: 'center',
        },
      },
      {
        field: 'base_tags_side',
        label: 'Tags',
        isSortable: false,
        width: 19,
      },
      {
        field: 'execution_date',
        label: 'Execution date',
        isSortable: true,
        width: 16,
      },
    ],
    renderers: {
      inject_title: textRenderer,
      inject_status: status => <InjectStatus status={status as InjectStatusType['status_name']} />,
      base_attack_patterns_side: attackPatternsRenderer,
      base_platforms_side_denormalized: platformsRenderer,
      base_tags_side: tagsRenderer,
      execution_date: dateRenderer,
    },
    rowIcon: element => <InjectRowIcon element={element as EsInject} />,
    loaderIcon: HelpOutlineOutlined,
  },

  // Expectations have no left-menu page of their own: mirror the inject
  // expectation fragments used across simulation/atomic overview pages.
  'expectation-inject': {
    columns: [
      {
        field: 'inject_title',
        label: 'Inject',
        isSortable: false,
        width: 26,
      },
      {
        field: 'inject_expectation_type',
        label: 'Type',
        isSortable: false,
        width: 14,
      },
      {
        field: 'inject_expectation_status',
        label: 'Status',
        isSortable: false,
        width: 16,
      },
      {
        field: 'inject_expectation_source',
        label: 'Source',
        isSortable: false,
        width: 22,
      },
      {
        field: 'base_updated_at',
        label: 'Updated at',
        isSortable: true,
        width: 22,
      },
    ],
    renderers: {
      inject_title: textRenderer,
      inject_expectation_type: value => <ExpectationTypeChip type={value as string} />,
      inject_expectation_status: (_, { element }) => {
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
      inject_expectation_source: (_, { element }) => <InjectExpectationSourceFragment element={element} />,
      base_updated_at: dateRenderer,
    },
    rowIcon: (element) => {
      const Icon = expectationTypeIcon((element as EsInjectExpectation).inject_expectation_type);
      return <Icon color="primary" />;
    },
    loaderIcon: HelpOutlineOutlined,
  },

  // Mirrors the Simulations page (targets / global scores are computed
  // JPA-side and not available on the ES document).
  'simulation': {
    columns: [
      {
        field: 'name',
        label: 'Name',
        isSortable: false,
        width: 24,
      },
      {
        field: 'execution_date',
        label: 'Start time',
        isSortable: true,
        width: 15,
      },
      {
        field: 'status',
        label: 'Status',
        isSortable: false,
        width: 12,
      },
      {
        field: 'base_platforms_side_denormalized',
        label: 'Platforms',
        isSortable: false,
        width: 10,
        style: {
          display: 'flex',
          alignItems: 'center',
        },
      },
      {
        field: 'base_tags_side',
        label: 'Tags',
        isSortable: false,
        width: 23,
      },
      {
        field: 'base_updated_at',
        label: 'Updated',
        isSortable: true,
        width: 16,
      },
    ],
    renderers: {
      name: textRenderer,
      execution_date: dateRenderer,
      status: (_, { element }) => {
        const simulation = element as EsSimulation;
        return (
          <ExerciseStatus
            variant="list"
            exerciseStatus={simulation.status as Exercise['exercise_status']}
            exerciseStartDate={simulation.execution_date}
          />
        );
      },
      base_platforms_side_denormalized: platformsRenderer,
      base_tags_side: tagsRenderer,
      base_updated_at: dateRenderer,
    },
    rowIcon: () => <PlayCircleOutlineOutlined color="primary" />,
    loaderIcon: PlayCircleOutlineOutlined,
  },

  // Mirrors the Scenarios page (severity / category are JPA-only and not
  // available on the ES document).
  'scenario': {
    columns: [
      {
        field: 'name',
        label: 'Name',
        isSortable: false,
        width: 27,
      },
      {
        field: 'status',
        label: 'Status',
        isSortable: false,
        width: 13,
      },
      {
        field: 'base_platforms_side_denormalized',
        label: 'Platforms',
        isSortable: false,
        width: 10,
        style: {
          display: 'flex',
          alignItems: 'center',
        },
      },
      {
        field: 'base_tags_side',
        label: 'Tags',
        isSortable: false,
        width: 30,
      },
      {
        field: 'base_updated_at',
        label: 'Updated',
        isSortable: true,
        width: 20,
      },
    ],
    renderers: {
      name: textRenderer,
      status: value => <ScenarioStatusCell status={value as string} />,
      base_platforms_side_denormalized: platformsRenderer,
      base_tags_side: tagsRenderer,
      base_updated_at: dateRenderer,
    },
    rowIcon: () => <RouteOutlined color="primary" />,
    loaderIcon: RouteOutlined,
  },

  // Mirrors the People > Teams page (players / organization columns are
  // JPA-only: the ES team document only carries the name, indexed as its
  // base representative).
  'team': {
    columns: [
      {
        field: 'base_representative',
        label: 'Name',
        isSortable: false,
        width: 50,
      },
      {
        field: 'base_created_at',
        label: 'base_created_at',
        isSortable: true,
        width: 25,
      },
      {
        field: 'base_updated_at',
        label: 'Updated',
        isSortable: true,
        width: 25,
      },
    ],
    renderers: {
      base_representative: textRenderer,
      base_created_at: dateRenderer,
      base_updated_at: dateRenderer,
    },
    rowIcon: () => <GroupsOutlined color="primary" />,
    loaderIcon: GroupsOutlined,
  },

  // Mirrors the Assets > Asset groups page (description / assets count / tags
  // are JPA-only: the ES asset group document only carries the name, indexed
  // as its base representative).
  'asset-group': {
    columns: [
      {
        field: 'base_representative',
        label: 'Name',
        isSortable: false,
        width: 50,
      },
      {
        field: 'base_created_at',
        label: 'base_created_at',
        isSortable: true,
        width: 25,
      },
      {
        field: 'base_updated_at',
        label: 'Updated',
        isSortable: true,
        width: 25,
      },
    ],
    renderers: {
      base_representative: textRenderer,
      base_created_at: dateRenderer,
      base_updated_at: dateRenderer,
    },
    rowIcon: () => <SelectGroup color="primary" />,
    loaderIcon: SelectGroup,
  },

  // Mirrors the vulnerable endpoints list of the Findings area, reusing its
  // dedicated agent/privilege/action fragments.
  'vulnerable-endpoint': {
    columns: [
      {
        field: 'vulnerable_endpoint_hostname',
        label: 'Hostname',
        isSortable: false,
        width: 20,
      },
      {
        field: 'vulnerable_endpoint_platform',
        label: 'Platform',
        isSortable: false,
        width: 10,
        style: {
          display: 'flex',
          alignItems: 'center',
        },
      },
      {
        field: 'vulnerable_endpoint_architecture',
        label: 'Architecture',
        isSortable: false,
        width: 11,
      },
      {
        field: 'vulnerable_endpoint_agents_active_status',
        label: 'Status',
        isSortable: false,
        width: 10,
      },
      {
        field: 'vulnerable_endpoint_agents_privileges',
        label: 'vulnerable_endpoint_agents_privileges',
        isSortable: false,
        width: 11,
      },
      {
        field: 'vulnerable_endpoint_action',
        label: 'Action',
        isSortable: false,
        width: 20,
      },
      {
        field: 'base_created_at',
        label: 'base_created_at',
        isSortable: true,
        width: 18,
      },
    ],
    renderers: {
      vulnerable_endpoint_hostname: textRenderer,
      vulnerable_endpoint_platform: platform => <AssetPlatformFragment platform={platform as string} />,
      vulnerable_endpoint_architecture: arch => <EndpointArchFragment arch={arch as string} />,
      vulnerable_endpoint_agents_active_status: status => <EndpointActiveFragment activity_map={(status as boolean[]) ?? []} />,
      vulnerable_endpoint_agents_privileges: privileges => <EndpointAgentsPrivilegeFragment privileges={(privileges as string[]) ?? []} />,
      vulnerable_endpoint_action: action => <VulnerableEndpointActionFragment action={action as string} />,
      base_created_at: dateRenderer,
    },
    rowIcon: () => <DevicesOtherOutlined color="primary" />,
    loaderIcon: DevicesOtherOutlined,
  },
};

export const getEntityListConfig = (baseEntity: string): EntityListConfig | undefined => ENTITY_LIST_CONFIGS[baseEntity];

/** Builds the inline column styles from the canonical widths. */
export const buildEntityColumnStyles = (config: EntityListConfig): Record<string, CSSProperties> => Object.fromEntries(
  config.columns.map(column => [column.field, {
    width: `${column.width}%`,
    ...column.style,
  }]),
);

/** ES sorting is only safe on date fields (text fields have no sortable mapping). */
export const SORTABLE_DATE_COLUMNS = new Set(['base_created_at', 'base_updated_at', 'execution_date']);

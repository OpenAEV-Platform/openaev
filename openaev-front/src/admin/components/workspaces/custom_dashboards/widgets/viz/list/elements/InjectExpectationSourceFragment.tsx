import { Chip, type ChipEntity, type ChipSeverity, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { DevicesOtherOutlined, Groups3Outlined, PersonOutlined } from '@mui/icons-material';
import { SelectGroup } from 'mdi-material-ui';
import { type ComponentType } from 'react';

import { useFormatter } from '../../../../../../../../components/i18n';
import { type EsBase, type EsInjectExpectation } from '../../../../../../../../utils/api-types';
import getTargetTypeFromInjectExpectation from './injectExpectationTarget';
import useInjectExpectationTargetLabel from './useInjectExpectationTargetLabel';

// Icon + accent per expectation target kind, so the source reads at a glance.
const SOURCE_VISUALS: Record<string, {
  icon: ComponentType<{ style?: object }>;
  /** The chip's own tone: an entity token for a taxonomy, a severity otherwise. */
  entity?: ChipEntity;
  severity?: ChipSeverity;
}> = {
  PLAYERS: {
    icon: PersonOutlined,
    severity: 'info',
  },
  TEAMS: {
    icon: Groups3Outlined,
    entity: 'analyses',
  },
  ASSETS: {
    icon: DevicesOtherOutlined,
    entity: 'victimology',
  },
  ASSETS_GROUPS: {
    icon: SelectGroup,
    entity: 'arsenal',
  },
};

/**
 * Renders the source of an inject expectation as a chip: the icon and accent
 * encode the target kind (endpoint / asset group / team / player), the label
 * is the actual target name so two expectations on different assets no longer
 * look duplicated. The ES document only carries the target id, so the name is
 * resolved through the shared batched options cache; while loading (or when
 * the target is deleted / not readable) the generic kind label is shown.
 * Expectations without a resolvable target render a dash instead of crashing
 * formatjs with an empty translation.
 */
const InjectExpectationSourceFragment = ({ element }: { element: EsBase }) => {
  const { t } = useFormatter();
  const target = getTargetTypeFromInjectExpectation(element as EsInjectExpectation);
  const targetName = useInjectExpectationTargetLabel(target.type, target.id);
  if (!target.label) {
    return <span>-</span>;
  }
  const visual = SOURCE_VISUALS[target.type] ?? {
    icon: DevicesOtherOutlined,
    severity: 'neutral' as const,
  };
  const Icon = visual.icon;
  const kindLabel = t(target.label);
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Chip
          // The chip tints its own leading icon with the resolved tone, so the
          // glyph carries no colour of its own.
          startIcon={<Icon style={{ fontSize: 14 }} />}
          label={targetName ?? kindLabel}
          entity={visual.entity}
          severity={visual.severity}
          style={{ maxWidth: '100%' }}
        />
      </TooltipTrigger>
      {(targetName ? `${targetName} (${kindLabel})` : kindLabel) && <TooltipContent>{targetName ? `${targetName} (${kindLabel})` : kindLabel}</TooltipContent>}
    </Tooltip>
  );
};

export default InjectExpectationSourceFragment;

import { Box, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo } from 'react';

import FindingIcon from '../../../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../../../components/i18n';
import LogicNodeTooltip from '../../../../../chaining/logic/chaining_flow/NodeTooltip';
import graphTooltipSlotProps from '../../../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import attackPathStatusColor, { attackPathStatusLabel } from '../../attack-path-colors';
import { type AttackPathFlowNodeData } from '../../attack-path-flow-helpers';
import { buildCardSx, buildIconBoxSx, EYEBROW_SX, TITLE_COMPACT_SX } from './card-styles';

interface Props {
  data: AttackPathFlowNodeData;
  selected?: boolean;
}

// Aggregate finding cluster: a group of same-type findings ("header", click to expand into
// individual FindingCards) or the "+rest" overflow batch loader. Dashed while it stands for
// hidden findings.
const FindingClusterCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const isOverflow = data.clusterKind === 'overflow';
  const expanded = data.expanded ?? false;
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const countLabel = isOverflow ? `+${data.count ?? 0}` : String(data.count ?? 0);
  let action = t('Click to expand the findings');
  if (isOverflow) {
    action = t('Click to reveal the next batch');
  } else if (expanded) {
    action = t('Click to collapse the findings');
  }
  const tooltip = (
    <LogicNodeTooltip
      eyebrow={isOverflow ? t('More') : (data.typeFindings ?? t('Findings'))}
      title={`${countLabel} ${data.typeFindings ?? t('findings')}`}
      description={action}
      rows={data.status
        ? [{
            label: t('Status'),
            value: t(attackPathStatusLabel(data.status)),
          }]
        : []}
      accentColor={data.status ? verdict : undefined}
    />
  );
  return (
    <Tooltip title={tooltip} placement="top" arrow disableInteractive enterDelay={300} slotProps={graphTooltipSlotProps}>
      <Box
        sx={buildCardSx({
          theme,
          accent: verdict,
          selected,
          dimmed: data.dimmed,
          dashed: isOverflow || !expanded,
        })}
      >
        <Box sx={buildIconBoxSx(theme, verdict, 'small')}>
          <FindingIcon findingType={data.typeFindings ?? ''} />
        </Box>
        <Box sx={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: '1px',
        }}
        >
          <Typography component="span" sx={EYEBROW_SX}>
            {isOverflow ? t('More') : data.typeFindings}
          </Typography>
          <Typography component="div" sx={TITLE_COMPACT_SX}>
            {countLabel}
          </Typography>
        </Box>
      </Box>
    </Tooltip>
  );
};

export default memo(FindingClusterCard);

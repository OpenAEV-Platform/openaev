import { MoreHorizOutlined } from '@mui/icons-material';
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
// individual FindingCards), the "+rest" overflow batch loader, or the "+N other types" chip standing
// for the finding types the column caps away. Dashed while it stands for hidden content.
const FindingClusterCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const isOverflow = data.clusterKind === 'overflow';
  const isTypeOverflow = data.clusterKind === 'typeOverflow';
  const expanded = data.expanded ?? false;
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const countLabel = isOverflow || isTypeOverflow ? `+${data.count ?? 0}` : String(data.count ?? 0);
  let action = t('Click to expand the findings');
  if (isTypeOverflow) {
    action = expanded ? t('Click to hide the other finding types') : t('Click to reveal the other finding types');
  } else if (isOverflow) {
    action = t('Click to reveal the next batch');
  } else if (expanded) {
    action = t('Click to collapse the findings');
  }
  let eyebrow = data.typeFindings ?? t('Findings');
  if (isTypeOverflow) {
    eyebrow = t('Other types');
  } else if (isOverflow) {
    eyebrow = t('More');
  }
  const tooltip = (
    <LogicNodeTooltip
      eyebrow={eyebrow}
      title={isTypeOverflow
        ? `${countLabel} ${t('other types')}`
        : `${countLabel} ${data.typeFindings ?? t('findings')}`}
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
          dashed: isOverflow || isTypeOverflow || !expanded,
        })}
      >
        <Box sx={buildIconBoxSx(theme, verdict, 'small')}>
          {isTypeOverflow
            ? <MoreHorizOutlined sx={{ fontSize: 16 }} />
            : <FindingIcon findingType={data.typeFindings ?? ''} />}
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
            {eyebrow}
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

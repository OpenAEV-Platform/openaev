import { UnfoldLessOutlined, UnfoldMoreOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo } from 'react';

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

// Aggregate endpoint cluster: "header" (+N assets of an injector, click to expand/collapse) or
// "overflow" (+rest, click to reveal the next batch). Dashed while collapsed — it stands for
// hidden content; the accent carries the aggregated verdict of the endpoints inside.
const EndpointClusterCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const isOverflow = data.clusterKind === 'overflow';
  const expanded = data.expanded ?? false;
  const accent = attackPathStatusColor(theme, data.status);
  let sub = t('Assets');
  if (isOverflow) {
    sub = t('More');
  } else if (expanded) {
    sub = t('Collapse');
  }
  const statusText = t(attackPathStatusLabel(data.status));
  const Icon = expanded && !isOverflow ? UnfoldLessOutlined : UnfoldMoreOutlined;
  let action = t('Click to expand the endpoints');
  if (isOverflow) {
    action = t('Click to reveal the next batch');
  } else if (expanded) {
    action = t('Click to collapse the endpoints');
  }
  const tooltip = (
    <LogicNodeTooltip
      eyebrow={sub}
      title={`+${data.count ?? 0}`}
      description={action}
      rows={[{
        label: t('Status'),
        value: statusText,
      }]}
      accentColor={accent}
    />
  );
  return (
    <Tooltip title={tooltip} placement="top" arrow disableInteractive enterDelay={300} slotProps={graphTooltipSlotProps}>
      <Box
        aria-label={`+${data.count ?? 0} ${sub}, ${statusText}`}
        sx={buildCardSx({
          theme,
          accent,
          selected,
          dimmed: data.dimmed,
          dashed: !expanded || isOverflow,
        })}
      >
        <Box sx={buildIconBoxSx(theme, accent, 'small')}>
          <Icon />
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
            {sub}
          </Typography>
          <Typography component="div" sx={TITLE_COMPACT_SX}>
            {`+${data.count ?? 0}`}
          </Typography>
        </Box>
      </Box>
    </Tooltip>
  );
};

export default memo(EndpointClusterCard);

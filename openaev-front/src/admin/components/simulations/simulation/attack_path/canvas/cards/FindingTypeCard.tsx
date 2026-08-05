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

// A finding-type group for one endpoint (credentials, portscan, cve...): the type icon and name.
// Individual values hang off it as FindingCards.
const FindingTypeCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const tooltip = (
    <LogicNodeTooltip
      eyebrow={t('Finding type')}
      title={data.typeFindings ?? ''}
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
      <Box sx={buildCardSx({
        theme,
        accent: verdict,
        selected,
        dimmed: data.dimmed,
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
            {t('Finding type')}
          </Typography>
          <Typography component="div" sx={TITLE_COMPACT_SX}>
            {data.typeFindings}
          </Typography>
        </Box>
      </Box>
    </Tooltip>
  );
};

export default memo(FindingTypeCard);

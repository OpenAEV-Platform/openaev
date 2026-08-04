import { Box, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo } from 'react';

import FindingIcon from '../../../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../../../components/i18n';
import LogicNodeTooltip from '../../../../../chaining/logic/chaining_flow/NodeTooltip';
import graphTooltipSlotProps from '../../../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import attackPathStatusColor, { attackPathStatusLabel } from '../../attack-path-colors';
import { type AttackPathFlowNodeData, maskFindingValue } from '../../attack-path-flow-helpers';
import { buildCardSx, buildIconBoxSx, EYEBROW_SX, TITLE_COMPACT_SX } from './card-styles';

interface Props {
  data: AttackPathFlowNodeData;
  selected?: boolean;
}

// A single discovered finding: type icon in a verdict-tinted box and the (masked) value in
// monospace. The compact single-row variant of the card language — findings are leaves and there
// can be many of them.
const FindingCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const value = maskFindingValue(data.typeFindings, data.label);
  // Output-only value (a chaining output not persisted as a Finding, ADR-004): flagged so the
  // analyst can tell it apart from a real finding and knows its drawer is in a degraded mode.
  const isOutputOnly = data.isFinding === false;

  const tooltip = (
    <LogicNodeTooltip
      eyebrow={isOutputOnly ? t('Output only') : (data.typeFindings ?? t('Finding'))}
      title={value}
      description={isOutputOnly
        ? t('This is an output-only value used by the chaining and not recorded as a finding.')
        : undefined}
      rows={[
        ...(data.typeFindings
          ? [{
              label: t('Type'),
              value: data.typeFindings,
            }]
          : []),
        ...(data.status
          ? [{
              label: t('Status'),
              value: t(attackPathStatusLabel(data.status)),
            }]
          : []),
      ]}
      accentColor={verdict}
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
          <Typography
            component="span"
            sx={{
              ...EYEBROW_SX,
              ...(isOutputOnly ? { fontStyle: 'italic' } : {}),
            }}
          >
            {isOutputOnly ? t('Output only') : data.typeFindings}
          </Typography>
          <Typography
            component="div"
            sx={{
              ...TITLE_COMPACT_SX,
              fontFamily: 'Consolas, monaco, monospace',
              fontSize: '0.6875rem',
            }}
          >
            {value}
          </Typography>
        </Box>
      </Box>
    </Tooltip>
  );
};

export default memo(FindingCard);

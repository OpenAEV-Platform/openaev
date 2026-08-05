import { ComputerOutlined, Groups, LocalFireDepartment, Person, SwapHoriz, Workspaces } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { memo } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import LogicNodeTooltip, { type TooltipRow } from '../../../../../chaining/logic/chaining_flow/NodeTooltip';
import graphTooltipSlotProps from '../../../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import attackPathStatusColor, { attackPathChokepointColor, attackPathStatusLabel } from '../../attack-path-colors';
import { type AttackPathFlowNodeData, displayIp } from '../../attack-path-flow-helpers';
import { buildCardSx, buildIconBoxSx, CAPTION_SX, EYEBROW_SX, TITLE_SX } from './card-styles';

interface Props {
  data: AttackPathFlowNodeData;
  selected?: boolean;
}

const KIND_ICONS: Record<string, typeof Groups> = {
  TEAM: Groups,
  PERSON: Person,
  ASSET_GROUP: Workspaces,
};

// The target (endpoint / person / team / asset group) card: kind icon in a verdict-tinted box,
// uppercase kind kicker, hostname title and an ip + findings caption. The left accent bar carries
// the prevention/detection verdict; a chokepoint adds a violet flame chip with its exposure rank.
const TargetCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const counts = data.findingCounts;
  const total = counts ? Object.values(counts).reduce((sum, n) => sum + n, 0) : 0;
  const knownNoFindings = counts !== undefined && total === 0;
  const accent = knownNoFindings ? theme.palette.text.disabled : attackPathStatusColor(theme, data.status);
  const chokepointColor = attackPathChokepointColor(theme);
  const isChokepoint = data.chokepointRank !== undefined;
  const statusText = knownNoFindings ? t('No findings') : t(attackPathStatusLabel(data.status));

  const entityKind = data.entityKind ?? '';
  const KindIcon = KIND_ICONS[entityKind] ?? ComputerOutlined;
  const KIND_LABELS: Record<string, string> = {
    TEAM: t('Team'),
    PERSON: t('Person'),
    ASSET_GROUP: t('Asset group'),
  };
  const kindLabel = KIND_LABELS[entityKind] ?? t('Endpoint');

  const ipToShow = displayIp(data.seenIp, data.ip);
  const agents = data.agents ?? [];

  const tooltipRows: TooltipRow[] = [{
    label: t('Status'),
    value: statusText,
  }];
  const tooltipIp = data.ip || data.seenIp;
  if (tooltipIp) {
    tooltipRows.push({
      label: t('IP'),
      value: tooltipIp,
    });
  }
  if (data.platform) {
    tooltipRows.push({
      label: t('Platform'),
      value: data.platform,
    });
  }
  if (total > 0) {
    tooltipRows.push({
      label: t('Findings'),
      value: total,
    });
  }
  if (isChokepoint) {
    tooltipRows.push({
      label: t('Chokepoint'),
      value: t('Chokepoint #{rank} — most exposed endpoint ({count} findings)', {
        rank: String(data.chokepointRank),
        count: String(total),
      }),
    });
  }
  if (data.isPivot) {
    tooltipRows.push({
      label: t('Pivot node'),
      value: t('Both attacked and used as an attack source'),
    });
  }

  const tooltip = (
    <LogicNodeTooltip
      eyebrow={kindLabel}
      title={data.hostname || data.label || ''}
      rows={tooltipRows}
      chips={agents}
      accentColor={accent}
    />
  );

  const caption = [ipToShow, total > 0 ? `${total} ${t('finding(s)')}` : undefined]
    .filter(Boolean)
    .join(' · ');

  return (
    <Tooltip title={tooltip} placement="top" arrow disableInteractive enterDelay={300} slotProps={graphTooltipSlotProps}>
      <Box
        aria-label={`${data.label}, ${statusText}${isChokepoint ? `, ${t('chokepoint')}` : ''}`}
        sx={buildCardSx({
          theme,
          accent,
          selected,
          dimmed: data.dimmed,
          dashed: knownNoFindings,
        })}
      >
        <Box sx={buildIconBoxSx(theme, accent)}>
          <KindIcon />
        </Box>
        <Box sx={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: '2px',
        }}
        >
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          >
            <Typography
              component="span"
              sx={{
                ...EYEBROW_SX,
                flex: 1,
                minWidth: 0,
              }}
            >
              {kindLabel}
            </Typography>
            {data.isPivot && (
              <SwapHoriz sx={{
                fontSize: 13,
                color: theme.palette.warning.main,
                flexShrink: 0,
              }}
              />
            )}
            {isChokepoint && (
              <Box
                component="span"
                sx={{
                  flexShrink: 0,
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '2px',
                  fontSize: '0.5625rem',
                  fontWeight: 700,
                  letterSpacing: '0.03em',
                  color: chokepointColor,
                  backgroundColor: alpha(chokepointColor, 0.14),
                  borderRadius: 0.5,
                  paddingInline: 0.5,
                  paddingBlock: '1px',
                  lineHeight: 1.4,
                }}
              >
                <LocalFireDepartment sx={{ fontSize: 11 }} />
                {`#${data.chokepointRank}`}
              </Box>
            )}
          </Box>
          <Typography component="div" sx={TITLE_SX}>
            {data.label}
          </Typography>
          {caption && (
            <Typography component="div" sx={CAPTION_SX}>
              {caption}
            </Typography>
          )}
        </Box>
      </Box>
    </Tooltip>
  );
};

export default memo(TargetCard);

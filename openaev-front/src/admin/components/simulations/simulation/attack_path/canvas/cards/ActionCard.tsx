import { BoltOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { memo } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import { buildTenantApiPath } from '../../../../../../../utils/url-helper';
import LogicNodeTooltip, { type TooltipRow } from '../../../../../chaining/logic/chaining_flow/NodeTooltip';
import graphTooltipSlotProps from '../../../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import { type AttackPathFlowNodeData } from '../../attack-path-flow-helpers';
import ImageWithFallback from '../../ImageWithFallback';
import { buildCardSx, CAPTION_SX, EYEBROW_SX, TITLE_SX } from './card-styles';

interface Props {
  data: AttackPathFlowNodeData;
  selected?: boolean;
}

/** 'openaev_netexec' -> 'Netexec' (display form of the injector slug). */
const prettifyType = (value?: string): string =>
  (value ?? '')
    .replace(/^openaev_/i, '')
    .replace(/[_-]+/g, ' ')
    .replace(/\b\w/g, c => c.toUpperCase())
    .trim();

// The action (injector) card: the tool's own catalog logo in the icon slot, an uppercase "Action"
// kicker with the resolved ATT&CK technique count, and the tool name as title. Neutral accent —
// verdicts belong to targets and edges, not to the tool that ran.
const ActionCard = ({ data, selected = false }: Props) => {
  const theme = useTheme();
  const { t } = useFormatter();

  // Resolve the catalog image slug exactly like the previous node did (aliases cover renamed tools).
  const raw = (data.label ?? '').toLowerCase();
  const aliases: Record<string, string> = { crackmapexec: 'netexec' };
  const base = (data.injectorType ?? '').toLowerCase() || aliases[raw] || raw;
  const injectorSlug = base.startsWith('openaev_') ? base : `openaev_${base}`;

  const techniques = data.attackPatterns ?? [];
  const techLabels = techniques
    .map(tp => [tp.externalId, tp.name].filter(Boolean).join(' '))
    .filter(Boolean);

  const tooltipRows: TooltipRow[] = [];
  if (data.injectorType) {
    tooltipRows.push({
      label: t('Type'),
      value: prettifyType(data.injectorType),
    });
  }
  const tooltip = (
    <LogicNodeTooltip
      eyebrow={t('Action')}
      title={data.label ?? ''}
      rows={tooltipRows}
      chips={techLabels}
    />
  );

  return (
    <Tooltip title={tooltip} placement="top" arrow disableInteractive enterDelay={300} slotProps={graphTooltipSlotProps}>
      <Box sx={buildCardSx({
        theme,
        accent: theme.palette.primary.main,
        selected,
        dimmed: data.dimmed,
      })}
      >
        <Box sx={{
          // The tool logos are full-bleed square images designed on white; keep a white plate so
          // e.g. the Nmap art reads correctly in dark mode too.
          'display': 'flex',
          'alignItems': 'center',
          'justifyContent': 'center',
          'width': 34,
          'height': 34,
          'flexShrink': 0,
          'borderRadius': 0.75,
          'overflow': 'hidden',
          'backgroundColor': theme.palette.common.white,
          'border': `1px solid ${theme.palette.divider}`,
          '& img': {
            width: '100%',
            height: '100%',
            objectFit: 'cover',
          },
        }}
        >
          {base
            ? (
                <ImageWithFallback
                  src={buildTenantApiPath(`/api/injectors/${injectorSlug}/image`)}
                  alt={data.label ?? ''}
                  width={34}
                  height={34}
                  style={{ objectFit: 'cover' }}
                  fallback={(
                    <BoltOutlined sx={{
                      fontSize: 20,
                      color: theme.palette.grey[700],
                    }}
                    />
                  )}
                />
              )
            : (
                <BoltOutlined sx={{
                  fontSize: 20,
                  color: theme.palette.grey[700],
                }}
                />
              )}
        </Box>
        <Box sx={{
          flex: 1,
          minWidth: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: '2px',
        }}
        >
          <Typography component="span" sx={EYEBROW_SX}>
            {t('Action')}
          </Typography>
          <Typography component="div" sx={TITLE_SX}>
            {data.label}
          </Typography>
          {techniques.length > 0 && (
            <Typography
              component="div"
              sx={{
                ...CAPTION_SX,
                color: theme.palette.primary.main,
                fontWeight: 600,
              }}
            >
              {techniques.length === 1
                ? (techniques[0].externalId ?? techniques[0].name)
                : `${techniques[0].externalId ?? techniques[0].name} +${techniques.length - 1}`}
            </Typography>
          )}
        </Box>
      </Box>
    </Tooltip>
  );
};

export default memo(ActionCard);

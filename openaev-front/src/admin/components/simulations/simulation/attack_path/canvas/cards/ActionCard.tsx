import { BoltOutlined } from '@mui/icons-material';
import { Box, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { memo } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import { buildTenantApiPath } from '../../../../../../../utils/url-helper';
import LogicNodeTooltip, { type TooltipRow } from '../../../../../chaining/logic/chaining_flow/NodeTooltip';
import graphTooltipSlotProps from '../../../../../chaining/logic/logic-graph/graphTooltipSlotProps';
import InjectIcon from '../../../../../common/injects/InjectIcon';
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

  // An agent-executed action stands for a PAYLOAD, not an injector: its injectorType is always the
  // implant (openaev_agent), so the only faithful logo is the payload's collector logo (e.g.
  // openaev_netexec, openaev_atomic_red_team) - exactly what the logic tab shows. Resolve that first;
  // a payload with no backing collector (a hand-authored Command/Executable) falls back to the
  // payload-type glyph. Only a real injector node (network inject) keeps the injector-slug logo.
  const isPayloadAction = !!data.isPayload;
  const collectorType = (data.payloadCollectorType ?? '').toLowerCase();
  const hasCollectorLogo = collectorType.startsWith('openaev_');

  // Resolve the injector catalog image slug exactly like the previous node did (aliases cover
  // renamed tools); only used for a real injector node, never for an agent-executed payload.
  const raw = (data.label ?? '').toLowerCase();
  const aliases: Record<string, string> = { crackmapexec: 'netexec' };
  const base = (data.injectorType ?? '').toLowerCase() || aliases[raw] || raw;
  const injectorSlug = base.startsWith('openaev_') ? base : `openaev_${base}`;
  const showInjectorLogo = !isPayloadAction && !!base;

  // Whether the icon slot renders full-bleed catalog ART (a square image designed on white) versus a
  // themed glyph (a payload-type icon like Command/Executable, or the bolt fallback). The art needs a
  // white plate to read correctly in dark mode; a glyph must NOT sit on white - that is the ugly white
  // box with an off-colour primary icon. A glyph gets the neutral tinted plate the other cards use.
  const isImageLogo = hasCollectorLogo || showInjectorLogo;

  const boltFallback = (
    <BoltOutlined sx={{
      fontSize: 20,
      color: theme.palette.grey[700],
    }}
    />
  );

  // Icon precedence: collector logo (agent payload shipped by a collector) -> payload-type glyph
  // (hand-authored Command/Executable) -> injector-slug logo (network inject) -> generic bolt.
  let iconContent = boltFallback;
  if (hasCollectorLogo) {
    iconContent = (
      <ImageWithFallback
        src={buildTenantApiPath(`/api/collectors/${collectorType}/image`)}
        alt={data.label ?? ''}
        width={34}
        height={34}
        style={{ objectFit: 'cover' }}
        fallback={boltFallback}
      />
    );
  } else if (isPayloadAction) {
    iconContent = <InjectIcon type={data.payloadType} isPayload size="small" />;
  } else if (showInjectorLogo) {
    iconContent = (
      <ImageWithFallback
        src={buildTenantApiPath(`/api/injectors/${injectorSlug}/image`)}
        alt={data.label ?? ''}
        width={34}
        height={34}
        style={{ objectFit: 'cover' }}
        fallback={boltFallback}
      />
    );
  }

  const techniques = data.attackPatterns ?? [];
  const techLabels = techniques
    .map(tp => [tp.externalId, tp.name].filter(Boolean).join(' '))
    .filter(Boolean);

  const tooltipRows: TooltipRow[] = [];
  const typeForTooltip = isPayloadAction
    ? (data.payloadCollectorType || data.payloadType)
    : data.injectorType;
  if (typeForTooltip) {
    tooltipRows.push({
      label: t('Type'),
      value: prettifyType(typeForTooltip),
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
          // Full-bleed tool art keeps a white plate (so e.g. the Nmap logo reads in dark mode); a
          // themed glyph gets the neutral tinted plate the other canvas cards use, centered, so a pure
          // payload action is no longer a jarring white box with an off-centre primary icon.
          'display': 'flex',
          'alignItems': 'center',
          'justifyContent': 'center',
          'width': 34,
          'height': 34,
          'flexShrink': 0,
          'borderRadius': 0.75,
          'overflow': 'hidden',
          'backgroundColor': isImageLogo
            ? theme.palette.common.white
            : alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.16 : 0.1),
          'border': `1px solid ${theme.palette.divider}`,
          'color': theme.palette.primary.main,
          '& img': {
            width: '100%',
            height: '100%',
            objectFit: 'cover',
          },
          // The glyph carries its own explicit size; keep it block so flex centring is exact.
          '& svg': { display: 'block' },
        }}
        >
          {iconContent}
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

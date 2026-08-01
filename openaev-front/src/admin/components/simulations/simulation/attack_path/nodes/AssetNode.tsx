import { Groups, LocalFireDepartment, Person, SwapHoriz, Workspaces } from '@mui/icons-material';
import { Button, Chip, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo, useContext } from 'react';

import { useFormatter } from '../../../../../../components/i18n';
import attackPathStatusColor, { attackPathChokepointColor, attackPathStatusLabel } from '../attack-path-colors';
import { type AttackPathFlowNode, displayIp } from '../attack-path-flow-helpers';
import EndpointActionContext from '../attack-path-node-context';
import { AP_ENDPOINT_SIZE } from './node-sizes';

// The endpoint (target) node: a circle whose ring is the prevention/detection colour. An endpoint
// with no findings is a faint dashed grey circle; one with findings carries a coloured "+N" badge of
// its distinct-finding count (collapsed mode). Mirrors the product mockup's endpoint node.
const AssetNode = ({ id, data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const onDetails = useContext(EndpointActionContext);
  // findingCounts is only set in collapsed mode; when it is present and empty the endpoint is known
  // to have no findings (faint dashed grey). Otherwise the ring follows the prevention status.
  const counts = data.findingCounts;
  const total = counts ? Object.values(counts).reduce((sum, n) => sum + n, 0) : 0;
  const knownNoFindings = counts !== undefined && total === 0;
  const color = knownNoFindings ? theme.palette.text.disabled : attackPathStatusColor(theme, data.status);
  const isChokepoint = data.chokepointRank !== undefined;
  const chokepointColor = attackPathChokepointColor(theme);
  // Ring stays the verdict colour; the chokepoint signal is a separate violet halo + badge so a single
  // colour never carries two meanings. Status is also exposed as text below (a11y).
  const statusText = knownNoFindings ? t('No findings') : t(attackPathStatusLabel(data.status));
  let nodeShadow = 'none';
  if (isChokepoint) {
    nodeShadow = `0 0 0 6px ${alpha(chokepointColor, 0.3)}`;
  } else if (selected) {
    nodeShadow = `0 0 0 4px ${alpha(color, 0.45)}`;
  }
  const agents = data.agents ?? [];
  // The single relevant IP shown on the node: seen IP if known, else derived from the frozen list.
  // Gate the IP lines on this computed value (not on data.ip), so a seen IP still shows when the
  // frozen list is missing/empty. The tooltip shows the full list when present, else the seen IP.
  const ipToShow = displayIp(data.seenIp, data.ip);
  const tooltipIp = data.ip || data.seenIp;
  // A human-in-the-loop target (phishing/credential harvesting) is a TEAM, PERSON or ASSET_GROUP
  // rather than a machine, so it gets its own icon inside the circle and a kind label in the tooltip.
  const entityKind = data.entityKind;
  const KIND_ICONS: Record<string, typeof Groups> = {
    TEAM: Groups,
    PERSON: Person,
    ASSET_GROUP: Workspaces,
  };
  const KIND_LABELS: Record<string, string> = {
    TEAM: t('Team'),
    PERSON: t('Person'),
    ASSET_GROUP: t('Asset group'),
  };
  const KindIcon = entityKind ? (KIND_ICONS[entityKind] ?? null) : null;
  const kindLabel = entityKind ? (KIND_LABELS[entityKind] ?? null) : null;
  const tooltipTitle = (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      padding: 2,
      minWidth: 170,
    }}
    >
      <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{data.hostname || data.label}</Typography>
      {kindLabel && (
        <Typography
          variant="caption"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
          }}
          color="text.secondary"
        >
          {KindIcon && <KindIcon sx={{ fontSize: 15 }} />}
          {kindLabel}
        </Typography>
      )}
      {tooltipIp && (
        <Typography variant="caption" color="text.secondary">
          {t('IP')}
          :
          {' '}
          {tooltipIp}
        </Typography>
      )}
      {data.platform && (
        <Typography variant="caption" color="text.secondary">
          {t('Platform')}
          :
          {' '}
          {data.platform}
        </Typography>
      )}
      {data.isPivot && (
        <Typography
          variant="caption"
          sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 0.5,
            color: 'warning.main',
          }}
        >
          <SwapHoriz sx={{ fontSize: 16 }} />
          {t('Pivot node')}
        </Typography>
      )}
      {agents.length > 0 && (
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 4,
          marginTop: 2,
        }}
        >
          {agents.map(a => <Chip key={a} label={a} size="small" variant="outlined" />)}
        </div>
      )}
      {onDetails && (
        <Button
          size="small"
          variant="outlined"
          sx={{
            alignSelf: 'flex-end',
            mt: 0.5,
          }}
          onClick={() => onDetails(id, data.ref, data.label)}
        >
          {t('Details')}
          {' '}
          →
        </Button>
      )}
    </div>
  );
  return (
    <div
      style={{
        position: 'relative',
        width: AP_ENDPOINT_SIZE,
        height: AP_ENDPOINT_SIZE,
      }}
      aria-label={`${data.label}, ${statusText}${isChokepoint ? `, ${t('chokepoint')}` : ''}`}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Tooltip
        title={tooltipTitle}
        arrow
        // Interactive tooltip with a Details button: give the pointer time to travel the gap from
        // the circle to the tooltip so it does not close unless you cross exactly through the centre.
        leaveDelay={200}
      >
        <div
          style={{
            width: AP_ENDPOINT_SIZE,
            height: AP_ENDPOINT_SIZE,
            borderRadius: '50%',
            border: `${selected || isChokepoint ? 3 : 2}px ${knownNoFindings ? 'dashed' : 'solid'} ${color}`,
            // Keep the dark node fill (readable white label); show selection with a halo ring, not a
            // pale fill that would wash the text out. A chokepoint gets a violet halo so it stands out
            // without overriding the verdict-coloured ring.
            background: theme.palette.background.paper,
            boxShadow: nodeShadow,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: 4,
            boxSizing: 'border-box',
          }}
        >
          {KindIcon && (
            <KindIcon sx={{
              fontSize: 20,
              color,
              marginBottom: '2px',
            }}
            />
          )}
          <Typography
            variant="caption"
            fontWeight={700}
            sx={{
              maxWidth: AP_ENDPOINT_SIZE - 12,
              textAlign: 'center',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              lineHeight: 1.1,
            }}
          >
            {data.label}
          </Typography>
          {ipToShow && (
            <Typography variant="caption" color="text.secondary" sx={{ fontSize: 10 }}>
              {ipToShow}
            </Typography>
          )}
        </div>
      </Tooltip>
      {data.chokepointRank !== undefined && (
        <Tooltip title={t('Chokepoint #{rank} — most exposed endpoint ({count} findings)', {
          rank: String(data.chokepointRank),
          count: String(total),
        })}
        >
          <div
            style={{
              position: 'absolute',
              top: -12,
              left: -12,
              minWidth: 34,
              height: 30,
              padding: '0 9px',
              borderRadius: 15,
              background: chokepointColor,
              color: theme.palette.getContrastText(chokepointColor),
              fontSize: 15,
              fontWeight: 800,
              display: 'flex',
              alignItems: 'center',
              gap: 3,
              justifyContent: 'center',
              boxShadow: `0 0 0 3px ${theme.palette.background.paper}, 0 2px 6px ${alpha(theme.palette.common.black, 0.4)}`,
            }}
          >
            <LocalFireDepartment sx={{ fontSize: 19 }} />
            {data.chokepointRank}
          </div>
        </Tooltip>
      )}
      {total > 0 && (
        <div
          style={{
            position: 'absolute',
            top: -6,
            right: -6,
            minWidth: 22,
            height: 22,
            padding: '0 6px',
            borderRadius: 11,
            background: color,
            color: theme.palette.getContrastText(color),
            fontSize: 11,
            fontWeight: 700,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {`+${total}`}
        </div>
      )}
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(AssetNode);

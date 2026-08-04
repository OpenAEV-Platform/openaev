import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import FindingIcon from '../../../../../../components/FindingIcon';
import { useFormatter } from '../../../../../../components/i18n';
import attackPathStatusColor from '../attack-path-colors';
import { type AttackPathFlowNode, maskFindingValue } from '../attack-path-flow-helpers';
import { AP_FINDING_SIZE } from './node-sizes';

// Findings whose displayed value exceeds this many characters are hard-truncated on the map (the
// full value stays available in the node's tooltip), so a long output value (ADR-004) never renders
// as a sprawling one-line label across the graph.
const FINDING_LABEL_MAX_LENGTH = 16;

// A leaf finding node: the type icon with the discovered value ABOVE it (only the value, no type name),
// kept off the horizontal path so the incoming/outgoing edges never overlap the label. The handles sit on
// the icon so the edges reach it with no gap.
const FindingNode = ({ data, selected }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  const { t } = useFormatter();
  // Verdict colour (green/orange/red) by default; blue only when this finding is the selected path.
  const verdict = data.status ? attackPathStatusColor(theme, data.status) : theme.palette.divider;
  const color = selected ? theme.palette.primary.main : verdict;
  const value = maskFindingValue(data.typeFindings, data.label);
  // Hard-truncate what is rendered on the map (no word-boundary trimming, so it always keeps 16
  // characters); the full value stays in the tooltip below.
  const displayValue = value.length > FINDING_LABEL_MAX_LENGTH
    ? `${value.slice(0, FINDING_LABEL_MAX_LENGTH)}...`
    : value;
  // Output-only value (a chaining output not persisted as a Finding, ADR-004): flagged so the analyst
  // can tell it apart from a real finding and knows its drawer is in a degraded (details-less) mode.
  const isOutputOnly = data.isFinding === false;
  return (
    <div
      style={{
        position: 'relative',
        width: AP_FINDING_SIZE,
        height: AP_FINDING_SIZE,
        borderRadius: '50%',
        border: `${selected ? 2 : 1}px solid ${color}`,
        background: theme.palette.background.paper,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      {/* Value shown above the icon in the verdict colour. It is anchored at the icon's horizontal centre
          and flows RIGHTWARD (findings are the right-most column, so there is empty canvas there): this
          shows the full value untruncated while never sprawling LEFT over the incoming "<type> found" edge
          label or the neighbouring rows. */}
      <Typography
        variant="caption"
        title={isOutputOnly ? `${value} — ${t('Output only')}` : value}
        sx={{
          position: 'absolute',
          bottom: '100%',
          left: '50%',
          mb: 0.5,
          whiteSpace: 'nowrap',
          fontWeight: 700,
          color,
        }}
      >
        {displayValue}
      </Typography>
      <FindingIcon findingType={data.typeFindings ?? ''} />
      {isOutputOnly && (
        <Typography
          variant="caption"
          sx={{
            position: 'absolute',
            top: '100%',
            left: '50%',
            transform: 'translateX(-50%)',
            mt: 0.25,
            whiteSpace: 'nowrap',
            fontStyle: 'italic',
            color: theme.palette.text.secondary,
          }}
        >
          {t('Output only')}
        </Typography>
      )}
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(FindingNode);

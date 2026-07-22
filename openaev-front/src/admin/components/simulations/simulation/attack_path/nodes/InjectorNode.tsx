import { BoltOutlined } from '@mui/icons-material';
import { Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import AttackPatternChip from '../../../../../../components/AttackPatternChip';
import { buildTenantApiPath } from '../../../../../../utils/url-helper';
import { type AttackPathFlowNode } from '../attack-path-flow-helpers';
import ImageWithFallback from '../ImageWithFallback';
import { AP_INJECTOR_SIZE } from './node-sizes';

// The injector (source) node: a diamond carrying the injector's own catalog icon (falls back to a
// generic icon if the image is unavailable, e.g. for synthetic seed injectors), with its full name
// below the diamond (not truncated). Source of the execution edges.
const InjectorNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  // The injector image endpoint expects the full injector slug (e.g. "openaev_netexec"). Prefer the
  // backend-provided injector type; fall back to guessing from the short tool name (with a small alias
  // for renamed tools) for synthetic seed injectors that carry no type.
  const raw = (data.label ?? '').toLowerCase();
  const aliases: Record<string, string> = { crackmapexec: 'netexec' };
  const base = (data.injectorType ?? '').toLowerCase() || aliases[raw] || raw;
  const injectorSlug = base.startsWith('openaev_') ? base : `openaev_${base}`;
  // ATT&CK techniques resolved by the backend for this injector's contract; rendered with the shared
  // AttackPatternChip (the platform's canonical attack-pattern taxonomy component) in a hover tooltip,
  // plus a small count badge so the analyst sees them on the node without opening the drawer.
  const techniques = data.attackPatterns ?? [];
  const techniqueTitle = techniques.length > 0
    ? (
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 2,
        }}
        >
          {techniques.map(tp => (
            <AttackPatternChip
              key={tp.externalId ?? tp.name}
              attackPattern={{
                attack_pattern_id: tp.externalId ?? '',
                attack_pattern_external_id: tp.externalId ?? '',
                attack_pattern_name: tp.name ?? '',
              }}
            />
          ))}
        </div>
      )
    : '';
  return (
    <div style={{
      position: 'relative',
      width: AP_INJECTOR_SIZE,
      height: AP_INJECTOR_SIZE,
    }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <Tooltip title={techniqueTitle} arrow>
        <div
          style={{
            width: AP_INJECTOR_SIZE,
            height: AP_INJECTOR_SIZE,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            // Injector logos are drawn on white; fill the whole diamond with white so the logo colour
            // extends edge-to-edge instead of a small icon floating over the dark paper corners.
            background: theme.palette.common.white,
            border: `1px solid ${theme.palette.text.secondary}`,
            clipPath: 'polygon(50% 0, 100% 50%, 50% 100%, 0 50%)',
          }}
        >
          {base
            ? (
                <ImageWithFallback
                  src={buildTenantApiPath(`/api/injectors/${injectorSlug}/image`)}
                  alt={data.label ?? ''}
                  width={56}
                  height={56}
                  style={{ objectFit: 'contain' }}
                  fallback={(
                    <BoltOutlined sx={{
                      fontSize: 34,
                      color: theme.palette.grey[700],
                    }}
                    />
                  )}
                />
              )
            : (
                <BoltOutlined sx={{
                  fontSize: 34,
                  color: theme.palette.text.secondary,
                }}
                />
              )}
        </div>
      </Tooltip>
      {techniques.length > 0 && (
        <div
          style={{
            position: 'absolute',
            top: -4,
            right: -4,
            minWidth: 16,
            height: 16,
            padding: '0 4px',
            borderRadius: 8,
            background: theme.palette.primary.main,
            color: theme.palette.primary.contrastText,
            fontSize: 10,
            fontWeight: 700,
            lineHeight: '16px',
            textAlign: 'center',
          }}
        >
          {techniques.length}
        </div>
      )}
      <Typography
        variant="caption"
        fontWeight={700}
        sx={{
          position: 'absolute',
          top: '100%',
          left: '50%',
          transform: 'translateX(-50%)',
          mt: 0.5,
          whiteSpace: 'nowrap',
          lineHeight: 1.1,
          textAlign: 'center',
        }}
      >
        {data.label}
      </Typography>
      <Handle type="source" position={Position.Right} style={{ opacity: 0 }} />
    </div>
  );
};

export default memo(InjectorNode);

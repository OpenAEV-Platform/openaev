import { BoltOutlined } from '@mui/icons-material';
import { Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Handle, type NodeProps, Position } from '@xyflow/react';
import { memo } from 'react';

import { buildTenantApiPath } from '../../../../../../utils/url-helper';
import { type AttackPathFlowNode } from '../attack-path-flow-helpers';
import ImageWithFallback from '../ImageWithFallback';
import { AP_INJECTOR_SIZE } from './node-sizes';

// The injector (source) node: a diamond carrying the injector's own catalog icon (falls back to a
// generic icon if the image is unavailable, e.g. for synthetic seed injectors), with its full name
// below the diamond (not truncated). Source of the execution edges.
const InjectorNode = ({ data }: NodeProps<AttackPathFlowNode>) => {
  const theme = useTheme();
  // The injector image endpoint expects the full injector slug (e.g. "openaev_netexec"). The graph
  // node carries the short tool name, so map it to the slug (with a small alias for renamed tools).
  // TODO(#6647): have the backend expose the injector type/slug on the node instead of guessing.
  const raw = (data.label ?? '').toLowerCase();
  const aliases: Record<string, string> = { crackmapexec: 'netexec' };
  const injectorSlug = raw.startsWith('openaev_') ? raw : `openaev_${aliases[raw] ?? raw}`;
  return (
    <div style={{
      position: 'relative',
      width: AP_INJECTOR_SIZE,
      height: AP_INJECTOR_SIZE,
    }}
    >
      <Handle type="target" position={Position.Left} style={{ opacity: 0 }} />
      <div
        style={{
          width: AP_INJECTOR_SIZE,
          height: AP_INJECTOR_SIZE,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: theme.palette.background.paper,
          border: `1px solid ${theme.palette.text.secondary}`,
          clipPath: 'polygon(50% 0, 100% 50%, 50% 100%, 0 50%)',
        }}
      >
        {raw
          ? (
              <ImageWithFallback
                src={buildTenantApiPath(`/api/injectors/${injectorSlug}/image`)}
                alt={data.label ?? ''}
                width={40}
                height={40}
                style={{ objectFit: 'contain' }}
                fallback={(
                  <BoltOutlined sx={{
                    fontSize: 34,
                    color: theme.palette.text.secondary,
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

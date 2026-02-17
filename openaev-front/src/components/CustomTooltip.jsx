import { Tooltip } from '@mui/material';
import { useState } from 'react';

export default function CustomTooltip({ children, ...rest }) {
  const [renderTooltip, setRenderTooltip] = useState(false);
  return (
    <span
      onMouseEnter={() => !renderTooltip && setRenderTooltip(true)}
      style={{ lineHeight: '20px' }}
    >
      {!renderTooltip && children}
      {renderTooltip && <Tooltip {...rest}>{children}</Tooltip>}
    </span>
  );
}

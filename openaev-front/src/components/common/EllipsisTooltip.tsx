import { Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type MouseEvent, useCallback, useState } from 'react';

interface Props {
  children: string;
  style?: CSSProperties;
}

/**
 * Single-line text that ellipses instead of wrapping, with a tooltip carrying
 * the full text - shown only when the text is actually truncated. The inner
 * span uses the `width: 0 / min-width: 100%` containment trick so the label
 * never widens intrinsically-sized containers (e.g. ATT&CK matrix columns):
 * it always ellipses to whatever width the rest of the content dictates.
 */
const EllipsisTooltip: FunctionComponent<Props> = ({ children, style }) => {
  const [open, setOpen] = useState(false);
  const onMouseEnter = useCallback((event: MouseEvent<HTMLElement>) => {
    const element = event.currentTarget;
    if (element.scrollWidth > element.clientWidth) {
      setOpen(true);
    }
  }, []);
  const onMouseLeave = useCallback(() => setOpen(false), []);
  return (
    <Tooltip title={children} open={open} disableInteractive>
      <span
        onMouseEnter={onMouseEnter}
        onMouseLeave={onMouseLeave}
        style={{
          display: 'block',
          width: 0,
          minWidth: '100%',
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          ...style,
        }}
      >
        {children}
      </span>
    </Tooltip>
  );
};

export default EllipsisTooltip;

import { Tooltip } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type SyntheticEvent, useCallback, useState } from 'react';

interface Props {
  children: string;
  style?: CSSProperties;
}

/**
 * Single-line text that ellipses instead of wrapping, with a tooltip carrying
 * the full text - shown only when the text is actually truncated. Opens on
 * hover and on keyboard focus (the span is focusable) so the full text stays
 * reachable without a pointer. The inner span uses the `width: 0 / min-width:
 * 100%` containment trick so the label never widens intrinsically-sized
 * containers (e.g. ATT&CK matrix columns): it always ellipses to whatever
 * width the rest of the content dictates.
 */
const EllipsisTooltip: FunctionComponent<Props> = ({ children, style }) => {
  const [open, setOpen] = useState(false);
  const openIfTruncated = useCallback((event: SyntheticEvent<HTMLElement>) => {
    const element = event.currentTarget;
    if (element.scrollWidth > element.clientWidth) {
      setOpen(true);
    }
  }, []);
  const close = useCallback(() => setOpen(false), []);
  return (
    <Tooltip title={children} open={open} disableInteractive>
      <span
        tabIndex={0}
        onMouseEnter={openIfTruncated}
        onMouseLeave={close}
        onFocus={openIfTruncated}
        onBlur={close}
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

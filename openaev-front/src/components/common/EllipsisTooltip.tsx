import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { type CSSProperties, type FunctionComponent, type SyntheticEvent, useCallback, useState } from 'react';

interface Props {
  children: string;
  style?: CSSProperties;
  /** False inside a link or a button, which already takes the focus and carries the full text. */
  focusable?: boolean;
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
const EllipsisTooltip: FunctionComponent<Props> = ({ children, style, focusable = true }) => {
  const [open, setOpen] = useState(false);
  const openIfTruncated = useCallback((event: SyntheticEvent<HTMLElement>) => {
    const element = event.currentTarget;
    if (element.scrollWidth > element.clientWidth) {
      setOpen(true);
    }
  }, []);
  const close = useCallback(() => setOpen(false), []);
  return (
    <Tooltip open={open}>
      <TooltipTrigger asChild>
        <span
          tabIndex={focusable ? 0 : undefined}
          onMouseEnter={openIfTruncated}
          onMouseLeave={close}
          onFocus={focusable ? openIfTruncated : undefined}
          onBlur={focusable ? close : undefined}
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
      </TooltipTrigger>
      <TooltipContent>{children}</TooltipContent>
    </Tooltip>
  );
};

export default EllipsisTooltip;

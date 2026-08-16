import { Tooltip } from '@mui/material';
import { cloneElement, type PointerEvent as ReactPointerEvent, type ReactElement, type ReactNode, useCallback, useEffect, useState } from 'react';

import graphTooltipSlotProps from './graphTooltipSlotProps';

interface GraphCardTooltipProps {
  /** Rich tooltip body (see {@link LogicNodeTooltip}). */
  title: ReactNode;
  /**
   * When this value changes the tooltip force-closes. The graph passes its structural layout
   * signature here: a live poll that authors or removes a step re-lays-out the canvas and can slide
   * the hovered card out from under a stationary cursor - which fires no mouseleave - so the popover
   * would otherwise stay pinned over the graph at the card's old position.
   */
  dismissKey?: unknown;
  /** The card element the tooltip is anchored to. */
  children: ReactElement<{ onPointerDownCapture?: (event: ReactPointerEvent) => void }>;
}

/**
 * Controlled MUI Tooltip wrapper shared by the causal-graph cards. The cards live in a pan/zoom
 * canvas that re-renders on every autonomous poll and opens a drawer or row menu on press - both of
 * which routinely robbed an UNCONTROLLED tooltip of its mouseleave/blur close and left the rich
 * popover stuck open over the canvas (the reported "tooltip stays open" bug). Controlling `open`
 * lets us force it shut the instant the card is pressed (any press = select / drag / open drawer)
 * and whenever the graph structurally re-lays-out ({@link dismissKey}); neither is something the
 * hover-only close path can guarantee inside this canvas.
 *
 * The dismiss handler runs in the CAPTURE phase so it fires for every press inside the card - the
 * body, the row menu, the connect handle - even though those children {@code stopPropagation} in the
 * bubble phase to avoid starting a node drag.
 */
const GraphCardTooltip = ({ title, dismissKey, children }: GraphCardTooltipProps) => {
  const [open, setOpen] = useState(false);
  const close = useCallback(() => setOpen(false), []);

  const handlePointerDownCapture = useCallback((event: ReactPointerEvent) => {
    close();
    children.props.onPointerDownCapture?.(event);
  }, [children, close]);

  // Force the popover shut when the graph re-lays-out under the cursor (see dismissKey).
  useEffect(() => {
    close();
  }, [dismissKey, close]);

  return (
    <Tooltip
      title={title}
      placement="top"
      arrow
      disableInteractive
      enterDelay={300}
      open={open}
      onOpen={() => setOpen(true)}
      onClose={close}
      slotProps={graphTooltipSlotProps}
    >
      {cloneElement(children, { onPointerDownCapture: handlePointerDownCapture })}
    </Tooltip>
  );
};

export default GraphCardTooltip;

import { Tooltip, type TooltipProps } from '@mui/material';
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
  /**
   * Tooltip slot overrides. Defaults to the shared graph slot props (z-index capped below the
   * drawer). A caller rendering a rich card body outside the graph canvas (the decision timeline)
   * passes its own surface styling here while still reusing the controlled dismiss behaviour.
   */
  slotProps?: TooltipProps['slotProps'];
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
const GraphCardTooltip = ({ title, dismissKey, slotProps, children }: GraphCardTooltipProps) => {
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

  // Force the popover shut on any wheel while it is open. The cards live in a pan/zoom canvas that
  // is overflow:hidden and whose own wheel handler preventDefaults the browser zoom, so Popper
  // never sees a scroll to reposition against: an open card would otherwise detach from its anchor
  // and float over the graph while the operator zooms. A capture-phase passive listener catches the
  // wheel before the canvas swallows it, without blocking the zoom itself.
  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const closeOnWheel = () => close();
    window.addEventListener('wheel', closeOnWheel, {
      capture: true,
      passive: true,
    });
    return () => window.removeEventListener('wheel', closeOnWheel, { capture: true });
  }, [open, close]);

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
      slotProps={slotProps ?? graphTooltipSlotProps}
    >
      {cloneElement(children, { onPointerDownCapture: handlePointerDownCapture })}
    </Tooltip>
  );
};

export default GraphCardTooltip;

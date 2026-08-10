import { AccountTreeOutlined, AddOutlined, CenterFocusStrongOutlined, RemoveOutlined } from '@mui/icons-material';
import { Box, IconButton, Tooltip } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  type PointerEvent as ReactPointerEvent,
  type ReactNode,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';

import { useFormatter } from '../../../../../components/i18n';

interface PanZoomProps {
  /** Logical content size (bounding box of the laid-out graph). */
  contentWidth: number;
  contentHeight: number;
  /** Changes whenever the graph structure changes; triggers an auto re-fit. */
  fitSignature: string;
  minZoom?: number;
  maxZoom?: number;
  /** Called on a click on the empty canvas (no drag) - used to clear the selection. */
  onBackgroundClick?: () => void;
  /** Reports the current zoom so the parent can convert screen deltas to logical ones while dragging. */
  onZoomChange?: (zoom: number) => void;
  /** Re-organize control: clears manual positions and lets the auto-layout take over again. */
  onAutoLayout?: () => void;
  /** World content, rendered in logical coordinates inside the zoom/pan transform. */
  children: ReactNode;
}

// Inner padding kept around the content when fitting.
const FIT_PADDING = 56;
// Initial zoom is clamped to this range so a tiny chain is not blown up and a huge one still fits.
const INITIAL_MIN_ZOOM = 0.6;
const INITIAL_MAX_ZOOM = 1.1;
const DRAG_THRESHOLD = 4;
const ZOOM_STEP = 1.15;

interface Point {
  x: number;
  y: number;
}

/**
 * Self-contained pan/zoom viewport modeled on XTM One's `AgentFlowGraph`: a fixed logical coordinate
 * space rendered through a single CSS transform, fit-to-content on load, mouse-wheel / trackpad zoom
 * around the cursor (plain scroll, Ctrl/Cmd also works), drag-to-pan, and explicit zoom in / out /
 * fit controls. No extra dependency.
 */
const PanZoom = ({
  contentWidth,
  contentHeight,
  fitSignature,
  minZoom = 0.3,
  maxZoom = 2.5,
  onBackgroundClick,
  onZoomChange,
  onAutoLayout,
  children,
}: PanZoomProps) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const containerRef = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState<Point>({
    x: 0,
    y: 0,
  });
  const [panning, setPanning] = useState(false);

  // Whether the current content has been fitted at least once. Lets a late container measurement
  // (initial layout) trigger the fit, while preventing later resizes from resetting a manual zoom.
  const hasFitted = useRef(false);
  const drag = useRef<{
    startX: number;
    startY: number;
    panX: number;
    panY: number;
    moved: boolean;
    pointerId: number;
  } | null>(null);

  const clampZoom = useCallback(
    (value: number) => Math.min(maxZoom, Math.max(minZoom, value)),
    [minZoom, maxZoom],
  );

  // Publish the live zoom so the parent can translate pointer deltas into logical units when a
  // node is being dragged.
  useEffect(() => {
    onZoomChange?.(zoom);
  }, [zoom, onZoomChange]);

  const fit = useCallback(() => {
    const el = containerRef.current;
    if (!el) return;
    const availW = el.clientWidth - FIT_PADDING * 2;
    const availH = el.clientHeight - FIT_PADDING * 2;
    if (availW <= 0 || availH <= 0 || contentWidth <= 0 || contentHeight <= 0) return;
    const raw = Math.min(availW / contentWidth, availH / contentHeight);
    const initial = Math.min(INITIAL_MAX_ZOOM, Math.max(INITIAL_MIN_ZOOM, raw));
    const next = clampZoom(initial);
    setZoom(next);
    // Anchor on the top-left (with padding) whenever the content is larger than the viewport at this
    // zoom, so the top of the graph — the tactic column headers — is always in view after a fit; only
    // content that fully fits is centered. Centering a taller-than-viewport graph pushed its top
    // (the headers) off-screen.
    const scaledW = contentWidth * next;
    const scaledH = contentHeight * next;
    setPan({
      x: scaledW > el.clientWidth ? FIT_PADDING : (el.clientWidth - scaledW) / 2,
      y: scaledH > el.clientHeight ? FIT_PADDING : (el.clientHeight - scaledH) / 2,
    });
    hasFitted.current = true;
  }, [contentWidth, contentHeight, clampZoom]);

  // Re-fit whenever the graph structure changes.
  useLayoutEffect(() => {
    hasFitted.current = false;
    fit();
  }, [fitSignature, fit]);

  // Fit once the container gets a real size (initial mount / late layout). Never re-fits after the
  // first successful fit, so window/tab resizes preserve the user's manual zoom.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return undefined;
    const observer = new ResizeObserver(() => {
      if (!hasFitted.current) fit();
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [fit]);

  // Zoom around a point in container (screen) coordinates, keeping that world point under the cursor.
  const zoomAt = useCallback((factor: number, cx: number, cy: number) => {
    setZoom((prevZoom) => {
      const next = clampZoom(prevZoom * factor);
      setPan(prevPan => ({
        x: cx - ((cx - prevPan.x) / prevZoom) * next,
        y: cy - ((cy - prevPan.y) / prevZoom) * next,
      }));
      return next;
    });
  }, [clampZoom]);

  // Mouse-wheel / trackpad zoom around the cursor (native, non-passive listener so we can
  // preventDefault the browser page scroll/zoom). Plain scroll zooms - no modifier required - and
  // Ctrl/Cmd still works. The delta is normalized across wheel modes (pixel / line / page) and
  // mapped through exp() so a notched mouse and a fine-grained trackpad both zoom by a sensible,
  // consistent amount.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return undefined;
    const handleWheel = (e: WheelEvent) => {
      e.preventDefault();
      const rect = el.getBoundingClientRect();
      let unit = 1;
      if (e.deltaMode === 1) unit = 16;
      else if (e.deltaMode === 2) unit = el.clientHeight;
      const factor = Math.exp(-e.deltaY * unit * 0.0015);
      zoomAt(factor, e.clientX - rect.left, e.clientY - rect.top);
    };
    el.addEventListener('wheel', handleWheel, { passive: false });
    return () => el.removeEventListener('wheel', handleWheel);
  }, [zoomAt]);

  const zoomByButton = useCallback((factor: number) => {
    const el = containerRef.current;
    if (!el) return;
    zoomAt(factor, el.clientWidth / 2, el.clientHeight / 2);
  }, [zoomAt]);

  // Panning starts on the empty canvas only: cards stop propagation of their own pointerdown, so a
  // pointerdown reaching here means the background. A move beyond the threshold becomes a pan; a
  // click without movement clears the selection.
  const handlePointerDown = (e: ReactPointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    drag.current = {
      startX: e.clientX,
      startY: e.clientY,
      panX: pan.x,
      panY: pan.y,
      moved: false,
      pointerId: e.pointerId,
    };
  };

  const handlePointerMove = (e: ReactPointerEvent<HTMLDivElement>) => {
    const state = drag.current;
    if (!state) return;
    const dx = e.clientX - state.startX;
    const dy = e.clientY - state.startY;
    if (!state.moved && Math.hypot(dx, dy) > DRAG_THRESHOLD) {
      state.moved = true;
      setPanning(true);
      containerRef.current?.setPointerCapture(state.pointerId);
    }
    if (state.moved) {
      setPan({
        x: state.panX + dx,
        y: state.panY + dy,
      });
    }
  };

  const handlePointerUp = () => {
    const state = drag.current;
    drag.current = null;
    if (!state) return;
    if (state.moved) {
      containerRef.current?.releasePointerCapture?.(state.pointerId);
      setPanning(false);
    } else {
      onBackgroundClick?.();
    }
  };

  const controlButtonSx = {
    'padding': 0.75,
    'color': theme.palette.primary.main,
    'borderRadius': 0,
    'borderBottom': `1px solid ${theme.palette.divider}`,
    '&:last-of-type': { borderBottom: 'none' },
    '&:hover': { backgroundColor: theme.palette.action.hover },
  };

  return (
    <Box
      ref={containerRef}
      onPointerDown={handlePointerDown}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerLeave={handlePointerUp}
      sx={{
        position: 'relative',
        width: '100%',
        height: '100%',
        overflow: 'hidden',
        cursor: panning ? 'grabbing' : 'grab',
        touchAction: 'none',
        // Minimal low-opacity "paper" dot grid in screen space (does not clutter the graph).
        backgroundImage: `radial-gradient(${theme.palette.divider} 1px, transparent 1px)`,
        backgroundSize: '28px 28px',
        backgroundPosition: '-1px -1px',
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: contentWidth,
          height: contentHeight,
          transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
          transformOrigin: '0 0',
        }}
      >
        {children}
      </div>

      <Box
        sx={{
          position: 'absolute',
          bottom: theme.spacing(2),
          left: theme.spacing(2),
          display: 'flex',
          flexDirection: 'column',
          borderRadius: 1,
          overflow: 'hidden',
          border: `1px solid ${theme.palette.divider}`,
          backgroundColor: theme.palette.background.paper,
          boxShadow: theme.shadows[3],
        }}
      >
        <Tooltip title={t('Zoom in')}>
          <IconButton size="small" sx={controlButtonSx} onClick={() => zoomByButton(ZOOM_STEP)}>
            <AddOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Zoom out')}>
          <IconButton size="small" sx={controlButtonSx} onClick={() => zoomByButton(1 / ZOOM_STEP)}>
            <RemoveOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        <Tooltip title={t('Fit to view')}>
          <IconButton size="small" sx={controlButtonSx} onClick={fit}>
            <CenterFocusStrongOutlined fontSize="small" />
          </IconButton>
        </Tooltip>
        {onAutoLayout && (
          <Tooltip title={t('Auto-organize')}>
            <IconButton size="small" sx={controlButtonSx} onClick={onAutoLayout}>
              <AccountTreeOutlined fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Box>
    </Box>
  );
};

export default PanZoom;

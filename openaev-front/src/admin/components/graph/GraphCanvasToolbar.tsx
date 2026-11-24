import { IconButton, Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import {
  AccountTreeOutlined,
  AutorenewOutlined,
  BubbleChartOutlined,
  DonutLargeOutlined,
  DownloadOutlined,
  FitScreenOutlined,
  GestureOutlined,
  KeyboardOutlined,
  LinkOutlined,
  ListAltOutlined,
  UndoOutlined,
  VisibilityOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';
import { type ReactNode } from 'react';

import { useFormatter } from '../../../components/i18n';
import { type GraphLayout } from './graph-layout';

const LAYOUT_ICON: Record<GraphLayout, ReactNode> = {
  force: <BubbleChartOutlined fontSize="small" />,
  hierarchical: <AccountTreeOutlined fontSize="small" />,
  radial: <DonutLargeOutlined fontSize="small" />,
};

/** What each layout button does, said in its tooltip (Sam, 19 Nov 2026: no button without one). */
const LAYOUT_LABEL: Record<GraphLayout, string> = {
  force: 'Force layout: nodes repel each other, links pull them together. Click again to re-run it.',
  hierarchical: 'Hierarchical layout: scenarios on top, findings at the bottom',
  radial: 'Radial layout: rings of neighbours around the selected node',
};

/** The lasso button's tooltip and its shortcut line (OpenCTI's LassoSelection). */
export const LASSO_LABEL = 'Lasso selection: draw around nodes to select them';

interface GraphCanvasToolbarProps {
  /** The page graph shows the layout, selection and export actions; the embedded graph keeps the count, the zoom and the fit. */
  full: boolean;
  /** The width the open side panel takes on the right of the canvas. */
  offset: number;
  /** The entities shown. */
  visibleCount: number;
  layout: GraphLayout;
  lassoOn: boolean;
  canShowHidden: boolean;
  canUndo: boolean;
  onZoomIn: () => void;
  onZoomOut: () => void;
  onFit: () => void;
  /** Runs a layout from scratch; without a value, the active one again. */
  onRelayout: (layout?: GraphLayout) => void;
  onToggleLasso: () => void;
  onShowHidden: () => void;
  onUndo: () => void;
  onExportPng: () => void;
  onCopyLink: () => void;
  onListEntities: () => void;
  onShortcuts: () => void;
}

/**
 * The graph actions, as OpenCTI's GraphToolbar: ONE icon bar on the canvas (bottom right, left of
 * the open side panel, clear of the legend card) opened by the count of entities shown. Each
 * layout button runs its layout from scratch (a second click on the active one re-runs it), then
 * the view is framed; every button says what it does on hover.
 */
const GraphCanvasToolbar = ({
  full,
  offset,
  visibleCount,
  layout,
  lassoOn,
  canShowHidden,
  canUndo,
  onZoomIn,
  onZoomOut,
  onFit,
  onRelayout,
  onToggleLasso,
  onShowHidden,
  onUndo,
  onExportPng,
  onCopyLink,
  onListEntities,
  onShortcuts,
}: GraphCanvasToolbarProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const iconAction = (label: string, icon: ReactNode, onClick: () => void, disabled = false, active = false) => (
    <Tooltip>
      <TooltipTrigger asChild>
        <span>
          <IconButton priority="tertiary" icon={icon} aria-label={label} onClick={onClick} disabled={disabled} active={active} aria-pressed={active || undefined} />
        </span>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
  // A theme colour, not a class: the design system ships no divider background utility.
  const separator = <span aria-hidden className="mx-1 h-4 w-px" style={{ background: theme.palette.divider }} />;
  return (
    <div
      data-testid="graph-canvas-toolbar"
      role="toolbar"
      aria-label={t('Graph layout')}
      className="absolute flex items-center gap-1 rounded px-2"
      style={{
        bottom: 12,
        right: 12 + offset,
        zIndex: 4,
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.divider}`,
      }}
    >
      <Text variant="content-caption" className="px-1 text-default-secondary">{`${t('Entities')}: ${visibleCount}`}</Text>
      {separator}
      {iconAction(t('Zoom in'), <ZoomInOutlined fontSize="small" />, onZoomIn)}
      {iconAction(t('Zoom out'), <ZoomOutOutlined fontSize="small" />, onZoomOut)}
      {iconAction(t('Fit to screen'), <FitScreenOutlined fontSize="small" />, onFit)}
      {full && (
        <>
          {separator}
          {(['force', 'hierarchical', 'radial'] as GraphLayout[]).map(value => (
            <span key={value}>{iconAction(t(LAYOUT_LABEL[value]), LAYOUT_ICON[value], () => onRelayout(value), false, layout === value)}</span>
          ))}
          {iconAction(t('Re-run the layout'), <AutorenewOutlined fontSize="small" />, () => onRelayout())}
          {separator}
          {iconAction(t(LASSO_LABEL), <GestureOutlined fontSize="small" />, onToggleLasso, false, lassoOn)}
          {iconAction(t('Show hidden nodes'), <VisibilityOutlined fontSize="small" />, onShowHidden, !canShowHidden)}
          {iconAction(t('Undo'), <UndoOutlined fontSize="small" />, onUndo, !canUndo)}
          {separator}
          {iconAction(t('Export as PNG'), <DownloadOutlined fontSize="small" />, onExportPng)}
          {iconAction(t('Copy a link to this view'), <LinkOutlined fontSize="small" />, onCopyLink)}
          {iconAction(t('List the entities'), <ListAltOutlined fontSize="small" />, onListEntities)}
          {iconAction(t('Keyboard shortcuts'), <KeyboardOutlined fontSize="small" />, onShortcuts)}
        </>
      )}
    </div>
  );
};

export default GraphCanvasToolbar;

import { Text, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { ExpandLessOutlined, ExpandMoreOutlined } from '@mui/icons-material';
import { useTheme } from '@mui/material/styles';

import { useFormatter } from '../../../components/i18n';
import NODE_ICON from './graph-icons';
import { ALL_NODE_TYPES, ALL_RESULTS, type GraphNodeType, type GraphResult, NODE_TYPE_LABEL, RESULT_LABEL } from './graph-model';

interface GraphLegendProps {
  /** The visible nodes per type; a type absent from the map has no line. */
  typeCounts: Map<GraphNodeType, number>;
  /** The types the Entities filter shows. */
  shownTypes: GraphNodeType[];
  /** Show or hide one type (the Entities filter, in sync). */
  onToggleType: (type: GraphNodeType) => void;
  open: boolean;
  onToggleOpen: () => void;
}

/**
 * The legend, as OpenCTI's GraphLegend: a card floating in the canvas (bottom left), one line per
 * entity type present with its count (a click shows or hides the type), then the result colours;
 * collapsed by its header.
 */
const GraphLegend = ({ typeCounts, shownTypes, onToggleType, open, onToggleOpen }: GraphLegendProps) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const resultColour: Record<GraphResult, string> = {
    FAILED: theme.palette.error.main,
    PENDING: theme.palette.warning.main,
    SUCCESS: theme.palette.success.main,
  };
  return (
    <div
      data-testid="graph-legend"
      className="absolute flex flex-col rounded"
      style={{
        left: 12,
        bottom: 12,
        zIndex: 4,
        maxWidth: 240,
        maxHeight: 'calc(100% - 24px)',
        overflowY: 'auto',
        background: theme.palette.background.paper,
        border: `1px solid ${theme.palette.divider}`,
      }}
    >
      <button
        type="button"
        aria-expanded={open}
        onClick={onToggleOpen}
        className="inline-flex items-center justify-between gap-2 px-2 py-1 hover:bg-elevation-hover"
        style={{
          border: 0,
          background: 'transparent',
          color: 'inherit',
        }}
      >
        <Text variant="content-caption">{t('Legend')}</Text>
        {open ? <ExpandMoreOutlined fontSize="small" /> : <ExpandLessOutlined fontSize="small" />}
      </button>
      {open && (
        <div className="flex flex-col items-start gap-1 px-1 pb-1" role="group" aria-label={t('Legend')}>
          {ALL_NODE_TYPES.filter(type => typeCounts.has(type)).map((type) => {
            const Icon = NODE_ICON[type];
            const on = shownTypes.includes(type);
            return (
              <Tooltip key={type}>
                <TooltipTrigger asChild>
                  <button
                    type="button"
                    aria-pressed={on}
                    onClick={() => onToggleType(type)}
                    className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 hover:bg-elevation-hover"
                    style={{
                      opacity: on ? 1 : 0.4,
                      textDecoration: on ? 'none' : 'line-through',
                      border: 0,
                      background: 'transparent',
                      color: 'inherit',
                    }}
                  >
                    <Icon />
                    <Text variant="content-caption">{`${t(NODE_TYPE_LABEL[type])} (${typeCounts.get(type)})`}</Text>
                  </button>
                </TooltipTrigger>
                <TooltipContent>{on ? t('Hide this type') : t('Show this type')}</TooltipContent>
              </Tooltip>
            );
          })}
          <span aria-hidden className="my-1 h-px w-full" style={{ background: theme.palette.divider }} />
          {ALL_RESULTS.map(result => (
            <span key={result} className="inline-flex items-center gap-1 px-1">
              <span
                aria-hidden
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: '50%',
                  border: `2px solid ${resultColour[result]}`,
                }}
              />
              <Text variant="content-caption">{t(RESULT_LABEL[result])}</Text>
            </span>
          ))}
          <span className="inline-flex items-center gap-1 px-1">
            <span
              aria-hidden
              style={{
                width: 10,
                height: 10,
                borderRadius: '50%',
                border: `2px dashed ${theme.palette.text.secondary}`,
              }}
            />
            <Text variant="content-caption">{t('Group of neighbours, double-click to open')}</Text>
          </span>
        </div>
      )}
    </div>
  );
};

export default GraphLegend;

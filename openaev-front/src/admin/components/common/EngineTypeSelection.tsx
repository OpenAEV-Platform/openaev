import { OpenInNew } from '@mui/icons-material';
import { Card, CardActionArea, CardContent, Link, Radio, Stack, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../components/i18n';
import useEnterpriseEdition from '../../../utils/hooks/useEnterpriseEdition';
import EEChip from './entreprise_edition/EEChip';

const DIAGRAM_WIDTH = 176;
const DIAGRAM_HEIGHT = 60;

/**
 * Chaining engine illustration: a crisp, theme-aware outlined diagram of an
 * automated branching flow (start node -> trigger gate -> parallel next steps).
 * Drawn inline as SVG so it scales sharply and follows the palette / theme mode.
 */
const ChainingDiagram: FunctionComponent = () => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  const muted = theme.palette.text.disabled;
  const nodeFill = alpha(accent, 0.14);
  return (
    <svg
      width={DIAGRAM_WIDTH}
      height={DIAGRAM_HEIGHT}
      viewBox={`0 0 ${DIAGRAM_WIDTH} ${DIAGRAM_HEIGHT}`}
      fill="none"
      role="img"
      aria-label="Chaining flow diagram"
      style={{ filter: `drop-shadow(0 1px 3px ${alpha(accent, 0.3)})` }}
    >
      <defs>
        <marker id="chaining-arrow" markerWidth="6" markerHeight="6" refX="4.2" refY="3" orient="auto">
          <path d="M0.5,0.6 L4.6,3 L0.5,5.4" fill="none" stroke={accent} strokeWidth="1.2" strokeLinecap="round" strokeLinejoin="round" />
        </marker>
      </defs>
      {/* Connectors: start -> gate, then gate branches to the two next steps. */}
      <path d="M37,30 H68" stroke={accent} strokeWidth="1.5" markerEnd="url(#chaining-arrow)" />
      <path d="M90,25 C110,17 120,13 136,13" stroke={accent} strokeWidth="1.5" markerEnd="url(#chaining-arrow)" />
      <path d="M90,35 C110,43 120,47 136,47" stroke={accent} strokeWidth="1.5" markerEnd="url(#chaining-arrow)" />
      {/* Start node + one idle (not-yet-triggered) node below it. */}
      <rect x="14" y="22" width="23" height="16" rx="4" stroke={accent} strokeWidth="1.6" fill={nodeFill} />
      <rect x="14" y="41" width="23" height="13" rx="4" stroke={muted} strokeWidth="1.4" strokeDasharray="3 2.5" fill="none" />
      {/* Trigger gate. */}
      <circle cx="80" cy="30" r="11" stroke={accent} strokeWidth="1.6" fill={alpha(accent, 0.2)} />
      <path d="M77,25.5 L84,30 L77,34.5 Z" fill={accent} />
      {/* Two parallel next steps. */}
      <rect x="137" y="5" width="24" height="16" rx="4" stroke={accent} strokeWidth="1.6" fill={nodeFill} />
      <rect x="137" y="39" width="24" height="16" rx="4" stroke={accent} strokeWidth="1.6" fill={nodeFill} />
    </svg>
  );
};

/**
 * Time-based engine illustration: outlined nodes evenly spaced above a dashed
 * timeline with tick marks, conveying execution at fixed scheduled intervals.
 */
const TimeBasedDiagram: FunctionComponent = () => {
  const theme = useTheme();
  const accent = theme.palette.secondary.main;
  const line = theme.palette.text.disabled;
  const nodeFill = alpha(accent, 0.14);
  const xs = [30, 88, 146];
  return (
    <svg
      width={DIAGRAM_WIDTH}
      height={DIAGRAM_HEIGHT}
      viewBox={`0 0 ${DIAGRAM_WIDTH} ${DIAGRAM_HEIGHT}`}
      fill="none"
      role="img"
      aria-label="Time-based schedule diagram"
      style={{ filter: `drop-shadow(0 1px 3px ${alpha(accent, 0.25)})` }}
    >
      {/* Timeline. */}
      <line x1="12" y1="45" x2="164" y2="45" stroke={line} strokeWidth="1.3" strokeDasharray="3 3" strokeLinecap="round" />
      {xs.map(x => (
        <g key={x}>
          {/* Tick + connector from the node down to the timeline. */}
          <line x1={x} y1="30" x2={x} y2="41" stroke={accent} strokeWidth="1.3" strokeDasharray="2 2" />
          <circle cx={x} cy="45" r="2.4" fill={accent} />
          {/* Scheduled step node. */}
          <rect x={x - 12} y="11" width="24" height="18" rx="4" stroke={accent} strokeWidth="1.6" fill={nodeFill} />
          <line x1={x - 5} y1="17" x2={x + 5} y2="17" stroke={accent} strokeWidth="1.3" strokeLinecap="round" />
          <line x1={x - 5} y1="21" x2={x + 2} y2="21" stroke={accent} strokeWidth="1.3" strokeLinecap="round" />
        </g>
      ))}
    </svg>
  );
};

export type EngineType = 'chaining' | 'time-based' | null;

interface EngineTypeSelectionProps {
  selected: EngineType;
  onSelect: (type: EngineType) => void;
  context?: 'scenario' | 'simulation';
}

const EngineTypeSelection: FunctionComponent<EngineTypeSelectionProps> = ({
  selected,
  onSelect,
  context = 'scenario',
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const {
    isValidated: isEnterpriseEdition,
    openDialog: openEnterpriseEditionDialog,
    setEEFeatureDetectedInfo,
  } = useEnterpriseEdition();

  const isSimulation = context === 'simulation';

  const options: Array<{
    type: NonNullable<EngineType>;
    title: string;
    description: string;
  }> = [
    {
      type: 'chaining',
      title: t(isSimulation ? 'chaining.chaining-simulation.title' : 'chaining.chaining-scenario.title'),
      description: t('chaining.chaining-scenario.description'),
    },
    {
      type: 'time-based',
      title: t(isSimulation ? 'chaining.chaining-timebased-simulation.title' : 'chaining.chaining-timebased.title'),
      description: t('chaining.chaining-timebased.description'),
    },
  ];

  const handleCardClick = (type: NonNullable<EngineType>) => {
    if (type === 'chaining' && !isEnterpriseEdition) {
      setEEFeatureDetectedInfo(t(isSimulation ? 'chaining.chaining-simulation.title' : 'chaining.chaining-scenario.title'));
      openEnterpriseEditionDialog();
      return;
    }
    onSelect(type);
  };

  return (
    <>
      <Stack sx={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: theme.spacing(2),
      }}
      >
        <Typography variant="body2" color="text.secondary">
          {t(isSimulation ? 'chaining.select-type-simulation' : 'chaining.select-type')}
        </Typography>
        <Link
          href="https://docs.openaev.io/latest/usage/chaining/"
          target="_blank"
          rel="noopener noreferrer"
          variant="body2"
          sx={{
            color: theme.palette.primary.main,
            display: 'flex',
            alignItems: 'center',
            gap: theme.spacing(0.5),
          }}
        >
          <OpenInNew sx={{ fontSize: 14 }} />
          {isSimulation ? t('chaining.doc-link-simulation') : t('chaining.doc-link')}
        </Link>
      </Stack>
      <Stack
        sx={{
          display: 'grid',
          gap: theme.spacing(2),
          gridTemplateColumns: 'repeat(2, 1fr)',
        }}
      >
        {options.map((option) => {
          const isChaining = option.type === 'chaining';
          const isSelected = selected === option.type;
          const isDisabled = isChaining && !isEnterpriseEdition;
          return (
            <Card
              key={option.type}
              variant="outlined"
              sx={{
                'borderColor': isSelected ? theme.palette.primary.main : undefined,
                'borderWidth': isSelected ? 2 : 1,
                'opacity': isDisabled ? 0.6 : 1,
                'transition': 'border-color 0.2s, opacity 0.2s',
                '&:hover': { borderColor: theme.palette.primary.main },
              }}
            >
              <CardActionArea
                onClick={() => handleCardClick(option.type)}
                sx={{
                  height: '100%',
                  padding: theme.spacing(2),
                }}
              >
                <CardContent sx={{
                  'display': 'flex',
                  'flexDirection': 'column',
                  'alignItems': 'center',
                  'gap': theme.spacing(1),
                  'padding': 0,
                  '&:last-child': { paddingBottom: 0 },
                }}
                >
                  <Stack sx={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: theme.spacing(0.5),
                  }}
                  >
                    <Radio
                      checked={isSelected}
                      size="small"
                      disabled={isDisabled}
                      sx={{
                        padding: 0,
                        color: theme.palette.primary.main,
                      }}
                    />
                    <Typography variant="subtitle2" sx={{ fontWeight: 'bold' }}>
                      {option.title}
                    </Typography>
                    {isChaining && !isEnterpriseEdition && <EEChip clickable />}
                  </Stack>
                  <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'center' }}>
                    {option.description}
                  </Typography>
                  {/* Illustrative workflow diagram (crisp inline SVG). */}
                  <Stack sx={{
                    marginTop: theme.spacing(1),
                    alignItems: 'center',
                    justifyContent: 'center',
                  }}
                  >
                    {isChaining ? <ChainingDiagram /> : <TimeBasedDiagram />}
                  </Stack>
                </CardContent>
              </CardActionArea>
            </Card>
          );
        })}
      </Stack>
    </>
  );
};

export default EngineTypeSelection;

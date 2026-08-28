import { alpha, type Theme, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type KeyboardEvent, memo, useId, useRef } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';
import useSvgVisibilityPause from '../../../../../../../utils/hooks/useSvgVisibilityPause';
import { compactNumber } from '../../../../../../../utils/number';
import { expectationTypeColor } from '../../../../../common/ExpectationIconByType';

export interface DefenseLayer {
  key: string;
  success: number;
  failed: number;
  /** Attempted but not yet scored: counted by the adversary, by no gate. */
  pending: number;
}

interface Props {
  layers: DefenseLayer[];
  breached: number;
  onInvestigate: (typeKey: string) => void;
}

const W = 680;
const H = 240;
const CY = 104;
const ADVERSARY_X = 52;
const ASSETS_X = 624;
const GATES_START = 158;
const GATES_END = 528;
const GATE_HALF = 52;

// Shared vertical rhythm: every node (adversary, gates, assets) puts its
// caption, value, gauge and percentage on the exact same baselines, so nothing
// is staggered and every row reads as one aligned band.
const CAPTION_Y = CY - GATE_HALF - 14;
const VALUE_Y = CY + GATE_HALF + 22;
const GAUGE_Y = CY + GATE_HALF + 32;
const PCT_Y = CY + GATE_HALF + 52;

// Known expectation types get their shared categorical color (a cool spectrum
// that deliberately avoids green / orange / red, which are reserved for result
// semantics). Unknown / future dynamic types rotate through a cool fallback
// palette, so the gauntlet scales to any layer set without ever borrowing a
// result color.
const FALLBACK_COLORS = ['#38bdf8', '#818cf8', '#c084fc', '#22d3ee', '#a855f7'];
const KNOWN_TYPES = new Set([
  'PREVENTION',
  'DETECTION',
  'VULNERABILITY',
  'HUMAN_RESPONSE',
  'MANUAL',
  'ARTICLE',
  'CHALLENGE',
]);

const KNOWN_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Manual',
  CHALLENGE: 'Challenge',
  ARTICLE: 'Article',
};

// Outcome vocabulary per expectation type (covers every EXPECTATION_TYPE of the
// backend): "stopped" is only accurate for prevention, so each gate states what
// a success actually means for its pillar. Human-graded pillars (manual,
// challenge, article) are "validated". Unknown / future dynamic types fall back
// to the generic "stopped".
const OUTCOME_LABELS: Record<string, string> = {
  PREVENTION: 'prevented',
  DETECTION: 'detected',
  VULNERABILITY: 'not vulnerable',
  MANUAL: 'validated',
  CHALLENGE: 'validated',
  ARTICLE: 'validated',
};

const humanize = (key: string) => {
  const clean = key.replace(/[_-]+/g, ' ').toLowerCase();
  return clean.charAt(0).toUpperCase() + clean.slice(1);
};

// Success-rate accent (green good -> red bad), used by the per-gate gauge so
// its color always reflects performance rather than the pillar's brand color.
const rateAccent = (theme: Theme, rate: number): string => {
  if (rate >= 0.75) return theme.palette.success.main;
  if (rate >= 0.5) return theme.palette.warning.main;
  if (rate >= 0.25) return '#ff7043';
  return theme.palette.error.main;
};

// Truncate a label to what fits its column at a FIXED font size (ellipsis),
// instead of letting SVG condense glyph spacing to fit (textLength) - the
// squeezing is what made adjacent gate labels render at visibly different
// "font sizes". The full text stays available through a <title> tooltip.
const truncateLabel = (text: string, fontSize: number, maxWidth: number): string => {
  const maxChars = Math.max(3, Math.floor(maxWidth / (fontSize * 0.62)));
  return text.length > maxChars ? `${text.slice(0, maxChars - 1).trimEnd()}\u2026` : text;
};

/**
 * The defense gauntlet - a dynamic kill-chain where each expectation type found
 * in the data becomes a thin vertical defense gate between the adversary and
 * the assets. Gates auto-space to any number of layers, absorb attack particles
 * in proportion to real outcomes, and carry an aligned readout below the beam:
 * success count, a success-rate gauge colored by performance, and the rate.
 * Whatever slips through every gate reaches the assets.
 */
const AttackFlow: FunctionComponent<Props> = ({ layers, breached, onInvestigate }) => {
  const theme = useTheme();
  const { t } = useFormatter();
  const particleGlowId = useId();
  // Freeze the SMIL timeline while the tab is hidden so the browser never has
  // to reconcile minutes of missed sub-second animation loops on refocus.
  const svgRef = useRef<SVGSVGElement>(null);
  useSvgVisibilityPause(svgRef);

  const dark = theme.palette.mode === 'dark';
  const lineColor = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.10)';
  const trackColor = alpha(theme.palette.text.primary, 0.12);
  const attackColor = theme.palette.error.main;
  const assetsColor = theme.palette.text.secondary;

  // The adversary fired everything, including what no gate has scored yet, so the
  // node counts pending too. Gates and the breach node stay on resolved outcomes:
  // the readout below therefore does not sum back to the adversary while a run is
  // in flight, which is why the tooltip spells the split out.
  const totalAttacks = layers.reduce((acc, l) => acc + l.success + l.failed + l.pending, 0);
  const totalPending = layers.reduce((acc, l) => acc + l.pending, 0);
  const total = Math.max(totalAttacks, 1);

  // Only draw gates for expectation types that actually have activity. A pillar
  // with zero validations (no success, failure or pending) carries no signal, so
  // rendering an empty "0 / 0%" gate just wastes horizontal space - drop it and
  // let the remaining gates re-space evenly across the beam. (Totals, the score
  // and the breach node are computed upstream from the full set, so hiding an
  // all-zero gate here never changes any number.)
  const visibleLayers = layers.filter(l => l.success + l.failed + l.pending > 0);

  const n = Math.max(visibleLayers.length, 1);
  const gateX = (i: number) => GATES_START + ((GATES_END - GATES_START) * (i + 0.5)) / n;
  // Width of one gate column; labels are truncated to it so adjacent gate
  // labels (e.g. VULNERABILITY next to PREVENTION) can never overlap.
  const columnWidth = (GATES_END - GATES_START) / n;
  const labelMaxWidth = Math.max(columnWidth - 10, 24);
  const gaugeWidth = Math.max(Math.min(columnWidth - 24, 56), 32);
  const beamPath = `M ${ADVERSARY_X + 26} ${CY} L ${ASSETS_X - 26} ${CY}`;
  const pathTo = (endX: number) => `M ${ADVERSARY_X + 26} ${CY} L ${endX} ${CY}`;

  // Keyboard-accessible click-through props for the invisible SVG hit areas.
  const investigateProps = (label: string, action: () => void) => ({
    'role': 'button',
    'tabIndex': 0,
    'aria-label': label,
    'style': { cursor: 'pointer' },
    'onClick': action,
    'onKeyDown': (event: KeyboardEvent<SVGRectElement>) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        action();
      }
    },
  });

  const particlesFor = (value: number, max = 3) => {
    if (value <= 0) return 0;
    return Math.max(1, Math.min(max, Math.round((value / total) * 8)));
  };

  const particle = (path: string, dur: number, begin: string, opacity = 1) => (
    <>
      <circle r={3.2} fill={attackColor} opacity={0}>
        <animateMotion dur={`${dur}s`} begin={begin} repeatCount="indefinite" path={path} keyPoints="0;1" keyTimes="0;1" calcMode="linear" />
        <animate attributeName="opacity" values={`0;${opacity};${opacity};0`} keyTimes="0;0.08;0.9;1" dur={`${dur}s`} begin={begin} repeatCount="indefinite" />
      </circle>
      {/* soft trail glow: radial gradient fill, NOT a blur() filter - filters on elements
          animated by animateMotion are re-evaluated every frame and drag the whole SVG down */}
      <circle r={6.5} fill={`url(#${particleGlowId})`} opacity={0}>
        <animateMotion dur={`${dur}s`} begin={begin} repeatCount="indefinite" path={path} keyPoints="0;1" keyTimes="0;1" calcMode="linear" />
        <animate attributeName="opacity" values={`0;${opacity * 0.5};${opacity * 0.5};0`} keyTimes="0;0.08;0.9;1" dur={`${dur}s`} begin={begin} repeatCount="indefinite" />
      </circle>
    </>
  );

  const impactRing = (cx: number, color: string, dur: number, begin: string) => (
    <circle cx={cx} cy={CY} r={10} fill="none" stroke={color} strokeWidth={1.5} opacity={0}>
      <animate attributeName="opacity" values="0;0.9;0" keyTimes="0;0.1;1" dur={`${dur}s`} begin={begin} repeatCount="indefinite" />
      <animate attributeName="r" values="10;24" keyTimes="0;1" dur={`${dur}s`} begin={begin} repeatCount="indefinite" />
    </circle>
  );

  const breachParticles = particlesFor(breached);

  return (
    <div style={{
      flex: 1,
      minHeight: 0,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    }}
    >
      <svg
        ref={svgRef}
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="xMidYMid meet"
        style={{
          width: '100%',
          maxHeight: '100%',
          overflow: 'visible',
        }}
        className="noDrag"
      >
        <defs>
          <radialGradient id={particleGlowId} cx="50%" cy="50%" r="50%">
            <stop offset="0%" stopColor={attackColor} stopOpacity={0.9} />
            <stop offset="60%" stopColor={attackColor} stopOpacity={0.35} />
            <stop offset="100%" stopColor={attackColor} stopOpacity={0} />
          </radialGradient>
        </defs>

        {/* main beam - the attack corridor */}
        <path d={beamPath} fill="none" stroke={lineColor} strokeWidth={1} strokeDasharray="3 5">
          <animate attributeName="stroke-dashoffset" values="8;0" dur="0.8s" repeatCount="indefinite" />
        </path>

        {/* DEFENSE GATES - one thin vertical gate per expectation type WITH activity */}
        {visibleLayers.map((layer, i) => {
          const x = gateX(i);
          const layerKey = layer.key.toUpperCase();
          const color = KNOWN_TYPES.has(layerKey)
            ? expectationTypeColor(layerKey)
            : FALLBACK_COLORS[i % FALLBACK_COLORS.length];
          const resolved = layer.success + layer.failed;
          const rate = resolved > 0 ? layer.success / resolved : 0;
          const gaugeColor = resolved > 0 ? rateAccent(theme, rate) : theme.palette.text.disabled;
          const trackTop = CY - GATE_HALF;
          const trackH = GATE_HALF * 2;
          const fillH = Math.max(rate * trackH, resolved > 0 ? 3 : 0);
          const label = KNOWN_LABELS[layer.key.toUpperCase()]
            ? t(KNOWN_LABELS[layer.key.toUpperCase()])
            : humanize(layer.key);
          const outcomeText = `${Math.round(rate * 100)}% ${t(OUTCOME_LABELS[layer.key.toUpperCase()] ?? 'stopped')}`;
          const stopped = particlesFor(layer.success);
          const gaugeX = x - gaugeWidth / 2;
          const gaugeFillW = resolved > 0 ? Math.max(gaugeWidth * rate, rate > 0 ? 4 : 0) : 0;
          return (
            <g key={layer.key}>
              {/* thin gate: neutral visible track + stop-rate fill (bottom-up). The halo is a
                  wider translucent rect (not a drop-shadow filter, which the surrounding SMIL
                  animations would force the browser to re-evaluate every frame). */}
              <rect x={x - 1.5} y={trackTop} width={3} height={trackH} rx={1.5} fill={trackColor} />
              <rect x={x - 3.5} y={trackTop + trackH - fillH} width={7} height={fillH} rx={3.5} fill={alpha(color, 0.3)}>
                <animate attributeName="height" values={`0;${fillH}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
                <animate attributeName="y" values={`${trackTop + trackH};${trackTop + trackH - fillH}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
              </rect>
              <rect x={x - 1.5} y={trackTop + trackH - fillH} width={3} height={fillH} rx={1.5} fill={color}>
                <animate attributeName="height" values={`0;${fillH}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
                <animate attributeName="y" values={`${trackTop + trackH};${trackTop + trackH - fillH}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
              </rect>
              {/* beam crossing node */}
              <circle cx={x} cy={CY} r={5} fill={dark ? '#0c1526' : '#f4f8fc'} stroke={color} strokeWidth={1.5} />
              <circle cx={x} cy={CY} r={2} fill={color}>
                <animate attributeName="r" values="2;3;2" dur={`${2.4 + (i % 3) * 0.4}s`} repeatCount="indefinite" />
              </circle>
              {/* caption above (fixed font size, truncated to the column, full text on hover) */}
              <text
                x={x}
                y={CAPTION_Y}
                textAnchor="middle"
                fill={theme.palette.text.secondary}
                style={{
                  fontSize: 9.5,
                  letterSpacing: '0.1em',
                  textTransform: 'uppercase',
                }}
              >
                <title>{label}</title>
                {truncateLabel(label, 9.5, labelMaxWidth)}
              </text>
              {/* readout below: count, success-rate gauge, percentage - shared baselines */}
              <text
                x={x}
                y={VALUE_Y}
                textAnchor="middle"
                fill={color}
                style={{
                  fontFamily: '"Geologica", sans-serif',
                  fontSize: 17,
                  fontWeight: 600,
                }}
              >
                {/* compact readout (67.6K); the exact count stays on hover */}
                <title>{layer.success.toLocaleString()}</title>
                {compactNumber(layer.success)}
              </text>
              <g>
                <title>{outcomeText}</title>
                <rect x={gaugeX} y={GAUGE_Y} width={gaugeWidth} height={5} rx={2.5} fill={trackColor} />
                <rect x={gaugeX} y={GAUGE_Y} width={gaugeFillW} height={5} rx={2.5} fill={gaugeColor}>
                  <animate attributeName="width" values={`0;${gaugeFillW}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
                </rect>
                <text
                  x={x}
                  y={PCT_Y}
                  textAnchor="middle"
                  fill={gaugeColor}
                  style={{
                    fontSize: 10,
                    fontWeight: 600,
                  }}
                >
                  {`${Math.round(rate * 100)}%`}
                </text>
              </g>
              {/* absorbed particles + impact ring, density mirrors real outcomes */}
              {Array.from({ length: stopped }, (_, k) => (
                <g key={k}>
                  {particle(pathTo(x - 6), 2.2 + i * 0.4 + k * 0.6, `${i * 0.7 + k * 1.1}s`)}
                </g>
              ))}
              {stopped > 0 && impactRing(x, color, 2.6 + i * 0.3, `${1.1 + i * 0.7}s`)}
              {/* click-through to investigate this expectation type */}
              <rect
                x={x - columnWidth / 2}
                y={CAPTION_Y - 12}
                width={columnWidth}
                height={PCT_Y - CAPTION_Y + 24}
                fill="transparent"
                {...investigateProps(label, () => onInvestigate(layer.key))}
              />
            </g>
          );
        })}

        {/* breach particles - attacks that slip through every gate */}
        {Array.from({ length: breachParticles }, (_, i) => (
          <g key={`x${i}`}>
            {particle(pathTo(ASSETS_X - 26), 4.4 + i * 0.8, `${1.8 + i * 1.9}s`, 0.9)}
          </g>
        ))}
        {breachParticles > 0 && impactRing(ASSETS_X, attackColor, 4.4, '6.1s')}

        {/* ADVERSARY */}
        <g>
          <text
            x={ADVERSARY_X}
            y={CAPTION_Y}
            textAnchor="middle"
            fill={theme.palette.text.secondary}
            style={{
              fontSize: 9.5,
              letterSpacing: '0.1em',
              textTransform: 'uppercase',
            }}
          >
            {t('Adversary')}
          </text>
          <circle cx={ADVERSARY_X} cy={CY} r={22} fill={`${attackColor}14`} stroke={attackColor} strokeWidth={1.5} />
          <circle cx={ADVERSARY_X} cy={CY} r={22} fill="none" stroke={attackColor} strokeWidth={1} opacity={0.6}>
            <animate attributeName="r" values="22;33" dur="2s" repeatCount="indefinite" />
            <animate attributeName="opacity" values="0.6;0" dur="2s" repeatCount="indefinite" />
          </circle>
          <circle cx={ADVERSARY_X} cy={CY} r={9} fill="none" stroke={attackColor} strokeWidth={1.5} />
          <line x1={ADVERSARY_X - 14} y1={CY} x2={ADVERSARY_X - 5} y2={CY} stroke={attackColor} strokeWidth={1.5} />
          <line x1={ADVERSARY_X + 5} y1={CY} x2={ADVERSARY_X + 14} y2={CY} stroke={attackColor} strokeWidth={1.5} />
          <line x1={ADVERSARY_X} y1={CY - 14} x2={ADVERSARY_X} y2={CY - 5} stroke={attackColor} strokeWidth={1.5} />
          <line x1={ADVERSARY_X} y1={CY + 5} x2={ADVERSARY_X} y2={CY + 14} stroke={attackColor} strokeWidth={1.5} />
          <circle cx={ADVERSARY_X} cy={CY} r={2} fill={attackColor} />
          <text
            x={ADVERSARY_X}
            y={VALUE_Y}
            textAnchor="middle"
            fill={attackColor}
            style={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 17,
              fontWeight: 600,
            }}
          >
            <title>
              {totalPending > 0
                ? `${totalAttacks.toLocaleString()} - ${t('Pending')}: ${totalPending.toLocaleString()}`
                : totalAttacks.toLocaleString()}
            </title>
            {compactNumber(totalAttacks)}
          </text>
          {/* click-through: every attempted validation */}
          <rect
            x={ADVERSARY_X - 34}
            y={CAPTION_Y - 12}
            width={68}
            height={PCT_Y - CAPTION_Y + 24}
            fill="transparent"
            {...investigateProps(t('Adversary'), () => onInvestigate('all'))}
          />
        </g>

        {/* ASSETS (breach) */}
        {(() => {
          const idleFill = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)';
          const assetsFill = breached > 0 ? `${attackColor}10` : idleFill;
          const assetsStroke = breached > 0 ? attackColor : assetsColor;
          return (
            <g>
              <text
                x={ASSETS_X}
                y={CAPTION_Y}
                textAnchor="middle"
                fill={theme.palette.text.secondary}
                style={{
                  fontSize: 9.5,
                  letterSpacing: '0.1em',
                  textTransform: 'uppercase',
                }}
              >
                {t('Breached')}
              </text>
              <circle cx={ASSETS_X} cy={CY} r={24} fill={assetsFill} stroke={assetsStroke} strokeWidth={1.3} />
              {[-8, -1, 6].map(dy => (
                <g key={dy}>
                  <rect x={ASSETS_X - 10} y={CY + dy} width={20} height={5.5} rx={1.5} fill="none" stroke={breached > 0 ? attackColor : assetsColor} strokeWidth={1.2} />
                  <circle cx={ASSETS_X - 6} cy={CY + dy + 2.75} r={0.9} fill={breached > 0 ? attackColor : theme.palette.secondary.main}>
                    <animate attributeName="opacity" values="1;0.2;1" dur={`${1.5 + (dy + 8) * 0.15}s`} repeatCount="indefinite" />
                  </circle>
                </g>
              ))}
              <text
                x={ASSETS_X}
                y={VALUE_Y}
                textAnchor="middle"
                fill={breached > 0 ? attackColor : assetsColor}
                style={{
                  fontFamily: '"Geologica", sans-serif',
                  fontSize: 17,
                  fontWeight: 600,
                }}
              >
                <title>{breached.toLocaleString()}</title>
                {compactNumber(breached)}
              </text>
              <rect
                x={ASSETS_X - 34}
                y={CAPTION_Y - 12}
                width={68}
                height={PCT_Y - CAPTION_Y + 24}
                fill="transparent"
                {...investigateProps(t('Breached'), () => onInvestigate('breach'))}
              />
            </g>
          );
        })()}
      </svg>
    </div>
  );
};

export default memo(AttackFlow);

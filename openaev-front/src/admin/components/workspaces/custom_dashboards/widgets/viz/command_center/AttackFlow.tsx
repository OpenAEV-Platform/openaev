import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, memo } from 'react';

import { useFormatter } from '../../../../../../../components/i18n';

export interface DefenseLayer {
  key: string;
  success: number;
  failed: number;
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

// Known expectation types get stable colors; unknown / future dynamic types
// rotate through the fallback palette, so the gauntlet scales to any layer set.
const TYPE_COLORS: Record<string, string> = {
  PREVENTION: '#0fbcff',
  DETECTION: '#00f1bd',
  VULNERABILITY: '#ffa726',
  MANUAL: '#9575cd',
};
const FALLBACK_COLORS = ['#26a96c', '#ff7043', '#7e57c2', '#00bcd4', '#ffb300'];

const KNOWN_LABELS: Record<string, string> = {
  PREVENTION: 'Prevention',
  DETECTION: 'Detection',
  VULNERABILITY: 'Vulnerability',
  MANUAL: 'Manual',
};

const humanize = (key: string) => {
  const clean = key.replace(/[_-]+/g, ' ').toLowerCase();
  return clean.charAt(0).toUpperCase() + clean.slice(1);
};

/**
 * The defense gauntlet - a dynamic kill-chain where each expectation type found
 * in the data becomes a thin vertical defense gate between the adversary and
 * the assets. Gates auto-space to any number of layers (prevention, detection,
 * vulnerability, manual, and any future dynamic expectation types), each shows
 * its stop-rate as a filled level and absorbs attack particles in proportion
 * to real outcomes. Whatever slips through every gate reaches the assets.
 */
const AttackFlow: FunctionComponent<Props> = ({ layers, breached, onInvestigate }) => {
  const theme = useTheme();
  const { t } = useFormatter();

  const dark = theme.palette.mode === 'dark';
  const lineColor = dark ? 'rgba(255,255,255,0.10)' : 'rgba(0,0,0,0.10)';
  const attackColor = theme.palette.error.main;
  const assetsColor = theme.palette.text.secondary;

  const totalAttacks = layers.reduce((acc, l) => acc + l.success + l.failed, 0);
  const total = Math.max(totalAttacks, 1);

  const n = Math.max(layers.length, 1);
  const gateX = (i: number) => GATES_START + ((GATES_END - GATES_START) * (i + 0.5)) / n;
  const beamPath = `M ${ADVERSARY_X + 26} ${CY} L ${ASSETS_X - 26} ${CY}`;
  const pathTo = (endX: number) => `M ${ADVERSARY_X + 26} ${CY} L ${endX} ${CY}`;

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
      <circle r={6.5} fill={attackColor} opacity={0} style={{ filter: 'blur(4px)' }}>
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
        viewBox={`0 0 ${W} ${H}`}
        preserveAspectRatio="xMidYMid meet"
        style={{
          width: '100%',
          maxHeight: '100%',
          overflow: 'visible',
        }}
        className="noDrag"
      >
        {/* main beam - the attack corridor */}
        <path d={beamPath} fill="none" stroke={lineColor} strokeWidth={1} strokeDasharray="3 5">
          <animate attributeName="stroke-dashoffset" values="8;0" dur="0.8s" repeatCount="indefinite" />
        </path>

        {/* DEFENSE GATES - one thin vertical gate per expectation type */}
        {layers.map((layer, i) => {
          const x = gateX(i);
          const color = TYPE_COLORS[layer.key.toUpperCase()] ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length];
          const resolved = layer.success + layer.failed;
          const rate = resolved > 0 ? layer.success / resolved : 0;
          const trackTop = CY - GATE_HALF;
          const trackH = GATE_HALF * 2;
          const fillH = Math.max(rate * trackH, resolved > 0 ? 3 : 0);
          const label = KNOWN_LABELS[layer.key.toUpperCase()]
            ? t(KNOWN_LABELS[layer.key.toUpperCase()])
            : humanize(layer.key);
          const stopped = particlesFor(layer.success);
          return (
            <g key={layer.key}>
              {/* thin gate track + stop-rate fill (bottom-up) */}
              <rect x={x - 1.5} y={trackTop} width={3} height={trackH} rx={1.5} fill={`${color}26`} />
              <rect x={x - 1.5} y={trackTop + trackH - fillH} width={3} height={fillH} rx={1.5} fill={color} style={{ filter: `drop-shadow(0 0 4px ${color}99)` }}>
                <animate attributeName="height" values={`0;${fillH}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
                <animate attributeName="y" values={`${trackTop + trackH};${trackTop + trackH - fillH}`} dur="1.1s" fill="freeze" calcMode="spline" keySplines="0.22 1 0.36 1" />
              </rect>
              {/* beam crossing node */}
              <circle cx={x} cy={CY} r={5} fill={dark ? '#0c1526' : '#f4f8fc'} stroke={color} strokeWidth={1.5} />
              <circle cx={x} cy={CY} r={2} fill={color}>
                <animate attributeName="r" values="2;3;2" dur={`${2.4 + (i % 3) * 0.4}s`} repeatCount="indefinite" />
              </circle>
              {/* label above, outcome below */}
              <text
                x={x}
                y={trackTop - 14}
                textAnchor="middle"
                fill={theme.palette.text.secondary}
                style={{
                  fontSize: 9.5,
                  letterSpacing: '0.12em',
                  textTransform: 'uppercase',
                }}
              >
                {label}
              </text>
              <text
                x={x}
                y={CY + GATE_HALF + 22}
                textAnchor="middle"
                fill={color}
                style={{
                  fontFamily: '"Geologica", sans-serif',
                  fontSize: 17,
                  fontWeight: 600,
                }}
              >
                {layer.success}
              </text>
              <text
                x={x}
                y={CY + GATE_HALF + 38}
                textAnchor="middle"
                fill={theme.palette.text.secondary}
                style={{ fontSize: 9 }}
              >
                {`${Math.round(rate * 100)}% ${t('stopped')}`}
              </text>
              {/* absorbed particles + impact ring, density mirrors real outcomes */}
              {Array.from({ length: stopped }, (_, k) => (
                <g key={k}>
                  {particle(pathTo(x - 6), 2.2 + i * 0.4 + k * 0.6, `${i * 0.7 + k * 1.1}s`)}
                </g>
              ))}
              {stopped > 0 && impactRing(x, color, 2.6 + i * 0.3, `${1.1 + i * 0.7}s`)}
              {/* click-through to investigate this expectation type */}
              <rect
                x={x - 34}
                y={trackTop - 26}
                width={68}
                height={trackH + 70}
                fill="transparent"
                style={{ cursor: 'pointer' }}
                onClick={() => onInvestigate(layer.key)}
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
            y={CY + GATE_HALF + 4}
            textAnchor="middle"
            fill={theme.palette.text.secondary}
            style={{
              fontSize: 9.5,
              letterSpacing: '0.12em',
              textTransform: 'uppercase',
            }}
          >
            {t('Adversary')}
          </text>
          <text
            x={ADVERSARY_X}
            y={CY + GATE_HALF + 24}
            textAnchor="middle"
            fill={attackColor}
            style={{
              fontFamily: '"Geologica", sans-serif',
              fontSize: 17,
              fontWeight: 600,
            }}
          >
            {totalAttacks}
          </text>
        </g>

        {/* ASSETS (breach) */}
        {(() => {
          const idleFill = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)';
          const assetsFill = breached > 0 ? `${attackColor}10` : idleFill;
          const assetsStroke = breached > 0 ? attackColor : assetsColor;
          return (
            <g>
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
                y={CY + GATE_HALF + 4}
                textAnchor="middle"
                fill={theme.palette.text.secondary}
                style={{
                  fontSize: 9.5,
                  letterSpacing: '0.12em',
                  textTransform: 'uppercase',
                }}
              >
                {t('Breached')}
              </text>
              <text
                x={ASSETS_X}
                y={CY + GATE_HALF + 24}
                textAnchor="middle"
                fill={breached > 0 ? attackColor : assetsColor}
                style={{
                  fontFamily: '"Geologica", sans-serif',
                  fontSize: 17,
                  fontWeight: 600,
                }}
              >
                {breached}
              </text>
              <rect
                x={ASSETS_X - 34}
                y={CY - GATE_HALF}
                width={68}
                height={GATE_HALF * 2 + 40}
                fill="transparent"
                style={{ cursor: 'pointer' }}
                onClick={() => onInvestigate('breach')}
              />
            </g>
          );
        })()}
      </svg>
    </div>
  );
};

export default memo(AttackFlow);

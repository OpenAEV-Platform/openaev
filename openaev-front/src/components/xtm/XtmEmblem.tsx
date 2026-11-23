import { IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import { type CounterpartAnswer } from './counterpartStore';

// Contract v3 of the cross-product pivots (Sam, 22 Nov 2026), the same in OpenCTI, OpenAEV and
// OpenCRQ: one ICON-ONLY button per counterpart product, its emblem and no text, at the size of the
// sibling header actions (FDS IconButton md secondary, 36 px, glyph 20 px), ordered
// OpenCTI, OpenAEV, OpenCRQ (self skipped).
// - found: the emblem in the theme primary, a link to the counterpart; hover and focus open the rich preview;
// - connected and not found: the same emblem greyed (aria-disabled, still focusable), the tooltip
//   says it is not there and how it gets there;
// - not connected: the same emblem greyed, the tooltip is the teaser of what the product would show
//   (example skeletons, never figures); a configurer's emblem opens the connection guide;
// - unavailable: greyed, the tooltip says it comes back on its own;
// - loading: nothing yet (the slot fills once the answer is known).
// The greyed emblem is the product asset used as a CSS mask over currentColor, so the FDS disabled
// ink (text-icon-disabled) draws it: never a CSS filter on the logo.

// Two colours only, the same for every product and the same hex in OpenCTI, OpenAEV and OpenCRQ
// (Sam, 22 Nov 2026): found = the design system's highlight ink (--icon-highlight, the brand primary
// of the theme, a custom primary included), not found, not connected and unavailable = its disabled
// ink (--icon-disabled). No per-product colour and no full-colour artwork: every emblem is the
// design system's product-switcher glyph painted through a CSS mask.
export const EMBLEM_FOUND = 'var(--icon-highlight)';
export const EMBLEM_MUTED = 'var(--icon-disabled)';
/** The glyph inside the 36 px header button, the same in the three products. */
export const EMBLEM_GLYPH_SIZE = 20;

/** The emblem shape, painted in `color` (a theme palette path; currentColor by default). */
export const MaskedEmblem: FunctionComponent<{
  maskUrl: string;
  size?: number;
  color?: string;
}> = ({ maskUrl, size = EMBLEM_GLYPH_SIZE, color = 'currentColor' }) => (
  <Box
    component="span"
    aria-hidden
    data-testid="xtm-emblem-mask"
    sx={{
      display: 'inline-block',
      width: size,
      height: size,
      backgroundColor: color,
      maskImage: `url("${maskUrl}")`,
      maskSize: 'contain',
      maskRepeat: 'no-repeat',
      maskPosition: 'center',
      WebkitMaskImage: `url("${maskUrl}")`,
      WebkitMaskSize: 'contain',
      WebkitMaskRepeat: 'no-repeat',
      WebkitMaskPosition: 'center',
    }}
  />
);

/** The translated strings of one product's emblem, given by the caller as literal keys. */
export interface EmblemLabels {
  open: string;
  connect: string;
  notConnected: string;
  notFound: string;
  notFoundHint: string;
  unavailable: string;
  unavailableHint: string;
}

export interface CounterpartEmblemProps {
  answer: CounterpartAnswer;
  /** The URL of an emblem asset whose alpha draws the shape, for the greyed states. */
  maskUrl: string;
  labels: EmblemLabels;
  /** Found: the preview card of the counterpart. */
  preview: ReactNode;
  /** Not connected: the teaser of what the product would show. */
  teaser: ReactNode;
  /** Not connected: where a configurer connects the product; absent for a read-only user. */
  connectUrl?: string;
  testId: string;
}

/** The icon-only pivot of one counterpart product, in the state of its answer. */
const CounterpartEmblem: FunctionComponent<CounterpartEmblemProps> = ({
  answer,
  maskUrl,
  labels,
  preview,
  teaser,
  connectUrl,
  testId,
}) => {
  if (answer.state === 'loading') {
    return null;
  }
  const greyed = <MaskedEmblem maskUrl={maskUrl} color={EMBLEM_MUTED} />;
  if (answer.state === 'match') {
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton asChild icon={<MaskedEmblem maskUrl={maskUrl} color={EMBLEM_FOUND} />} aria-label={labels.open} priority="secondary" size="md">
            <a
              href={answer.counterpart?.counterpart_url}
              target="_blank"
              rel="noopener noreferrer"
              data-testid={`${testId}-found`}
            />
          </IconButton>
        </TooltipTrigger>
        <TooltipContent>{preview}</TooltipContent>
      </Tooltip>
    );
  }
  if (answer.state === 'not_connected' && connectUrl) {
    // A configurer: the greyed emblem is the way to connect the product.
    return (
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            asChild
            icon={greyed}
            aria-label={labels.connect}
            priority="secondary"
            size="md"
            className="text-icon-disabled"
          >
            <a href={connectUrl} target="_blank" rel="noopener noreferrer" data-testid={`${testId}-not-connected`} />
          </IconButton>
        </TooltipTrigger>
        <TooltipContent>{teaser}</TooltipContent>
      </Tooltip>
    );
  }
  let label = labels.unavailable;
  let content: ReactNode = labels.unavailableHint;
  let state = 'unavailable';
  if (answer.state === 'no_match') {
    state = 'not-found';
    label = labels.notFound;
    content = (
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          gap: 0.5,
          maxWidth: 288,
        }}
        data-testid="xtm-emblem-not-found"
      >
        <Box component="span" sx={{ fontWeight: 600 }}>{labels.notFound}</Box>
        <Box component="span" sx={{ opacity: 0.72 }}>{labels.notFoundHint}</Box>
      </Box>
    );
  } else if (answer.state === 'not_connected') {
    state = 'not-connected';
    label = labels.notConnected;
    content = teaser;
  }
  // aria-disabled keeps the tab stop and the pointer events, so the tooltip opens on focus and on
  // hover; the FDS IconButton ignores the activation and draws the disabled ink.
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <IconButton
          icon={greyed}
          aria-label={label}
          aria-disabled
          priority="secondary"
          size="md"
          data-testid={`${testId}-${state}`}
        />
      </TooltipTrigger>
      <TooltipContent>{content}</TooltipContent>
    </Tooltip>
  );
};

export default CounterpartEmblem;

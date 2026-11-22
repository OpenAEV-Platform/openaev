import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import octiDark from '../../static/images/xtm/octi_dark.png';
import useExplorationGraph from '../../utils/hooks/useExplorationGraph';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { OPENCTI_FACT_LABELS } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import { withSourceLink } from './sourcedCounterpart';
import useCounterpart, { OPENCTI_PRODUCT } from './useCounterpart';
import CounterpartEmblem, { EMBLEM_FOUND, EMBLEM_MUTED, MaskedEmblem } from './XtmEmblem';
import { PivotPreview, PivotTeaser } from './XtmPivotPreview';

// One visual language for every cross-platform pivot of OpenAEV (contract v3, Sam, 22 Nov 2026,
// the same in OpenCTI and OpenCRQ): an icon-only emblem per counterpart product in every hero and
// header (CounterpartEmblem: found, not found, not connected), and a 16 px mark on a list row only
// when the counterpart is found.

// The OpenCTI connection of a tenant is deployment configuration (openaev.xtm.opencti.{tenant}.*),
// so the emblem of a configurer opens its documentation page.
export const CONNECT_OPENCTI_DOC_URL = 'https://docs.openaev.io/latest/integrations/xtm-suite/xtm-suite-connector/';

/** The OpenCTI glyph in the theme primary, the one colour of every found emblem (Sam, 22 Nov 2026). */
export const ProductMark: FunctionComponent<{ size?: number; muted?: boolean }> = ({ size = 18, muted = false }) => (
  <MaskedEmblem maskUrl={octiDark} size={size} color={muted ? EMBLEM_MUTED : EMBLEM_FOUND} />
);

const useCanConfigure = () => {
  const ability = useAbility();
  return ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
};

/** Not connected: the preview of what OpenCTI would show here, and the next step for this user. */
const OpenCtiTeaser: FunctionComponent<{ canConfigure: boolean }> = ({ canConfigure }) => {
  const { t } = useFormatter();
  return (
    <PivotTeaser
      mark={<ProductMark muted />}
      productName="OpenCTI"
      labels={OPENCTI_FACT_LABELS}
      pitch={t('Connect OpenCTI to see the reports and intelligence on this object.')}
      next={canConfigure ? t('Open the connection guide to connect OpenCTI.') : t('Ask an administrator to connect OpenCTI to see this object there.')}
    />
  );
};

interface PivotProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
  /** The object's source link (an asset's external reference): found from it first. */
  sourceUrl?: string | null;
}

/** The preview of an OpenCTI match: what the click opens, with the facts OpenCTI answered. */
export const OpenCtiMatchPreview: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  return (
    <PivotPreview
      mark={<ProductMark />}
      productName="OpenCTI"
      entityType={answer.counterpart?.counterpart_entity_type}
      name={answer.counterpart?.counterpart_name}
      facts={answer.counterpart?.counterpart_facts}
      labels={OPENCTI_FACT_LABELS}
      action={t('See the reports, relationships and context of this object in OpenCTI.')}
    />
  );
};

/** Renders the answer once loaded as the icon-only emblem of contract v3; tests drive every state. */
export const OpenCtiPivotView: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  const canConfigure = useCanConfigure();
  return (
    <CounterpartEmblem
      answer={answer}
      maskUrl={octiDark}
      labels={{
        open: t('Open in OpenCTI'),
        connect: t('Connect OpenCTI'),
        notConnected: t('OpenCTI is not connected'),
        notFound: t('Not found in OpenCTI'),
        notFoundHint: t('OpenCTI holds it once a connector or an analyst creates it there.'),
        unavailable: t('OpenCTI is unavailable'),
        unavailableHint: t('OpenCTI did not answer this time. The pivot comes back on its own.'),
      }}
      preview={<OpenCtiMatchPreview answer={answer} />}
      teaser={<OpenCtiTeaser canConfigure={canConfigure} />}
      connectUrl={canConfigure ? CONNECT_OPENCTI_DOC_URL : undefined}
      testId="xtm-pivot"
    />
  );
};

/**
 * Hero action: the OpenCTI emblem, in the state of the counterpart. An object imported from OpenCTI
 * (its source link points into the connected OpenCTI) is found from that link first.
 */
const OpenCtiPivotLookup: FunctionComponent<PivotProps> = ({ kind, objectKey, sourceUrl }) => {
  const answer = useCounterpart(kind, objectKey, OPENCTI_PRODUCT);
  return <OpenCtiPivotView answer={withSourceLink(answer, sourceUrl)} />;
};

// Width of the mark slot a row keeps when its siblings may carry a mark, so the controls after it
// stay in one column whether this row matches or not.
export const ROW_MARK_SLOT_WIDTH = 24;

interface RowMarkProps extends PivotProps {
  label?: string;
  reserveSlot?: boolean;
}

/**
 * List-row mark (contract v3, rule 6): the 16 px product mark linking to the counterpart, only when
 * it is found; nothing on the row otherwise. The label names
 * the action ("Investigate in OpenCTI" on a collected alert), "Open in OpenCTI" by default.
 * With reserveSlot, a row keeps a slot of the mark's width while the answer loads or OpenCTI is
 * unavailable; a row without a counterpart, or of a tenant not connected, keeps nothing (Sam,
 * 22 Nov 2026: connected and not found shows nothing).
 */
const OpenCtiRowMarkLookup: FunctionComponent<RowMarkProps> = ({ kind, objectKey, sourceUrl, label = 'Open in OpenCTI', reserveSlot = false }) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const answer = withSourceLink(useCounterpart(kind, objectKey, OPENCTI_PRODUCT), sourceUrl);
  const slot = (content: ReactNode) => (
    <Box
      component="span"
      data-testid="xtm-row-mark-slot"
      sx={{
        display: 'inline-flex',
        justifyContent: 'center',
        alignItems: 'center',
        width: ROW_MARK_SLOT_WIDTH,
        flexShrink: 0,
      }}
    >
      {content}
    </Box>
  );
  if (answer.state !== 'match') {
    // Connected and not found, or not connected: nothing, not even an empty slot.
    return reserveSlot && (answer.state === 'loading' || answer.state === 'unavailable') ? slot(null) : null;
  }
  const mark = (
    <Tooltip>
      <TooltipTrigger asChild>
        <Box
          component="a"
          href={answer.counterpart?.counterpart_url}
          target="_blank"
          rel="noopener noreferrer"
          aria-label={t(label)}
          onClick={(e: MouseEvent) => e.stopPropagation()}
          data-testid="xtm-row-mark"
          sx={{
            'display': 'inline-flex',
            'borderRadius': '2px',
            // The OpenAEV global :focus reset removes the outline: a keyboard user still sees the mark.
            '&:focus-visible': {
              outline: `2px solid ${theme.palette.primary.main}`,
              outlineOffset: '2px',
            },
          }}
        >
          <ProductMark size={16} />
        </Box>
      </TooltipTrigger>
      <TooltipContent><OpenCtiMatchPreview answer={answer} /></TooltipContent>
    </Tooltip>
  );
  return reserveSlot ? slot(mark) : mark;
};

// The pivots belong to the exploration graph preview feature: off (the default), they draw nothing
// and never ask the backend (the lookup component, and its request, is not mounted).
export const OpenCtiRowMark: FunctionComponent<RowMarkProps> = props => (useExplorationGraph() ? <OpenCtiRowMarkLookup {...props} /> : null);
const OpenCtiPivot: FunctionComponent<PivotProps> = props => (useExplorationGraph() ? <OpenCtiPivotLookup {...props} /> : null);

export default OpenCtiPivot;

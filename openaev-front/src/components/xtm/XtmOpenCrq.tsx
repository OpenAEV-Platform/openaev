import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type MouseEvent, type ReactNode } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import ocrqMark from '../../static/images/xtm/ocrq.svg';
import useExplorationGraph from '../../utils/hooks/useExplorationGraph';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { FACT_LABELS } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import { withSourceLink } from './sourcedCounterpart';
import useCounterpart from './useCounterpart';
import CounterpartEmblem from './XtmEmblem';
import { CONNECT_OPENCTI_DOC_URL, ROW_MARK_SLOT_WIDTH } from './XtmPivot';
import { PivotPreview, PivotTeaser } from './XtmPivotPreview';

// The OpenCRQ side of the cross-platform pivots, contract v3 (Sam, 22 Nov 2026): the same
// icon-only emblem as the OpenCTI one (XtmPivot), the OpenCRQ mark, and the risk OpenCRQ quantifies
// for the object (the risk scenarios it drives, their annual loss expectancy and, for a CVE, the
// loss avoided by fixing it) in its preview.

export const OPENCRQ_PRODUCT = 'opencrq';
// The OpenCRQ connection of a tenant is deployment configuration (openaev.xtm.opencrq.{tenant}.*),
// documented on the same XTM integration page as the OpenCTI one.
export const CONNECT_OPENCRQ_DOC_URL = CONNECT_OPENCTI_DOC_URL;

export const OpenCrqMark: FunctionComponent<{ size?: number }> = ({ size = 18 }) => (
  <img src={ocrqMark} alt="" aria-hidden width={size} height={size} style={{ borderRadius: 2 }} />
);

/** Not connected: the preview of what OpenCRQ would show here, and the next step for this user. */
const OpenCrqTeaser: FunctionComponent<{ canConfigure: boolean }> = ({ canConfigure }) => {
  const { t } = useFormatter();
  return (
    <PivotTeaser
      mark={<OpenCrqMark />}
      productName="OpenCRQ"
      labels={FACT_LABELS}
      pitch={t('Connect OpenCRQ to see the financial impact of this object: its annual loss and the loss avoided by fixing it.')}
      next={canConfigure ? t('Open the connection guide to connect OpenCRQ.') : t('Ask an administrator to connect OpenCRQ to see this object there.')}
    />
  );
};

/** The preview of an OpenCRQ match: the financial impact the click opens. */
export const OpenCrqMatchPreview: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  return (
    <PivotPreview
      mark={<OpenCrqMark />}
      productName="OpenCRQ"
      entityType={answer.counterpart?.counterpart_entity_type}
      name={answer.counterpart?.counterpart_name}
      facts={answer.counterpart?.counterpart_facts}
      labels={FACT_LABELS}
      action={t('See the financial impact of this object and the risk scenarios it drives in OpenCRQ.')}
    />
  );
};

/** Renders one answer as the icon-only emblem of contract v3; tests drive every state. */
export const OpenCrqPivotView: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canConfigure = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  return (
    <CounterpartEmblem
      answer={answer}
      colour={<OpenCrqMark />}
      maskUrl={ocrqMark}
      labels={{
        open: t('Open in OpenCRQ'),
        connect: t('Connect OpenCRQ'),
        notConnected: t('OpenCRQ is not connected'),
        notFound: t('Not found in OpenCRQ'),
        notFoundHint: t('OpenCRQ holds it once a risk scenario or a supporting asset names it.'),
        unavailable: t('OpenCRQ is unavailable'),
        unavailableHint: t('OpenCRQ did not answer this time. The pivot comes back on its own.'),
      }}
      preview={<OpenCrqMatchPreview answer={answer} />}
      teaser={<OpenCrqTeaser canConfigure={canConfigure} />}
      connectUrl={canConfigure ? CONNECT_OPENCRQ_DOC_URL : undefined}
      testId="xtm-crq-pivot"
    />
  );
};

interface PivotProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
  /** The object's source link (an asset's external reference): found from it first. */
  sourceUrl?: string | null;
}

/** Hero action: the OpenCRQ emblem, in the state of the counterpart (source link first). */
const OpenCrqPivotLookup: FunctionComponent<PivotProps> = ({ kind, objectKey, sourceUrl }) => {
  const answer = useCounterpart(kind, objectKey, OPENCRQ_PRODUCT);
  return <OpenCrqPivotView answer={withSourceLink(answer, sourceUrl)} />;
};

interface RowMarkProps extends PivotProps {
  label?: string;
  reserveSlot?: boolean;
}

/**
 * List-row chip: the OpenCRQ mark linking to the counterpart, nothing otherwise (same contract as
 * OpenCtiRowMark). The tooltip names the action and the counterpart.
 * With reserveSlot, a row keeps a slot of the mark's width while the answer loads or OpenCRQ is
 * unavailable; a row without a counterpart, or of a tenant not connected, keeps nothing.
 */
const OpenCrqRowMarkLookup: FunctionComponent<RowMarkProps> = ({
  kind,
  objectKey,
  sourceUrl,
  label = 'Open in OpenCRQ',
  reserveSlot = false,
}) => {
  const { t } = useFormatter();
  const theme = useTheme();
  const answer = withSourceLink(useCounterpart(kind, objectKey, OPENCRQ_PRODUCT), sourceUrl);
  const slot = (content: ReactNode) => (
    <Box
      component="span"
      data-testid="xtm-crq-row-mark-slot"
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
          data-testid="xtm-crq-row-mark"
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
          <OpenCrqMark size={16} />
        </Box>
      </TooltipTrigger>
      <TooltipContent><OpenCrqMatchPreview answer={answer} /></TooltipContent>
    </Tooltip>
  );
  return reserveSlot ? slot(mark) : mark;
};

// The pivots belong to the exploration graph preview feature: off (the default), they draw nothing
// and never ask the backend (the lookup component, and its request, is not mounted).
export const OpenCrqRowMark: FunctionComponent<RowMarkProps> = props => (useExplorationGraph() ? <OpenCrqRowMarkLookup {...props} /> : null);
const OpenCrqPivot: FunctionComponent<PivotProps> = props => (useExplorationGraph() ? <OpenCrqPivotLookup {...props} /> : null);

export default OpenCrqPivot;

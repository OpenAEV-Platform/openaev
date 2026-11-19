import { Button, Chip, IconButton, Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { CloseOutlined, LinkOffOutlined } from '@mui/icons-material';
import { Box } from '@mui/material';
import { type FunctionComponent, useState } from 'react';

import { type CounterpartKindPath } from '../../actions/xtm/counterpart-actions';
import ocrqMark from '../../static/images/xtm/ocrq.svg';
import { type FactOutput } from '../../utils/api-types';
import { useAbility } from '../../utils/permissions/permissionsContext';
import { ACTIONS, SUBJECTS } from '../../utils/permissions/types';
import { useFormatter } from '../i18n';
import { FACT_LABELS, MONEY_FACTS, orderedFacts } from './counterpartFacts';
import { type CounterpartAnswer } from './counterpartStore';
import useCounterpart from './useCounterpart';
import { CONNECT_OPENCTI_DOC_URL } from './XtmPivot';

// The OpenCRQ side of the cross-platform pivots: same visual language and wording as the OpenCTI
// ones (XtmPivot), the OpenCRQ mark, and the risk OpenCRQ quantifies for the object (the risk
// scenarios it drives, their annual loss expectancy and, for a CVE, the loss avoided by fixing it).

export const OPENCRQ_PRODUCT = 'opencrq';
// The OpenCRQ connection of a tenant is deployment configuration (openaev.xtm.opencrq.{tenant}.*),
// documented on the same XTM integration page as the OpenCTI one.
export const CONNECT_OPENCRQ_DOC_URL = CONNECT_OPENCTI_DOC_URL;
const DISMISS_KEY = 'xtm-connect-opencrq-dismissed';

const isDismissed = () => {
  try {
    return window.localStorage.getItem(DISMISS_KEY) === 'true';
  } catch {
    return false;
  }
};

export const OpenCrqMark: FunctionComponent<{ size?: number }> = ({ size = 18 }) => (
  <img src={ocrqMark} alt="" aria-hidden width={size} height={size} style={{ borderRadius: 2 }} />
);

/** The "Connect OpenCRQ" call to action, shown where a pivot would be when not connected. */
export const ConnectOpenCrqCta: FunctionComponent = () => {
  const { t } = useFormatter();
  const ability = useAbility();
  const canConfigure = ability.can(ACTIONS.MANAGE, SUBJECTS.TENANT_SETTINGS);
  const [dismissed, setDismissed] = useState(isDismissed);
  if (dismissed) {
    return null;
  }
  if (!canConfigure) {
    return (
      <Box
        component="span"
        sx={{
          typography: 'caption',
          color: 'text.secondary',
        }}
        data-testid="xtm-crq-connect-hint"
      >
        {t('Ask an administrator to connect OpenCRQ to see this object there.')}
      </Box>
    );
  }
  const dismiss = () => {
    try {
      window.localStorage.setItem(DISMISS_KEY, 'true');
    } catch {
      // Storage refused: dismissed for this view only.
    }
    setDismissed(true);
  };
  return (
    <Box
      sx={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 0.5,
      }}
      data-testid="xtm-crq-connect-cta"
    >
      <Button asChild priority="tertiary" size="sm">
        <a href={CONNECT_OPENCRQ_DOC_URL} target="_blank" rel="noopener noreferrer">
          <OpenCrqMark />
          {t('Connect OpenCRQ')}
        </a>
      </Button>
      <Tooltip>
        <TooltipTrigger asChild>
          <IconButton
            icon={<CloseOutlined fontSize="small" />}
            onClick={dismiss}
            aria-label={t('Hide this suggestion')}
            priority="tertiary"
            size="sm"
          />
        </TooltipTrigger>
        <TooltipContent>{t('Hide this suggestion')}</TooltipContent>
      </Tooltip>
    </Box>
  );
};

/** Renders one answer; kept separate so tests drive every state. */
export const OpenCrqPivotView: FunctionComponent<{
  answer: CounterpartAnswer;
  size?: 'sm' | 'md';
}> = ({ answer, size = 'sm' }) => {
  const { t } = useFormatter();
  switch (answer.state) {
    case 'match':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Button asChild priority="secondary" size={size}>
              <a href={answer.counterpart?.counterpart_url} target="_blank" rel="noopener noreferrer" data-testid="xtm-crq-pivot-open">
                <OpenCrqMark />
                {t('Open in OpenCRQ')}
              </a>
            </Button>
          </TooltipTrigger>
          <TooltipContent>{answer.counterpart?.counterpart_name}</TooltipContent>
        </Tooltip>
      );
    case 'not_connected':
      return <ConnectOpenCrqCta />;
    case 'no_match':
      return (
        <span data-testid="xtm-crq-pivot-none">
          <Chip label={t('Not in OpenCRQ')} />
        </span>
      );
    case 'unavailable':
      return (
        <Tooltip>
          <TooltipTrigger asChild>
            <Box component="span" sx={{ display: 'inline-flex' }} data-testid="xtm-crq-pivot-unavailable">
              <LinkOffOutlined fontSize="small" color="disabled" />
            </Box>
          </TooltipTrigger>
          <TooltipContent>{t('OpenCRQ is unavailable')}</TooltipContent>
        </Tooltip>
      );
    default:
      return null;
  }
};

/** The OpenCRQ facts of a counterpart: risk scenarios driven, annual loss, loss avoided by fixing. */
export const OpenCrqFacts: FunctionComponent<{ facts: FactOutput[] | undefined }> = ({ facts }) => {
  const { t, n } = useFormatter();
  const shown = orderedFacts(facts);
  if (shown.length === 0) {
    return null;
  }
  return (
    <Box
      component="dl"
      sx={{
        display: 'grid',
        gridTemplateColumns: 'max-content 1fr',
        columnGap: 2,
        rowGap: 0.5,
        margin: 0,
        typography: 'body2',
      }}
      data-testid="xtm-crq-facts"
    >
      {shown.map((fact) => {
        const value = Number(fact.fact_value);
        // Amounts are whole units of the OpenCRQ tenant's currency, shown compact (125K).
        const display = MONEY_FACTS.has(fact.fact_key ?? '') && Number.isFinite(value) ? n(value) : fact.fact_value;
        return (
          <Box key={fact.fact_key} sx={{ display: 'contents' }}>
            <Box component="dt" sx={{ color: 'text.secondary' }}>{t(FACT_LABELS[fact.fact_key ?? ''])}</Box>
            <Box component="dd" sx={{ margin: 0 }}>{display}</Box>
          </Box>
        );
      })}
    </Box>
  );
};

interface PivotProps {
  kind: CounterpartKindPath;
  objectKey: string | null | undefined;
}

/** Hero action: "Open in OpenCRQ", "Not in OpenCRQ" or "Connect OpenCRQ". */
const OpenCrqPivot: FunctionComponent<PivotProps & { size?: 'sm' | 'md' }> = ({ kind, objectKey, size }) => {
  const answer = useCounterpart(kind, objectKey, OPENCRQ_PRODUCT);
  return <OpenCrqPivotView answer={answer} size={size} />;
};

/** Overview card: "In OpenCRQ" with the risk OpenCRQ quantifies for the object, or the CTA. */
export const OpenCrqCardView: FunctionComponent<{ answer: CounterpartAnswer }> = ({ answer }) => {
  const { t } = useFormatter();
  if (answer.state === 'loading') {
    return null;
  }
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
      }}
      data-testid="xtm-crq-card"
    >
      {answer.state === 'match' && (
        <>
          <Box sx={{
            display: 'flex',
            alignItems: 'center',
            gap: 1,
          }}
          >
            <OpenCrqMark />
            <Box
              component="span"
              sx={{
                typography: 'body2',
                fontWeight: 600,
              }}
            >
              {answer.counterpart?.counterpart_name}
            </Box>
            {answer.counterpart?.counterpart_entity_type && <Chip label={answer.counterpart.counterpart_entity_type} />}
          </Box>
          <OpenCrqFacts facts={answer.counterpart?.counterpart_facts} />
        </>
      )}
      <OpenCrqPivotView answer={answer} />
      {answer.state === 'no_match' && (
        <Box
          component="span"
          sx={{
            typography: 'caption',
            color: 'text.secondary',
          }}
        >
          {t('This object is not known in the connected OpenCRQ.')}
        </Box>
      )}
    </Box>
  );
};

export const OpenCrqCard: FunctionComponent<PivotProps> = ({ kind, objectKey }) => {
  const answer = useCounterpart(kind, objectKey, OPENCRQ_PRODUCT);
  return <OpenCrqCardView answer={answer} />;
};

export default OpenCrqPivot;

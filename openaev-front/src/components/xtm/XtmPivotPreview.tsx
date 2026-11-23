import { Chip, Skeleton } from '@filigran/design-system';
import { Box } from '@mui/material';
import { type FunctionComponent, type ReactNode } from 'react';

import { type FactOutput } from '../../utils/api-types';
import { useFormatter } from '../i18n';
import { DATE_FACTS, MONEY_FACTS, orderedFacts } from './counterpartFacts';

// The preview card every cross-platform pivot of OpenAEV opens on hover and on keyboard focus: what
// the click brings. The design system has no hover card yet, so it is the content of the FDS
// Tooltip (300 px wide), laid out as a card: the product mark and the object's type, its name, the
// key facts the other product answered, and one line saying what can be done there. Same contract
// in OpenCTI and OpenCRQ.

interface FactListProps {
  facts: FactOutput[] | undefined;
  labels: Record<string, string>;
}

/** The facts answered for these labels, as a two-column list; nothing when none was answered. */
export const PivotFactList: FunctionComponent<FactListProps> = ({ facts, labels }) => {
  const { t, n, fsd } = useFormatter();
  const shown = orderedFacts(facts, labels);
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
      }}
      data-testid="xtm-preview-facts"
    >
      {shown.map((fact) => {
        const key = fact.fact_key ?? '';
        const value = Number(fact.fact_value);
        let display: ReactNode = fact.fact_value;
        if (MONEY_FACTS.has(key) && Number.isFinite(value)) {
          display = n(value);
        } else if (DATE_FACTS.has(key)) {
          display = fsd(fact.fact_value);
        }
        return (
          <Box key={key} sx={{ display: 'contents' }}>
            <Box component="dt" sx={{ opacity: 0.72 }}>{t(labels[key])}</Box>
            <Box
              component="dd"
              sx={{
                margin: 0,
                fontWeight: 600,
              }}
            >
              {display}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
};

interface PreviewProps {
  mark: ReactNode;
  productName: string;
  /** The object's type in the other product (Attack-Pattern, Risk scenario...). */
  entityType?: string;
  name?: string;
  facts?: FactOutput[];
  labels: Record<string, string>;
  /** What the user can do there, one line ("See the financial impact of this object"). */
  action: string;
}

/** Match: the counterpart, its facts and what the click opens. */
export const PivotPreview: FunctionComponent<PreviewProps> = ({ mark, productName, entityType, name, facts, labels, action }) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1,
      minWidth: 220,
    }}
    data-testid="xtm-preview"
  >
    <Box sx={{
      display: 'flex',
      alignItems: 'center',
      gap: 1,
    }}
    >
      {mark}
      <Box component="span" sx={{ fontWeight: 600 }}>{productName}</Box>
      {entityType && <Chip label={entityType} />}
    </Box>
    {name && <Box component="span" sx={{ fontWeight: 600 }}>{name}</Box>}
    <PivotFactList facts={facts} labels={labels} />
    <Box component="span" sx={{ opacity: 0.72 }}>{action}</Box>
  </Box>
);

interface TeaserProps {
  mark: ReactNode;
  productName: string;
  /** The fact labels the connected product would answer, shown with illustrative skeleton values. */
  labels: Record<string, string>;
  /** Why connecting is worth it ("Connect OpenCRQ to see the annual loss of this object"). */
  pitch: string;
  /** The next step for this user: the settings link for a configurer, ask an administrator otherwise. */
  next: string;
}

// The example value of a teaser fact: an FDS Skeleton the size of a short figure. It stands for a
// value the user would see once connected, not for content loading, so no SkeletonGroup announces
// it; the example list is aria-hidden and the pitch below carries the meaning.
const EXAMPLE_VALUE = {
  width: 56,
  height: 12,
} as const;

/**
 * Not connected: what the other product would show here, with skeleton values labelled as an
 * example (never figures that could read as real), the pitch and the next step.
 */
export const PivotTeaser: FunctionComponent<TeaserProps> = ({ mark, productName, labels, pitch, next }) => {
  const { t } = useFormatter();
  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        minWidth: 220,
      }}
      data-testid="xtm-teaser"
    >
      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1,
      }}
      >
        {mark}
        <Box component="span" sx={{ fontWeight: 600 }}>{productName}</Box>
        <Chip label={t('Example')} />
      </Box>
      <Box
        component="dl"
        aria-hidden
        sx={{
          display: 'grid',
          gridTemplateColumns: 'max-content 1fr',
          columnGap: 2,
          rowGap: 0.5,
          margin: 0,
          alignItems: 'center',
        }}
      >
        {Object.entries(labels).map(([key, label]) => (
          <Box key={key} sx={{ display: 'contents' }}>
            <Box component="dt" sx={{ opacity: 0.72 }}>{t(label)}</Box>
            <Box component="dd" sx={{ margin: 0 }}><Skeleton style={EXAMPLE_VALUE} /></Box>
          </Box>
        ))}
      </Box>
      <Box component="span">{pitch}</Box>
      <Box component="span" sx={{ opacity: 0.72 }}>{next}</Box>
    </Box>
  );
};

import { Text } from '@filigran/design-system';
import { KeyboardArrowDown } from '@mui/icons-material';
// fds:keep-mui the library has no Accordion yet (ROADMAP "Accordion: todo"; designed in the DS Figma, page Accordion 2495:15312): MUI renders it, styled to that design
import { Accordion, AccordionDetails, AccordionSummary } from '@mui/material';
import { type CSSProperties, type FunctionComponent, type ReactNode } from 'react';

interface Props {
  /** The item's title (Figma "Accordion title", Title/xs). */
  title: ReactNode;
  /** Optional 16px icon before the title. */
  icon?: ReactNode;
  /** Short secondary information right after the title (a count, chips). */
  meta?: ReactNode;
  /** Information pushed to the right, before the chevron (who, when, an action). */
  end?: ReactNode;
  /** What opens under the title. Without it the item is a plain row with the same look and no chevron. */
  children?: ReactNode;
  defaultExpanded?: boolean;
  /** Controlled state, with onExpandedChange. */
  expanded?: boolean;
  onExpandedChange?: (expanded: boolean) => void;
  /** Gap above the content (default 8px, the Figma spacing-2). */
  contentStyle?: CSSProperties;
  /**
   * 'heading' (default): the Figma title style, Title/xs (Geologica), for sections.
   * 'list': the body font at medium weight, for dense lists (versions, history entries), so a row
   * never shouts louder than the drawer or page title above it.
   */
  density?: 'heading' | 'list';
}

// The DS Figma Accordion: a 36px title row (Title/xs, optional icon, chevron on the right pointing down
// when closed and up when open), the content under it, a 1px line under each item. No card, no fill.
const ROW_STYLE: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  minWidth: 0,
  width: '100%',
};

const ITEM_SX = {
  'background': 'transparent',
  'backgroundImage': 'none',
  'borderBottom': '1px solid var(--border-elevation-default)',
  '&::before': { display: 'none' },
} as const;

const titleRow = (title: ReactNode, icon?: ReactNode, meta?: ReactNode, end?: ReactNode, density: 'heading' | 'list' = 'heading') => (
  <div style={ROW_STYLE}>
    {icon && (
      <span
        aria-hidden
        style={{
          display: 'inline-flex',
          width: 16,
          height: 16,
          color: 'var(--icon-default)',
        }}
      >
        {icon}
      </span>
    )}
    <Text variant={density === 'list' ? 'content-base-medium' : 'title-xs'} style={{ flexShrink: 0 }}>{title}</Text>
    {meta && (
      <span style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 8,
        minWidth: 0,
        overflow: 'hidden',
      }}
      >
        {meta}
      </span>
    )}
    {end && (
      <span
        style={{
          marginLeft: 'auto',
          display: 'inline-flex',
          alignItems: 'center',
          gap: 8,
          flexShrink: 0,
        }}
        // An action at the end (e.g. Install) acts on its own, without opening the item.
        onClick={event => event.stopPropagation()}
      >
        {end}
      </span>
    )}
  </div>
);

/**
 * The accordion of the design system Figma, used the same way everywhere (Versions, History, dialogs,
 * review groups) until the library ships its own.
 */
const FdsAccordion: FunctionComponent<Props> = ({
  title,
  icon,
  meta,
  end,
  children,
  defaultExpanded,
  expanded,
  onExpandedChange,
  contentStyle,
  density = 'heading',
}) => {
  if (!children) {
    return (
      <div style={{
        minHeight: 36,
        padding: '8px 24px 8px 0',
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        borderBottom: '1px solid var(--border-elevation-default)',
      }}
      >
        {titleRow(title, icon, meta, end, density)}
      </div>
    );
  }
  return (
    <Accordion
      disableGutters
      square
      elevation={0}
      defaultExpanded={defaultExpanded}
      expanded={expanded}
      onChange={onExpandedChange ? (_, next) => onExpandedChange(next) : undefined}
      slotProps={{ transition: { unmountOnExit: true } }}
      sx={ITEM_SX}
    >
      <AccordionSummary
        expandIcon={<KeyboardArrowDown sx={{ fontSize: 16 }} style={{ color: 'var(--icon-default)' }} />}
        sx={{
          'minHeight': 36,
          'padding': 0,
          '& .MuiAccordionSummary-content': {
            margin: '8px 0',
            minWidth: 0,
          },
          '& .MuiAccordionSummary-expandIconWrapper': { marginLeft: 1 },
        }}
      >
        {titleRow(title, icon, meta, end, density)}
      </AccordionSummary>
      <AccordionDetails sx={{ padding: '8px 0 16px' }} style={contentStyle}>
        {children}
      </AccordionDetails>
    </Accordion>
  );
};

export default FdsAccordion;

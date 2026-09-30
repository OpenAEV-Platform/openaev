import { makeStyles } from 'tss-react/mui';

// Shared vertical-alignment rules for the payload "arguments" and "prerequisites"
// rows (Command Line payload form). The row layout itself (grid vs flex and the
// column template) is left to each consumer via inline styles; this hook only
// carries the alignment concerns those rows have in common.
const useArgumentRowStyles = makeStyles()(theme => ({
  // Apply on the row container (which sets its own `display`/columns inline).
  alignedRow: {
    'gap': theme.spacing(1),
    // Top-align the columns so that an inline validation error, which renders
    // *below* a field, grows downward instead of pushing the control up and
    // breaking the row alignment.
    'alignItems': 'start',
    // The design-system Input label has no line-height utility and inherits it,
    // whereas the Select label pins `leading-normal` (150%). Normalizing the
    // Input labels to the same 150% keeps every label the same height, so the
    // controls below them share a single top baseline under top alignment.
    '& label': { lineHeight: 1.5 },
    // The Input's required asterisk is rendered one size up (`text-3` = 14px)
    // with no line-height, so in the `items-start` label row it grows a ~21px
    // line box and pushes the whole Input label row (and thus its control) below
    // the Select's 18px label row — making the Select look "too high". Capping
    // the asterisk's line box keeps the Input label row at the label height.
    '& label + span': { lineHeight: 1.2 },
  },
  // The trash button carries no label, so under top alignment it needs a hidden
  // spacer the height of a field label (+ the label→control gap) to drop it onto
  // the same row as the inputs.
  deleteCell: {
    display: 'flex',
    flexDirection: 'column',
    gap: theme.spacing(1),
    flex: '0 0 auto',
  },
  deleteLabelSpacer: {
    fontSize: 12,
    lineHeight: 1.5,
    visibility: 'hidden',
  },
  deleteControl: {
    // Match the design-system control height (h-9 = 36px) and center the icon.
    height: theme.spacing(4.5),
    display: 'flex',
    alignItems: 'center',
  },
}));

export default useArgumentRowStyles;


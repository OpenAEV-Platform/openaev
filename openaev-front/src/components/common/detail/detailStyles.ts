import { type SxProps, type Theme } from '@mui/material';

// Shared section-subtitle style. Kept in a component-free module so importing
// it does not trip react-refresh/only-export-components on the components that
// consume it. Section titles that need a custom layout (e.g. a title row with
// inline actions, like the chaining scope boxes) can adopt the exact same look
// without the plain SectionLabel wrapper.
// eslint-disable-next-line import/prefer-default-export -- a named token reads clearer than a default at every call site
export const SECTION_LABEL_SX: SxProps<Theme> = {
  fontFamily: '"Geologica", sans-serif',
  fontWeight: 600,
  fontSize: 11,
  letterSpacing: '0.12em',
  textTransform: 'uppercase',
  color: 'text.secondary',
  marginBottom: 1.5,
};

import { CheckOutlined } from '@mui/icons-material';
import { alpha, useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent } from 'react';

interface Props {
  checked: boolean;
  style?: CSSProperties;
}

// Purely visual 16px checkbox box, with no hover halo of its own: the clickable row or card
// around it carries the checkbox role and the hover feedback (see FacetRowItem).
const CheckboxIndicator: FunctionComponent<Props> = ({ checked, style }) => {
  const theme = useTheme();
  return (
    <span
      aria-hidden
      style={{
        width: 16,
        height: 16,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: 2,
        border: `1px solid ${checked ? theme.palette.primary.main : theme.palette.divider}`,
        backgroundColor: checked ? theme.palette.primary.main : 'transparent',
        boxShadow: checked ? `0 0 6px ${alpha(theme.palette.primary.main, 0.5)}` : 'none',
        transition: 'all 0.15s ease',
        ...style,
      }}
    >
      {checked && (
        <CheckOutlined sx={{
          fontSize: 12,
          color: theme.palette.primary.contrastText,
        }}
        />
      )}
    </span>
  );
};

export default CheckboxIndicator;

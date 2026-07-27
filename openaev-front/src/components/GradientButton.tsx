import { Button, type ButtonProps } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type React from 'react';

type GradientButtonProps = ButtonProps & {
  component?: React.ElementType;
  href?: string;
  target?: string;
  rel?: string;
};

/**
 * Design-system CTA button aligned with the OpenCTI gradient button: transparent
 * background, 2px gradient border (primary -> gradient) drawn with the
 * padding-box / border-box trick, gradient-clipped text and a soft glow on hover.
 */
const GradientButton: React.FC<GradientButtonProps> = ({ children, sx, ...props }) => {
  const theme = useTheme();
  const start = theme.palette.primary.main;
  const end = theme.palette.gradient.main;
  const paper = theme.palette.background.paper;
  const gradient = `linear-gradient(90deg, ${start} 0%, ${end} 100%)`;
  const surface = `linear-gradient(${paper}, ${paper}) padding-box, ${gradient} border-box`;

  return (
    <Button
      {...props}
      sx={[
        {
          'textTransform': 'none',
          'fontWeight': theme.typography.fontWeightBold,
          'border': '2px solid transparent',
          'background': surface,
          'boxShadow': 'none',
          'transition': 'box-shadow 0.3s ease-out',
          '&:hover': {
            background: surface,
            boxShadow: `1px 0 6px -1px ${start}, -1px 0 6px -1px ${end}`,
          },
          '&:active': {
            background: surface,
            boxShadow: `1px 2px 8px -1px ${start}, -1px 2px 8px -1px ${end}`,
          },
          '&.Mui-disabled': { opacity: 0.4 },
          '& .gradient-button-content': {
            background: gradient,
            backgroundClip: 'text',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
            color: 'transparent',
            display: 'inline-flex',
            alignItems: 'center',
            gap: theme.spacing(1),
          },
          '& .gradient-button-content svg': {
            fill: start,
            color: start,
          },
        },
        ...(Array.isArray(sx) ? sx : [sx]),
      ]}
    >
      <span className="gradient-button-content">{children}</span>
    </Button>
  );
};

export default GradientButton;

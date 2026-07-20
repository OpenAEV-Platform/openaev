import { KeyboardArrowRight } from '@mui/icons-material';
import { Box, ButtonBase, Collapse } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode, useCallback, useState } from 'react';

interface Props {
  header: ReactNode;
  children: ReactNode;
  /** When true, the section starts expanded (it stays collapsible). */
  forceExpanded?: boolean;
}

// A clean, self-contained collapsible card: a full-width header row with a
// hover state and a chevron that rotates on expand, over a smoothly animated
// body. Replaces the previous bare, unstyled toggle button.
const ExpandableSection: FunctionComponent<Props> = ({ header, children, forceExpanded = false }) => {
  const theme = useTheme();
  const [isExpanded, setIsExpanded] = useState(forceExpanded);

  const handleToggle = useCallback(() => {
    setIsExpanded(prev => !prev);
  }, []);

  return (
    <Box
      sx={{
        'border': `1px solid ${alpha(theme.palette.text.primary, 0.08)}`,
        'borderRadius': 1,
        'overflow': 'hidden',
        'marginBottom': 1,
        'backgroundColor': alpha(theme.palette.background.paper, 0.4),
        'transition': theme.transitions.create(['border-color']),
        '&:hover': { borderColor: alpha(theme.palette.primary.main, 0.3) },
      }}
    >
      <ButtonBase
        onClick={handleToggle}
        aria-expanded={isExpanded}
        sx={{
          'width': '100%',
          'display': 'flex',
          'alignItems': 'center',
          'gap': 1,
          'justifyContent': 'flex-start',
          'textAlign': 'left',
          'paddingBlock': 1,
          'paddingInline': 1.5,
          'transition': theme.transitions.create(['background-color']),
          '&:hover': { backgroundColor: theme.palette.action.hover },
        }}
      >
        <KeyboardArrowRight
          fontSize="small"
          sx={{
            color: 'text.secondary',
            flexShrink: 0,
            transition: theme.transitions.create('transform'),
            transform: isExpanded ? 'rotate(90deg)' : 'none',
          }}
        />
        <Box sx={{
          flex: 1,
          minWidth: 0,
        }}
        >
          {header}
        </Box>
      </ButtonBase>
      <Collapse in={isExpanded} unmountOnExit>
        <Box sx={{
          paddingBlock: 1,
          paddingInline: 0.5,
          borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
        }}
        >
          {children}
        </Box>
      </Collapse>
    </Box>
  );
};

export default ExpandableSection;

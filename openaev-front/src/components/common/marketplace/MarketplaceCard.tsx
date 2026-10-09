import { Tooltip, TooltipContent, TooltipTrigger } from '@filigran/design-system';
import { Box, Card, CardActionArea, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type FunctionComponent, type ReactNode } from 'react';
import { Link } from 'react-router';

import PlatformIcon from '../../PlatformIcon';

interface Props {
  accent: string;
  icon: ReactNode;
  title: string;
  chips: ReactNode;
  platforms: string[];
  caption: ReactNode;
  footerRight?: ReactNode;
  badge?: ReactNode;
  topLeft?: ReactNode;
  topLeftVisible?: boolean;
  topRight?: ReactNode;
  selected?: boolean;
  onClick?: () => void;
  /** Makes the card body a real link (keeps open-in-new-tab and copy link). */
  linkTo?: string;
  testId?: string;
}

// Marketplace card: accent banner with an icon medallion, title, chips, platforms and a footer.
const MarketplaceCard: FunctionComponent<Props> = ({
  accent,
  icon,
  title,
  chips,
  platforms,
  caption,
  footerRight,
  badge,
  topLeft,
  topLeftVisible = false,
  topRight,
  selected = false,
  onClick,
  linkTo,
  testId,
}) => {
  const theme = useTheme();

  const body = (
    <>
      <Tooltip>
        <TooltipTrigger asChild>
          <Typography
            sx={{
              fontSize: 13.5,
              fontWeight: 600,
              lineHeight: 1.35,
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              wordBreak: 'break-word',
              minHeight: 36,
            }}
          >
            {title}
          </Typography>
        </TooltipTrigger>
        {title && <TooltipContent>{title}</TooltipContent>}
      </Tooltip>

      <Box sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 0.5,
        flexWrap: 'wrap',
        minHeight: 22,
      }}
      >
        {chips}
      </Box>

      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          marginTop: 'auto',
          paddingTop: 1,
          minHeight: 28,
          borderTop: `1px solid ${alpha(theme.palette.text.primary, 0.05)}`,
        }}
      >
        <Box sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 0.5,
          flexShrink: 0,
        }}
        >
          {platforms.slice(0, 4).map(platform => (
            <PlatformIcon
              key={platform}
              width={16}
              platform={platform}
              tooltip
            />
          ))}
          {platforms.length > 4 && (
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              +
              {platforms.length - 4}
            </Typography>
          )}
        </Box>
        <Box sx={{
          flex: 1,
          display: 'flex',
          justifyContent: 'flex-end',
          overflow: 'hidden',
        }}
        >
          {footerRight}
        </Box>
      </Box>

      <Typography
        variant="caption"
        sx={{
          color: 'text.disabled',
          fontSize: 11,
          marginTop: 0.25,
        }}
      >
        {caption}
      </Typography>
    </>
  );

  const bodySx = {
    'flex': 1,
    'paddingTop': 4,
    'paddingInline': 2,
    'paddingBottom': 1.5,
    'display': 'flex',
    'flexDirection': 'column',
    'gap': 1,
    'alignItems': 'stretch',
    'justifyContent': 'flex-start',
    '& .MuiCardActionArea-focusHighlight': { background: 'transparent' },
  };

  return (
    <Card
      variant="outlined"
      data-testid={testId}
      sx={{
        'position': 'relative',
        'display': 'flex',
        'flexDirection': 'column',
        'height': '100%',
        'borderRadius': 1,
        'overflow': 'hidden',
        'borderColor': selected ? accent : theme.palette.divider,
        'backgroundColor': selected
          ? alpha(accent, 0.06)
          : theme.palette.background.paper,
        'transition': theme.transitions.create(
          ['border-color', 'box-shadow', 'transform'],
          { duration: theme.transitions.duration.shorter },
        ),
        // Signature marketplace hover (same as the Integrations cards), tinted
        // with the domain accent instead of the primary color.
        '&:hover': {
          borderColor: alpha(accent, 0.3),
          boxShadow: `0 0 30px ${alpha(accent, 0.12)}`,
          transform: 'translateY(-2px)',
        },
        '&:hover .threat-card-checkbox': { opacity: 1 },
      }}
    >
      <Box
        aria-hidden
        sx={{
          height: 64,
          background: `linear-gradient(135deg, ${alpha(accent, 0.22)} 0%, ${alpha(accent, 0.04)} 100%)`,
          borderBottom: `1px solid ${alpha(accent, 0.18)}`,
          position: 'relative',
        }}
      >
        <Box
          sx={{
            position: 'absolute',
            left: 16,
            bottom: -22,
            width: 44,
            height: 44,
            borderRadius: 1.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: theme.palette.background.paper,
            border: `1px solid ${alpha(accent, 0.4)}`,
            boxShadow: `0 4px 12px -4px ${alpha(accent, 0.4)}`,
          }}
        >
          {icon}
        </Box>
        {badge}
      </Box>

      {topLeft && (
        <Box
          className="threat-card-checkbox"
          sx={{
            'position': 'absolute',
            'top': 10,
            'left': 10,
            'zIndex': 2,
            'opacity': topLeftVisible ? 1 : 0,
            'transition': theme.transitions.create(['opacity', 'transform']),
            '& .MuiCheckbox-root': {
              'padding': 0.5,
              'color': alpha('#fff', 0.85),
              '&.Mui-checked': { color: theme.palette.primary.main },
              '&:hover': { backgroundColor: alpha('#fff', 0.08) },
            },
            '& .MuiSvgIcon-root': {
              fontSize: 22,
              filter: `drop-shadow(0 1px 2px ${alpha('#000', 0.5)})`,
            },
          }}
        >
          {topLeft}
        </Box>
      )}

      {topRight && (
        <Box
          sx={{
            'position': 'absolute',
            'top': 6,
            'right': 6,
            'zIndex': 2,
            '& .MuiIconButton-root': {
              'color': alpha('#fff', 0.85),
              'padding': 0.75,
              'transition': theme.transitions.create('background-color'),
              '&:hover': { backgroundColor: alpha('#fff', 0.08) },
            },
            '& .MuiSvgIcon-root': {
              fontSize: 20,
              filter: `drop-shadow(0 1px 2px ${alpha('#000', 0.5)})`,
            },
          }}
          onClick={event => event.stopPropagation()}
        >
          {topRight}
        </Box>
      )}

      {linkTo && <CardActionArea component={Link} to={linkTo} sx={bodySx}>{body}</CardActionArea>}
      {!linkTo && onClick && <CardActionArea onClick={onClick} sx={bodySx}>{body}</CardActionArea>}
      {!linkTo && !onClick && <Box sx={bodySx}>{body}</Box>}
    </Card>
  );
};

export default MarketplaceCard;

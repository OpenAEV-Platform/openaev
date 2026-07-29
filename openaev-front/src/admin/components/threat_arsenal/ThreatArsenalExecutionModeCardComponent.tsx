import { ChevronRightOutlined } from '@mui/icons-material';
import { Box, Card, CardActionArea, Tooltip, Typography } from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import { type ReactElement } from 'react';

interface Props {
  executionMode: {
    icon: ReactElement;
    title: string;
    description: string;
    onClick: () => void;
    disabled: boolean;
    tooltip?: string;
  };
}

const ThreatArsenalExecutionModeCardComponent = ({ executionMode }: Props) => {
  const theme = useTheme();
  const accent = theme.palette.primary.main;
  const { disabled } = executionMode;
  const frameColor = disabled ? theme.palette.text.disabled : accent;

  return (
    <Tooltip title={executionMode.tooltip ?? ''}>
      <Card
        variant="outlined"
        data-testid="threat-arsenal-execution-mode-card"
        sx={{
          borderRadius: 1,
          borderColor: theme.palette.divider,
          backgroundColor: theme.palette.background.paper,
          transition: theme.transitions.create(
            ['border-color', 'box-shadow', 'transform'],
            { duration: theme.transitions.duration.shorter },
          ),
          // Signature marketplace hover, same as the Threat Arsenal cards.
          ...(!disabled && {
            '&:hover': {
              borderColor: alpha(accent, 0.3),
              boxShadow: `0 0 30px ${alpha(accent, 0.12)}`,
              transform: 'translateY(-2px)',
            },
          }),
        }}
      >
        <CardActionArea onClick={executionMode.onClick} disabled={disabled}>
          <Box
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 2,
              padding: 2,
            }}
          >
            <Box
              sx={{
                width: 44,
                height: 44,
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 1.5,
                border: `1px solid ${alpha(frameColor, 0.4)}`,
                backgroundColor: alpha(frameColor, 0.08),
              }}
            >
              {executionMode.icon}
            </Box>
            <Box sx={{
              flex: 1,
              minWidth: 0,
            }}
            >
              <Typography
                sx={{
                  fontSize: 13.5,
                  fontWeight: 600,
                  lineHeight: 1.35,
                  color: disabled ? theme.palette.text.disabled : theme.palette.text.primary,
                }}
              >
                {executionMode.title}
              </Typography>
              <Typography
                variant="caption"
                sx={{ color: disabled ? theme.palette.text.disabled : theme.palette.text.secondary }}
              >
                {executionMode.description}
              </Typography>
            </Box>
            <ChevronRightOutlined
              fontSize="small"
              sx={{ color: disabled ? theme.palette.text.disabled : theme.palette.text.secondary }}
            />
          </Box>
        </CardActionArea>
      </Card>
    </Tooltip>
  );
};

export default ThreatArsenalExecutionModeCardComponent;

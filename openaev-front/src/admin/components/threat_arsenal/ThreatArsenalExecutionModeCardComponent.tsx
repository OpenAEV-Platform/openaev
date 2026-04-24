import { Card, CardActionArea, CardContent, Stack, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
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

  return (
    <Tooltip title={executionMode.tooltip}>
      <Card
        style={{ borderBottomColor: theme.palette.border?.pagination }}
        sx={{
          borderRadius: 0,
          boxShadow: 'none',
          backgroundImage: 'none',
          backgroundColor: 'inherit',
          borderBottomStyle: 'solid',
          borderBottomWidth: 1,
        }}
      >
        <CardActionArea
          onClick={executionMode.onClick}
          disabled={executionMode.disabled}
        >
          <CardContent
            sx={{
              display: 'flex',
              flexDirection: 'row',
              alignItems: 'center',
              padding: theme.spacing(2),
            }}
          >
            <div style={{ marginRight: theme.spacing(2) }}>{executionMode.icon}</div>
            <Stack flexDirection="column">
              <Typography
                style={{ color: executionMode.disabled ? theme.palette.text?.disabled : 'inherit' }}
                sx={{ fontSize: 14 }}
              >
                {executionMode.title}
              </Typography>
              <Typography
                style={{ color: executionMode.disabled ? theme.palette.text?.disabled : 'inherit' }}
                sx={{ fontSize: 12 }}
              >
                {executionMode.description}
              </Typography>
            </Stack>
          </CardContent>
        </CardActionArea>
      </Card>
    </Tooltip>
  );
};

export default ThreatArsenalExecutionModeCardComponent;

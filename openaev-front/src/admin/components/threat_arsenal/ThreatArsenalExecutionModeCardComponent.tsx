import { Card, CardActionArea, CardContent, Stack, Tooltip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type ReactElement } from 'react';
import { makeStyles } from 'tss-react/mui';

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

const useStyles = makeStyles()(() => ({
  card: {
    borderRadius: 0,
    boxShadow: 'none',
    backgroundImage: 'none',
    backgroundColor: 'inherit',
    borderBottomStyle: 'solid',
    borderBottomWidth: 1,
  },
  cardTitle: { fontSize: 14 },
  cardDescription: { fontSize: 12 },
}));

const ThreatArsenalExecutionModeCardComponent = ({ executionMode }: Props) => {
  const theme = useTheme();
  const { classes } = useStyles();

  return (
    <Tooltip title={executionMode.tooltip}>
      <Card style={{ borderBottomColor: theme.palette.border?.main }} classes={{ root: classes.card }}>
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
                className={classes.cardTitle}
              >
                {executionMode.title}
              </Typography>
              <Typography
                style={{ color: executionMode.disabled ? theme.palette.text?.disabled : 'inherit' }}
                className={classes.cardDescription}
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

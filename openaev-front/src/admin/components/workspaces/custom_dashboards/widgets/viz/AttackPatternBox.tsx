import { Button, type Theme, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type CSSProperties, type FunctionComponent, memo, useMemo } from 'react';
import { makeStyles } from 'tss-react/mui';

import { SUCCESS_25_COLOR, SUCCESS_50_COLOR, SUCCESS_75_COLOR, SUCCESS_100_COLOR } from './securityCoverageUtils';

const useStyles = makeStyles()(theme => ({
  button: {
    textTransform: 'capitalize',
    color: theme.palette.text?.primary,
    backgroundColor: theme.palette.background.accent,
    borderRadius: theme.borderRadius,
  },
  container: {
    display: 'grid',
    gridTemplateColumns: '3fr 1fr',
    gap: theme.spacing(1),
    width: '100%',
    padding: `${theme.spacing(0.5)} ${theme.spacing(1)}`,
  },
}));

const getBackgroundColor = (successRate: number | null): string | undefined => {
  if (successRate === null) return undefined;
  if (successRate === 1) return SUCCESS_100_COLOR;
  if (successRate === 0) return SUCCESS_25_COLOR;
  if (successRate >= 0.75) return SUCCESS_75_COLOR;
  return SUCCESS_50_COLOR;
};

const getTextColor = (theme: Theme, total: number): string | undefined => {
  if (total === 0) return theme.typography.h3.color;
  return theme.palette.common.white;
};

interface AttackPatternBoxProps {
  attackPatternName: string;
  attackPatternExternalId: string;
  successRate: number | null;
  total?: number;
  style?: CSSProperties;
  onClick?: () => void;
}

const AttackPatternBox: FunctionComponent<AttackPatternBoxProps> = ({
  attackPatternName,
  attackPatternExternalId,
  successRate = null,
  total,
  style = {},
  onClick,
}) => {
  // Standard hooks
  const { classes } = useStyles();
  const theme = useTheme();

  const backgroundColor = getBackgroundColor(successRate);
  const textColor = getTextColor(theme, total ?? 0);

  // Memoize button style
  const buttonStyle = useMemo(() => ({
    backgroundColor,
    ...style,
  }), [backgroundColor, style]);

  // Memoize typography styles
  const nameTypographySx = useMemo(() => ({
    textAlign: 'left' as const,
    color: textColor,
    whiteSpace: 'normal' as const,
    fontSize: theme.typography.h3.fontSize,
    gridColumn: total && total > 0 ? 'span 1' : 'span 2',
  }), [textColor, theme.typography.h3.fontSize, total]);

  const rateTypographySx = useMemo(() => ({
    textAlign: 'right' as const,
    fontSize: theme.typography.h3.fontSize,
  }), [theme.typography.h3.fontSize]);

  const idTypographySx = useMemo(() => ({
    textAlign: 'left' as const,
    color: textColor,
    gridColumn: 'span 2',
  }), [textColor]);

  // Calculate success count
  const successCount = successRate ? Math.round(successRate * (total ?? 0)) : 0;

  return (
    <Button
      aria-haspopup="true"
      style={buttonStyle}
      className={classes.button}
      disabled={!onClick}
      onClick={onClick}
    >
      <div className={classes.container}>
        <Typography sx={nameTypographySx}>
          {attackPatternName}
        </Typography>
        {successRate !== null && total && total > 0 && (
          <Typography sx={rateTypographySx}>
            {successCount}
            /
            {total}
          </Typography>
        )}
        <Typography sx={idTypographySx}>
          {attackPatternExternalId}
        </Typography>
      </div>
    </Button>
  );
};

export default memo(AttackPatternBox);

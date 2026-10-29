import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../i18n';

const useStyles = makeStyles()(theme => ({
  mode: {
    borderRadius: 4,
    fontFamily: 'Consolas, monaco, monospace',
    backgroundColor: theme.palette.action?.selected,
    padding: '0 8px',
    display: 'flex',
    alignItems: 'center',
  },
  hasClickEvent: {
    'cursor': 'pointer',
    '&:hover': {
      backgroundColor: theme.palette.action?.disabled,
      textDecorationLine: 'underline',
    },
  },
}));

interface Props {
  onClick?: () => void;
  mode?: string;
  /** Match the chips it separates when they are not the default height. */
  height?: number;
}

const ClickableModeChip: FunctionComponent<Props> = ({
  onClick,
  mode,
  height,
}) => {
  // Standard hooks
  const { classes, cx } = useStyles();
  const { t } = useFormatter();

  if (!mode) {
    return <></>;
  }

  return (
    (
      <div
        onClick={onClick}
        style={height ? { height } : undefined}
        className={cx({
          [classes.mode]: true,
          [classes.hasClickEvent]: !!onClick,
        })}
      >
        {t(mode.toUpperCase())}
      </div>
    )
  );
};

export default ClickableModeChip;

import { CircularProgress } from '@mui/material';
import { type CSSProperties, type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(() => ({
  container: {
    width: '100vh',
    height: 'calc(100vh-180px)',
    padding: '0 0 0 180px',
  },
  containerInElement: {
    width: '100%',
    height: '100%',
    display: 'table',
  },
  containerSizeXS: { width: 'auto' },
  loader: {
    width: '100%',
    margin: 0,
    padding: 0,
    position: 'absolute',
    top: '46%',
    left: 0,
    textAlign: 'center',
    zIndex: 20,
  },
  loaderInElement: {
    width: '100%',
    margin: 0,
    padding: 0,
    display: 'table-cell',
    verticalAlign: 'middle',
    textAlign: 'center',
  },
  loaderCircle: { display: 'inline-block' },
}));

type LoaderVariant = 'inElement' | 'default';
type LoaderSize = 'xs' | 'sm' | 'md' | 'lg';

interface LoaderProps {
  variant?: LoaderVariant;
  withRightPadding?: boolean;
  size?: LoaderSize;
}

const Loader: FunctionComponent<LoaderProps> = ({
  variant = 'default',
  withRightPadding = false,
  size,
}) => {
  const { classes } = useStyles();

  const getContainer = (): string => {
    if (size === 'xs') {
      return classes.containerSizeXS;
    }
    if (variant === 'inElement') {
      return classes.containerInElement;
    }
    return classes.container;
  };

  const getSize = (): string | number => {
    if (size === 'xs') {
      return '1rem';
    }
    if (variant === 'inElement') {
      return 40;
    }
    return 80;
  };

  const containerStyle: CSSProperties = variant === 'inElement'
    ? { paddingRight: withRightPadding ? 200 : 0 }
    : {};

  const loaderStyle: CSSProperties = variant !== 'inElement'
    ? { paddingRight: withRightPadding ? 100 : 0 }
    : {};

  return (
    <div className={getContainer()} style={containerStyle}>
      <div
        className={variant === 'inElement' ? classes.loaderInElement : classes.loader}
        style={loaderStyle}
      >
        <CircularProgress
          size={getSize()}
          thickness={1}
          className={classes.loaderCircle}
        />
      </div>
    </div>
  );
};

export default Loader;

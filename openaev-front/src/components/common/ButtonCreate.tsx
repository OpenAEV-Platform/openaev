import { Add } from '@mui/icons-material';
import { Fab } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

const RIGHT_MENU_OFFSET = 230;

const useStyles = makeStyles<{ variant: ButtonCreateVariant }>()((_, { variant }) => ({
  createButton: {
    position: 'fixed',
    bottom: 30,
    right: variant === 'rightMenu' ? RIGHT_MENU_OFFSET : 30,
  },
}));

type ButtonCreateVariant = 'default' | 'rightMenu';

interface Props {
  onClick: () => void;
  style?: React.CSSProperties;
  variant?: ButtonCreateVariant;
}

const ButtonCreate: FunctionComponent<Props> = ({ onClick, style, variant = 'default' }) => {
  const { classes } = useStyles({ variant });

  return (
    <Fab
      onClick={onClick}
      color="primary"
      aria-label="Add"
      className={classes.createButton}
      style={style}
    >
      <Add />
    </Fab>
  );
};

export default ButtonCreate;

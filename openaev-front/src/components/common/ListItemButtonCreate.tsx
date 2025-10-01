import { ControlPointOutlined } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText } from '@mui/material';
import { type FunctionComponent } from 'react';
import { makeStyles } from 'tss-react/mui';

const useStyles = makeStyles()(theme => ({
  text: {
    fontSize: 15,
    color: theme.palette.primary.main,
    fontWeight: 500,
  },
}));

interface Props {
  title: string;
  onClick: () => void;
}

const ListItemButtonCreate: FunctionComponent<Props> = ({
  title,
  onClick,
}) => {
  // Standard hooks
  const { classes } = useStyles();

  return (
    <ListItemButton
      divider
      onClick={onClick}
      color="primary"
    >
      <ListItemIcon color="primary">
        <ControlPointOutlined color="primary" />
      </ListItemIcon>
      <ListItemText
        primary={title}
        classes={{ primary: classes.text }}
      />
    </ListItemButton>
  );
};

export default ListItemButtonCreate;

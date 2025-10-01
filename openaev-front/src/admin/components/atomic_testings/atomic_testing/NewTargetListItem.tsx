import { Groups3Outlined, PersonOutlined } from '@mui/icons-material';
import { ListItemButton, ListItemIcon, ListItemText, Paper } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { SelectGroup } from 'mdi-material-ui';
import { makeStyles } from 'tss-react/mui';

import PlatformIcon from '../../../../components/PlatformIcon';
import type { InjectTarget } from '../../../../utils/api-types';
import NewAtomicTestingResult from './NewAtomicTestingResult';

const useStyles = makeStyles()(() => ({
  bodyTarget: {
    fontSize: 13,
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    verticalAlign: 'middle',
    textOverflow: 'ellipsis',
  },
}));

interface Props {
  selected?: boolean;
  onClick: (target: InjectTarget) => void;
  target: InjectTarget;
}

const NewTargetListItem: React.FC<Props> = ({ onClick, target, selected }) => {
  const { classes } = useStyles();
  const theme = useTheme();
  const handleItemClick = () => {
    onClick(target);
  };
  const getIcon = (target: InjectTarget) => {
    const iconMap = {
      // TODO: for Endpoints and Agents, check the targetSubType attribute
      ASSETS_GROUPS: <SelectGroup />,
      ASSETS: <PlatformIcon platform={target?.target_subtype ?? 'Unknown'} width={20} marginRight={theme.spacing(2)} />,
      TEAMS: <Groups3Outlined />,
      PLAYERS: <PersonOutlined fontSize="small" />,
      AGENT: (
        <img
          src={`/api/images/executors/icons/${target.target_subtype}`}
          alt={target.target_subtype}
          style={{
            width: 20,
            height: 20,
            borderRadius: 4,
          }}
        />
      ),
    };

    return iconMap[target.target_type];
  };
  return (
    <>
      <Paper elevation={1} key={target?.target_id}>
        <ListItemButton onClick={handleItemClick} style={{ marginBottom: theme.spacing() }} selected={selected}>
          <ListItemIcon>
            {getIcon(target)}
          </ListItemIcon>
          <ListItemText
            primary={(
              <div style={{
                display: 'flex',
                alignItems: 'center',
              }}
              >
                <div className={classes.bodyTarget} style={{ width: '80%' }}>
                  {target?.target_name}
                </div>
                <div className={classes.bodyTarget} style={{ width: '20%' }}>
                  <NewAtomicTestingResult target={target} />
                </div>
              </div>
            )}
          />
        </ListItemButton>
      </Paper>
    </>
  );
};

export default NewTargetListItem;

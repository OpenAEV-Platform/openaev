import { Kayaking } from '@mui/icons-material';
import { Box } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { fetchScenarios } from '../actions/scenarios/scenario-actions';
import { useHelper } from '../store';
import { useAppDispatch } from '../utils/hooks';
import useDataLoader from '../utils/hooks/useDataLoader';
import Autocomplete from './Autocomplete';

const useStyles = makeStyles()(() => ({
  icon: {
    paddingTop: 4,
    display: 'inline-block',
  },
  text: {
    display: 'inline-block',
    flexGrow: 1,
    marginLeft: 10,
  },
  autoCompleteIndicator: { display: 'none' },
}));

const ScenarioField = (props) => {
  // Standard hooks
  const { classes } = useStyles();
  const dispatch = useAppDispatch();
  // Fetching data
  const scenarios = useHelper(helper => helper.getScenarios());
  useDataLoader(() => {
    dispatch(fetchScenarios());
  });

  const { name, onKeyDown, style, label, placeholder } = props;
  const scenarioOptions = (scenarios || []).map(n => ({
    id: n.scenario_id,
    label: n.scenario_name,
  }));
  return (
    <Autocomplete
      variant="standard"
      size="small"
      name={name}
      fullWidth
      multiple
      label={label}
      placeholder={placeholder}
      options={scenarioOptions}
      style={style}
      onKeyDown={onKeyDown}
      renderOption={(renderProps, option) => (
        <Box component="li" {...renderProps} key={option.id}>
          <div className={classes.icon}>
            <Kayaking />
          </div>
          <div className={classes.text}>{option.label}</div>
        </Box>
      )}
      classes={{ clearIndicator: classes.autoCompleteIndicator }}
    />
  );
};

export default ScenarioField;

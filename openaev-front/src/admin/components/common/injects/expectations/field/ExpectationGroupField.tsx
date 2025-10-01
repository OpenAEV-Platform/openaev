import { InfoOutlined } from '@mui/icons-material';
import { FormControlLabel, FormLabel, Radio, RadioGroup, Tooltip } from '@mui/material';
import { type ChangeEvent, type FunctionComponent } from 'react';
import { type Control, Controller } from 'react-hook-form';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../../components/i18n';
import { type ExpectationInputForm } from '../Expectation';

const useStyles = makeStyles()(theme => ({
  marginTop_2: { marginTop: theme.spacing(2) },
  container: {
    display: 'flex',
    alignItems: 'end',
    gap: 5,
  },
}));

interface Props {
  control: Control<ExpectationInputForm>;
  isTechnicalExpectation: boolean;
}

const ExpectationGroupField: FunctionComponent<Props> = ({
  control,
  isTechnicalExpectation,
}) => {
  const { t } = useFormatter();
  const { classes } = useStyles();

  return (
    <Controller
      control={control}
      name="expectation_expectation_group"
      render={({ field: { onChange, value } }) => (
        <div className={classes.marginTop_2}>
          <FormLabel className={classes.container}>
            {t('Validation mode')}
            <Tooltip
              title={isTechnicalExpectation
                ? t('An isolated asset is considered as a group of one asset')
                : t('An isolated player is considered as a group of one player')}
            >
              <InfoOutlined
                fontSize="small"
                color="primary"
                style={{ marginTop: 8 }}
              />
            </Tooltip>
          </FormLabel>
          <RadioGroup
            value={value}
            onChange={(event: ChangeEvent<HTMLInputElement>) => {
              onChange(event.target.value === 'true');
            }}
          >
            <FormControlLabel
              value={false}
              control={<Radio />}
              label={isTechnicalExpectation ? t('All assets (per group) must validate the expectation')
                : t('All players (per team) must validate the expectation')}
            />
            <FormControlLabel
              value={true}
              control={<Radio />}
              label={isTechnicalExpectation ? t('At least one asset (per group) must validate the expectation')
                : t('At least one player (per team) must validate the expectation')}
            />
          </RadioGroup>
        </div>
      )}
    />
  );
};

export default ExpectationGroupField;

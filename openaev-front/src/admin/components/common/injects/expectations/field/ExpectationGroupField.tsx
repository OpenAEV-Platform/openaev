import { Radio, RadioGroup } from '@filigran/design-system';
import { InfoOutlined } from '@mui/icons-material';
import { FormLabel, Tooltip } from '@mui/material';
import { type FunctionComponent, useId } from 'react';
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

  const validationModeLabelId = useId();

  return (
    <Controller
      control={control}
      name="expectation_expectation_group"
      render={({ field: { onChange, value } }) => (
        <div className={classes.marginTop_2}>
          <FormLabel className={classes.container} id={validationModeLabelId}>
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
            aria-labelledby={validationModeLabelId}
            value={String(value)}
            onValueChange={next => onChange(next === 'true')}
          >
            <Radio
              value="false"
              label={isTechnicalExpectation ? t('All assets (per group) must validate the expectation')
                : t('All players (per team) must validate the expectation')}
            />
            <Radio
              value="true"
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

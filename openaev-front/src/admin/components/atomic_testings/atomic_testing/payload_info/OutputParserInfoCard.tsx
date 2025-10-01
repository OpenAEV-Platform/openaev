import { Box, Paper, Typography } from '@mui/material';
import { makeStyles } from 'tss-react/mui';

import { useFormatter } from '../../../../../components/i18n';
import ItemCopy from '../../../../../components/ItemCopy';
import ItemTags from '../../../../../components/ItemTags';
import { type OutputParserSimple } from '../../../../../utils/api-types';

const useStyles = makeStyles()(theme => ({
  paperContainer: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
  },
  contractOutputContainer: {
    'gridColumn': 'span 2',
    'marginTop': theme.spacing(2),
    'backgroundColor': theme.palette.background.paperInCard,
    'display': 'grid',
    'columnGap': theme.spacing(2),
    'gridTemplateColumns': '1fr 1fr 1fr 1fr',
    '& .allWidth': { gridColumn: 'span 4' },
    '& .newLine': { marginTop: theme.spacing(2) },
  },
  regexGroupContainer: {
    display: 'grid',
    gridTemplateColumns: 'auto 1fr auto 1fr',
    alignItems: 'center',
    rowGap: theme.spacing(1),
    columnGap: theme.spacing(2),
  },
}));

interface Props { outputParsers: OutputParserSimple[] }

const OutputParserInfoCard = ({ outputParsers }: Props) => {
  const { t } = useFormatter();
  const { classes } = useStyles();
  if (outputParsers.length == 0) {
    return (
      <Paper className="paper" variant="outlined">
        <Typography variant="body1">{t('No data available')}</Typography>
      </Paper>
    );
  }

  return (
    <Paper className={`paper ${classes.paperContainer}`} variant="outlined">
      {outputParsers.map((outputParser: OutputParserSimple) => (
        <>
          <Typography variant="h3" gutterBottom>{t('Output mode')}</Typography>
          <Typography variant="h3" gutterBottom>{t('Parsing')}</Typography>
          <Typography variant="body2">{outputParser.output_parser_mode}</Typography>
          <Typography variant="body2">{outputParser.output_parser_type}</Typography>

          { (outputParser.output_parser_contract_output_elements || []).map(contractOutput => (
            <Paper key={contractOutput.contract_output_element_id} className={`paper ${classes.contractOutputContainer}`}>

              <Typography variant="h3" gutterBottom>{t('Name')}</Typography>
              <Typography variant="h3" gutterBottom>{t('Key')}</Typography>
              <Typography variant="h3" gutterBottom>{t('Type')}</Typography>
              <Typography variant="h3" gutterBottom>{t('Tags')}</Typography>
              <Typography variant="body2">{contractOutput.contract_output_element_name}</Typography>
              <Typography variant="body2">{contractOutput.contract_output_element_key}</Typography>
              <Typography variant="body2">
                {contractOutput.contract_output_element_type
                  ? t(contractOutput.contract_output_element_type.charAt(0).toUpperCase() + contractOutput.contract_output_element_type.slice(1)) : ''}
              </Typography>
              <ItemTags variant="reduced-view" tags={contractOutput.contract_output_element_tags} />

              <Typography className="allWidth newLine" variant="h3" gutterBottom>{t('Regex group rules')}</Typography>
              {!contractOutput.contract_output_element_rule ? '-' : (
                <pre style={{ margin: 0 }} className="allWidth" key={contractOutput.contract_output_element_rule}>
                  <ItemCopy content={contractOutput.contract_output_element_rule ?? ''} />
                </pre>
              )}
              <Typography className="allWidth newLine" variant="h3" gutterBottom>{t('Output value')}</Typography>
              <Box className={`allWidth ${classes.regexGroupContainer}`}>
                {(contractOutput.contract_output_element_regex_groups || []).map(group => (
                  <>
                    <Typography variant="h3" gutterBottom>
                      {group.regex_group_field
                        ? t(group.regex_group_field.charAt(0).toUpperCase() + group.regex_group_field.slice(1)) : ''}
                    </Typography>
                    <pre style={{ margin: 0 }} key={group.regex_group_index_values}>
                      <ItemCopy content={group.regex_group_index_values ?? ' '} />
                    </pre>
                  </>
                ))}
              </Box>
            </Paper>
          ))}
        </>
      ))}
    </Paper>
  );
};

export default OutputParserInfoCard;

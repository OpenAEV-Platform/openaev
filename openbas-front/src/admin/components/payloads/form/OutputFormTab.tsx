import { Button, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { useEffect } from 'react';
import { useFieldArray, useFormContext } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import ContractOutputElementCard from './ContractOutputElementCard';

const OutputFormTab = () => {
  const { t } = useFormatter();
  const theme = useTheme();
  const { control, setValue } = useFormContext();
  const outputParserName = 'payload_output_parsers.0.output_parser_contract_output_elements';

  const { fields: contractOutputElements, append: outputElementAppend, remove: outputElementRemove } = useFieldArray({
    control,
    name: outputParserName,
  });

  useEffect(() => {
    if (contractOutputElements.length === 1) {
      setValue('payload_output_parsers.0.output_parser_mode', 'STDOUT');
      setValue('payload_output_parsers.0.output_parser_type', 'REGEX');
    } else if (contractOutputElements.length === 0) {
      setValue('payload_output_parsers', []);
    }
  }, [contractOutputElements]);

  return (
    <>
      <Typography>
        {t('Define structured outputs by parsing the raw output of your payload.')}
&nbsp;
        <a
          href="https://docs.openbas.io/latest/usage/payloads/payloads/#output-parser"
          target="_blank"
          rel="noreferrer"
        >
          {t('Learn more about parser.')}
        </a>
      </Typography>
      <Typography variant="h5" marginTop={theme.spacing(3)}>{t('Parsing rules')}</Typography>
      <div style={{
        display: 'flex',
        alignItems: 'center',
      }}
      >
        <Typography sx={{ marginBottom: 0 }} variant="h3">
          {`${t('Output mode')} :`}
&nbsp;
        </Typography>
        <Typography>{t('Stdout')}</Typography>
        <Typography
          sx={{
            marginBottom: 0,
            marginLeft: theme.spacing(2),
          }}
          variant="h3"
        >
          {`${t('Parsing')} :`}
&nbsp;
        </Typography>
        <Typography>{t('Regex')}</Typography>
        <Button
          onClick={() => outputElementAppend({
            contract_output_element_name: '',
            contract_output_element_key: '',
            contract_output_element_type: '',
            contract_output_element_tags: [],
            contract_output_element_is_finding: true,
            contract_output_element_rule: '',
            contract_output_element_regex_groups: [],
          })}
          sx={{ marginLeft: 'auto' }}
          variant="contained"
        >
          {t('add_attribute')}
        </Button>
      </div>

      {contractOutputElements.map((contracOutputElement, contractOutputElementIndex) => (
        <ContractOutputElementCard
          key={contracOutputElement.id} // DO NOT REMOVE, it's used to remove contractOutput from list
          prefixName={outputParserName}
          index={contractOutputElementIndex}
          remove={outputElementRemove}
        />
      ))}

    </>
  );
};

export default OutputFormTab;

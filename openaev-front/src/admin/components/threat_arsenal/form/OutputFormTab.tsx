import { Add } from '@mui/icons-material';
import { Alert, Button, Chip, Link } from '@mui/material';
import { useEffect } from 'react';
import { useFieldArray, useFormContext } from 'react-hook-form';

import { useFormatter } from '../../../../components/i18n';
import InjectFormSection from '../../common/injects/form/InjectFormSection';
import ContractOutputElementCard from './ContractOutputElementCard';

const OutputFormTab = () => {
  const { t } = useFormatter();
  const { control, setValue } = useFormContext();
  const outputParserName = 'action_output_parsers.0.output_parser_contract_output_elements';

  const { fields: contractOutputElements, append: outputElementAppend, remove: outputElementRemove } = useFieldArray({
    control,
    name: outputParserName,
  });

  useEffect(() => {
    if (contractOutputElements.length === 1) {
      setValue('action_output_parsers.0.output_parser_mode', 'STDOUT');
      setValue('action_output_parsers.0.output_parser_type', 'REGEX');
    } else if (contractOutputElements.length === 0) {
      setValue('action_output_parsers', []);
    }
  }, [contractOutputElements]);

  return (
    <>
      <Alert severity="info" variant="outlined">
        {t('Define structured outputs by parsing the raw output of your arsenal item.')}
        {' '}
        <Link
          href="https://docs.openaev.io/latest/usage/threat-arsenals/threat-arsenals/#output-parsers"
          target="_blank"
          rel="noreferrer"
          underline="always"
        >
          {t('Learn more about parser.')}
        </Link>
      </Alert>
      <InjectFormSection
        title={t('Parsing rules')}
        helper={t('Each attribute extracts a structured value from the raw output.')}
        action={(
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
            variant="contained"
            size="small"
            startIcon={<Add fontSize="small" />}
          >
            {t('add_attribute')}
          </Button>
        )}
      >
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
        >
          <Chip
            variant="outlined"
            size="small"
            sx={{ borderRadius: 1 }}
            label={`${t('Output mode')} : ${t('Stdout')}`}
          />
          <Chip
            variant="outlined"
            size="small"
            sx={{ borderRadius: 1 }}
            label={`${t('Parsing')} : ${t('Regex')}`}
          />
        </div>

        {contractOutputElements.map((contracOutputElement, contractOutputElementIndex) => (
          <ContractOutputElementCard
            key={contracOutputElement.id} // DO NOT REMOVE, it's used to remove contractOutput from list
            prefixName={outputParserName}
            index={contractOutputElementIndex}
            remove={outputElementRemove}
          />
        ))}
      </InjectFormSection>
    </>
  );
};

export default OutputFormTab;

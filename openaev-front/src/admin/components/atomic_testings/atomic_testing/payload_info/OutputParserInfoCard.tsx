import { DataObjectOutlined } from '@mui/icons-material';
import { Box, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { Fragment } from 'react';

import CodeBlock from '../../../../../components/common/overview/CodeBlock';
import Field from '../../../../../components/common/overview/Field';
import Section from '../../../../../components/common/overview/Section';
import { useFormatter } from '../../../../../components/i18n';
import ItemCopy from '../../../../../components/ItemCopy';
import ItemTags from '../../../../../components/ItemTags';
import { type OutputParserSimple } from '../../../../../utils/api-types';
import ContractOutputElementType from '../../../findings/ContractOutputElementType';

interface Props { outputParsers: OutputParserSimple[] }

const OutputParserInfoCard = ({ outputParsers }: Props) => {
  const { t } = useFormatter();
  const theme = useTheme();

  if (outputParsers.length == 0) {
    return (
      <Section title={t('Output parser')} icon={<DataObjectOutlined fontSize="small" />}>
        <Typography variant="body1">{t('No data available')}</Typography>
      </Section>
    );
  }

  return (
    <Section title={t('Output parser')} icon={<DataObjectOutlined fontSize="small" />}>
      <div style={{
        display: 'flex',
        flexDirection: 'column',
        gap: theme.spacing(2),
      }}
      >
        {outputParsers.map((outputParser: OutputParserSimple, parserIndex) => (
          <Fragment key={`output-parser-${parserIndex}`}>
            <div style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: theme.spacing(2),
            }}
            >
              <Field label="Output mode">
                <Typography variant="body2">{outputParser.output_parser_mode}</Typography>
              </Field>
              <Field label="Parsing">
                <Typography variant="body2">{outputParser.output_parser_type}</Typography>
              </Field>
            </div>

            {(outputParser.output_parser_contract_output_elements || []).map(contractOutput => (
              <Box
                key={contractOutput.contract_output_element_id}
                sx={{
                  border: `1px solid ${theme.palette.divider}`,
                  borderRadius: 1,
                  padding: 1.5,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 2,
                }}
              >
                <div style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
                  gap: theme.spacing(2),
                }}
                >
                  <Field label="Name">
                    <Typography variant="body2">{contractOutput.contract_output_element_name}</Typography>
                  </Field>
                  <Field label="Key">
                    <Typography variant="body2">{contractOutput.contract_output_element_key}</Typography>
                  </Field>
                  <Field label="Type">
                    <Typography variant="body2">
                      {contractOutput.contract_output_element_type
                        ? t(ContractOutputElementType[contractOutput.contract_output_element_type as keyof typeof ContractOutputElementType] ?? contractOutput.contract_output_element_type) : ''}
                    </Typography>
                  </Field>
                  <Field label="Tags">
                    <ItemTags variant="reduced-view" tags={contractOutput.contract_output_element_tags} />
                  </Field>
                </div>

                <Field label="Regex group rules">
                  {!contractOutput.contract_output_element_rule ? '-' : (
                    <CodeBlock content={contractOutput.contract_output_element_rule ?? ''} />
                  )}
                </Field>
                <Field label="Output value">
                  <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'auto 1fr auto 1fr',
                    alignItems: 'center',
                    rowGap: theme.spacing(1),
                    columnGap: theme.spacing(2),
                  }}
                  >
                    {(contractOutput.contract_output_element_regex_groups || []).map((group, groupIndex) => (
                      <Fragment key={`${group.regex_group_index_values}-${groupIndex}`}>
                        <Typography
                          variant="overline"
                          sx={{
                            color: 'text.secondary',
                            fontSize: 10.5,
                            letterSpacing: '0.08em',
                            lineHeight: 1.2,
                          }}
                        >
                          {group.regex_group_field
                            ? t(group.regex_group_field.charAt(0).toUpperCase() + group.regex_group_field.slice(1)) : ''}
                        </Typography>
                        <ItemCopy content={group.regex_group_index_values ?? ' '} />
                      </Fragment>
                    ))}
                  </div>
                </Field>
              </Box>
            ))}
          </Fragment>
        ))}
      </div>
    </Section>
  );
};

export default OutputParserInfoCard;

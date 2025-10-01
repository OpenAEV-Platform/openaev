import { useTheme } from '@mui/material/styles';
import { type FunctionComponent } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { a11yDark, coy } from 'react-syntax-highlighter/dist/esm/styles/prism';

interface CodeBlockProps {
  code: string;
  language: string;
  maxHeight?: string;
}

const CodeBlock: FunctionComponent<CodeBlockProps> = ({ language, code, maxHeight }) => {
  const theme = useTheme();
  return (
    <SyntaxHighlighter
      language={language}
      style={theme.palette.mode === 'dark' ? a11yDark : coy}
      customStyle={{
        minHeight: '100px',
        minWidth: '550px',
        maxHeight,
      }}
      showLineNumbers
    >
      {code}
    </SyntaxHighlighter>
  );
};

export default CodeBlock;

import { type Theme } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { type FunctionComponent, type SyntheticEvent, useState } from 'react';
import Markdown, { type Options as MarkdownOptions } from 'react-markdown';
import remarkFlexibleMarkers from 'remark-flexible-markers';
import remarkGfm from 'remark-gfm';
import remarkParse from 'remark-parse';

import { truncate } from '../utils/String';
import ExternalLinkPopover from './ExternalLinkPopover';
import FieldOrEmpty from './FieldOrEmpty';

type PluggableList = MarkdownOptions['remarkPlugins'];

export const MarkDownComponents = (
  theme: Theme,
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): Record<string, FunctionComponent<any>> => ({
  table: ({ tableProps }) => (
    <table
      style={{
        border: `1px solid ${theme.palette.divider}`,
        borderCollapse: 'collapse',
      }}
      {...tableProps}
    />
  ),
  tr: ({ trProps }) => (
    <tr style={{ border: `1px solid ${theme.palette.divider}` }} {...trProps} />
  ),
  td: ({ tdProps }) => (
    <td
      style={{
        border: `1px solid ${theme.palette.divider}`,
        padding: 5,
      }}
      {...tdProps}
    />
  ),
  th: ({ tdProps }) => (
    <th
      style={{
        border: `1px solid ${theme.palette.divider}`,
        padding: 5,
      }}
      {...tdProps}
    />
  ),
});

interface MarkdownWithRedirectionWarningProps {
  content: string | null;
  expand?: boolean;
  limit?: number;
  remarkGfmPlugin?: boolean;
  markdownComponents?: boolean;
  commonmark?: boolean;
  removeLinks?: boolean;
  removeLineBreaks?: boolean;
  remarkPlugins?: PluggableList;
}

const MarkdownDisplay: FunctionComponent<
  MarkdownWithRedirectionWarningProps
> = ({
  content,
  expand,
  limit,
  remarkGfmPlugin,
  markdownComponents,
  commonmark,
  removeLinks,
  removeLineBreaks,
  remarkPlugins,
}) => {
  const theme = useTheme();
  const [displayExternalLink, setDisplayExternalLink] = useState(false);
  const [externalLink, setExternalLink] = useState<string | URL | undefined>(
    undefined,
  );
  const handleOpenExternalLink = (url: string) => {
    setDisplayExternalLink(true);
    setExternalLink(url);
  };
  const disallowedElements: string[] = [];
  if (removeLinks) {
    disallowedElements.push('a');
  }
  if (removeLineBreaks) {
    disallowedElements.push('p');
  }
  const markdownElement = () => {
    return (
      <Markdown
        disallowedElements={disallowedElements}
        unwrapDisallowed={true}
      >
        {limit ? truncate(content, limit) : content}
      </Markdown>
    );
  };
  const remarkGfmMarkdownElement = () => {
    if (remarkPlugins) {
      return (
        <Markdown
          remarkPlugins={remarkPlugins}
          disallowedElements={disallowedElements}
          unwrapDisallowed={true}
        >
          {expand || !limit ? content : truncate(content, limit)}
        </Markdown>
      );
    }
    if (markdownComponents) {
      return (
        <Markdown
          remarkPlugins={
            [
              remarkGfm,
              remarkFlexibleMarkers,
              [remarkParse, { commonmark: !!commonmark }],
            ] as PluggableList
          }
          components={MarkDownComponents(theme)}
          disallowedElements={disallowedElements}
          unwrapDisallowed={true}
        >
          {expand || !limit ? content : truncate(content, limit)}
        </Markdown>
      );
    }
    return (
      <Markdown
        remarkPlugins={
          [
            remarkGfm,
            remarkFlexibleMarkers,
            [remarkParse, { commonmark: !!commonmark }],
          ] as PluggableList
        }
        disallowedElements={disallowedElements}
        unwrapDisallowed={true}
      >
        {limit ? truncate(content, limit) : content}
      </Markdown>
    );
  };
  const browseLinkWarning = (
    event: SyntheticEvent<HTMLElement, MouseEvent>,
  ) => {
    if ((event.target as HTMLElement).localName === 'a') {
      // if the user clicks on a link
      event.stopPropagation();
      event.preventDefault();
      const link = event.target as HTMLLinkElement;
      handleOpenExternalLink(link.href);
    }
  };
  if (removeLinks || removeLineBreaks) {
    return (
      <FieldOrEmpty source={content}>
        {remarkGfmPlugin ? remarkGfmMarkdownElement() : markdownElement()}
      </FieldOrEmpty>
    );
  }
  return (
    <FieldOrEmpty source={content}>
      <div onClick={event => browseLinkWarning(event)} style={{ wordBreak: 'break-word' }}>
        {remarkGfmPlugin ? remarkGfmMarkdownElement() : markdownElement()}
      </div>
      <ExternalLinkPopover
        displayExternalLink={displayExternalLink}
        externalLink={externalLink}
        setDisplayExternalLink={setDisplayExternalLink}
        setExternalLink={setExternalLink}
      />
    </FieldOrEmpty>
  );
};

export default MarkdownDisplay;

// docs/links.json is the single map of documentation links shared with the backend. The docs
// build checks every path in it against the built site (`yarn check-docs-links`), so code refers
// to a page by its key and an unknown key fails `yarn check-ts`.
import docsLinks from '../../../docs/links.json';

export type DocsLinkKey = keyof typeof docsLinks.links;

const docsUrl = (key: DocsLinkKey): string => `${docsLinks.base}${docsLinks.links[key]}`;

export default docsUrl;

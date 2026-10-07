/* Placeholders and tags a translation must keep unchanged. No I/O, so it can be unit tested.
   Consumed by i18n-placeholders.js. */

// The optional "$" matters: the email templates use ${var}, and a lost "$" prints the variable name.
// A tag is matched whole, attributes included: in <a href='${url}'>, a translation that drops or
// corrupts the opening tag while keeping ${url} and </a> must not pass.
const TOKEN_RE = /<\/?[a-zA-Z0-9]+(?:\s[^<>]*)?\/?>|\$?\{\{[^{}]+\}\}|\$?\{[^{}]+\}/g;

// A set, not a count: react-intl replaces every occurrence, so a translation may repeat a placeholder.
const tokens = value => [...new Set(value.match(TOKEN_RE) ?? [])].sort();

export default tokens;

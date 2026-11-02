/* Placeholders and tags a translation must keep unchanged. No I/O, so it can be unit tested.
   Consumed by i18n-placeholders.js. */

// The optional "$" matters: the email templates use ${var}, and a lost "$" prints the variable name.
// A tag is matched whole, attributes included: in <a href='${url}'>, a translation that drops or
// corrupts the opening tag while keeping ${url} and </a> must not pass.
const TOKEN_RE = /<\/?[a-zA-Z0-9]+(?:\s[^<>]*)?\/?>|\$?\{\{[^{}]+\}\}|\$?\{[^{}]+\}/g;

// ICU plural / select messages ("{count, plural, one {# item} other {# items}}"): each branch is
// translated text and languages have different plural categories (ru: one / few / many / other),
// so only the argument and its type must match. Branches may nest placeholders: strip the
// innermost "category {…}" bodies until none is left, then normalise the header.
const ICU_BRANCH_RE = /(?<![\w=])(?:=\d+|zero|one|two|few|many|other)\s*\{[^{}]*\}/g;
const ICU_RE = /\{\s*\w+\s*,\s*(?:plural|selectordinal|select)\s*,/;
const ICU_HEADER_RE = /\{\s*(\w+)\s*,\s*(plural|selectordinal|select)\s*,[^{}]*\}/g;

const normaliseIcu = (value) => {
  if (!ICU_RE.test(value)) return value;
  let previous;
  let current = value;
  do {
    previous = current;
    current = current.replace(ICU_BRANCH_RE, '');
  } while (current !== previous);
  return current.replace(ICU_HEADER_RE, '{$1, $2}');
};

// A set, not a count: react-intl replaces every occurrence, so a translation may repeat a placeholder.
const tokens = value => [...new Set(normaliseIcu(value).match(TOKEN_RE) ?? [])].sort();

export default tokens;

package io.openaev.docs;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the docs links map ({@code docs/links.json}) and renders it as a Java class holding one
 * compile-time constant per key, so that code refers to documentation pages by key and a key that
 * does not exist fails compilation.
 *
 * <p>The map has the shape {@code {"base": "https://docs.openaev.io/latest/", "links":
 * {"<snake_case_key>": "<page path relative to base, optional #anchor>"}}}. The shape, key and
 * value rules mirror the ones the docs build enforces in {@code docs/hooks/checks.py}; only the
 * docs build checks that pages and anchors exist.
 */
public class DocsLinksGenerator {

  /** Name of the map in the repository, as written in the header of the generated class. */
  static final String LINKS_FILE_NAME = "docs/links.json";

  /** The only accepted base: the docs site of the latest version. */
  static final String BASE = "https://docs.openaev.io/latest/";

  static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(_[a-z0-9]+)*$");

  /**
   * Longest key: keeps the generated declaration and its javadoc within the 100 columns of
   * google-java-format, so the committed class passes spotless as written.
   */
  static final int MAX_KEY_LENGTH = 64;

  /**
   * RFC 3986 characters of a path, query and fragment: unreserved, sub-delims, ":@/?#", and "%"
   * only as the start of a percent-escape of two hex digits.
   */
  private static final Pattern URL_CHARACTERS =
      Pattern.compile("^(?:[A-Za-z0-9\\-._~!$&'()*+,;=:@/?#]|%[0-9A-Fa-f]{2})*$");

  private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

  /** Any URL of the docs site, whatever its version, until a character that cannot end it. */
  private static final Pattern DOCS_URL =
      Pattern.compile("https?://docs\\.openaev\\.io[^\\s\"'<>\\[\\]{}|\\\\^`]*");

  /** Sentence punctuation that follows a URL in prose rather than belonging to it. */
  private static final String TRAILING_PUNCTUATION = ".,;:!?)";

  private static final Set<String> TOP_LEVEL_FIELDS = Set.of("base", "links");
  private static final int MAX_LINE_LENGTH = 100;

  static final String MESSAGE_FILE_NOT_FOUND = "Docs links file not found: ";
  static final String MESSAGE_INVALID_JSON = "Invalid JSON in ";
  static final String MESSAGE_SHAPE = "must hold a JSON object with \"base\" and \"links\"";
  static final String MESSAGE_UNKNOWN_FIELD = "unknown top-level field ";
  static final String MESSAGE_BASE = "\"base\" must be \"" + BASE + "\"";
  static final String MESSAGE_LINKS = "\"links\" must be an object mapping each key to a path";
  static final String MESSAGE_KEY = "must be snake_case, matching " + KEY_PATTERN.pattern();
  static final String MESSAGE_KEY_LENGTH = "is longer than " + MAX_KEY_LENGTH + " characters";
  static final String MESSAGE_NOT_STRING = "must map to a string (a path relative to \"base\")";
  static final String MESSAGE_RELATIVE =
      "must be a path relative to \"base\" (no scheme, no host, no leading \"/\")";
  static final String MESSAGE_CHARACTERS =
      "holds characters a URL cannot (whitespace, control or non-ASCII characters, one of"
          + " \"<>\\^`{|}, or a \"%\" not followed by two hex digits); percent-encode them";
  static final String MESSAGE_PAGE =
      "must be the URL of a page, ending with \"/\" before any \"#\" (a.md is a/)";
  static final String MESSAGE_UNKNOWN_URL =
      "is not a link of " + LINKS_FILE_NAME + ": add a key for it, or fix the URL";
  static final String MESSAGE_OTHER_VERSION =
      "is not under " + BASE + ": link the latest docs through a key of " + LINKS_FILE_NAME;

  private final ObjectMapper mapper =
      new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

  /** A validated links map: the full URL of each key, sorted by key. */
  public record LinksMap(SortedMap<String, String> urls) {}

  /**
   * Reads and validates the links map.
   *
   * @throws InvalidDocsLinksException when the file is missing, is not valid JSON, or breaks the
   *     expected shape (base URL, key format, page paths)
   */
  public LinksMap read(Path linksFile) throws InvalidDocsLinksException {
    String json;
    try {
      json = Files.readString(linksFile, StandardCharsets.UTF_8);
    } catch (NoSuchFileException e) {
      throw new InvalidDocsLinksException(MESSAGE_FILE_NOT_FOUND + linksFile);
    } catch (IOException e) {
      throw new InvalidDocsLinksException(
          "Cannot read docs links file " + linksFile + ": " + e.getMessage());
    }

    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new InvalidDocsLinksException(
          MESSAGE_INVALID_JSON + linksFile + ": " + e.getOriginalMessage() + location(e));
    }
    if (root == null || !root.isObject()) {
      throw new InvalidDocsLinksException(linksFile + " " + MESSAGE_SHAPE);
    }

    List<String> errors = new ArrayList<>();
    for (Iterator<String> names = root.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      if (!TOP_LEVEL_FIELDS.contains(name)) {
        errors.add(MESSAGE_UNKNOWN_FIELD + "\"" + name + "\"");
      }
    }
    JsonNode base = root.get("base");
    if (base == null || !base.isTextual() || !BASE.equals(base.asText())) {
      errors.add(MESSAGE_BASE + ", not " + base);
    }

    JsonNode links = root.get("links");
    SortedMap<String, String> urls = new TreeMap<>();
    if (links == null || !links.isObject()) {
      errors.add(MESSAGE_LINKS);
    } else {
      for (Iterator<Map.Entry<String, JsonNode>> it = links.fields(); it.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = it.next();
        String path = readPath(entry.getKey(), entry.getValue(), errors);
        if (path != null) {
          urls.put(entry.getKey(), BASE + path);
        }
      }
    }

    if (!errors.isEmpty()) {
      throw new InvalidDocsLinksException(
          "Invalid docs links file " + linksFile + ":\n  - " + String.join("\n  - ", errors));
    }
    return new LinksMap(urls);
  }

  /**
   * Lists the docs URLs written in a file (e.g. the {@code see also} comments of {@code
   * application.properties}) that are not links of the map, as {@code file:line: url ...} messages.
   * A URL of another docs version, or of the bare site, is reported too: the latest docs are linked
   * only through the map.
   */
  public List<String> unknownLinks(Path file, LinksMap links) throws IOException {
    Set<String> known = new HashSet<>(links.urls().values());
    List<String> unknown = new ArrayList<>();
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    for (int i = 0; i < lines.size(); i++) {
      Matcher matcher = DOCS_URL.matcher(lines.get(i));
      while (matcher.find()) {
        String url = stripTrailingPunctuation(matcher.group());
        String where = file + ":" + (i + 1) + ": " + url + " ";
        if (!url.startsWith(BASE)) {
          unknown.add(where + MESSAGE_OTHER_VERSION);
        } else if (!known.contains(url)) {
          unknown.add(where + MESSAGE_UNKNOWN_URL);
        }
      }
    }
    return unknown;
  }

  /**
   * Renders the Java source of the constants class, formatted as google-java-format (spotless)
   * would. The output depends only on the links, so an unchanged map gives a byte-identical file.
   */
  public String generate(SortedMap<String, String> urls, String packageName, String className) {
    StringBuilder out = new StringBuilder();
    out.append("// Generated from ")
        .append(LINKS_FILE_NAME)
        .append(" by the docs-links goal of openaev-maven-plugin. Do not edit:\n")
        .append("// change ")
        .append(LINKS_FILE_NAME)
        .append(" and build openaev-api with Maven, which rewrites this file.\n")
        .append("package ")
        .append(packageName)
        .append(";\n\n")
        .append("/** Links to the OpenAEV documentation, one constant per key of ")
        .append(LINKS_FILE_NAME)
        .append(". */\n")
        .append("public final class ")
        .append(className)
        .append(" {\n");
    urls.forEach(
        (key, url) -> {
          String declaration = "  public static final String " + constantName(key) + " =";
          String literal = "\"" + javaString(url) + "\";";
          out.append("\n  /** Key {@code ").append(key).append("}. */\n");
          if (declaration.length() + 1 + literal.length() <= MAX_LINE_LENGTH) {
            out.append(declaration).append(' ').append(literal).append('\n');
          } else {
            out.append(declaration).append("\n      ").append(literal).append('\n');
          }
        });
    out.append("\n  private ").append(className).append("() {}\n}\n");
    return out.toString();
  }

  /** Name of the constant generated for a key: {@code configuration_imap -> CONFIGURATION_IMAP}. */
  public static String constantName(String key) {
    return key.toUpperCase(Locale.ROOT);
  }

  /**
   * Checks a link path: only URL characters, relative to the base (no scheme, no host, no leading
   * {@code /}), and the part before the first {@code #} (a {@code ?query} included) empty or ending
   * with {@code /}, as MkDocs builds every page as a directory URL.
   *
   * @return the problem, or {@code null} when the path is valid
   */
  static String pathProblem(String path) {
    if (!URL_CHARACTERS.matcher(path).matches()) {
      return MESSAGE_CHARACTERS;
    }
    if (SCHEME.matcher(path).find() || path.startsWith("/")) {
      return MESSAGE_RELATIVE;
    }
    int anchor = path.indexOf('#');
    String page = anchor < 0 ? path : path.substring(0, anchor);
    if (!page.isEmpty() && !page.endsWith("/")) {
      return MESSAGE_PAGE;
    }
    return null;
  }

  static String stripTrailingPunctuation(String url) {
    int end = url.length();
    while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
      end--;
    }
    return url.substring(0, end);
  }

  private static String readPath(String key, JsonNode node, List<String> errors) {
    if (!KEY_PATTERN.matcher(key).matches()) {
      errors.add("key \"" + key + "\" " + MESSAGE_KEY);
      return null;
    }
    if (key.length() > MAX_KEY_LENGTH) {
      errors.add("key \"" + key + "\" " + MESSAGE_KEY_LENGTH);
      return null;
    }
    if (node == null || !node.isTextual()) {
      errors.add("key \"" + key + "\" " + MESSAGE_NOT_STRING);
      return null;
    }
    String path = node.asText();
    String problem = pathProblem(path);
    if (problem != null) {
      errors.add("key \"" + key + "\": " + node + " " + problem);
      return null;
    }
    return path;
  }

  /**
   * Escapes a value for a Java string literal. Validation only lets URL characters through; the
   * escapes are a backstop so that a value can never break the generated source. Line terminators
   * use their escape sequences, not {@code \\u}, which javac would turn back into a line break.
   */
  static String javaString(String value) {
    StringBuilder escaped = new StringBuilder();
    for (char c : value.toCharArray()) {
      switch (c) {
        case '\\' -> escaped.append("\\\\");
        case '"' -> escaped.append("\\\"");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (c < 0x20 || c == 0x7f) {
            escaped.append("\\%03o".formatted((int) c));
          } else if (c > 0x7f) {
            escaped.append("\\u%04x".formatted((int) c));
          } else {
            escaped.append(c);
          }
        }
      }
    }
    return escaped.toString();
  }

  private static String location(JsonProcessingException e) {
    return e.getLocation() == null
        ? ""
        : " (line %d, column %d)"
            .formatted(e.getLocation().getLineNr(), e.getLocation().getColumnNr());
  }

  /** The links map is missing or does not follow the expected shape. */
  public static class InvalidDocsLinksException extends Exception {
    public InvalidDocsLinksException(String message) {
      super(message);
    }
  }
}

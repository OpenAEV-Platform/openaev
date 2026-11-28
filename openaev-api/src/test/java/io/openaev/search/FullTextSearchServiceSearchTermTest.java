package io.openaev.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The search box text is not a tsquery: it is forwarded verbatim by the frontend and turned into
 * {@code to_tsquery('simple', ...)}. Every word therefore has to leave this conversion quoted, or
 * the characters a user can legitimately type become tsquery syntax - an interior double space, a
 * {@code &}, a parenthesis or a colon all made Postgres reject the query with {@code syntax error
 * in tsquery}, which reached the client as a 500.
 */
@DisplayName("FullTextSearchService search term to tsquery conversion")
class FullTextSearchServiceSearchTermTest {

  private static String finalSearchTerm(String userInput) throws Exception {
    Method method =
        FullTextSearchService.class.getDeclaredMethod("getFinalSearchTerm", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, userInput);
  }

  @Test
  @DisplayName("given_one_word, should produce_one_quoted_prefix_lexeme")
  void given_oneWord_should_produceOneQuotedPrefixLexeme() throws Exception {
    assertThat(finalSearchTerm("acme")).isEqualTo("('acme':*)");
  }

  @Test
  @DisplayName("given_words_separated_by_several_spaces, should_and_the_lexemes")
  void given_wordsSeparatedBySeveralSpaces_should_andTheLexemes() throws Exception {
    // Arrange: the interior double space used to leave an empty lexeme, (:*), and a syntax error

    // Act / Assert
    assertThat(finalSearchTerm("acme  corp")).isEqualTo("('acme':*) & ('corp':*)");
  }

  @Test
  @DisplayName("given_leading_and_trailing_spaces, should_not_produce_empty_lexemes")
  void given_leadingAndTrailingSpaces_should_notProduceEmptyLexemes() throws Exception {
    assertThat(finalSearchTerm("   acme corp\t")).isEqualTo("('acme':*) & ('corp':*)");
  }

  @Test
  @DisplayName("given_tsquery_operators, should_keep_them_inside_a_lexeme")
  void given_tsqueryOperators_should_keepThemInsideALexeme() throws Exception {
    // Arrange: each of these used to be read as tsquery syntax and rejected by Postgres

    // Act / Assert
    assertThat(finalSearchTerm("a & b")).isEqualTo("('a':*) & ('&':*) & ('b':*)");
    assertThat(finalSearchTerm("(acme")).isEqualTo("('(acme':*)");
    assertThat(finalSearchTerm("foo:bar")).isEqualTo("('foo:bar':*)");
    assertThat(finalSearchTerm("!foo")).isEqualTo("('!foo':*)");
  }

  @Test
  @DisplayName("given_a_quote_or_a_backslash, should escape_it_for_the_tsquery_parser")
  void given_aQuoteOrABackslash_should_escapeItForTheTsqueryParser() throws Exception {
    assertThat(finalSearchTerm("O'Brien")).isEqualTo("('O\\'Brien':*)");
    assertThat(finalSearchTerm("a\\b")).isEqualTo("('a\\\\b':*)");
  }

  @Test
  @DisplayName("given_a_value_the_parser_tokenizes, should_leave_matching_unchanged")
  void given_aValueTheParserTokenizes_should_leaveMatchingUnchanged() throws Exception {
    // A quoted lexeme is still tokenized by the same parser as the documents, so C++ keeps
    // searching 'c':* - the fix closes the syntax hole without changing what a word matches.
    assertThat(finalSearchTerm("C++")).isEqualTo("('C++':*)");
    assertThat(finalSearchTerm("127.0.0.1")).isEqualTo("('127.0.0.1':*)");
  }
}

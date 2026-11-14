package io.openaev.service.targets.search;

import static io.openaev.utils.InjectContentUtils.MANUAL_TARGETS_CONTENT_KEY;
import static io.openaev.utils.InjectContentUtils.MANUAL_TARGET_SELECTOR;
import static io.openaev.utils.InjectContentUtils.TARGET_SELECTOR_CONTENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.database.model.ManualTarget;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
@DisplayName("Manual target search adaptor")
class ManualTargetSearchAdaptorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private HelperTargetSearchAdaptor helperTargetSearchAdaptor;
  @InjectMocks private ManualTargetSearchAdaptor adaptor;

  @BeforeEach
  void setUp() {
    lenient()
        .when(helperTargetSearchAdaptor.buildTargetWithExpectations(any(), any(), eq(true)))
        .thenAnswer(invocation -> ((Supplier<InjectTarget>) invocation.getArgument(1)).get());
  }

  private static Inject manualInject(String targets) {
    ObjectNode content = MAPPER.createObjectNode();
    content.put(TARGET_SELECTOR_CONTENT_KEY, MANUAL_TARGET_SELECTOR);
    content.put(MANUAL_TARGETS_CONTENT_KEY, targets);
    Inject inject = new Inject();
    inject.setId("inject-1");
    inject.setContent(content);
    return inject;
  }

  private static SearchPaginationInput page(int page, int size, String textSearch) {
    SearchPaginationInput input = new SearchPaginationInput();
    input.setPage(page);
    input.setSize(size);
    input.setTextSearch(textSearch);
    return input;
  }

  private static String nameOf(InjectTarget target) {
    return ((ManualTarget) target).getName();
  }

  @Nested
  @DisplayName("Search")
  class Search {

    @Test
    @DisplayName("given_manualTargets_should_returnOneTargetPerValue")
    void given_manualTargets_should_returnOneTargetPerValue() {
      // Arrange
      Inject inject = manualInject("127.0.0.1,scanme.nmap.org,10.0.0.0/24");

      // Act
      Page<InjectTarget> result = adaptor.search(page(0, 20, null), inject);

      // Assert
      assertThat(result.getTotalElements()).isEqualTo(3);
      assertThat(result.getContent())
          .extracting(ManualTargetSearchAdaptorTest::nameOf)
          .containsExactly("127.0.0.1", "scanme.nmap.org", "10.0.0.0/24");
      assertThat(result.getContent())
          .allSatisfy(
              target -> {
                assertThat(target.getTargetType()).isEqualTo("MANUAL");
                assertThat(target.getId()).isEqualTo(ManualTarget.idOf(nameOf(target)));
                assertThat(target.getId()).doesNotContain("/");
              });
    }

    @Test
    @DisplayName("given_textSearch_should_keepMatchingTargetsOnly")
    void given_textSearch_should_keepMatchingTargetsOnly() {
      // Arrange
      Inject inject = manualInject("127.0.0.1,scanme.nmap.org");

      // Act
      Page<InjectTarget> result = adaptor.search(page(0, 20, "NMAP"), inject);

      // Assert
      assertThat(result.getContent())
          .extracting(ManualTargetSearchAdaptorTest::nameOf)
          .containsExactly("scanme.nmap.org");
    }

    @Test
    @DisplayName("given_secondPage_should_returnRemainingTargets")
    void given_secondPage_should_returnRemainingTargets() {
      // Arrange
      Inject inject = manualInject("a.example.com,b.example.com,c.example.com");

      // Act
      Page<InjectTarget> result = adaptor.search(page(1, 2, null), inject);

      // Assert
      assertThat(result.getTotalElements()).isEqualTo(3);
      assertThat(result.getContent())
          .extracting(ManualTargetSearchAdaptorTest::nameOf)
          .containsExactly("c.example.com");
    }

    @Test
    @DisplayName("given_injectNotInManualMode_should_returnNoTarget")
    void given_injectNotInManualMode_should_returnNoTarget() {
      // Arrange
      Inject inject = new Inject();
      inject.setContent(MAPPER.createObjectNode().put(TARGET_SELECTOR_CONTENT_KEY, "assets"));

      // Act
      Page<InjectTarget> result = adaptor.search(page(0, 20, null), inject);

      // Assert
      assertThat(result.getContent()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Options")
  class Options {

    @Test
    @DisplayName("given_textSearch_should_returnMatchingOptionsKeyedByTargetId")
    void given_textSearch_should_returnMatchingOptionsKeyedByTargetId() {
      // Arrange
      Inject inject = manualInject("127.0.0.1,scanme.nmap.org");

      // Act
      List<FilterUtilsJpa.Option> options = adaptor.getOptionsForInject(inject, "127");

      // Assert
      assertThat(options)
          .containsExactly(new FilterUtilsJpa.Option(ManualTarget.idOf("127.0.0.1"), "127.0.0.1"));
    }
  }
}

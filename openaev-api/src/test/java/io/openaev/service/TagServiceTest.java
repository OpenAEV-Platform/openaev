package io.openaev.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.tag.TagService;
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.rest.tag.form.TagUpdateInput;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TagServiceTest {

  @Mock private TagRepository tagRepository;

  @Spy @InjectMocks private TagService tagService;

  @Captor private ArgumentCaptor<Tag> tagCaptor;

  /* ============================================================
   * tagSet — Retrieve tags by IDs
   * ============================================================ */
  @Nested
  class TagSet {

    @Test
    void shouldReturnSetOfTagsForGivenIds() {
      // -------- Prepare --------
      String id1 = UUID.randomUUID().toString();
      String id2 = UUID.randomUUID().toString();
      List<String> tagIds = List.of(id1, id2);

      Tag tag1 = createTag(id1, "tag1", "#000001");
      Tag tag2 = createTag(id2, "tag2", "#000002");

      when(tagRepository.findAllById(tagIds)).thenReturn(List.of(tag1, tag2));

      // -------- Act --------
      Set<Tag> result = tagService.tagSet(tagIds);

      // -------- Assert --------
      assertEquals(2, result.size());
      assertTrue(result.contains(tag1));
      assertTrue(result.contains(tag2));

      verify(tagRepository).findAllById(tagIds);
      verifyNoMoreInteractions(tagRepository);
    }

    @Test
    void shouldReturnEmptySetWhenNoTagsFound() {
      // -------- Prepare --------
      List<String> tagIds = List.of(UUID.randomUUID().toString());

      when(tagRepository.findAllById(tagIds)).thenReturn(Collections.emptyList());

      // -------- Act --------
      Set<Tag> result = tagService.tagSet(tagIds);

      // -------- Assert --------
      assertTrue(result.isEmpty());

      verify(tagRepository).findAllById(tagIds);
      verifyNoMoreInteractions(tagRepository);
    }

    @Test
    void shouldHandleEmptyIdList() {
      // -------- Prepare --------
      List<String> tagIds = Collections.emptyList();

      when(tagRepository.findAllById(tagIds)).thenReturn(Collections.emptyList());

      // -------- Act --------
      Set<Tag> result = tagService.tagSet(tagIds);

      // -------- Assert --------
      assertTrue(result.isEmpty());

      verify(tagRepository).findAllById(tagIds);
      verifyNoMoreInteractions(tagRepository);
    }
  }

  /* ============================================================
   * createTag — Create tag with name only (uses well-known or random color)
   * ============================================================ */
  @Nested
  class CreateTagWithNameOnly {

    @Test
    void shouldCreateTagWithWellKnownColorForKnownName() {
      // -------- Prepare --------
      String wellKnownName = Tag.OPENCTI_TAG_NAME;
      String expectedColor = Tag.WellKnown.get(wellKnownName);

      Tag existingOrNewTag = createTag(UUID.randomUUID().toString(), wellKnownName, expectedColor);

      // upsertTag will be called; mock findByName to return empty so new tag is created
      when(tagRepository.findByName(wellKnownName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(existingOrNewTag);

      // -------- Act --------
      Tag result = tagService.createTag(wellKnownName);

      // -------- Assert --------
      assertNotNull(result);

      verify(tagRepository).findByName(wellKnownName.toLowerCase());
      verify(tagRepository).save(tagCaptor.capture());

      Tag savedTag = tagCaptor.getValue();
      assertEquals(expectedColor.toLowerCase(), savedTag.getColor());
    }

    @Test
    void shouldCreateTagWithRandomColorForUnknownName() {
      // -------- Prepare --------
      String unknownName = "unknown-tag-name";
      Tag newTag = createTag(UUID.randomUUID().toString(), unknownName, "#abcdef");

      when(tagRepository.findByName(unknownName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

      // -------- Act --------
      Tag result = tagService.createTag(unknownName);

      // -------- Assert --------
      assertNotNull(result);

      verify(tagRepository).findByName(unknownName.toLowerCase());
      verify(tagRepository).save(tagCaptor.capture());

      Tag savedTag = tagCaptor.getValue();
      // Color should be set (random, but not null)
      assertNotNull(savedTag.getColor());
    }

    @Test
    void shouldReturnExistingTagIfFoundByName() {
      // -------- Prepare --------
      String tagName = "existing-tag";
      Tag existingTag = createTag(UUID.randomUUID().toString(), tagName, "#123456");

      when(tagRepository.findByName(tagName.toLowerCase())).thenReturn(Optional.of(existingTag));

      // -------- Act --------
      Tag result = tagService.createTag(tagName);

      // -------- Assert --------
      assertSame(existingTag, result);

      verify(tagRepository).findByName(tagName.toLowerCase());
      verify(tagRepository, never()).save(any());
    }
  }

  /* ============================================================
   * upsertTag — Create or return existing tag
   * ============================================================ */
  @Nested
  class UpsertTag {

    @Test
    void shouldReturnExistingTagWhenFound() {
      // -------- Prepare --------
      String tagName = "existing";
      TagCreateInput input = createTagCreateInput(tagName, "#aabbcc");

      Tag existingTag = createTag(UUID.randomUUID().toString(), tagName, "#aabbcc");

      when(tagRepository.findByName(tagName.toLowerCase())).thenReturn(Optional.of(existingTag));

      // -------- Act --------
      Tag result = tagService.upsertTag(input);

      // -------- Assert --------
      assertSame(existingTag, result);

      verify(tagRepository).findByName(tagName.toLowerCase());
      verify(tagRepository, never()).save(any());
    }

    @Test
    void shouldCreateNewTagWhenNotFound() {
      // -------- Prepare --------
      String tagName = "new-tag";
      String tagColor = "#ff0000";
      TagCreateInput input = createTagCreateInput(tagName, tagColor);

      Tag newTag = createTag(UUID.randomUUID().toString(), tagName, tagColor);

      when(tagRepository.findByName(tagName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

      // -------- Act --------
      Tag result = tagService.upsertTag(input);

      // -------- Assert --------
      assertNotNull(result);

      verify(tagRepository).findByName(tagName.toLowerCase());
      verify(tagRepository).save(tagCaptor.capture());

      Tag savedTag = tagCaptor.getValue();
      assertEquals(tagName.toLowerCase(), savedTag.getName());
      assertEquals(tagColor.toLowerCase(), savedTag.getColor());
    }

    @Test
    void shouldHandleCaseSensitiveNameLookup() {
      // -------- Prepare --------
      String tagName = "MixedCase";
      TagCreateInput input = createTagCreateInput(tagName, "#123456");

      Tag existingTag = createTag(UUID.randomUUID().toString(), tagName.toLowerCase(), "#123456");

      when(tagRepository.findByName(tagName.toLowerCase())).thenReturn(Optional.of(existingTag));

      // -------- Act --------
      Tag result = tagService.upsertTag(input);

      // -------- Assert --------
      assertSame(existingTag, result);

      verify(tagRepository).findByName(tagName.toLowerCase());
    }
  }

  /* ============================================================
   * updateTag — Update existing tag
   * ============================================================ */
  @Nested
  class UpdateTag {

    @Test
    void shouldUpdateTagWhenFound() {
      // -------- Prepare --------
      String tagId = UUID.randomUUID().toString();
      String newName = "updated-name";
      String newColor = "#ffffff";
      TagUpdateInput input = createTagUpdateInput(newName, newColor);

      Tag existingTag = createTag(tagId, "old-name", "#000000");

      when(tagRepository.findById(tagId)).thenReturn(Optional.of(existingTag));
      when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

      // -------- Act --------
      Tag result = tagService.updateTag(tagId, input);

      // -------- Assert --------
      assertNotNull(result);
      assertEquals(newName.toLowerCase(), result.getName());
      assertEquals(newColor.toLowerCase(), result.getColor());
      assertNotNull(result.getUpdatedAt());

      verify(tagRepository).findById(tagId);
      verify(tagRepository).save(tagCaptor.capture());

      Tag savedTag = tagCaptor.getValue();
      assertSame(existingTag, savedTag);
    }

    @Test
    void shouldThrowElementNotFoundExceptionWhenTagNotFound() {
      // -------- Prepare --------
      String tagId = UUID.randomUUID().toString();
      TagUpdateInput input = createTagUpdateInput("name", "#000000");

      when(tagRepository.findById(tagId)).thenReturn(Optional.empty());

      // -------- Act + Assert --------
      assertThrows(ElementNotFoundException.class, () -> tagService.updateTag(tagId, input));

      verify(tagRepository).findById(tagId);
      verify(tagRepository, never()).save(any());
    }
  }

  /* ============================================================
   * findOrCreateTagsFromNames — Find or create multiple tags
   * ============================================================ */
  @Nested
  class FindOrCreateTagsFromNames {

    @Test
    void shouldReturnEmptySetWhenNamesIsNull() {
      // -------- Prepare --------
      Set<String> names = null;

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertTrue(result.isEmpty());

      verifyNoInteractions(tagRepository);
    }

    @Test
    void shouldReturnEmptySetWhenNamesIsEmpty() {
      // -------- Prepare --------
      Set<String> names = Collections.emptySet();

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertTrue(result.isEmpty());

      verifyNoInteractions(tagRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    void shouldSkipNullBlankOrWhitespaceNames(String invalidName) {
      // -------- Prepare --------
      Set<String> names = new HashSet<>();
      names.add(invalidName);

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertTrue(result.isEmpty());

      verifyNoInteractions(tagRepository);
    }

    @Test
    void shouldCreateTagsForValidNames() {
      // -------- Prepare --------
      String name1 = "tag-one";
      String name2 = "tag-two";
      Set<String> names = Set.of(name1, name2);

      Tag tag1 = createTag(UUID.randomUUID().toString(), name1, "#111111");
      Tag tag2 = createTag(UUID.randomUUID().toString(), name2, "#222222");

      when(tagRepository.findByName(name1.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.findByName(name2.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class)))
          .thenAnswer(
              invocation -> {
                Tag arg = invocation.getArgument(0);
                if (name1.toLowerCase().equals(arg.getName())) {
                  return tag1;
                } else {
                  return tag2;
                }
              });

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertEquals(2, result.size());
      assertTrue(result.contains(tag1));
      assertTrue(result.contains(tag2));

      verify(tagRepository, times(2)).findByName(any());
      verify(tagRepository, times(2)).save(any(Tag.class));
    }

    @Test
    void shouldReturnExistingTagsWhenFound() {
      // -------- Prepare --------
      String existingName = "existing-tag";
      Set<String> names = Set.of(existingName);

      Tag existingTag = createTag(UUID.randomUUID().toString(), existingName, "#333333");

      when(tagRepository.findByName(existingName.toLowerCase()))
          .thenReturn(Optional.of(existingTag));

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertEquals(1, result.size());
      assertTrue(result.contains(existingTag));

      verify(tagRepository).findByName(existingName.toLowerCase());
      verify(tagRepository, never()).save(any());
    }

    @Test
    void shouldHandleMixOfExistingAndNewTags() {
      // -------- Prepare --------
      String existingName = "existing";
      String newName = "new-tag";
      Set<String> names = Set.of(existingName, newName);

      Tag existingTag = createTag(UUID.randomUUID().toString(), existingName, "#444444");
      Tag newTag = createTag(UUID.randomUUID().toString(), newName, "#555555");

      when(tagRepository.findByName(existingName.toLowerCase()))
          .thenReturn(Optional.of(existingTag));
      when(tagRepository.findByName(newName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertEquals(2, result.size());
      assertTrue(result.contains(existingTag));
      assertTrue(result.contains(newTag));

      verify(tagRepository, times(2)).findByName(any());
      verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    void shouldSkipNullInMixedSet() {
      // -------- Prepare --------
      String validName = "valid-tag";
      Set<String> names = new HashSet<>();
      names.add(validName);
      names.add(null);
      names.add("");
      names.add("   ");

      Tag validTag = createTag(UUID.randomUUID().toString(), validName, "#666666");

      when(tagRepository.findByName(validName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(validTag);

      // -------- Act --------
      Set<Tag> result = tagService.findOrCreateTagsFromNames(names);

      // -------- Assert --------
      assertEquals(1, result.size());
      assertTrue(result.contains(validTag));

      verify(tagRepository).findByName(validName.toLowerCase());
      verify(tagRepository).save(any(Tag.class));
    }
  }

  /* ============================================================
   * ensureWellKnownTags — Ensure all well-known tags exist
   * ============================================================ */
  @Nested
  class EnsureWellKnownTags {

    @Test
    void shouldCreateAllWellKnownTagsWhenNoneExist() {
      // -------- Prepare --------
      int wellKnownTagCount = Tag.WellKnown.size();

      // All findByName calls return empty
      when(tagRepository.findByName(any())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class)))
          .thenAnswer(
              invocation -> {
                Tag arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID().toString());
                return arg;
              });

      // -------- Act --------
      Set<Tag> result = tagService.ensureWellKnownTags();

      // -------- Assert --------
      assertEquals(wellKnownTagCount, result.size());

      verify(tagRepository, times(wellKnownTagCount)).findByName(any());
      verify(tagRepository, times(wellKnownTagCount)).save(any(Tag.class));
    }

    @Test
    void shouldReturnExistingWellKnownTagsWhenAllExist() {
      // -------- Prepare --------
      Map<String, Tag> existingTags = new HashMap<>();
      for (Map.Entry<String, String> entry : Tag.WellKnown.entrySet()) {
        Tag tag = createTag(UUID.randomUUID().toString(), entry.getKey(), entry.getValue());
        existingTags.put(entry.getKey(), tag);
      }

      when(tagRepository.findByName(any()))
          .thenAnswer(
              invocation -> {
                String name = invocation.getArgument(0);
                return Optional.ofNullable(existingTags.get(name));
              });

      // -------- Act --------
      Set<Tag> result = tagService.ensureWellKnownTags();

      // -------- Assert --------
      assertEquals(Tag.WellKnown.size(), result.size());

      verify(tagRepository, times(Tag.WellKnown.size())).findByName(any());
      verify(tagRepository, never()).save(any());
    }

    @Test
    void shouldHandleMixOfExistingAndMissingWellKnownTags() {
      // -------- Prepare --------
      // Make half the tags exist
      Iterator<Map.Entry<String, String>> iterator = Tag.WellKnown.entrySet().iterator();
      Map<String, Tag> existingTags = new HashMap<>();
      Set<String> missingNames = new HashSet<>();

      int count = 0;
      while (iterator.hasNext()) {
        Map.Entry<String, String> entry = iterator.next();
        if (count % 2 == 0) {
          Tag tag = createTag(UUID.randomUUID().toString(), entry.getKey(), entry.getValue());
          existingTags.put(entry.getKey(), tag);
        } else {
          missingNames.add(entry.getKey());
        }
        count++;
      }

      when(tagRepository.findByName(any()))
          .thenAnswer(
              invocation -> {
                String name = invocation.getArgument(0);
                return Optional.ofNullable(existingTags.get(name));
              });

      when(tagRepository.save(any(Tag.class)))
          .thenAnswer(
              invocation -> {
                Tag arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID().toString());
                return arg;
              });

      // -------- Act --------
      Set<Tag> result = tagService.ensureWellKnownTags();

      // -------- Assert --------
      assertEquals(Tag.WellKnown.size(), result.size());

      verify(tagRepository, times(Tag.WellKnown.size())).findByName(any());
      verify(tagRepository, times(missingNames.size())).save(any(Tag.class));
    }

    @ParameterizedTest(name = "Should create well-known tag: {0}")
    @MethodSource("wellKnownTagsProvider")
    void shouldCreateCorrectWellKnownTag(String tagName, String expectedColor) {
      // -------- Prepare --------
      when(tagRepository.findByName(any())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class)))
          .thenAnswer(
              invocation -> {
                Tag arg = invocation.getArgument(0);
                arg.setId(UUID.randomUUID().toString());
                return arg;
              });

      // -------- Act --------
      Set<Tag> result = tagService.ensureWellKnownTags();

      // -------- Assert --------
      // Find the tag with the expected name
      Optional<Tag> foundTag = result.stream().filter(t -> tagName.equals(t.getName())).findFirst();
      assertTrue(foundTag.isPresent(), "Expected to find tag with name: " + tagName);
      assertEquals(expectedColor.toLowerCase(), foundTag.get().getColor());
    }

    static Stream<Arguments> wellKnownTagsProvider() {
      return Tag.WellKnown.entrySet().stream()
          .map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }
  }

  /* ============================================================
   * Edge cases and boundary conditions
   * ============================================================ */
  @Nested
  class EdgeCases {

    @Test
    void shouldHandleDuplicateIdsInTagSet() {
      // -------- Prepare --------
      String id1 = UUID.randomUUID().toString();
      List<String> tagIds = List.of(id1, id1, id1);

      Tag tag1 = createTag(id1, "tag1", "#000001");

      when(tagRepository.findAllById(tagIds)).thenReturn(List.of(tag1));

      // -------- Act --------
      Set<Tag> result = tagService.tagSet(tagIds);

      // -------- Assert --------
      assertEquals(1, result.size());
      assertTrue(result.contains(tag1));
    }

    @Test
    void shouldHandleSpecialCharactersInTagName() {
      // -------- Prepare --------
      String specialName = "tag with spaces & special: chars!";
      TagCreateInput input = createTagCreateInput(specialName, "#abcdef");

      Tag newTag = createTag(UUID.randomUUID().toString(), specialName, "#abcdef");

      when(tagRepository.findByName(specialName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

      // -------- Act --------
      Tag result = tagService.upsertTag(input);

      // -------- Assert --------
      assertNotNull(result);

      verify(tagRepository).findByName(specialName.toLowerCase());
      verify(tagRepository).save(any(Tag.class));
    }

    @Test
    void shouldHandleUnicodeInTagName() {
      // -------- Prepare --------
      String unicodeName = "tag-\u00e9\u00e0\u00fc-unicode";
      TagCreateInput input = createTagCreateInput(unicodeName, "#ffffff");

      Tag newTag = createTag(UUID.randomUUID().toString(), unicodeName, "#ffffff");

      when(tagRepository.findByName(unicodeName.toLowerCase())).thenReturn(Optional.empty());
      when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

      // -------- Act --------
      Tag result = tagService.upsertTag(input);

      // -------- Assert --------
      assertNotNull(result);

      verify(tagRepository).findByName(unicodeName.toLowerCase());
    }
  }

  /* ============================================================
   * Helpers
   * ============================================================ */

  private Tag createTag(String id, String name, String color) {
    Tag tag = new Tag();
    tag.setId(id);
    tag.setName(name);
    tag.setColor(color);
    return tag;
  }

  private TagCreateInput createTagCreateInput(String name, String color) {
    TagCreateInput input = new TagCreateInput();
    input.setName(name);
    input.setColor(color);
    return input;
  }

  private TagUpdateInput createTagUpdateInput(String name, String color) {
    TagUpdateInput input = new TagUpdateInput();
    input.setName(name);
    input.setColor(color);
    return input;
  }
}

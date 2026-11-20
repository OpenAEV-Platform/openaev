package io.openaev.service.marking_definition;

/**
 * One data row of an imported marking file, as text. The boundary between how the file is read and
 * the validation of a row, so the validation does not depend on the file format.
 *
 * @param position 1-based position of the data row, the first row under the header being 1
 */
public record MarkingRow(
    int position, String type, String definition, String color, String order) {}

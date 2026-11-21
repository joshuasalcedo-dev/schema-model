package io.joshuasalcedo.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextFormatTest {

    @Test
    void textFieldsSayHowTheyAreWritten() {
        Model note = Model.valueObject("Note")
                .field(Field.text("title").required())
                .field(Field.text("body").format(TextFormat.MARKDOWN))
                .field(Field.text("html").format(TextFormat.HTML))
                .field(Field.text("remarks").format(TextFormat.MULTILINE))
                .build();
        assertThat(SchemaValidator.validate(note)).isEmpty();
        assertThat(note.field("title").orElseThrow().format()).isEqualTo(TextFormat.PLAIN);
        assertThat(note.field("body").orElseThrow().format().multiline()).isTrue();
        assertThat(note.field("body").orElseThrow().toBuilder().build()).isEqualTo(note.field("body").orElseThrow());
    }

    @Test
    void onlyTextHasAFormat() {
        Model bad = Model.valueObject("Bad").field(Field.integer("count").format(TextFormat.HTML)).build();
        assertThat(SchemaValidator.validate(bad)).extracting(SchemaProblem::message)
                .anyMatch(m -> m.contains("format is only for TEXT fields"));
    }
}

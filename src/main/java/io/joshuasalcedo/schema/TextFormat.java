package io.joshuasalcedo.schema;

/**
 * How a {@link FieldType#TEXT} value is written and shown. It changes the editor a form offers and how the value is
 * displayed, never what may be stored: limits are set with the field's length and pattern.
 */
public enum TextFormat {

    /** One line of text: a name, a code, a title. The default. */
    PLAIN,
    /** Several lines of plain text: notes, an address block, remarks. */
    MULTILINE,
    /** Markdown: headings, lists, emphasis and links, shown formatted. */
    MARKDOWN,
    /** HTML from a rich-text editor, shown formatted. Sanitise it before showing it in a browser. */
    HTML;

    /**
     * Whether the value may have line breaks.
     *
     * @return false only for {@link #PLAIN}
     */
    public boolean multiline() {
        return this != PLAIN;
    }
}

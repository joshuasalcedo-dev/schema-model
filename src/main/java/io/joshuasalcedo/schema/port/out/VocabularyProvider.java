package io.joshuasalcedo.schema.port.out;

import io.joshuasalcedo.schema.Vocabulary;

/**
 * Outbound port: the glossary terms models may point to. Read again for each change, because a glossary can change
 * between calls. A service backs it with its glossary; {@code Vocabulary::open} accepts any term.
 */
@FunctionalInterface
public interface VocabularyProvider {

    /**
     * The terms as they are now.
     *
     * @return the current vocabulary
     */
    Vocabulary current();
}

package io.joshuasalcedo.schema;

import java.util.Collection;
import java.util.Set;

/**
 * The glossary terms that models and fields may point to. The validator asks it whether each term a model uses
 * exists. An application can back this with its glossary; on its own, use {@link #open()} to
 * accept any term or {@link #of(Collection)} to check against a fixed list.
 */
@FunctionalInterface
public interface Vocabulary {

    /**
     * Whether {@code term} may be used.
     *
     * @param term a term's name, never empty
     * @return true if the term exists
     */
    boolean hasTerm(String term);

    /**
     * A vocabulary that accepts every term, for when terms are not checked.
     *
     * @return a vocabulary accepting everything
     */
    static Vocabulary open() {
        return term -> true;
    }

    /**
     * A vocabulary of exactly these terms.
     *
     * @param terms the allowed terms; copied
     * @return a vocabulary accepting only them
     */
    static Vocabulary of(Collection<String> terms) {
        Set<String> known = Set.copyOf(terms);
        return known::contains;
    }

    /**
     * A vocabulary of exactly these terms.
     *
     * @param terms the allowed terms
     * @return a vocabulary accepting only them
     */
    static Vocabulary of(String... terms) {
        return of(Set.of(terms));
    }
}

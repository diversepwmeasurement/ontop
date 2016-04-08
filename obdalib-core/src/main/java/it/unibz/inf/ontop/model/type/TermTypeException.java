package it.unibz.inf.ontop.model.type;

import it.unibz.inf.ontop.exception.OntopRuntimeException;
import it.unibz.inf.ontop.model.Term;
import it.unibz.inf.ontop.model.TermType;

import java.util.Optional;

/**
 * TODO: better integrate into the Ontop exception hierarchy
 *
 * TODO: explain
 */
public class TermTypeException extends OntopRuntimeException {
    public TermTypeException(Term term, TermType expectedTermType, TermType actualTermType) {
        this(term, expectedTermType, actualTermType, Optional.empty());
    }

    public TermTypeException(Term term, TermType expectedTermType, TermType actualTermType, String additionalMessage) {
        this(term, expectedTermType, actualTermType, Optional.of(additionalMessage));
    }

    private TermTypeException(Term term, TermType expectedTermType, TermType actualTermType,
                              Optional<String> optionalMessage) {
        super("Incompatible type infered for " + term + ": expected: " + expectedTermType
                + ", actual: " + actualTermType + optionalMessage.map(m -> ". " + m).orElse(""));
    }
}

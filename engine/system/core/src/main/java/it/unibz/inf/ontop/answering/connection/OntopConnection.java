package it.unibz.inf.ontop.answering.connection;

import it.unibz.inf.ontop.query.KGQueryFactory;
import it.unibz.inf.ontop.exception.OntopConnectionException;

public interface OntopConnection extends OBDAConnection {

	@Override
	OntopStatement createStatement() throws OntopConnectionException;

	KGQueryFactory getInputQueryFactory();

}

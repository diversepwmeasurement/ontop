package it.unibz.inf.ontop.docker.lightweight.presto;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.docker.lightweight.AbstractDistinctInAggregateTest;
import it.unibz.inf.ontop.docker.lightweight.PrestoLightweightTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.sql.SQLException;

@PrestoLightweightTest
public class DistinctInAggregatePrestoTest extends AbstractDistinctInAggregateTest {

    private static final String PROPERTIES_FILE = "/university/university-presto.properties";

    @BeforeAll
    public static void before() throws IOException, SQLException {
        initOBDA(OBDA_FILE, OWL_FILE, PROPERTIES_FILE);
    }

    @AfterAll
    public static void after() throws SQLException {
        release();
    }

    @Override
    protected ImmutableSet<ImmutableMap<String, String>> getTuplesForAvg() {
        return ImmutableSet.of(
                ImmutableMap.of(
                        "p",buildAnswerIRI("1"),
                        "ad", "\"10.5\"^^xsd:decimal"
                ),
                ImmutableMap.of(
                        "p",buildAnswerIRI("3"),
                        "ad", "\"12.0\"^^xsd:decimal"
                ),
                ImmutableMap.of(
                        "p",buildAnswerIRI("8"),
                        "ad", "\"13.0\"^^xsd:decimal"
                )
        );
    }

    @Override
    protected ImmutableSet<ImmutableMap<String, String>> getTuplesForConcat() {
        return ImmutableSet.of(
                ImmutableMap.of(
                        "p", buildAnswerIRI("1"),
                        "sd", "\"11|10\"^^xsd:string"
                ),
                ImmutableMap.of(
                        "p", buildAnswerIRI("3"),
                        "sd", "\"12\"^^xsd:string"
                ),
                ImmutableMap.of(
                        "p", buildAnswerIRI("8"),
                        "sd", "\"13\"^^xsd:string"
                ));
    }
}

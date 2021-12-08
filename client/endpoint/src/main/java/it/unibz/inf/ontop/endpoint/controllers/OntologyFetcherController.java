package it.unibz.inf.ontop.endpoint.controllers;

import it.unibz.inf.ontop.endpoint.beans.PortalConfigComponent;
import it.unibz.inf.ontop.injection.OntopSQLOWLAPIConfiguration;
import it.unibz.inf.ontop.rdf4j.repository.impl.OntopVirtualRepository;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

/**
 * @author Davide Lanti
 */
@RestController
public class OntologyFetcherController {

    private final OntopSQLOWLAPIConfiguration configuration;

    // Davide> The useless "repository" argument is apparently required by Spring. If not provided, then
    //         the instantiation of the configuration object fails. Can some expert of Spring explain me
    //         what is going on here?
    @Autowired
    public OntologyFetcherController(OntopVirtualRepository repository, OntopSQLOWLAPIConfiguration configuration) {
        this.configuration = configuration;
    }

    @RequestMapping(value = "/ontology",
            method = {RequestMethod.GET}
    )
    @ResponseBody
    public ResponseEntity<String> ontology() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CONTENT_TYPE, "application/rdf+xml;charset=UTF-8");
        OWLOntology ontology = null;
        String output = null;
        OutputStream out = new ByteArrayOutputStream();
        try{
            ontology = configuration.loadInputOntology().get();
            boolean downOnto = configuration.getSettings().downloadOntology();
            if( true || downOnto ){
                ontology.getOWLOntologyManager().saveOntology(ontology, out);
                output = out.toString();
                out.close();
            }
            return new ResponseEntity<>(output, headers, HttpStatus.OK);
        } catch (OWLOntologyCreationException | OWLOntologyStorageException | IOException e) {
            e.printStackTrace();
        }
        // Forbidden
        return new ResponseEntity<>("Forbidden", headers, HttpStatus.FORBIDDEN);
    }
}
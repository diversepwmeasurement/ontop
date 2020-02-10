package it.unibz.inf.ontop.spec.mapping.transformer.impl;

/*
 * #%L
 * ontop-reformulation-core
 * %%
 * Copyright (C) 2009 - 2014 Free University of Bozen-Bolzano
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.google.common.collect.*;
import com.google.inject.Inject;
import it.unibz.inf.ontop.constraints.impl.ImmutableCQContainmentCheckUnderLIDs;
import it.unibz.inf.ontop.datalog.*;
import it.unibz.inf.ontop.exception.MinorOntopInternalBugException;
import it.unibz.inf.ontop.injection.IntermediateQueryFactory;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.tools.UnionBasedQueryMerger;
import it.unibz.inf.ontop.iq.transform.NoNullValueEnforcer;
import it.unibz.inf.ontop.model.atom.AtomFactory;
import it.unibz.inf.ontop.model.atom.RDFAtomPredicate;
import it.unibz.inf.ontop.model.atom.RelationPredicate;
import it.unibz.inf.ontop.model.term.IRIConstant;
import it.unibz.inf.ontop.model.term.ImmutableTerm;
import it.unibz.inf.ontop.model.term.TermFactory;
import it.unibz.inf.ontop.model.vocabulary.RDF;
import it.unibz.inf.ontop.spec.mapping.MappingAssertion;
import it.unibz.inf.ontop.spec.mapping.MappingAssertionIndex;
import it.unibz.inf.ontop.spec.mapping.TMappingExclusionConfig;
import it.unibz.inf.ontop.spec.mapping.transformer.MappingCQCOptimizer;
import it.unibz.inf.ontop.spec.ontology.*;
import it.unibz.inf.ontop.substitution.SubstitutionFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.apache.commons.rdf.api.IRI;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TMappingProcessor {

	// TODO: the implementation of EXCLUDE ignores equivalent classes / properties

	private final AtomFactory atomFactory;
	private final TermFactory termFactory;
    private final QueryUnionSplitter unionSplitter;
    private final UnionFlattener unionNormalizer;
    private final MappingCQCOptimizer mappingCqcOptimizer;
    private final NoNullValueEnforcer noNullValueEnforcer;
    private final IntermediateQueryFactory iqFactory;
    private final UnionBasedQueryMerger queryMerger;
    private final SubstitutionFactory substitutionFactory;

    @Inject
	private TMappingProcessor(AtomFactory atomFactory,
                              TermFactory termFactory,
                              QueryUnionSplitter unionSplitter,
                              UnionFlattener unionNormalizer,
                              MappingCQCOptimizer mappingCqcOptimizer,
                              NoNullValueEnforcer noNullValueEnforcer,
                              IntermediateQueryFactory iqFactory,
                              UnionBasedQueryMerger queryMerger,
                              SubstitutionFactory substitutionFactory) {
		this.atomFactory = atomFactory;
		this.termFactory = termFactory;
        this.unionSplitter = unionSplitter;
        this.unionNormalizer = unionNormalizer;
        this.mappingCqcOptimizer = mappingCqcOptimizer;
        this.noNullValueEnforcer = noNullValueEnforcer;
        this.iqFactory = iqFactory;
        this.queryMerger = queryMerger;
        this.substitutionFactory = substitutionFactory;
    }


	/**
	 * constructs the TMappings using DAG
	 * @param mapping
	 * @param reasoner
	 * @return
	 */

	public ImmutableList<MappingAssertion> getTMappings(ImmutableList<MappingAssertion> mapping, ClassifiedTBox reasoner, TMappingExclusionConfig excludeFromTMappings, ImmutableCQContainmentCheckUnderLIDs<RelationPredicate> cqContainmentCheck) {

	    if (mapping.isEmpty())
	        return mapping;

	    // index mapping assertions by the predicate type
        //     same IRI can be a class name and a property name
        //     but the same IRI cannot be an object and a data or annotation property name at the same time
        // see https://www.w3.org/TR/owl2-new-features/#F12:_Punning

        ImmutableMultimap<MappingAssertionIndex, TMappingRule> source = mapping.stream()
                .flatMap(a -> unionSplitter.splitUnion(unionNormalizer.optimize(a.getQuery()))
                        .map(IQ::normalizeForOptimization) // replaces join equalities
                        .map(q -> mappingCqcOptimizer.optimize(cqContainmentCheck, q))
                        .map(q -> Maps.immutableEntry(a.getIndex(), new TMappingRule(q, termFactory, atomFactory))))
                .collect(ImmutableCollectors.toMultimap());

        RDFAtomPredicate rdfTriple = source.keySet().iterator().next().getPredicate();
        MappingRuleHeadTransformer transformer = new MappingRuleHeadTransformer(rdfTriple, termFactory);

        ImmutableMap<MappingAssertionIndex, ImmutableList<TMappingRule>> saturated = Stream.concat(Stream.concat(
                saturate(reasoner.objectPropertiesDAG(),
                        p -> !p.isInverse() && !excludeFromTMappings.contains(p), source,
                        transformer::transformer, cqContainmentCheck, (r, p) -> !p.isInverse() || p.getInverse() != r),

                saturate(reasoner.dataPropertiesDAG(),
                        p -> !excludeFromTMappings.contains(p), source,
                        transformer::transformer, cqContainmentCheck, (r, p) -> true)),

                saturate(reasoner.classesDAG(),
                        s -> (s instanceof OClass) && !excludeFromTMappings.contains((OClass)s), source,
                        transformer::transformer, cqContainmentCheck, (r, c) -> c instanceof OClass))

                .collect(ImmutableCollectors.toMap());

        ImmutableMap<MappingAssertionIndex, IQ> entries = Stream.concat(
                saturated.entrySet().stream(),
                source.asMap().entrySet().stream()
                        // probably required for vocabulary terms that are not in the ontology
                        // also, for all "excluded" mappings
                        .filter(e -> !saturated.containsKey(e.getKey()))
                        .map(e -> Maps.immutableEntry(e.getKey(), e.getValue().stream()
                                .collect(TMappingEntry.toTMappingEntry(cqContainmentCheck, termFactory)))))
                .collect(ImmutableCollectors.toMap(
                        Map.Entry::getKey,
                        // In case some legacy implementations do not preserve IS_NOT_NULL conditions
                        e -> noNullValueEnforcer.transform(
                                queryMerger.mergeDefinitions(e.getValue().stream()
                                        .map(r -> r.asIQ(iqFactory, termFactory, substitutionFactory))
                                        .collect(ImmutableCollectors.toList())).get())
                                .normalizeForOptimization()));

        return entries.entrySet().stream()
                .map(e -> new MappingAssertion(e.getKey(), e.getValue(), null))
                .collect(ImmutableCollectors.toList());
    }

    private <T> Stream<Map.Entry<MappingAssertionIndex, ImmutableList<TMappingRule>>> saturate(EquivalencesDAG<T> dag,
                                                                                               Predicate<T> representativeFilter,
                                                                                               ImmutableMultimap<MappingAssertionIndex, TMappingRule> originalMappingIndex,
                                                                                               Function<T, EntityRuleHeadTransformer> transformer,
                                                                                               ImmutableCQContainmentCheckUnderLIDs<RelationPredicate> cqc,
                                                                                               BiPredicate<T, T> populationFilter) {

	    java.util.function.BiFunction<T, T, java.util.function.Function<TMappingRule, TMappingRule>> headReplacer =
                (d, rep) -> (m -> new TMappingRule(transformer.apply(d).getArguments(m.getHeadTerms(), transformer.apply(rep).getIri()), m));

	    ImmutableMap<MappingAssertionIndex, ImmutableList<TMappingRule>> representatives = dag.stream()
                .filter(node -> representativeFilter.test(node.getRepresentative()))
                .collect(ImmutableCollectors.toMap(
                        node -> transformer.apply(node.getRepresentative()).indexOf(),
                        node -> saturate(transformer.apply(node.getRepresentative()).getIri(),
                                dag.getSub(node), originalMappingIndex, transformer, cqc)));

	    return dag.stream()
                .filter(node -> representativeFilter.test(node.getRepresentative()))
                .flatMap(node -> node.getMembers().stream()
                    .filter(d -> populationFilter.test(node.getRepresentative(), d))
                    .map(populate(transformer, saturate(transformer.apply(node.getRepresentative()).getIri(),
                            dag.getSub(node), originalMappingIndex, transformer, cqc))))
                .filter(e -> !e.getValue().isEmpty());
    }

    private <T> Function<T, Map.Entry<MappingAssertionIndex, ImmutableList<TMappingRule>>> populate(Function<T, EntityRuleHeadTransformer> transformer, ImmutableList<TMappingRule> saturated) {
	    return d -> Maps.immutableEntry(transformer.apply(d).indexOf(),
                        saturated.stream()
                                .map(m -> new TMappingRule(transformer.apply(d).getArguments(m.getHeadTerms(), transformer.apply(d).getIri()), m))
                                .collect(ImmutableCollectors.toList()));
    }

    private <T> ImmutableList<TMappingRule> saturate(IRI iri,
                                                     ImmutableSet<Equivalences<T>> sub,
                                                     ImmutableMultimap<MappingAssertionIndex, TMappingRule> originalMappingIndex,
                                                     Function<T, EntityRuleHeadTransformer> transformer,
                                                     ImmutableCQContainmentCheckUnderLIDs<RelationPredicate> cqc) {

        return sub.stream()
                .flatMap(subnode -> subnode.getMembers().stream())
                .map(transformer)
                .flatMap(t -> originalMappingIndex.get(t.indexOf()).stream()
                        .map(m -> new TMappingRule(t.getArguments(m.getHeadTerms(), iri), m)))
                .collect(TMappingEntry.toTMappingEntry(cqc, termFactory));
    }

    private interface EntityRuleHeadTransformer {
        MappingAssertionIndex indexOf();

        default IRI getIri() { return indexOf().getIri(); }

        ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri);
    }

    private static class MappingRuleHeadTransformer {
	    private final RDFAtomPredicate rdfAtomPredicate;
	    private final TermFactory termFactory;

        MappingRuleHeadTransformer(RDFAtomPredicate rdfAtomPredicate, TermFactory termFactory) {
            this.rdfAtomPredicate = rdfAtomPredicate;
            this.termFactory = termFactory;
        }

        EntityRuleHeadTransformer transformer(ClassExpression ce) {
            if (ce instanceof OClass) {
                OClass oc = (OClass)ce;
                return new EntityRuleHeadTransformer() {
                    @Override
                    public MappingAssertionIndex indexOf() {
                        return MappingAssertionIndex.ofClass(rdfAtomPredicate, oc.getIRI());
                    }
                    @Override
                    public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                        return ImmutableList.of(args.get(0), getConstantIRI(RDF.TYPE), getConstantIRI(newIri));
                    }
                };
            }
            else if (ce instanceof ObjectSomeValuesFrom) {
                ObjectPropertyExpression ope = ((ObjectSomeValuesFrom) ce).getProperty();
                return new EntityRuleHeadTransformer() {
                    @Override
                    public MappingAssertionIndex indexOf() {
                        return MappingAssertionIndex.ofProperty(rdfAtomPredicate, ope.getIRI());
                    }
                    @Override
                    public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                        return ImmutableList.of(args.get(ope.isInverse() ? 2 : 0), getConstantIRI(RDF.TYPE), getConstantIRI(newIri));
                    }
                };
            }
            else if (ce instanceof DataSomeValuesFrom) {
                DataPropertyExpression dpe = ((DataSomeValuesFrom) ce).getProperty();
                return new EntityRuleHeadTransformer() {
                    @Override
                    public MappingAssertionIndex indexOf() {
                        return MappingAssertionIndex.ofProperty(rdfAtomPredicate, dpe.getIRI());
                    }
                    @Override
                    public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                        return ImmutableList.of(args.get(0), getConstantIRI(RDF.TYPE), getConstantIRI(newIri));
                    }
                };
            }
            else
                throw new MinorOntopInternalBugException("Unexpected type" + ce);
        }

        EntityRuleHeadTransformer transformer(ObjectPropertyExpression ope) {
            return new EntityRuleHeadTransformer() {
                @Override
                public MappingAssertionIndex indexOf() {
                    return MappingAssertionIndex.ofProperty(rdfAtomPredicate, ope.getIRI());
                }
                @Override
                public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                    return ope.isInverse()
                            ? ImmutableList.of(args.get(2), getConstantIRI(newIri), args.get(0))
                            : ImmutableList.of(args.get(0), getConstantIRI(newIri), args.get(2));
                }
            };
        }

        EntityRuleHeadTransformer transformer(DataPropertyExpression dpe) {
            return new EntityRuleHeadTransformer() {
                @Override
                public MappingAssertionIndex indexOf() {
                    return MappingAssertionIndex.ofProperty(rdfAtomPredicate, dpe.getIRI());
                }
                @Override
                public ImmutableList<ImmutableTerm> getArguments(ImmutableList<ImmutableTerm> args, IRI newIri) {
                    return ImmutableList.of(args.get(0), getConstantIRI(newIri), args.get(2));
                }
            };
        }

        private IRIConstant getConstantIRI(IRI iri) {
            return termFactory.getConstantIRI(iri);
        }

    }

}

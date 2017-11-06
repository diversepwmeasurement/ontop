package it.unibz.inf.ontop.model.term.impl;

/*
 * #%L
 * ontop-obdalib-core
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

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unibz.inf.ontop.model.term.functionsymbol.ExpressionOperation;
import it.unibz.inf.ontop.model.term.functionsymbol.OperationPredicate;
import it.unibz.inf.ontop.model.term.functionsymbol.Predicate;
import it.unibz.inf.ontop.model.type.*;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.type.impl.URITemplatePredicateImpl;
import it.unibz.inf.ontop.model.vocabulary.RDF;
import it.unibz.inf.ontop.model.vocabulary.XSD;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import org.apache.commons.rdf.api.IRI;

import java.util.List;
import java.util.stream.IntStream;

@Singleton
public class TermFactoryImpl implements TermFactory {

	private final TermType rootTermType;
	private final TypeFactory typeFactory;
	private final ValueConstant valueTrue;
	private final ValueConstant valueFalse;
	private final ValueConstant valueNull;
	private final ImmutabilityTools immutabilityTools;

	@Inject
	private TermFactoryImpl(TypeFactory typeFactory) {
		// protected constructor prevents instantiation from other classes.
		this.typeFactory = typeFactory;
		RDFDatatype xsdBoolean = typeFactory.getXsdBooleanDatatype();
		this.valueTrue = new ValueConstantImpl("true", xsdBoolean);
		this.valueFalse = new ValueConstantImpl("false", xsdBoolean);
		this.valueNull = new ValueConstantImpl("null", typeFactory.getXsdStringDatatype());
		this.rootTermType = typeFactory.getAbstractAtomicTermType();
		this.immutabilityTools = new ImmutabilityTools(this);
	}

	@Deprecated
	public PredicateImpl getPredicate(String name, int arity) {
		ImmutableList<TermType> expectedArgumentTypes = IntStream.range(0, arity)
				.boxed()
				.map(i -> rootTermType)
				.collect(ImmutableCollectors.toList());

			return new PredicateImpl(name, arity, expectedArgumentTypes);
//		}
	}
	
	@Override
	public Predicate getPredicate(String uri, ImmutableList<TermType> types) {
		return new PredicateImpl(uri, types.size(), types);
	}

	@Override
	@Deprecated
	public URIConstant getConstantURI(String uriString) {
		return new URIConstantImpl(uriString, typeFactory);
	}
	
	@Override
	public ValueConstant getConstantLiteral(String value) {
		return new ValueConstantImpl(value, typeFactory.getXsdStringDatatype());
	}

	@Override
	public ValueConstant getConstantLiteral(String value, RDFDatatype type) {
		return new ValueConstantImpl(value, type);
	}

	@Override
	public ValueConstant getConstantLiteral(String value, IRI type) {
		return getConstantLiteral(value, typeFactory.getDatatype(type));
	}

	@Override
	public Function getTypedTerm(Term value, TermType type) {
		Predicate pred = typeFactory.getRequiredTypePredicate(type);
		if (pred == null)
			throw new RuntimeException("Unknown data type!");
		
		return getFunction(pred, value);
	}
	
	@Override
	public ValueConstant getConstantLiteral(String value, String language) {
		return new ValueConstantImpl(value, language.toLowerCase(), typeFactory);
	}

	@Override
	public Function getTypedTerm(Term value, Term language) {
		Predicate pred = typeFactory.getRequiredTypePredicate(RDF.LANGSTRING);
		return getFunction(pred, value, language);
	}

	@Override
	public Function getTypedTerm(Term value, String language) {
		Term lang = getConstantLiteral(language.toLowerCase(), typeFactory.getXsdStringDatatype());
		Predicate pred = typeFactory.getRequiredTypePredicate(RDF.LANGSTRING);
		return getFunction(pred, value, lang);
	}

	@Override
	public ImmutableFunctionalTerm getImmutableTypedTerm(ImmutableTerm value, TermType type) {
		Predicate pred = typeFactory.getRequiredTypePredicate(type);
		if (pred == null)
			throw new RuntimeException("Unknown data type: " + type);

		return getImmutableFunctionalTerm(pred, value);
	}

	@Override
	public ImmutableFunctionalTerm getImmutableTypedTerm(ImmutableTerm value, String language) {
		ValueConstant lang = getConstantLiteral(language.toLowerCase(), XSD.STRING);
		Predicate pred = typeFactory.getRequiredTypePredicate(RDF.LANGSTRING);
		return getImmutableFunctionalTerm(pred, value, lang);
	}

	@Override
	public Variable getVariable(String name) {
		return new VariableImpl(name);
	}

	@Override
	public Function getFunction(Predicate functor, Term... arguments) {
		if (functor instanceof OperationPredicate) {
			return getExpression((OperationPredicate)functor, arguments);
		}

		// Default constructor
		return new FunctionalTermImpl(functor, arguments);
	}
	
	@Override
	public Expression getExpression(OperationPredicate functor, Term... arguments) {
		return new ExpressionImpl(functor, arguments);
	}

	@Override
	public Expression getExpression(OperationPredicate functor, List<Term> arguments) {
		return new ExpressionImpl(functor, arguments);
	}

	@Override
	public ImmutableExpression getImmutableExpression(OperationPredicate functor, ImmutableTerm... arguments) {
		return getImmutableExpression(functor, ImmutableList.copyOf(arguments));
	}

	@Override
	public ImmutableExpression getImmutableExpression(OperationPredicate functor,
													  ImmutableList<? extends ImmutableTerm> arguments) {
		if (GroundTermTools.areGroundTerms(arguments)) {
			return new GroundExpressionImpl(functor, (ImmutableList<GroundTerm>)arguments);
		}
		else {
			return new NonGroundExpressionImpl(functor, arguments);
		}
	}

	@Override
	public ImmutableExpression getImmutableExpression(Expression expression) {
		if (GroundTermTools.isGroundTerm(expression)) {
			return new GroundExpressionImpl(expression.getFunctionSymbol(),
					(ImmutableList<? extends GroundTerm>)(ImmutableList<?>)convertTerms(expression));
		}
		else {
			return new NonGroundExpressionImpl(expression.getFunctionSymbol(), convertTerms(expression));
		}
	}

	@Override
	public Function getFunction(Predicate functor, List<Term> arguments) {
		if (functor instanceof OperationPredicate) {
			return getExpression((OperationPredicate) functor, arguments);
		}

		// Default constructor
		return new FunctionalTermImpl(functor, arguments);
	}

	@Override
	public ImmutableFunctionalTerm getImmutableFunctionalTerm(Predicate functor, ImmutableList<ImmutableTerm> terms) {
		if (functor instanceof OperationPredicate) {
			return getImmutableExpression((OperationPredicate)functor, terms);
		}

		if (GroundTermTools.areGroundTerms(terms)) {
			return new GroundFunctionalTermImpl(functor, terms);
		}
		else {
			// Default constructor
			return new NonGroundFunctionalTermImpl(functor, terms);
		}
	}

	@Override
	public ImmutableFunctionalTerm getImmutableFunctionalTerm(Predicate functor, ImmutableTerm... terms) {
		return getImmutableFunctionalTerm(functor, ImmutableList.copyOf(terms));
	}

	@Override
	public ImmutableFunctionalTerm getImmutableFunctionalTerm(Function functionalTerm) {
		if (GroundTermTools.isGroundTerm(functionalTerm)) {
			return new GroundFunctionalTermImpl(functionalTerm);
		}
		else {
			return new NonGroundFunctionalTermImpl(functionalTerm.getFunctionSymbol(), convertTerms(functionalTerm));
		}

	}

	@Override
	public NonGroundFunctionalTerm getNonGroundFunctionalTerm(Predicate functor, ImmutableTerm... terms) {
		return new NonGroundFunctionalTermImpl(functor, terms);
	}

	@Override
	public NonGroundFunctionalTerm getNonGroundFunctionalTerm(Predicate functor, ImmutableList<ImmutableTerm> terms) {
		return new NonGroundFunctionalTermImpl(functor, terms);
	}

	public TypeFactory getTypeFactory() {
		return typeFactory;
	}

	@Override
	public Function getUriTemplate(Term... terms) {
		Predicate uriPred = typeFactory.getURITemplatePredicate(terms.length);
		return getFunction(uriPred, terms);		
	}

	@Override
	public ImmutableFunctionalTerm getImmutableUriTemplate(ImmutableTerm... terms) {
		Predicate pred = typeFactory.getURITemplatePredicate(terms.length);
		return getImmutableFunctionalTerm(pred, terms);
	}

	@Override
	public ImmutableFunctionalTerm getImmutableUriTemplate(ImmutableList<ImmutableTerm> terms) {
		Predicate pred = typeFactory.getURITemplatePredicate(terms.size());
		return getImmutableFunctionalTerm(pred, terms);
	}

	@Override
	public Function getUriTemplate(List<Term> terms) {
		Predicate uriPred = typeFactory.getURITemplatePredicate(terms.size());
		return getFunction(uriPred, terms);		
	}

	@Override
	public Function getUriTemplateForDatatype(String type) {
		return getFunction(typeFactory.getURITemplatePredicate(1), getConstantLiteral(type));
	}
	
	@Override
	public Function getBNodeTemplate(Term... terms) {
		Predicate pred = new BNodePredicateImpl(terms.length, typeFactory);
		return getFunction(pred, terms);
	}

	@Override
	public ImmutableFunctionalTerm getImmutableBNodeTemplate(ImmutableTerm... terms) {
		Predicate pred = new BNodePredicateImpl(terms.length, typeFactory);
		return getImmutableFunctionalTerm(pred, terms);
	}

	@Override
	public ImmutableFunctionalTerm getImmutableBNodeTemplate(ImmutableList<ImmutableTerm> terms) {
		Predicate pred = new BNodePredicateImpl(terms.size(), typeFactory);
		return getImmutableFunctionalTerm(pred, terms);
	}

	@Override
	public Function getBNodeTemplate(List<Term> terms) {
		Predicate pred = new BNodePredicateImpl(terms.size(), typeFactory);
		return getFunction(pred, terms);
	}

	@Override
	public Expression getFunctionEQ(Term firstTerm, Term secondTerm) {
		return getExpression(ExpressionOperation.EQ, firstTerm, secondTerm);
	}

	@Override
	public Expression getFunctionGTE(Term firstTerm, Term secondTerm) {
		return getExpression(ExpressionOperation.GTE, firstTerm, secondTerm);
	}

	@Override
	public Expression getFunctionGT(Term firstTerm, Term secondTerm) {
		return getExpression(ExpressionOperation.GT, firstTerm, secondTerm);
	}

	@Override
	public Expression getFunctionLTE(Term firstTerm, Term secondTerm) {
		return getExpression(ExpressionOperation.LTE, firstTerm, secondTerm);
	}

	@Override
	public Expression getFunctionLT(Term firstTerm, Term secondTerm) {
		return getExpression(ExpressionOperation.LT, firstTerm, secondTerm);
	}

	@Override
	public Expression getFunctionNEQ(Term firstTerm, Term secondTerm) {
		return getExpression(ExpressionOperation.NEQ, firstTerm, secondTerm);
	}

	@Override
	public Expression getFunctionNOT(Term term) {
		return getExpression(ExpressionOperation.NOT, term);
	}

	@Override
	public Expression getFunctionAND(Term term1, Term term2) {
		return getExpression(ExpressionOperation.AND, term1, term2);
	}

//	@Override
//	public Function getANDFunction(List<Term> terms) {
//		if (terms.size() < 2) {
//			throw new IllegalArgumentException("AND requires at least 2 terms");
//		}
//		LinkedList<Term> auxTerms = new LinkedList<Term>();
//
//		if (terms.size() == 2) {
//			return getFunctionalTerm(ExpressionOperation.AND, terms.get(0), terms.get(1));
//		}
//		Term nested = getFunctionalTerm(ExpressionOperation.AND, terms.get(0), terms.get(1));
//		terms.remove(0);
//		terms.remove(0);
//		while (auxTerms.size() > 1) {
//			nested = getFunctionalTerm(ExpressionOperation.AND, nested, terms.get(0));
//			terms.remove(0);
//		}
//		return getFunctionalTerm(ExpressionOperation.AND, nested, terms.get(0));
//	}

	@Override
	public Expression getFunctionOR(Term term1, Term term2) {
		return getExpression(ExpressionOperation.OR,term1, term2);
	}

	
//	@Override
//	public Function getORFunction(List<Term> terms) {
//		if (terms.size() < 2) {
//			throw new IllegalArgumentException("OR requires at least 2 terms");
//		}
//		LinkedList<Term> auxTerms = new LinkedList<Term>();
//
//		if (terms.size() == 2) {
//			return getFunctionalTerm(ExpressionOperation.OR, terms.get(0), terms.get(1));
//		}
//		Term nested = getFunctionalTerm(ExpressionOperation.OR, terms.get(0), terms.get(1));
//		terms.remove(0);
//		terms.remove(0);
//		while (auxTerms.size() > 1) {
//			nested = getFunctionalTerm(ExpressionOperation.OR, nested, terms.get(0));
//			terms.remove(0);
//		}
//		return getFunctionalTerm(ExpressionOperation.OR, nested, terms.get(0));
//	}

	@Override
	public Expression getFunctionIsNull(Term term) {
		return getExpression(ExpressionOperation.IS_NULL, term);
	}

	@Override
	public Expression getFunctionIsNotNull(Term term) {
		return getExpression(ExpressionOperation.IS_NOT_NULL, term);
	}


	@Override
	public Expression getLANGMATCHESFunction(Term term1, Term term2) {
		return getExpression(ExpressionOperation.LANGMATCHES, term1, term2);
	}

	@Override
	public Expression getSQLFunctionLike(Term term1, Term term2) {
		return getExpression(ExpressionOperation.SQL_LIKE, term1, term2);
	}

	@Override
	public Expression getFunctionCast(Term term1, Term term2) {
		// TODO implement cast function
		return getExpression(ExpressionOperation.QUEST_CAST, term1, term2);
	}

	
	@Override
	public BNode getConstantBNode(String name) {
		return new BNodeConstantImpl(name, typeFactory);
	}

	@Override
	public Expression getFunctionIsTrue(Term term) {
		return getExpression(ExpressionOperation.IS_TRUE, term);
	}


	@Override
	public ValueConstant getBooleanConstant(boolean value) {
		return value ? valueTrue : valueFalse;
	}

	@Override
	public ValueConstant getNullConstant() {
		return valueNull;
	}

	private ImmutableList<ImmutableTerm> convertTerms(Function functionalTermToClone) {
		ImmutableList.Builder<ImmutableTerm> builder = ImmutableList.builder();
		for (Term term : functionalTermToClone.getTerms()) {
			builder.add(immutabilityTools.convertIntoImmutableTerm(term));
		}
		return builder.build();
	}

}

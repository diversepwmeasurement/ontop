package it.unibz.inf.ontop.datalog.impl;

import java.util.*;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import it.unibz.inf.ontop.constraints.LinearInclusionDependencies;
import it.unibz.inf.ontop.model.atom.AtomPredicate;
import it.unibz.inf.ontop.model.atom.DataAtom;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.model.term.functionsymbol.Predicate;
import it.unibz.inf.ontop.model.term.impl.ImmutabilityTools;
import it.unibz.inf.ontop.substitution.Substitution;
import it.unibz.inf.ontop.substitution.SubstitutionBuilder;
import it.unibz.inf.ontop.utils.ImmutableCollectors;

public class CQContainmentCheckUnderLIDs {

	private final Map<ImmutableList<DataAtom<AtomPredicate>>, Multimap<Predicate, Function>> indexedCQcache = new HashMap<>();
	
	private final LinearInclusionDependencies<AtomPredicate> dependencies;
	private final TermFactory termFactory;
	private final ImmutabilityTools immutabilityTools;

	/**
	 * *@param sigma
	 * A set of ABox dependencies
	 */
	public CQContainmentCheckUnderLIDs(LinearInclusionDependencies<AtomPredicate> dependencies,
									   TermFactory termFactory,
									   ImmutabilityTools immutabilityTools) {
		this.dependencies = dependencies;
		this.termFactory = termFactory;
		this.immutabilityTools = immutabilityTools;
	}

	private ImmutableList<Function> fromI(ImmutableCollection<DataAtom<AtomPredicate>> c) {
		return c.stream()
				.map(immutabilityTools::convertToMutableFunction)
				.collect(ImmutableCollectors.toList());
	}

	private Multimap<Predicate, Function> getFactMap(ImmutableList<DataAtom<AtomPredicate>> body) {
		Multimap<Predicate, Function> factMap = indexedCQcache.get(body);
		if (factMap == null) {
			factMap = HashMultimap.create();
			for (Function atom : fromI(dependencies.chaseAllAtoms(body)))
				factMap.put(atom.getFunctionSymbol(), atom);

			indexedCQcache.put(body, factMap);
		}
		return factMap;
	}

	public Substitution computeHomomorphsim(ImmutableList<Term> h1, ImmutableList<DataAtom<AtomPredicate>> b1, ImmutableList<Term> h2, ImmutableList<DataAtom<AtomPredicate>> b2) {

		SubstitutionBuilder sb = new SubstitutionBuilder(termFactory);

		// get the substitution for the head first
		// it will ensure that all answer variables are mapped either to constants or
		//       to answer variables in the base (but not to the labelled nulls generated by the chase)
		boolean headResult = extendHomomorphism(sb, h2, h1);
		if (!headResult)
			return null;

		Substitution sub = computeSomeHomomorphism(sb, fromI(b2), getFactMap(b1));

		return sub;
	}



	@Override
	public String toString() {
		if (dependencies != null)
			return dependencies.toString();
		
		return "(empty)";
	}

	private static boolean extendHomomorphism(SubstitutionBuilder sb, List<Term> from, List<Term> to) {

		int arity = from.size();
		for (int i = 0; i < arity; i++) {
			Term fromTerm = from.get(i);
			Term toTerm = to.get(i);
			if (fromTerm instanceof Variable) {
				boolean result = sb.extend((Variable)fromTerm, toTerm);
				// if we cannot find a match, terminate the process and return false
				if (!result)
					return false;
			}
			else if (fromTerm instanceof Constant) {
				// constants must match
				if (!fromTerm.equals(toTerm))
					return false;
			}
			else /*if (fromTerm instanceof Function)*/ {
				// the to term must also be a function
				if (!(toTerm instanceof Function))
					return false;

				boolean result = extendHomomorphism(sb, (Function)fromTerm, (Function)toTerm);
				// if we cannot find a match, terminate the process and return false
				if (!result)
					return false;
			}
		}
		return true;
	}

	private static boolean extendHomomorphism(SubstitutionBuilder sb, Function from, Function to) {

		if ((from.getArity() != to.getArity()) || !(from.getFunctionSymbol().equals(to.getFunctionSymbol())))
			return false;

		return extendHomomorphism(sb, from.getTerms(), to.getTerms());
	}

	/**
	 * Extends a given substitution that maps each atom in {@code from} to match at least one atom in {@code to}
	 *
	 * @param sb
	 * @param from
	 * @param to
	 * @return
	 */
	private static Substitution computeSomeHomomorphism(SubstitutionBuilder sb, List<Function> from, Multimap<Predicate, Function> to) {

		int fromSize = from.size();
		if (fromSize == 0)
			return sb.getSubstituition();

		// stack of partial homomorphisms
		Stack<SubstitutionBuilder> sbStack = new Stack<>();
		sbStack.push(sb);
		// set the capacity to reduce memory re-allocations
		List<Stack<Function>> choicesMap = new ArrayList<>(fromSize);

		int currentAtomIdx = 0;
		while (currentAtomIdx >= 0) {
			Function currentAtom = from.get(currentAtomIdx);

			Stack<Function> choices;
			if (currentAtomIdx >= choicesMap.size()) {
				// we have never reached this atom (this is lazy initialization)
				// initializing the stack
				choices = new Stack<>();
				// add all choices for the current predicate symbol
				choices.addAll(to.get(currentAtom.getFunctionSymbol()));
				choicesMap.add(currentAtomIdx, choices);
			}
			else
				choices = choicesMap.get(currentAtomIdx);

			boolean choiceMade = false;
			while (!choices.isEmpty()) {
				SubstitutionBuilder sb1 = sb.clone(); // clone!
				choiceMade = extendHomomorphism(sb1, currentAtom, choices.pop());
				if (choiceMade) {
					// we reached the last atom
					if (currentAtomIdx == fromSize - 1)
						return sb1.getSubstituition();

					// otherwise, save the partial homomorphism
					sbStack.push(sb);
					sb = sb1;
					currentAtomIdx++;  // move to the next atom
					break;
				}
			}
			if (!choiceMade) {
				// backtracking
				// restore all choices for the current predicate symbol
				choices.addAll(to.get(currentAtom.getFunctionSymbol()));
				sb = sbStack.pop();   // restore the partial homomorphism
				currentAtomIdx--;   // move to the previous atom
			}
		}

		// checked all possible substitutions and have not found anything
		return null;
	}

}

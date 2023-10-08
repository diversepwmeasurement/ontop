package it.unibz.inf.ontop.constraints.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import it.unibz.inf.ontop.constraints.Homomorphism;
import it.unibz.inf.ontop.constraints.HomomorphismFactory;
import it.unibz.inf.ontop.dbschema.ForeignKeyConstraint;
import it.unibz.inf.ontop.dbschema.RelationDefinition;
import it.unibz.inf.ontop.iq.node.ExtensionalDataNode;
import it.unibz.inf.ontop.model.term.VariableOrGroundTerm;
import it.unibz.inf.ontop.utils.CoreUtilsFactory;
import it.unibz.inf.ontop.utils.ImmutableCollectors;
import it.unibz.inf.ontop.utils.VariableGenerator;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ExtensionalDataNodeListContainmentCheck {

    private final HomomorphismFactory homomorphismFactory;
    private final CoreUtilsFactory coreUtilsFactory;

    public ExtensionalDataNodeListContainmentCheck(HomomorphismFactory homomorphismFactory, CoreUtilsFactory coreUtilsFactory) {
        this.homomorphismFactory = homomorphismFactory;
        this.coreUtilsFactory = coreUtilsFactory;
    }

    public boolean isContainedIn(ImmutableList<? extends VariableOrGroundTerm> answerVariables1, ImmutableList<ExtensionalDataNode> nodes1, ImmutableList<? extends VariableOrGroundTerm> answerVariables2, ImmutableList<ExtensionalDataNode> nodes2) {
        System.out.println("CHECKING: " + nodes1 + " <- " + nodes2 + " (" + answerVariables1 + " <- " + answerVariables2 + ")");
        Homomorphism.Builder builder = homomorphismFactory.getHomomorphismBuilder();
        // get the substitution for the answer variables first
        // this will ensure that all answer variables are mapped either to constants or
        //       to answer variables in the base (but not to the labelled nulls generated by the chase)
        if (answerVariables1.size() != answerVariables2.size())
            return false;

        for (int i = 0; i < answerVariables1.size(); i++)
            builder.extend(answerVariables1.get(i), answerVariables2.get(i));

        if (builder.isValid()) {
            ImmutableList<ChasedExtensionalDataNode> chase = chaseExtensionalDataNodes(nodes1);
            ImmutableSet<RelationDefinition> relationsInChase = chase.stream()
                    .map(ChasedExtensionalDataNode::getRelationDefinition)
                    .collect(ImmutableCollectors.toSet());

            if (nodes2.stream()
                    .map(ExtensionalDataNode::getRelationDefinition)
                    .anyMatch(r -> !relationsInChase.contains(r)))
                return false;

            Iterator<Homomorphism> iterator = new ExtensionalDataNodeHomomorphismIteratorImpl(
                    builder.build(),
                    nodes2,
                    chase);
            return iterator.hasNext();
        }
        return false;
    }


    private ImmutableList<ChasedExtensionalDataNode> chaseExtensionalDataNodes(ImmutableList<ExtensionalDataNode> nodes) {
        VariableGenerator variableGenerator = coreUtilsFactory.createVariableGenerator(nodes.stream()
                .flatMap(n -> n.getVariables().stream())
                .collect(ImmutableCollectors.toSet()));

        ImmutableList<ChasedExtensionalDataNode> result = nodes.stream()
                .flatMap(node -> chase(node, variableGenerator))
                .collect(ImmutableCollectors.toList());

        System.out.println("CHASED: " + nodes + "\n" + "RESULT: " + result);
        return result;
    }

    private Stream<ChasedExtensionalDataNode> chase(ExtensionalDataNode node, VariableGenerator variableGenerator) {
        ChasedExtensionalDataNode chasedNode = new ChasedExtensionalDataNode(node, variableGenerator);
        return Stream.concat(Stream.of(chasedNode),
                node.getRelationDefinition().getForeignKeys().stream()
                        .map(fk -> chase(fk, chasedNode, variableGenerator))
                        .flatMap(Optional::stream));
    }

    private Optional<ChasedExtensionalDataNode> chase(ForeignKeyConstraint fk, ChasedExtensionalDataNode node, VariableGenerator variableGenerator) {
        if (fk.getComponents().stream().anyMatch(c -> c.getAttribute().isNullable()))
            return Optional.empty();

        ImmutableMap<Integer, VariableOrGroundTerm> map = fk.getComponents().stream()
                .collect(ImmutableCollectors.toMap(
                        c ->c.getReferencedAttribute().getIndex() - 1,
                        c -> node.getArgument(c.getAttribute().getIndex() - 1)));

        ChasedExtensionalDataNode chasedNode = new ChasedExtensionalDataNode(fk.getReferencedRelation(), map, variableGenerator);
        return Optional.of(chasedNode);
    }

    static class ChasedExtensionalDataNode {
        private final RelationDefinition relationDefinition;
        private final Map<Integer, VariableOrGroundTerm> argumentMap;
        private final VariableGenerator variableGenerator;

        public ChasedExtensionalDataNode(ExtensionalDataNode node, VariableGenerator variableGenerator) {
            this.relationDefinition = node.getRelationDefinition();
            this.argumentMap = new HashMap<>(node.getArgumentMap());
            this.variableGenerator = variableGenerator;
        }

        public ChasedExtensionalDataNode(RelationDefinition relationDefinition, ImmutableMap<Integer, ? extends VariableOrGroundTerm> argumentMap, VariableGenerator variableGenerator) {
            this.relationDefinition = relationDefinition;
            this.argumentMap = new HashMap<>(argumentMap);
            this.variableGenerator = variableGenerator;
        }

        public RelationDefinition getRelationDefinition() {
            return relationDefinition;
        }

        public VariableOrGroundTerm getArgument(int index) {
            return argumentMap.computeIfAbsent(index, i -> variableGenerator.generateNewVariable());
        }

        @Override
        public String toString() {
            return relationDefinition.getAtomPredicate().getName() + argumentMap;
        }
    }

}

package io.joshuasalcedo.schema.action;

import dev.cel.common.CelFunctionDecl;
import dev.cel.common.CelOverloadDecl;
import dev.cel.common.types.ListType;
import dev.cel.common.types.SimpleType;
import dev.cel.runtime.CelFunctionBinding;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.Taxonomy;

import java.util.List;
import java.util.Map;

/**
 * Functions over the schema's taxonomies and decision tables:
 * <ul>
 *   <li>{@code IS_A(taxonomy, term, ancestor)}: the term is the ancestor or under it, e.g.
 *       {@code IS_A('facility-types', record.facilityType, 'station')} for a platform under station</li>
 *   <li>{@code PARENT_OF(taxonomy, term)}: the broader term's code, or null at the top</li>
 *   <li>{@code ANCESTORS(taxonomy, term)}: the codes above it, nearest first</li>
 *   <li>{@code LABEL_OF(taxonomy, term)}: the label people see</li>
 *   <li>{@code DECIDE(table, facts)}: a decision table's outputs for the facts (a map, usually {@code record}),
 *       e.g. {@code DECIDE('sla', record).responseTime}</li>
 * </ul>
 * They need the schema, which the engine and record checks give ({@link Expressions#eval(String, Map, Schema)}).
 */
final class SchemaFunctions {

    private SchemaFunctions() {
    }

    static List<CelFunctionDecl> declarations() {
        var dyn = SimpleType.DYN;
        return List.of(
                CelFunctionDecl.newFunctionDeclaration("IS_A", CelOverloadDecl.newGlobalOverload("IS_A_dyn_dyn_dyn", SimpleType.BOOL, dyn, dyn, dyn)),
                CelFunctionDecl.newFunctionDeclaration("PARENT_OF", CelOverloadDecl.newGlobalOverload("PARENT_OF_dyn_dyn", dyn, dyn, dyn)),
                CelFunctionDecl.newFunctionDeclaration("ANCESTORS", CelOverloadDecl.newGlobalOverload("ANCESTORS_dyn_dyn", ListType.create(SimpleType.STRING), dyn, dyn)),
                CelFunctionDecl.newFunctionDeclaration("LABEL_OF", CelOverloadDecl.newGlobalOverload("LABEL_OF_dyn_dyn", dyn, dyn, dyn)),
                CelFunctionDecl.newFunctionDeclaration("DECIDE", CelOverloadDecl.newGlobalOverload("DECIDE_dyn_dyn", dyn, dyn, dyn)));
    }

    static List<CelFunctionBinding> bindings() {
        List<Class<?>> two = List.of(Object.class, Object.class);
        List<Class<?>> three = List.of(Object.class, Object.class, Object.class);
        return List.of(
                CelFunctionBinding.from("IS_A_dyn_dyn_dyn", three, a -> !ExcelFunctions.isEmpty(a[1])
                        && taxonomy(a[0]).isA(a[1].toString(), String.valueOf(a[2]))),
                CelFunctionBinding.from("PARENT_OF_dyn_dyn", two, a -> ExcelFunctions.isEmpty(a[1]) ? Expressions.celNull()
                        : taxonomy(a[0]).term(a[1].toString()).map(Taxonomy.Term::parent).filter(p -> !p.isEmpty())
                        .<Object>map(p -> p).orElse(Expressions.celNull())),
                CelFunctionBinding.from("ANCESTORS_dyn_dyn", two, a -> ExcelFunctions.isEmpty(a[1]) ? List.of()
                        : taxonomy(a[0]).ancestors(a[1].toString())),
                CelFunctionBinding.from("LABEL_OF_dyn_dyn", two, a -> ExcelFunctions.isEmpty(a[1]) ? Expressions.celNull()
                        : taxonomy(a[0]).term(a[1].toString()).<Object>map(Taxonomy.Term::label).orElse(Expressions.celNull())),
                CelFunctionBinding.from("DECIDE_dyn_dyn", two, a -> {
                    DecisionTable table = schema().decision(String.valueOf(a[0])).orElseThrow(
                            () -> new IllegalArgumentException("decision table '" + a[0] + "' was not found"));
                    Map<?, ?> facts = a[1] instanceof Map<?, ?> m ? m : Map.of();
                    @SuppressWarnings("unchecked") Map<String, ?> typed = (Map<String, ?>) facts;
                    return table.decide(typed, Expressions.shared());
                }));
    }

    private static Schema schema() {
        if (!Expressions.SCHEMA.isBound()) {
            throw new IllegalArgumentException("taxonomies and decision tables need the schema; run the expression "
                                               + "through the engine or Expressions.eval(expression, variables, schema)");
        }
        return Expressions.SCHEMA.get();
    }

    private static Taxonomy taxonomy(Object id) {
        return schema().taxonomy(String.valueOf(id)).orElseThrow(
                () -> new IllegalArgumentException("taxonomy '" + id + "' was not found"));
    }
}

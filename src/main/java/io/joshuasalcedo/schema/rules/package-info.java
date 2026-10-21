/**
 * Ready-made rules, so people add checks by picking from a list instead of writing CEL or regular expressions.
 * <ul>
 *   <li>{@link io.joshuasalcedo.schema.rules.RuleTemplates}: model rules such as "not before", "at most" or "required when";
 *       a {@link io.joshuasalcedo.schema.rules.RuleTemplate} filled in with fields of a model becomes an
 *       {@link io.joshuasalcedo.schema.Invariant}, and {@link io.joshuasalcedo.schema.rules.RuleTemplates#recognize} turns such a rule
 *       back into its template and arguments.</li>
 *   <li>{@link io.joshuasalcedo.schema.rules.PatternPresets}: named patterns for text fields (email, URL, UUID, ...), each
 *       with the clean-ups that suit it.</li>
 * </ul>
 * <pre>{@code
 * Model order = Model.entity("Order")
 *         .field(Field.text("id").identity().required())
 *         .field(Field.date("orderedOn").required())
 *         .field(Field.date("shippedOn"))
 *         .build();
 * Invariant rule = RuleTemplates.byId("not-before").orElseThrow()
 *         .create(order, Map.of("field", "shippedOn", "other", "orderedOn"));
 * // rule.expression(): !has(value.shippedOn) || value.shippedOn >= value.orderedOn
 * // rule.message():    Shipped on cannot be before ordered on
 * }</pre>
 */
package io.joshuasalcedo.schema.rules;

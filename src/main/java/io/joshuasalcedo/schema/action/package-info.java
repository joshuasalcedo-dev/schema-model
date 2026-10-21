/**
 * Actions: business logic as data. An {@link io.joshuasalcedo.schema.action.Action} takes input, checks
 * {@link io.joshuasalcedo.schema.action.Rule}s and runs {@link io.joshuasalcedo.schema.action.Step}s (set a field, call a Java handler,
 * emit an event). {@link io.joshuasalcedo.schema.action.ActionValidator} checks an action when it is defined, and
 * {@link io.joshuasalcedo.schema.action.Expressions} compiles and runs its CEL expressions.
 * <p>
 * Keep logic in three tiers: simple steps as data, small CEL expressions for rules and computed values, and named
 * Java handlers for anything bigger. Turning actions into a programming language of their own (loops, variables,
 * branches) is the usual way such platforms become unmaintainable.
 */
package io.joshuasalcedo.schema.action;

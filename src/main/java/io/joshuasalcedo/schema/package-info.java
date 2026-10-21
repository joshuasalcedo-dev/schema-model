/**
 * The platform core: describe the business in code (models), store data that always fits that description
 * (records), and define the logic as data (actions) that one engine runs.
 *
 * <h2>The idea</h2>
 * Instead of writing a new service, table and screen for every business concept, an application defines them at
 * runtime and one generic engine handles them all. Add a model or an action and it is live: stored, checked, callable
 * and visible to any generic client, with no new code. This is how metadata-driven platforms such as Salesforce or
 * ServiceNow work.
 *
 * <h2>The parts</h2>
 * <table>
 *   <caption>Packages</caption>
 *   <tr><th scope="col">Package</th><th scope="col">What it holds</th></tr>
 *   <tr><td>{@code io.joshuasalcedo.schema}</td>
 *       <td>Models: {@link io.joshuasalcedo.schema.Model} (an entity or a value object), its {@link io.joshuasalcedo.schema.Field}s,
 *       the {@link io.joshuasalcedo.schema.Schema} they form, and the rules ({@link io.joshuasalcedo.schema.SchemaValidator}) and
 *       quality checks ({@link io.joshuasalcedo.schema.SchemaAnalysis}) they follow.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.predefined}</td>
 *       <td>Predefined value objects (Money, EmailAddress, Address, ...) ready to install and use.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.text}</td>
 *       <td>Values as text for people: field and value object formats, labels.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.rules}</td>
 *       <td>Ready-made model rules (templates that become invariants) and named patterns for text fields.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.record}</td>
 *       <td>Records: stored instances of entity models, checked and converted against their model.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.action}</td>
 *       <td>Actions: business logic as data, made of input fields, CEL rules and steps.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.engine}</td>
 *       <td>The {@link io.joshuasalcedo.schema.engine.Engine} that runs everything, and the Java handlers actions can
 *       call.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.in}</td>
 *       <td>Inbound ports: what callers use ({@link io.joshuasalcedo.schema.port.in.Platform},
 *       {@link io.joshuasalcedo.schema.port.in.ModelDesign}).</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out}</td>
 *       <td>Outbound ports: what an application plugs in (storage, events, vocabulary).</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.adapter}</td>
 *       <td>Ready-made in-memory adapters for the outbound ports.</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.application}</td>
 *       <td>The model use cases ({@link io.joshuasalcedo.schema.application.ModelService}).</td></tr>
 * </table>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * Engine engine = Engine.builder().build();                 // everything in memory
 *
 * engine.models().createModel(Model.entity("Customer")
 *         .field(Field.text("id").identity().required())
 *         .field(Field.text("email").required().pattern(".+@.+"))
 *         .field(Field.choice("status", "active", "closed").required())
 *         .build());
 *
 * engine.defineAction(Action.named("CloseAccount")
 *         .on("customer")
 *         .input(Field.text("reason").required())
 *         .require("record.status == 'active'", "only active accounts can be closed")
 *         .set("status", "'closed'")
 *         .emit("customer.closed")
 *         .build());
 *
 * engine.create("customer", Map.of("id", "c-1", "email", "a@example.com", "status", "active"));
 * engine.run("CloseAccount", "c-1", Map.of("reason", "moved away"));
 * }</pre>
 *
 * <h2>Errors</h2>
 * Every refusal is a {@link io.joshuasalcedo.schema.SchemaException}: its {@code reason()} says why (invalid, not found,
 * already exists, in use, rule failed, conflict) and its {@code problems()} list every mistake at once, each with a
 * path to where it is.
 *
 */
package io.joshuasalcedo.schema;

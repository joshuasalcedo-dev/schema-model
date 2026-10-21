# schema-model

Describe a business in code, store data that always fits that description, and write its logic as data that one
engine runs. Add a model or an action at runtime and it is live: stored, checked and callable, with no new code,
table or endpoint. This is how metadata-driven platforms such as Salesforce or ServiceNow work.

- **Models**: entities and value objects with typed fields (text, numbers, dates, choices, other models, files),
  identity fields, patterns, and invariants written in CEL
- **Records**: stored instances of entity models, always checked against their model
- **Actions**: business logic as data: input fields, rules (CEL) that must hold, steps that set fields, call Java
  handlers or emit events. All or nothing
- **Ports and adapters**: storage, files and events plug in; in-memory adapters are included
- **Predefined value objects**: Money, EmailAddress, Address, countries, currencies
- One dependency: Google's [CEL](https://github.com/google/cel-java)

Documentation: <https://joshuasalcedo-dev.github.io/schema-model/>

## Add it

Maven:

```xml
<dependency>
    <groupId>io.joshuasalcedo</groupId>
    <artifactId>schema-model</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.joshuasalcedo:schema-model:0.1.0")
```

Java 25.

## Quick start

```java
Engine engine = Engine.builder().build();                 // everything in memory

engine.models().createModel(Model.entity("Customer")
        .field(Field.text("id").identity().required())
        .field(Field.text("email").required().pattern(".+@.+"))
        .field(Field.choice("status", "active", "closed").required())
        .build());

engine.defineAction(Action.named("CloseAccount")
        .on("customer")
        .input(Field.text("reason").required())
        .require("record.status == 'active'", "only active accounts can be closed")
        .set("status", "'closed'")
        .emit("customer.closed")
        .build());

engine.create("customer", Map.of("id", "c-1", "email", "a@example.com", "status", "active"));
engine.run("CloseAccount", "c-1", Map.of("reason", "moved away"));
```

A value object with a rule across its fields:

```java
Model.valueObject("DateRange")
        .field(Field.date("startDate").required())
        .field(Field.date("endDate").required())
        .invariant("value.endDate >= value.startDate", "the end date is before the start date")
        .build();
```

## Errors

Every refusal is a `SchemaException`. Its `reason()` says why (invalid, not found, already exists, in use, rule
failed, conflict), and its `problems()` list every mistake at once, each with a path to where it is, so a form can
show them all.

## Packages

| Package | What it holds |
|---|---|
| `io.joshuasalcedo.schema` | `Model`, `Field`, `Schema`, the rules (`SchemaValidator`) and quality checks (`SchemaAnalysis`) |
| `...schema.record` | Records, checked and converted against their model |
| `...schema.action` | Actions: input, CEL rules, steps |
| `...schema.engine` | The `Engine` that runs everything, and Java handlers actions can call |
| `...schema.port.in` / `port.out` | What callers use (`Platform`, `ModelDesign`); what an application plugs in (storage, events, vocabulary) |
| `...schema.adapter` | In-memory adapters for the outbound ports |
| `...schema.predefined` | Money, EmailAddress, Address, countries, currencies |
| `...schema.rules` | Ready-made rules (templates that become invariants) and named text patterns |
| `...schema.text` | Values as text for people: formats and labels |

## Build

```shell
mvn verify                    # tests, plus the sources and Javadoc jars
mvn pretty-docs:generate      # the documentation site in target/pretty-docs (pretty-docs:serve to browse it)
```

Releases: tag `vX.Y.Z` and push; the Release workflow signs and publishes to Maven Central. The Docs workflow
publishes the site to GitHub Pages on every push to main.

## Licence

[Apache License 2.0](LICENSE)

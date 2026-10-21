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

## What you implement

Nothing, to start: every port has a default, so `Engine.builder().build()` runs entirely in memory. To keep data
in your database or hear about changes, implement the interfaces in `io.joshuasalcedo.schema.port.out` (and
`ActionHandler` for custom logic) and hand them to the builder.

| Interface | Implement it to | Methods | Default |
|---|---|---|---|
| `ModelRepository` | keep models in a database | `findAll()`, `findById(id)`, `save(model)`, `delete(id)` | `InMemoryModelRepository` |
| `RecordRepository` | keep records in a database | `findAll(model)`, `find(model, id)`, `save(record)` → boolean, `delete(model, id, version)` → boolean | `InMemoryRecordRepository` |
| `ActionRepository` | keep action definitions in a database | `findAll()`, `find(name)`, `save(action)`, `delete(name)` | `InMemoryActionRepository` |
| `FileStore` | keep uploaded files (S3, R2, a disk) | `put(name, contentType, content)`, `open(location)`, `exists(location)`, `delete(location)` | `InMemoryFileStore` (`DirectoryFileStore` for a folder) |
| `RecordEvents` | hear every saved record change (message bus, audit log) | `publish(event)` | `RecordEvents.none()` |
| `ModelEvents` | hear every model change | `created(model)`, `updated(before, after)`, `deleted(model)` | `ModelEvents.none()` |
| `VocabularyProvider` | check the glossary terms models point to | `current()` | any term accepted |
| `ActionHandler` (`engine`) | run Java code an action calls by name (`Step.Call`) | `handle(context)` | none: register only what your actions call |

Everything that reaches a port has already been checked, so adapters only store and forward. The one rule an
adapter keeps itself: `RecordRepository.save` stores a record only if the stored version is `record.version() - 1`
(version 1 means "must not exist yet"), and `delete` only if the version matches; both return false otherwise. In
SQL that is `UPDATE ... WHERE version = ?` plus a unique key on `(model, id)`. That is how two writers never
overwrite each other.

Wiring them in:

```java
Engine engine = Engine.builder()
        .models(new ModelService(new JdbcModelRepository(db), myGlossary, myModelEvents))
        .records(new JdbcRecordRepository(db))
        .actions(new JdbcActionRepository(db))
        .files(new DirectoryFileStore(Path.of("uploads")))
        .events(event -> bus.publish(event.name(), event))        // RecordEvents
        .handler("billing.refund", context -> refunds.issue(context))
        .build();
```

`Jdbc...Repository`, `myGlossary` and the rest are yours; `ModelService`, `DirectoryFileStore` and the in-memory
adapters come with the library.

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

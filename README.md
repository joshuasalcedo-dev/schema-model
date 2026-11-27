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
- **Lifecycles**: the states a record goes through and the moves between them; people name and colour the states,
  code reads each state's fixed phase
- **Taxonomies**: lists or trees of terms records are classified under (`IS_A('facility-types', x, 'station')`)
- **Decision tables**: rules as a spreadsheet, one row per rule (`DECIDE('sla', record).responseTime`)
- **Spreadsheet functions** in expressions: `SUM`, `IF`, `ROUND`, `DATE_ADD`, `DAYS_BETWEEN`, `FORMAT`, `TO_CASE`,
  `IN_WORDS`, `MONEY` and more
- **Predefined value objects**: every value object of
  [io.joshuasalcedo:value-object](https://central.sonatype.com/artifact/io.joshuasalcedo/value-object) (email,
  phone number, money, IBAN, address, date range...) as a model, checked and printed by the Java class itself; plus
  file, image, video and audio
- Two dependencies: Google's [CEL](https://github.com/google/cel-java) and io.joshuasalcedo:value-object

Documentation: <https://joshuasalcedo-dev.github.io/schema-model/>

## Add it

Maven:

```xml
<dependency>
    <groupId>io.joshuasalcedo</groupId>
    <artifactId>schema-model</artifactId>
    <version>0.2.0</version>
</dependency>
```

Gradle:

```kotlin
implementation("io.joshuasalcedo:schema-model:0.2.0")
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

A lifecycle, a taxonomy and a decision table, all business data stored and changed at runtime like models:

```java
Lifecycle.named("Facility lifecycle")
        .state(State.of("active", "Active", Phase.ACTIVE).asInitial().withColor("#16A34A"))
        .state(State.of("under_maintenance", "Under maintenance", Phase.ON_HOLD))
        .state(State.of("decommissioned", "Decommissioned", Phase.RETIRED).asTerminal())
        .transition(Transition.of("start_maintenance", "Start maintenance", "under_maintenance", "active"))
        .transition(Transition.of("finish_maintenance", "Finish maintenance", "active", "under_maintenance"))
        .transition(Transition.of("decommission", "Decommission", "decommissioned", "active", "under_maintenance"))
        .build();

Taxonomy.named("Facility types")
        .term("station", "Station")
        .term("platform", "Platform", "station")     // under station
        .build();

DecisionTable.named("SLA")
        .input("priority").input("contractType")
        .output("responseTime").output("penalty")
        .row("'emergency'", "-", "'PT4H'", "1.0")
        .row("'high'", "'comprehensive'", "'PT8H'", "0.5")
        .row("-", "-", "'P5D'", "0")                 // "-" matches anything
        .build();
```

A cell is `-` (any), a value (`'high'`, `5`), a comparison (`> 1000`), a range (`[1..10]`) or a list
(`in ['a', 'b']`); the first matching row answers.

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
| `LifecycleRepository` | keep lifecycles in a database | `findAll()`, `findById(id)`, `save(lifecycle)`, `delete(id)` | `InMemoryLifecycleRepository` |
| `TaxonomyRepository` | keep taxonomies in a database | `findAll()`, `findById(id)`, `save(taxonomy)`, `delete(id)` | `InMemoryTaxonomyRepository` |
| `DecisionRepository` | keep decision tables in a database | `findAll()`, `findById(id)`, `save(table)`, `delete(id)` | `InMemoryDecisionRepository` |
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
        .models(new ModelService(new JdbcModelRepository(db), new JdbcTaxonomyRepository(db),
                                 new JdbcDecisionRepository(db), new JdbcLifecycleRepository(db),
                                 myGlossary, myModelEvents))
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
| `io.joshuasalcedo.schema` | `Model`, `Field`, `Schema`, `Lifecycle`, `Taxonomy`, `TextCase`, the rules (`SchemaValidator`) and quality checks (`SchemaAnalysis`) |
| `...schema.record` | Records, checked and converted against their model |
| `...schema.action` | Actions: input, CEL rules, steps; decision tables; the functions expressions can call |
| `...schema.engine` | The `Engine` that runs everything, and Java handlers actions can call |
| `...schema.port.in` / `port.out` | What callers use (`Platform`, `ModelDesign`); what an application plugs in (storage, events, vocabulary) |
| `...schema.adapter` | In-memory adapters for the outbound ports |
| `...schema.predefined` | `ValueObjectLibrary` (the value-object models), `CommonObjects` (person, company, bank account, currency, time zones, recurrence...), `MediaObjects` (file, image, video, audio) |
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

## Predefined value objects

`CommonObjects.installAll(engine.models())` adds every predefined value object, in dependency order:

- **`ValueObjectLibrary`**: a model for each value object of io.joshuasalcedo:value-object, named after its class
  (`Email` is `email`, `PhoneNumber` is `phone-number`). Fields come from the record's components; a field is
  required when Java requires it (`@NullMarked` without `@Nullable`); enums, and interfaces such as `Currency`, are
  choices. A value is checked by the Java class and stored as Java cleaned it up, given as text or as an object.
  Descriptions, limits and cross-field rules are kept with the models (`value-object-metadata.tsv`). Technical value
  objects (`jwt-token`, `hashed-password`, `table`, `pdf-file`) get no model.
- **`CommonObjects`**: value objects the library has no class for, written by hand: `person`, `company`,
  `bank-account`, `currency`, `ip-address`, `ssh-connection`, `time-zone`, `zoned-date-time`, `time-of-day`,
  `effective-period`, `time-block`, `recurrence`.
- **`MediaObjects`**: `file`, `image`, `video`, `audio`, for uploads.

```java
CommonObjects.installAll(engine.models());
engine.models().createModel(Model.entity("Customer")
        .field(Field.text("id").identity().required())
        .field(Field.model("email", "email").required())
        .field(Field.model("phone", "phone-number"))
        .build());

engine.create("customer", Map.of("id", "c-1", "email", " Mia@Example.com ", "phone", "+63 917 123 4567"));
// email: {value=mia@example.com}, phone: {countryCode=63, nationalNumber=9171234567}
```

Changed in 0.3.0: the library's models use its class names, so `email-address` is now `email` (and a bank account's
IBAN alone is `iban`; `bank-account` is still there). `ValueObjects.install` is now `CommonObjects.installAll`.

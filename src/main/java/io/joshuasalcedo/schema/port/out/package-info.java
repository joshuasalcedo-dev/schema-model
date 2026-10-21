/**
 * Outbound ports: what an application plugs in. Every one has a ready-made default, so {@code Engine.builder().build()}
 * runs entirely in memory; implement a port only to keep its data elsewhere or to be told about changes.
 *
 * <table>
 *   <caption>The interfaces to implement</caption>
 *   <tr><th scope="col">Interface</th><th scope="col">Implement it to</th><th scope="col">Methods</th>
 *       <th scope="col">Default</th></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.ModelRepository}</td><td>keep models in a database</td>
 *       <td>{@code findAll}, {@code findById}, {@code save}, {@code delete}</td>
 *       <td>{@link io.joshuasalcedo.schema.adapter.InMemoryModelRepository}</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.RecordRepository}</td><td>keep records in a database</td>
 *       <td>{@code findAll}, {@code find}, {@code save} and {@code delete} (both conditional on the version)</td>
 *       <td>{@link io.joshuasalcedo.schema.adapter.InMemoryRecordRepository}</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.ActionRepository}</td><td>keep action definitions in a database</td>
 *       <td>{@code findAll}, {@code find}, {@code save}, {@code delete}</td>
 *       <td>{@link io.joshuasalcedo.schema.adapter.InMemoryActionRepository}</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.FileStore}</td><td>keep uploaded files (S3, R2, a disk)</td>
 *       <td>{@code put}, {@code open}, {@code exists}, {@code delete}</td>
 *       <td>{@link io.joshuasalcedo.schema.adapter.InMemoryFileStore};
 *       {@link io.joshuasalcedo.schema.adapter.DirectoryFileStore} for a folder</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.RecordEvents}</td><td>hear every saved record change (a message
 *       bus, an audit log)</td><td>{@code publish}</td><td>{@link io.joshuasalcedo.schema.port.out.RecordEvents#none()}</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.ModelEvents}</td><td>hear every model change</td>
 *       <td>{@code created}, {@code updated}, {@code deleted}</td>
 *       <td>{@link io.joshuasalcedo.schema.port.out.ModelEvents#none()}</td></tr>
 *   <tr><td>{@link io.joshuasalcedo.schema.port.out.VocabularyProvider}</td><td>check the terms models point to
 *       against your glossary</td><td>{@code current}</td><td>any term is accepted</td></tr>
 * </table>
 *
 * <p>Besides these, {@link io.joshuasalcedo.schema.engine.ActionHandler} ({@code handle}) is Java code an action
 * calls by name, for logic too big for an expression.
 *
 * <p>Everything reaching these ports has already been checked, so adapters only store and forward. The one rule an
 * adapter must keep itself: {@link io.joshuasalcedo.schema.port.out.RecordRepository#save} and
 * {@link io.joshuasalcedo.schema.port.out.RecordRepository#delete} act only when the stored version is the one
 * expected ({@code UPDATE ... WHERE version = ?}) and return false otherwise; that is how two writers never
 * overwrite each other.
 */
package io.joshuasalcedo.schema.port.out;

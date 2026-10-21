/**
 * Outbound ports: what an application plugs in. Storage ({@link io.joshuasalcedo.schema.port.out.ModelRepository},
 * {@link io.joshuasalcedo.schema.port.out.ActionRepository}, {@link io.joshuasalcedo.schema.port.out.RecordRepository}), notifications
 * ({@link io.joshuasalcedo.schema.port.out.ModelEvents}, {@link io.joshuasalcedo.schema.port.out.RecordEvents}) and the glossary
 * ({@link io.joshuasalcedo.schema.port.out.VocabularyProvider}). Everything reaching these ports has already been checked, so
 * adapters only store and forward.
 */
package io.joshuasalcedo.schema.port.out;

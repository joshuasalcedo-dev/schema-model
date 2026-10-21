/**
 * Records: stored instances of entity models. {@link io.joshuasalcedo.schema.record.Record} is one record;
 * {@link io.joshuasalcedo.schema.record.RecordValues} checks values against a model, converts them to the record types and
 * prepares them for CEL expressions. Records are created and changed through
 * {@link io.joshuasalcedo.schema.port.in.Platform}, never directly.
 */
package io.joshuasalcedo.schema.record;

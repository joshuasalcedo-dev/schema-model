/**
 * The engine: {@link io.joshuasalcedo.schema.engine.Engine} implements {@link io.joshuasalcedo.schema.port.in.Platform} over the
 * outbound ports and runs every model, record and action. {@link io.joshuasalcedo.schema.engine.ActionHandler}s are the Java
 * code actions call by name; they see the running action through {@link io.joshuasalcedo.schema.engine.ActionContext}.
 */
package io.joshuasalcedo.schema.engine;

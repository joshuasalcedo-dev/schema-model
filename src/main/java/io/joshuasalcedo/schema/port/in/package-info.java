/**
 * Inbound ports: what callers use. {@link io.joshuasalcedo.schema.port.in.Platform} is the one entry point for actions and
 * records; {@link io.joshuasalcedo.schema.port.in.ModelDesign} is for models. Adapters on the calling side (a REST controller,
 * a gRPC service, a desktop app, a CLI) call these and nothing else.
 */
package io.joshuasalcedo.schema.port.in;

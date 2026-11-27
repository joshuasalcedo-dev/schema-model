/**
 * Predefined value objects: ready-made models for common values such as an email, a phone number, an amount of
 * money, an address, a date range or a network address, so an entity can hold them with
 * {@code Field.model("price", "money")} instead of defining its own.
 * {@link io.joshuasalcedo.schema.predefined.ValueObjectLibrary} makes them from the Java value objects of
 * io.joshuasalcedo:value-object, checks values with them and prints them as they print, and installs them into a
 * schema or a {@link io.joshuasalcedo.schema.port.in.ModelDesign}.
 */
package io.joshuasalcedo.schema.predefined;

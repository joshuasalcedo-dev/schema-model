package io.joshuasalcedo.schema.predefined;

import static io.joshuasalcedo.schema.Normalize.BLANK_TO_ABSENT;
import static io.joshuasalcedo.schema.Normalize.COLLAPSE_SPACES;
import static io.joshuasalcedo.schema.Normalize.DIGITS_ONLY;
import static io.joshuasalcedo.schema.Normalize.IDN_ASCII;
import static io.joshuasalcedo.schema.Normalize.IPV6_CANONICAL;
import static io.joshuasalcedo.schema.Normalize.LOWERCASE;
import static io.joshuasalcedo.schema.Normalize.MAC_CANONICAL;
import static io.joshuasalcedo.schema.Normalize.TRIM;
import static io.joshuasalcedo.schema.Normalize.UPPERCASE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.port.in.ModelDesign;

/**
 * The predefined value objects: ready-made {@link Model}s for the everyday values most entities need, such as an
 * amount of money, an email address or a postal address. Use them from any entity by their ID:
 * <pre>{@code
 * ValueObjects.install(engine.models());          // adds the ones that are missing
 *
 * engine.models().createModel(Model.entity("Product")
 *         .field(Field.text("sku").identity().required())
 *         .field(Field.model("price", "money").required())
 *         .field(Field.model("weight", "weight"))
 *         .build());
 * }</pre>
 *
 * <h2>Where they come from</h2>
 * Each model is a faithful translation of a Java value object in {@code swaswa.domain.valueobject} (the swaswa-domain
 * library), named after its class so the IDs read the same ({@code EmailAddress} becomes {@code email-address}).
 * Components map to fields as follows:
 * <ul>
 *   <li>strings become {@code TEXT}, with the Java validation regex as the {@code pattern} where there is one;</li>
 *   <li>Java enums become {@code CHOICE} fields listing the enum's constant names in declaration order (no models are
 *       made for the enums themselves);</li>
 *   <li>whole numbers become {@code INTEGER}, {@code BigDecimal} and {@code double} become {@code DECIMAL},
 *       {@code LocalDate} becomes {@code DATE}, {@code Instant} becomes {@code TIMESTAMP};</li>
 *   <li>a component that is itself a value object becomes a {@code MODEL} field holding that object's model;</li>
 *   <li>a field is required when the Java constructor rejects a missing value (or, in a package marked
 *       {@code @NullMarked}, when the component is not {@code @Nullable}).</li>
 * </ul>
 *
 * <h2>Same logic as the Java value objects</h2>
 * Each model behaves like its Java constructor:
 * <ul>
 *   <li><b>Clean-up first</b> ({@link io.joshuasalcedo.schema.Normalize}): input is trimmed, lower- or upper-cased, collapsed,
 *       reduced to digits or rewritten to one canonical form exactly where the Java class does it, so
 *       {@code " Alice@Example.COM "} is stored as {@code alice@example.com}, {@code 2001:DB8::1} as
 *       {@code 2001:db8:0:0:0:0:0:1} and {@code 001a.2b3c.4d5e} as {@code 00:1A:2B:3C:4D:5E}. Enum choices accept any
 *       case ({@code philippines} becomes {@code PHILIPPINES}).</li>
 *   <li><b>Limits</b>: number ranges ({@code min}/{@code max}), text lengths and decimal scale (a
 *       {@link #PERCENTAGE} keeps four places, rounded half-even) are enforced, not just described.</li>
 *   <li><b>Rules across fields</b> ({@link io.joshuasalcedo.schema.Invariant}): an end not before its start, a currency code
 *       of the right type, a postal code in its country's format, an SSH host that is a specific unicast address, a
 *       date of birth not in the future, at most 15 digits in a phone number.</li>
 * </ul>
 * One Java behaviour has no model equivalent: {@code Money} rounds its amount to its currency's decimal places (2
 * for most fiat currencies, 8 for BTC). That rounding depends on another field's value, so {@link #MONEY} stores the
 * amount as given; round it before saving.
 *
 * <h2>Hierarchies</h2>
 * <ul>
 *   <li>{@code Currency} is an interface implemented by the enums {@code FiatCurrency} (ISO 4217) and
 *       {@code CryptoCoin}. It becomes one {@link #CURRENCY} model with a {@code type} choice naming the
 *       implementation (as {@code Currency.type()} does) and a {@code code} choice listing the constants of both
 *       enums, so {@link #MONEY} can hold either kind.</li>
 *   <li>{@code IpAddress} is a sealed interface over {@code Ipv4Address} and {@code Ipv6Address}. Each concrete type
 *       has its own model ({@link #IPV4_ADDRESS}, {@link #IPV6_ADDRESS}), and {@link #IP_ADDRESS} accepts either
 *       form in one {@code value} field; the version follows from the form, so it is not stored.</li>
 * </ul>
 *
 * <h2>Not included</h2>
 * Helpers and behaviour-only types ({@code Utility}, {@code Measurable}, {@code MeasurementUnit}), the messaging
 * envelopes {@code NatsRequest} and {@code NatsReply} (generic wrappers, not business values), {@code JwtToken} (a
 * bearer credential, which should not be stored as plain record data), and enums that no value object uses as a
 * component ({@code Continent}, {@code Demonym}, {@code Language}, {@code OperatingSystem},
 * {@code RecurrenceInterval}, {@code TimeZone}).
 */
public final class ValueObjects {

    private ValueObjects() {
    }

    // ==================== Patterns ====================

    /** One DNS label: letters and digits, hyphens inside, at most 63 characters. */
    private static final String DNS_LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";

    /** A lower-case ASCII domain of at least two labels, at most 253 characters, with a TLD that is not all digits. */
    private static final String DOMAIN = "(?=.{1,253}$)(?:" + DNS_LABEL + "\\.)+(?![0-9]+$)" + DNS_LABEL;

    /** The unquoted local part of a mailbox (dot-atom), lower-case. */
    private static final String LOCAL_PART =
            "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*";

    /** One IPv4 octet, 0 to 255, without leading zeros. */
    private static final String OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";

    private static final String IPV4 = "(?:" + OCTET + "\\.){3}" + OCTET;

    /** One IPv6 group, lower-case hexadecimal without leading zeros. */
    private static final String HEX_GROUP = "(?:0|[1-9a-f][0-9a-f]{0,3})";

    private static final String IPV6 = "(?:" + HEX_GROUP + ":){7}" + HEX_GROUP;

    /** A name: letters, single spaces, hyphens and apostrophes, starting with a letter, at most 50 characters. */
    private static final String NAME = "(?=.{1,50}$)\\p{L}[\\p{L}'-]*(?: [\\p{L}'-]+)*";

    private static final String NAME_NOTE = "Letters, single spaces, hyphens and apostrophes, starting with a letter; "
                                            + "1 to 50 characters, trimmed.";

    // ==================== Rules across fields ====================

    /** A CEL list literal of the given strings, e.g. {@code ['USD', 'PHP']}. */
    private static String celList(String... values) {
        return Arrays.stream(values).map(v -> "'" + v + "'").collect(Collectors.joining(", ", "[", "]"));
    }

    /** The country formats PostalCode checks (its COUNTRY_PATTERNS), as RE2 patterns over the upper-cased code. */
    private static final Map<String, String> POSTAL_FORMATS = postalFormats();

    private static Map<String, String> postalFormats() {
        Map<String, String> formats = new LinkedHashMap<>();
        formats.put("UNITED_STATES", "^\\d{5}(-\\d{4})?$");
        formats.put("CANADA", "^[A-Z]\\d[A-Z][ -]?\\d[A-Z]\\d$");
        formats.put("UNITED_KINGDOM", "^[A-Z]{1,2}\\d[A-Z\\d]?[ ]?\\d[A-Z]{2}$");
        formats.put("GERMANY", "^\\d{5}$");
        formats.put("FRANCE", "^\\d{5}$");
        formats.put("AUSTRALIA", "^\\d{4}$");
        formats.put("JAPAN", "^\\d{3}-?\\d{4}$");
        formats.put("BRAZIL", "^\\d{5}-?\\d{3}$");
        formats.put("INDIA", "^\\d{6}$");
        formats.put("CHINA", "^\\d{6}$");
        formats.put("ITALY", "^\\d{5}$");
        formats.put("SPAIN", "^\\d{5}$");
        formats.put("NETHERLANDS", "^\\d{4}[ ]?[A-Z]{2}$");
        formats.put("SWEDEN", "^\\d{3}[ ]?\\d{2}$");
        formats.put("SWITZERLAND", "^\\d{4}$");
        formats.put("MEXICO", "^\\d{5}$");
        formats.put("SOUTH_KOREA", "^\\d{5}$");
        formats.put("RUSSIA", "^\\d{6}$");
        formats.put("POLAND", "^\\d{2}-?\\d{3}$");
        formats.put("BELGIUM", "^\\d{4}$");
        return Map.copyOf(formats);
    }

    /** True when the postal code fits its country's format, or the country has no specific format. */
    private static final String POSTAL_CODE_FITS_COUNTRY;

    static {
        String formats = POSTAL_FORMATS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> "'" + e.getKey() + "': r'" + e.getValue() + "'")
                .collect(Collectors.joining(", ", "{", "}"));
        POSTAL_CODE_FITS_COUNTRY = "!(value.country in " + formats + ") || value.value.matches(" + formats
                                   + "[value.country])";
    }

    /** True when the currency code is a constant of the enum its type names. */
    private static final String CURRENCY_CODE_FITS_TYPE =
            "value.code in (value.type == 'CryptoCoin' ? " + celList(JavaEnums.CRYPTO_COIN) + " : "
            + celList(JavaEnums.FIAT_CURRENCY) + ")";

    // ==================== contact ====================

    /** {@code Username} ({@code username}): a canonical, case-insensitive user name. */
    public static final Model USERNAME = Model.valueObject("Username")
            .description("Canonical case-insensitive username, independent of the user's stable ID.")
            .field(Field.text("value").required()
                           .normalize(TRIM, LOWERCASE)
                           .pattern("[a-z0-9][a-z0-9._-]{2,63}")
                           .description("3 to 64 characters of letters, digits, dots, underscores and hyphens, "
                                        + "starting with a letter or digit; stored lower-case."))
            .build();

    /** {@code EmailAddress} ({@code email-address}): a mailbox address, lower-case. */
    public static final Model EMAIL_ADDRESS = Model.valueObject("EmailAddress")
            .description("Case-insensitive email identity with unquoted mailbox syntax; ownership is not verified "
                         + "and provider aliases are never rewritten.")
            .field(Field.text("value").required()
                           .normalize(TRIM, LOWERCASE)
                           .pattern("(?=.{3,254}$)(?=[^@]{1,64}@)" + LOCAL_PART + "@" + DOMAIN)
                           .description("local@domain, stored lower-case, at most 254 characters; the local part is "
                                        + "at most 64 characters and the domain is a valid DomainName (an "
                                        + "internationalised domain is stored in its ASCII form)."))
            .build();

    /** {@code FullName} ({@code full-name}): a person's first, middle and last name. */
    public static final Model FULL_NAME = Model.valueObject("FullName")
            .description("A person's name: a first name and an optional middle and last name.")
            .field(Field.text("firstName").required()
                           .normalize(TRIM, COLLAPSE_SPACES).pattern(NAME).description(NAME_NOTE))
            .field(Field.text("middleName")
                           .normalize(TRIM, COLLAPSE_SPACES, BLANK_TO_ABSENT).pattern(NAME).description(NAME_NOTE))
            .field(Field.text("lastName")
                           .normalize(TRIM, COLLAPSE_SPACES, BLANK_TO_ABSENT).pattern(NAME).description(NAME_NOTE))
            .build();

    /** {@code PhoneNumber} ({@code phone-number}): an E.164 phone number split into its two parts. */
    public static final Model PHONE_NUMBER = Model.valueObject("PhoneNumber")
            .description("A phone number in E.164 form: a country calling code and a national number.")
            .field(Field.text("countryCode").required()
                           .normalize(DIGITS_ONLY)
                           .pattern("[0-9]{1,3}")
                           .description("The country calling code, 1 to 3 digits; '+', spaces and punctuation are "
                                        + "dropped, so +63 is stored as 63."))
            .field(Field.text("nationalNumber").required()
                           .normalize(DIGITS_ONLY)
                           .pattern("[0-9]{1,14}")
                           .description("The national number, digits only; spaces, dashes and brackets are dropped."))
            .invariant("size(value.countryCode) + size(value.nationalNumber) <= 15",
                       "an E.164 number has at most 15 digits")
            .build();

    /** {@code Link} ({@code link}): a URL. */
    public static final Model LINK = Model.valueObject("Link")
            .description("A URL: text that parses as a URI with a scheme.")
            .field(Field.text("value").required()
                           .normalize(TRIM)
                           .pattern("[A-Za-z][A-Za-z0-9+.-]*:\\S+")
                           .description("A URI with a scheme, such as https://example.com. The full java.net.URI "
                                        + "syntax check is not repeated here."))
            .build();

    /** {@code Address} ({@code address}): a postal address in any country's format. */
    public static final Model ADDRESS = Model.valueObject("Address")
            .description("A physical address in any international format; only the country is required. Blank "
                         + "parts are left out, as the Java record turns them into null.")
            .field(Field.text("street").normalize(TRIM, BLANK_TO_ABSENT).description("The street and number."))
            .field(Field.text("city").normalize(TRIM, BLANK_TO_ABSENT))
            .field(Field.text("stateOrProvince").normalize(TRIM, BLANK_TO_ABSENT))
            .field(Field.text("postalCode").normalize(TRIM, BLANK_TO_ABSENT)
                           .description("The postal or ZIP code, as written."))
            .field(Field.choice("country", JavaEnums.COUNTRY).required()
                           .normalize(TRIM, UPPERCASE)
                           .description("A Country constant; UNKNOWN when the country is not known."))
            .field(Field.text("addressLine2").normalize(TRIM, BLANK_TO_ABSENT)
                           .description("A second line, such as an apartment or suite."))
            .build();

    // ==================== financial ====================

    /** {@code Currency} ({@code currency}): a fiat currency or a crypto coin. */
    public static final Model CURRENCY = Model.valueObject("Currency")
            .description("A currency, fiat (ISO 4217) or crypto, by the constant that names it.")
            .field(Field.choice("type", "FiatCurrency", "CryptoCoin").required()
                           .normalize(TRIM)
                           .description("Which kind of currency: FiatCurrency (ISO 4217) or CryptoCoin."))
            .field(Field.choice("code", concat(JavaEnums.FIAT_CURRENCY, JavaEnums.CRYPTO_COIN)).required()
                           .normalize(TRIM, UPPERCASE)
                           .description("The FiatCurrency or CryptoCoin constant, such as USD or BTC, of the given "
                                        + "type. FiatCurrency.UNKNOWN stands for ISO code XXX."))
            .invariant(CURRENCY_CODE_FITS_TYPE, "the code is not a currency of that type")
            .build();

    /** {@code Money} ({@code money}): an amount in a currency. */
    public static final Model MONEY = Model.valueObject("Money")
            .description("A monetary amount with its currency; never a floating-point number.")
            .field(Field.decimal("amount").required()
                           .description("The amount. The Java value object rounds it half-even to the currency's "
                                        + "decimal places (2 for most fiat currencies, 8 for BTC, ...); round it "
                                        + "the same way before saving."))
            .field(Field.model("currency", "currency").required())
            .build();

    /** {@code Percentage} ({@code percentage}): a percentage where 100 is the whole. */
    public static final Model PERCENTAGE = Model.valueObject("Percentage")
            .description("A percentage, where 100 is the whole: 25 means 25%.")
            .field(Field.decimal("value").required()
                           .scale(4)
                           .description("The percentage, such as 12.5 for 12.5%, rounded half-even to four decimal "
                                        + "places; it may be negative or above 100."))
            .build();

    /** {@code CryptoCurrencyAddress} ({@code crypto-currency-address}): a wallet address on a coin's network. */
    public static final Model CRYPTO_CURRENCY_ADDRESS = Model.valueObject("CryptoCurrencyAddress")
            .description("A wallet address on a coin's network.")
            .field(Field.text("address").required().description("The address text; its format is not checked."))
            .field(Field.choice("coin", JavaEnums.CRYPTO_COIN).required().normalize(TRIM, UPPERCASE))
            .build();

    // ==================== geo ====================

    /** {@code Coordinates} ({@code coordinates}): a latitude and longitude. */
    public static final Model COORDINATES = Model.valueObject("Coordinates")
            .description("Geographic coordinates: a latitude and a longitude in degrees.")
            .field(Field.decimal("latitude").required().range("-90", "90").description("Degrees, from -90 to 90."))
            .field(Field.decimal("longitude").required().range("-180", "180")
                           .description("Degrees, from -180 to 180."))
            .build();

    /** {@code PostalCode} ({@code postal-code}): a postal or ZIP code for a country. */
    public static final Model POSTAL_CODE = Model.valueObject("PostalCode")
            .description("A postal or ZIP code whose format follows its country's rules.")
            .field(Field.text("value").required()
                           .normalize(TRIM, UPPERCASE)
                           .pattern("[A-Z0-9-][A-Z0-9 -]{0,8}[A-Z0-9-]")
                           .description("Stored upper-case: 2 to 10 letters, digits, spaces and hyphens, in the "
                                        + "country's format where PostalCode knows one (e.g. UNITED_STATES 12345 or "
                                        + "12345-6789, CANADA A1A 1A1, UNITED_KINGDOM SW1A 1AA, JAPAN 123-4567)."))
            .field(Field.choice("country", JavaEnums.COUNTRY).required()
                           .normalize(TRIM, UPPERCASE)
                           .description("The country whose format the code follows."))
            .invariant(POSTAL_CODE_FITS_COUNTRY, "the postal code does not match the country's format")
            .build();

    // ==================== identifier ====================

    /** {@code Slug} ({@code slug}): a URL-friendly name. */
    public static final Model SLUG = Model.valueObject("Slug")
            .description("A URL-friendly slug: lower-case letters and digits separated by single hyphens.")
            .field(Field.text("value").required()
                           .normalize(TRIM, LOWERCASE)
                           .maxLength(100)
                           .pattern("[a-z0-9]+(?:-[a-z0-9]+)*")
                           .description("At most 100 characters, such as my-first-post; stored lower-case."))
            .build();

    // ==================== measurement ====================

    /** {@code Distance} ({@code distance}): a length in a metric or imperial unit. */
    public static final Model DISTANCE = Model.valueObject("Distance")
            .description("A distance: a value in a metric or imperial unit, never negative.")
            .field(Field.decimal("value").required().min("0").description("The number of units, 0 or more."))
            .field(Field.choice("unit", JavaEnums.DISTANCE_UNIT).required().normalize(TRIM, UPPERCASE))
            .build();

    /** {@code Weight} ({@code weight}): a weight or mass in a metric or imperial unit. */
    public static final Model WEIGHT = Model.valueObject("Weight")
            .description("A weight or mass: a value in a metric or imperial unit, never negative.")
            .field(Field.decimal("value").required().min("0").description("The number of units, 0 or more."))
            .field(Field.choice("unit", JavaEnums.WEIGHT_UNIT).required().normalize(TRIM, UPPERCASE))
            .build();

    /** {@code FileSize} ({@code file-size}): an amount of data in a binary or decimal unit. */
    public static final Model FILE_SIZE = Model.valueObject("FileSize")
            .description("A file or data size: a value in a binary (IEC) or decimal (SI) unit, never negative.")
            .field(Field.decimal("value").required().min("0").description("The number of units, 0 or more."))
            .field(Field.choice("unit", JavaEnums.DATA_UNIT).required().normalize(TRIM, UPPERCASE))
            .build();

    // ==================== network ====================

    /** {@code DomainName} ({@code domain-name}): a DNS name. */
    public static final Model DOMAIN_NAME = Model.valueObject("DomainName")
            .description("A multi-label DNS name in lower-case ASCII; syntax only, not registration or DNS "
                         + "existence.")
            .field(Field.text("value").required()
                           .normalize(TRIM, IDN_ASCII)
                           .pattern(DOMAIN)
                           .description("At least two labels, at most 253 characters, no trailing dot, a TLD that "
                                        + "is not all digits; an internationalised name is stored in its ASCII "
                                        + "(punycode) form."))
            .build();

    /** {@code Ipv4Address} ({@code ipv4-address}): an IPv4 address. */
    public static final Model IPV4_ADDRESS = Model.valueObject("Ipv4Address")
            .description("An IPv4 address in dotted decimal form.")
            .field(Field.text("value").required()
                           .normalize(TRIM)
                           .pattern(IPV4)
                           .description("Four octets from 0 to 255 without leading zeros, such as 192.168.0.1."))
            .build();

    /** {@code Ipv6Address} ({@code ipv6-address}): an IPv6 address in its expanded form. */
    public static final Model IPV6_ADDRESS = Model.valueObject("Ipv6Address")
            .description("An IPv6 address, stored as eight groups the way InetAddress.getHostAddress() writes it.")
            .field(Field.text("value").required()
                           .normalize(TRIM, IPV6_CANONICAL)
                           .pattern(IPV6)
                           .description("Any IPv6 literal (2001:DB8::1, ::1, ...) is stored as eight groups of "
                                        + "lower-case hexadecimal without leading zeros, such as "
                                        + "2001:db8:0:0:0:0:0:1. Zone suffixes such as %eth0 are not supported."))
            .build();

    /** {@code IpAddress} ({@code ip-address}): an IPv4 or IPv6 address. */
    public static final Model IP_ADDRESS = Model.valueObject("IpAddress")
            .description("An IP address of either version; the version follows from the form of the value.")
            .field(Field.text("value").required()
                           .normalize(TRIM, IPV6_CANONICAL)
                           .pattern("(?:" + IPV4 + ")|(?:" + IPV6 + ")")
                           .description("An Ipv4Address or Ipv6Address value, stored in the same form as those "
                                        + "models."))
            .build();

    /** {@code MacAddress} ({@code mac-address}): a hardware address. */
    public static final Model MAC_ADDRESS = Model.valueObject("MacAddress")
            .description("A MAC (Media Access Control) address of six bytes.")
            .field(Field.text("value").required()
                           .normalize(TRIM, MAC_CANONICAL)
                           .pattern("[0-9A-F]{2}(?::[0-9A-F]{2}){5}")
                           .description("Accepts the colon, hyphen, Cisco-dot and plain forms, like the Java value "
                                        + "object; stored upper-case and colon-separated, such as "
                                        + "00:1A:2B:3C:4D:5E."))
            .build();

    /** {@code Port} ({@code port}): a network port number. */
    public static final Model PORT = Model.valueObject("Port")
            .description("A network port number.")
            .field(Field.integer("value").required().range("0", "65535").description("From 0 to 65535."))
            .build();

    /** {@code SshConnection} ({@code ssh-connection}): where and as whom to connect over SSH. */
    public static final Model SSH_CONNECTION = Model.valueObject("SshConnection")
            .description("SSH endpoint metadata; the identity file is a path on the connecting machine, never "
                         + "private-key contents.")
            .field(Field.model("host", "ip-address").required()
                           .description("A specific unicast address: not multicast, not 0.0.0.0, "
                                        + "255.255.255.255 or ::."))
            .field(Field.model("port", "port").required().description("From 1 to 65535; 22 is the usual port."))
            .field(Field.text("username").required()
                           .pattern("[A-Za-z_][A-Za-z0-9_.-]*\\$?")
                           .description("A POSIX-style user name, optionally ending in $."))
            .field(Field.text("identityFile")
                           .pattern("[^\\x00-\\x1F\\x7F-\\x9F]*[^\\s\\x00-\\x1F\\x7F-\\x9F][^\\x00-\\x1F\\x7F-\\x9F]*")
                           .description("The path of the key file, not blank and without control characters; "
                                        + "absent to use the SSH client's configured identities or agent."))
            .invariant("value.port.value >= 1", "SSH destination port must be between 1 and 65535")
            .invariant("!(value.host.value in ['0.0.0.0', '255.255.255.255', '0:0:0:0:0:0:0:0'])"
                       + " && !value.host.value.matches(r'^2(2[4-9]|3[0-9])\\.')"
                       + " && !value.host.value.matches(r'^ff[0-9a-f]{2}:')",
                       "SSH requires a specific unicast host address")
            .build();

    // ==================== temporal ====================

    /** {@code DateOfBirth} ({@code date-of-birth}): the day someone was born. */
    public static final Model DATE_OF_BIRTH = Model.valueObject("DateOfBirth")
            .description("A date of birth.")
            .field(Field.date("value").required().description("Not in the future."))
            .invariant("value.value <= today", "a date of birth cannot be in the future")
            .build();

    /** {@code DateRange} ({@code date-range}): a range of days, both ends included. */
    public static final Model DATE_RANGE = Model.valueObject("DateRange")
            .description("A range between two dates, both included.")
            .field(Field.date("startDate").required())
            .field(Field.date("endDate").required().description("Not before the start date."))
            .invariant("value.endDate >= value.startDate", "the end date cannot be before the start date")
            .build();

    /** {@code DateTimeWithZone} ({@code date-time-with-zone}): a date and time with its offset from UTC. */
    public static final Model DATE_TIME_WITH_ZONE = Model.valueObject("DateTimeWithZone")
            .description("A point in time that keeps its offset from UTC (an OffsetDateTime).")
            .field(Field.text("value").required()
                           .normalize(TRIM)
                           .pattern("[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])"
                                    + "T(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9](?:\\.[0-9]{1,9})?)?"
                                    + "(?:Z|[+-](?:0[0-9]|1[0-8]):[0-5][0-9](?::[0-5][0-9])?)")
                           .description("ISO-8601 with an offset, such as 2026-10-18T14:00:00+08:00 or "
                                        + "2026-10-18T06:00Z. Kept as text because a TIMESTAMP field would drop the "
                                        + "offset; whether the day exists in its month is not checked here."))
            .build();

    /** {@code DurationNano} ({@code duration-nano}): a length of time in nanoseconds. */
    public static final Model DURATION_NANO = Model.valueObject("DurationNano")
            .description("A duration of time, counted in nanoseconds.")
            .field(Field.integer("nanos").required().min("0").description("The length in nanoseconds, 0 or more."))
            .build();

    /** {@code EffectivePeriod} ({@code effective-period}): from an instant until an optional end. */
    public static final Model EFFECTIVE_PERIOD = Model.valueObject("EffectivePeriod")
            .description("An effective period: an inclusive start and an optional exclusive end.")
            .field(Field.timestamp("from").required().description("The first instant of the period."))
            .field(Field.timestamp("through")
                           .description("When the period ends, exclusive and not before the start; absent for no "
                                        + "end."))
            .invariant("!has(value.through) || value.through >= value.from", "through must not be before from")
            .build();

    /** {@code Recurrence} ({@code recurrence}): how often something repeats. */
    public static final Model RECURRENCE = Model.valueObject("Recurrence")
            .description("How often something repeats: every interval days, weeks, months, quarters or years.")
            .field(Field.choice("frequency", JavaEnums.FREQUENCY).required()
                           .normalize(TRIM, UPPERCASE)
                           .description("The unit of the interval; ONCE means it does not repeat."))
            .field(Field.integer("interval").required().min("1")
                           .description("How many units between runs, at least 1."))
            .build();

    /** {@code Time} ({@code time}): a time of day on the 12-hour clock. */
    public static final Model TIME = Model.valueObject("Time")
            .description("A time of day in 12-hour format.")
            .field(Field.integer("hour").required().range("1", "12")
                           .description("The hour on the 12-hour clock, 1 to 12."))
            .field(Field.integer("minute").required().range("0", "59").description("The minute, 0 to 59."))
            .field(Field.choice("meridiem", JavaEnums.MERIDIEM).required().normalize(TRIM, UPPERCASE))
            .build();

    /** {@code TimeBlock} ({@code time-block}): a start and end time within a day. */
    public static final Model TIME_BLOCK = Model.valueObject("TimeBlock")
            .description("A start/end block of time of day, with no date or zone; an end not after the start spans "
                         + "midnight.")
            .field(Field.model("start", "time").required())
            .field(Field.model("end", "time").required().description("Must differ from the start."))
            .invariant("value.start != value.end", "start and end must differ")
            .build();

    /** {@code Timestamp} ({@code timestamp}): an instant in time. */
    public static final Model TIMESTAMP = Model.valueObject("Timestamp")
            .description("A point in time (an Instant, in UTC).")
            .field(Field.timestamp("value").required())
            .build();

    // ==================== versioning ====================

    /** {@code SemanticVersion} ({@code semantic-version}): a SemVer 2.0.0 version. */
    public static final Model SEMANTIC_VERSION = Model.valueObject("SemanticVersion")
            .description("A semantic version (SemVer 2.0.0): MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD].")
            .relatedArticle("https://semver.org/")
            .field(Field.integer("major").required().min("0").description("0 or more."))
            .field(Field.integer("minor").required().min("0").description("0 or more."))
            .field(Field.integer("patch").required().min("0").description("0 or more."))
            .field(Field.text("preRelease")
                           .pattern("(?:0|[1-9][0-9]*|[0-9]*[a-zA-Z-][0-9a-zA-Z-]*)"
                                    + "(?:\\.(?:0|[1-9][0-9]*|[0-9]*[a-zA-Z-][0-9a-zA-Z-]*))*")
                           .description("The pre-release identifier, such as rc.1; absent for a release."))
            .field(Field.text("buildMetadata")
                           .pattern("[0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*")
                           .description("The build metadata, such as build.5; absent for none."))
            .build();

    // ==================== visual ====================

    /** {@code Color} ({@code color}): an RGBA color. */
    public static final Model COLOR = Model.valueObject("Color")
            .description("A color as red, green, blue and alpha components.")
            .field(Field.integer("red").range("0", "255").description("0 to 255; absent means 0."))
            .field(Field.integer("green").range("0", "255").description("0 to 255; absent means 0."))
            .field(Field.integer("blue").range("0", "255").description("0 to 255; absent means 0."))
            .field(Field.integer("alpha").range("0", "255")
                           .description("0 (transparent) to 255 (opaque); absent means 255."))
            .build();

    // ==================== general ====================

    /** {@code Count} ({@code count}): a whole number of things. */
    public static final Model COUNT = Model.valueObject("Count")
            .description("A whole number of things, zero or more.")
            .field(Field.integer("value").required().min("0").description("0 or more."))
            .build();

    // ==================== media ====================

    /**
     * {@code file} ({@code file}): a stored file — its name, type, size, checksum and where its bytes are. Not a
     * Java value object: the platform's own, for {@link io.joshuasalcedo.schema.file.FileRef}.
     */
    public static final Model FILE = Model.valueObject("File")
            .description("A stored file: what it is and where its bytes are kept; the bytes are in the file store, "
                         + "not in the record. Made by uploading.")
            .field(Field.text("name").required().normalize(TRIM).maxLength(255)
                           .pattern("[^/\\\\\\x00-\\x1F\\x7F]+")
                           .description("The file name as uploaded, e.g. contract.pdf; no slashes."))
            .field(Field.text("contentType").required().normalize(TRIM, LOWERCASE)
                           .pattern("[a-z0-9][a-z0-9!#$&^_.+-]*/[a-z0-9][a-z0-9!#$&^_.+-]*")
                           .description("The media type, e.g. application/pdf or image/png."))
            .field(Field.integer("sizeBytes").required().min("0").description("The size in bytes."))
            .field(Field.text("sha256").required().normalize(TRIM, LOWERCASE).pattern("[0-9a-f]{64}")
                           .description("The SHA-256 of the bytes: the same bytes always have the same checksum."))
            .field(Field.text("location").required()
                           .description("Where the file store keeps the bytes; set by the store, never by hand."))
            .build();

    /** {@code image} ({@code image}): a stored picture with its size and a text alternative. */
    public static final Model IMAGE = Model.valueObject("Image")
            .description("A picture: a stored image file, its size in pixels and a description for people who "
                         + "cannot see it.")
            .field(Field.model("file", "file").required())
            .field(Field.integer("width").min("1").description("In pixels."))
            .field(Field.integer("height").min("1").description("In pixels."))
            .field(Field.text("altText").normalize(TRIM, BLANK_TO_ABSENT).maxLength(500)
                           .description("What the picture shows, for screen readers and when it cannot load."))
            .invariant("value.file.contentType.startsWith('image/')", "the file is not an image")
            .build();

    /** {@code video} ({@code video}): a stored video with its size and length. */
    public static final Model VIDEO = Model.valueObject("Video")
            .description("A video: a stored video file, its frame size and its length.")
            .field(Field.model("file", "file").required())
            .field(Field.integer("width").min("1").description("In pixels."))
            .field(Field.integer("height").min("1").description("In pixels."))
            .field(Field.decimal("durationSeconds").min("0").scale(3).description("The length in seconds."))
            .invariant("value.file.contentType.startsWith('video/')", "the file is not a video")
            .build();

    /** {@code audio} ({@code audio}): a stored sound recording with its length. */
    public static final Model AUDIO = Model.valueObject("Audio")
            .description("A sound recording: a stored audio file and its length.")
            .field(Field.model("file", "file").required())
            .field(Field.decimal("durationSeconds").min("0").scale(3).description("The length in seconds."))
            .invariant("value.file.contentType.startsWith('audio/')", "the file is not audio")
            .build();

    // ==================== Catalog ====================

    /**
     * The areas the value objects are grouped in, after the Java subpackages they come from. Every model a value
     * object holds is in the same area, listed before it.
     */
    public enum Area {
        /** People and how to reach them: names, email, phone, links, addresses. */
        CONTACT,
        /** Money and currencies. */
        FINANCIAL,
        /** Places on Earth. */
        GEO,
        /** Identifiers meant for people and URLs. */
        IDENTIFIER,
        /** Quantities with units. */
        MEASUREMENT,
        /** Network addresses and endpoints. */
        NETWORK,
        /** Dates, times, periods and repetition. */
        TEMPORAL,
        /** Versions. */
        VERSIONING,
        /** Colors. */
        VISUAL,
        /** Values that fit no other area. */
        GENERAL,
        /** Files and media: a stored file, pictures, video, audio. */
        MEDIA;

        /**
         * The value objects in this area.
         *
         * @return the models, each after the models it holds; unmodifiable
         */
        public List<Model> models() {
            return switch (this) {
                case CONTACT -> List.of(USERNAME, EMAIL_ADDRESS, FULL_NAME, PHONE_NUMBER, LINK, ADDRESS);
                case FINANCIAL -> List.of(CURRENCY, MONEY, PERCENTAGE, CRYPTO_CURRENCY_ADDRESS);
                case GEO -> List.of(COORDINATES, POSTAL_CODE);
                case IDENTIFIER -> List.of(SLUG);
                case MEASUREMENT -> List.of(DISTANCE, WEIGHT, FILE_SIZE);
                case NETWORK -> List.of(DOMAIN_NAME, IPV4_ADDRESS, IPV6_ADDRESS, IP_ADDRESS, MAC_ADDRESS, PORT,
                                        SSH_CONNECTION);
                case TEMPORAL -> List.of(DATE_OF_BIRTH, DATE_RANGE, DATE_TIME_WITH_ZONE, DURATION_NANO,
                                         EFFECTIVE_PERIOD, RECURRENCE, TIME, TIME_BLOCK, TIMESTAMP);
                case VERSIONING -> List.of(SEMANTIC_VERSION);
                case VISUAL -> List.of(COLOR);
                case GENERAL -> List.of(COUNT);
                case MEDIA -> List.of(FILE, IMAGE, VIDEO, AUDIO);
            };
        }
    }

    /**
     * Every predefined value object, area by area, each after the models it holds, so they can be created one by one
     * in this order.
     *
     * @return the models; unmodifiable
     */
    public static List<Model> all() {
        return Arrays.stream(Area.values()).flatMap(area -> area.models().stream()).toList();
    }

    /**
     * Adds the value objects to a schema being built, skipping any whose ID the builder already has (that model is
     * kept as it is).
     *
     * @param builder the schema builder
     * @return the same builder
     */
    public static Schema.Builder install(Schema.Builder builder) {
        Schema current = builder.buildUnchecked();
        for (Model model : all()) {
            if (!current.contains(model.id())) {
                builder.model(model);
            }
        }
        return builder;
    }

    /**
     * Creates the value objects that are missing, in dependency order. A model whose ID already exists is left as it
     * is, so running this again changes nothing.
     *
     * @param design where models are created, such as {@code engine.models()} or a
     *               {@link io.joshuasalcedo.schema.application.ModelService}
     * @return the models that were created; empty when all were there already
     * @throws io.joshuasalcedo.schema.SchemaException INVALID if a value object does not fit the existing models, e.g. when a
     *                                       model it holds was replaced by an incompatible one
     */
    public static List<Model> install(ModelDesign design) {
        List<Model> created = new ArrayList<>();
        for (Model model : all()) {
            if (design.getModel(model.id()).isEmpty()) {
                created.add(design.createModel(model));
            }
        }
        return List.copyOf(created);
    }

    private static String[] concat(String[] first, String[] second) {
        return Stream.concat(Arrays.stream(first), Arrays.stream(second)).toArray(String[]::new);
    }
}

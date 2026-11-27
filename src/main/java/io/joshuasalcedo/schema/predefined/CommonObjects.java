package io.joshuasalcedo.schema.predefined;

import static io.joshuasalcedo.schema.Normalize.BLANK_TO_ABSENT;
import static io.joshuasalcedo.schema.Normalize.COLLAPSE_SPACES;
import static io.joshuasalcedo.schema.Normalize.IPV6_CANONICAL;
import static io.joshuasalcedo.schema.Normalize.TRIM;
import static io.joshuasalcedo.schema.Normalize.UPPERCASE;

import io.joshuasalcedo.schema.Field;
import io.joshuasalcedo.schema.Model;
import io.joshuasalcedo.schema.Schema;
import io.joshuasalcedo.schema.port.in.ModelDesign;
import io.joshuasalcedo.valueobject.ValueObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Value objects people need that io.joshuasalcedo:value-object has no class for: a person, a company, a bank account,
 * a currency with its kind, IP addresses of either version, time zones and times in them, periods, recurrences and an
 * SSH connection. Written by hand (kept from schema-model 0.2.0); they hold the {@link ValueObjectLibrary} models
 * where they hold an email, a phone number, a link, an address, a time or a port, so install the library first, or
 * use {@link #installAll}.
 */
public final class CommonObjects {

    private static final String OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final String IPV4 = "(?:" + OCTET + "\\.){3}" + OCTET;
    /** One IPv6 group, lower-case hexadecimal without leading zeros. */
    private static final String HEX_GROUP = "(?:0|[1-9a-f][0-9a-f]{0,3})";
    private static final String IPV6 = "(?:" + HEX_GROUP + ":){7}" + HEX_GROUP;

    /** Fiat currencies: the library's FiatCurrency constants. */
    private static final String[] FIAT_CURRENCY = Arrays.stream(ValueObject.Type.FIAT_CURRENCY.type().getEnumConstants())
            .map(c -> ((Enum<?>) c).name()).toArray(String[]::new);
    /** Crypto coins (the library's CryptoCoin is an interface, with no list to read). */
    private static final String[] CRYPTO_COIN = {
            "BTC", "ETH", "USDT", "BNB", "SOL", "XRP", "ADA", "DOGE", "MATIC", "DOT", "AVAX", "LINK", "UNI", "LTC",
            "ATOM", "USDC"
    };
    private static final String[] FREQUENCY = {"ONCE", "DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"};
    private static final String[] ZONE_IDS =
            java.time.ZoneId.getAvailableZoneIds().stream().sorted().toArray(String[]::new);

    // ==================== people and organisations ====================

    /** {@code person}: a person as a value — who signed, who requested, who to contact. */
    public static final Model PERSON = Model.valueObject("Person")
            .description("A person as a value: who signed, who requested, who to contact. Their name, what they do, "
                         + "how to reach them and the organisation they belong to; an id when they are known to "
                         + "some system (a staff number, a user id). A person with a history of their own is an "
                         + "entity instead.")
            .field(Field.model("name", "full-name").required())
            .field(Field.text("personId").normalize(TRIM, BLANK_TO_ABSENT)
                           .description("Their id in the system or organisation that knows them: staff number, "
                                        + "user id."))
            .field(Field.text("jobTitle").normalize(TRIM, COLLAPSE_SPACES, BLANK_TO_ABSENT))
            .field(Field.text("organization").normalize(TRIM, COLLAPSE_SPACES, BLANK_TO_ABSENT)
                           .description("The organisation they belong to, by name or code."))
            .field(Field.model("email", "email"))
            .field(Field.model("phone", "phone-number"))
            .build();

    /** {@code company}: a company or other organisation as a value. */
    public static final Model COMPANY = Model.valueObject("Company")
            .description("A company or other organisation as a value: the party on an invoice, a supplier on a "
                         + "quotation. Its trading and legal names, registration and tax numbers, and how to reach "
                         + "it. An organisation with a history of its own is an entity instead.")
            .field(Field.text("name").required().normalize(TRIM, COLLAPSE_SPACES)
                           .description("The name it trades under."))
            .field(Field.text("legalName").normalize(TRIM, COLLAPSE_SPACES, BLANK_TO_ABSENT)
                           .description("The registered legal name, when it differs."))
            .field(Field.text("registrationNumber").normalize(TRIM, BLANK_TO_ABSENT)
                           .description("Commercial registration number, as written."))
            .field(Field.text("taxNumber").normalize(TRIM, BLANK_TO_ABSENT)
                           .description("Tax or VAT registration number, as written."))
            .field(Field.model("email", "email"))
            .field(Field.model("phone", "phone-number"))
            .field(Field.model("website", "link"))
            .field(Field.model("address", "address"))
            .build();

    // ==================== money ====================

    /** {@code currency}: a currency, fiat or crypto, by the constant that names it. */
    public static final Model CURRENCY = Model.valueObject("Currency")
            .description("A currency, fiat (ISO 4217) or crypto, by the constant that names it.")
            .field(Field.choice("type", "FiatCurrency", "CryptoCoin").required()
                           .normalize(TRIM)
                           .description("Which kind of currency: FiatCurrency (ISO 4217) or CryptoCoin."))
            .field(Field.choice("code", Stream.concat(Arrays.stream(FIAT_CURRENCY), Arrays.stream(CRYPTO_COIN))
                                   .distinct().toArray(String[]::new)).required()
                           .normalize(TRIM, UPPERCASE)
                           .description("The FiatCurrency or CryptoCoin constant, such as USD or BTC, of the given "
                                        + "type."))
            .invariant("value.code in (value.type == 'CryptoCoin' ? " + celList(CRYPTO_COIN) + " : "
                       + celList(FIAT_CURRENCY) + ")", "the code is not a currency of that type")
            .build();

    /** {@code bank-account}: a bank account to pay into. */
    public static final Model BANK_ACCOUNT = Model.valueObject("BankAccount")
            .description("A bank account to pay into: whose it is, the bank, its IBAN or local account number, "
                         + "the SWIFT/BIC code and the account's currency.")
            .field(Field.text("accountName").required().normalize(TRIM, COLLAPSE_SPACES)
                           .description("The account holder's name, as the bank has it."))
            .field(Field.text("bankName").normalize(TRIM, COLLAPSE_SPACES, BLANK_TO_ABSENT))
            .field(Field.text("iban").normalize(TRIM, UPPERCASE, BLANK_TO_ABSENT)
                           .pattern("[A-Z]{2}[0-9]{2}[A-Z0-9 ]{11,40}")
                           .description("The IBAN, with or without spaces between groups."))
            .field(Field.text("accountNumber").normalize(TRIM, BLANK_TO_ABSENT)
                           .description("The local account number, where there is no IBAN."))
            .field(Field.text("swiftBic").normalize(TRIM, UPPERCASE, BLANK_TO_ABSENT)
                           .pattern("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?")
                           .description("The bank's SWIFT/BIC code, 8 or 11 characters."))
            .field(Field.model("currency", "currency"))
            .invariant("has(value.iban) || has(value.accountNumber)", "give an IBAN or an account number")
            .build();

    // ==================== network ====================

    /** {@code ip-address}: an IP address of either version. */
    public static final Model IP_ADDRESS = Model.valueObject("IpAddress")
            .description("An IP address of either version; the version follows from the form of the value.")
            .field(Field.text("value").required()
                           .normalize(TRIM, IPV6_CANONICAL)
                           .pattern("(?:" + IPV4 + ")|(?:" + IPV6 + ")")
                           .description("An IPv4 or IPv6 address, stored in the same form as the ipv4-address and "
                                        + "ipv6-address models."))
            .build();

    /** {@code ssh-connection}: SSH endpoint metadata. */
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

    // ==================== time ====================

    /** {@code time-zone}: a time zone by its IANA id. */
    public static final Model TIME_ZONE = Model.valueObject("TimeZone")
            .description("A time zone by its IANA id, such as Asia/Qatar or Europe/London: the rules of a place's "
                         + "clocks, summer time included. Not an offset: +03:00 says nothing about next summer.")
            .field(Field.choice("id", ZONE_IDS).required().normalize(TRIM)
                           .description("The IANA (tz database) id, as java.time.ZoneId knows it."))
            .build();

    /** {@code zoned-date-time}: a date and time on the wall clock of a time zone. */
    public static final Model ZONED_DATE_TIME = Model.valueObject("ZonedDateTime")
            .description("A date and time as the clock on the wall shows it in a time zone: a meeting at 09:00 in "
                         + "Doha stays at 09:00 Doha time even if the zone's rules change. Use a TIMESTAMP field for "
                         + "an instant that only needs to be ordered.")
            .field(Field.text("dateTime").required().normalize(TRIM)
                           .pattern("[0-9]{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12][0-9]|3[01])"
                                    + "T(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9](?:\\.[0-9]{1,9})?)?")
                           .description("The local date and time, ISO-8601 without an offset: 2026-11-21T09:00."))
            .field(Field.model("zone", "time-zone").required())
            .build();

    /** {@code time-of-day}: a time of day on the 24-hour clock. */
    public static final Model TIME_OF_DAY = Model.valueObject("TimeOfDay")
            .description("A time of day on the 24-hour clock, such as 06:00 or 22:30, for opening hours, shift "
                         + "starts and cut-off times; with a time zone when it belongs to a place.")
            .field(Field.text("time").required().normalize(TRIM)
                           .pattern("(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?")
                           .description("HH:mm or HH:mm:ss, 24-hour."))
            .field(Field.model("zone", "time-zone").description("The zone it is in; absent = wherever it is used."))
            .build();

    /** {@code effective-period}: an inclusive start and an optional exclusive end. */
    public static final Model EFFECTIVE_PERIOD = Model.valueObject("EffectivePeriod")
            .description("An effective period: an inclusive start and an optional exclusive end.")
            .field(Field.timestamp("from").required().description("The first instant of the period."))
            .field(Field.timestamp("through")
                           .description("When the period ends, exclusive and not before the start; absent for no "
                                        + "end."))
            .invariant("!has(value.through) || value.through >= value.from", "through must not be before from")
            .build();

    /** {@code time-block}: a start/end block of time of day. */
    public static final Model TIME_BLOCK = Model.valueObject("TimeBlock")
            .description("A start/end block of time of day, with no date or zone; an end not after the start spans "
                         + "midnight.")
            .field(Field.model("start", "time").required())
            .field(Field.model("end", "time").required().description("Must differ from the start."))
            .invariant("value.start != value.end", "start and end must differ")
            .build();

    /** {@code recurrence}: how often something repeats. */
    public static final Model RECURRENCE = Model.valueObject("Recurrence")
            .description("How often something repeats: every interval days, weeks, months, quarters or years.")
            .field(Field.choice("frequency", FREQUENCY).required()
                           .normalize(TRIM, UPPERCASE)
                           .description("The unit of the interval; ONCE means it does not repeat."))
            .field(Field.integer("interval").required().min("1")
                           .description("How many units between runs, at least 1."))
            .build();

    private CommonObjects() {
    }

    /** Every model, each after the ones it holds (of this class; the library's come first, see {@link #installAll}). */
    public static List<Model> all() {
        return List.of(PERSON, COMPANY, CURRENCY, BANK_ACCOUNT, IP_ADDRESS, SSH_CONNECTION, TIME_ZONE,
                ZONED_DATE_TIME, TIME_OF_DAY, EFFECTIVE_PERIOD, TIME_BLOCK, RECURRENCE);
    }

    /** Adds the models a schema being built lacks; the library's must be there (see {@link #installAll}). */
    public static Schema.Builder install(Schema.Builder builder) {
        Schema current = builder.buildUnchecked();
        for (Model model : all()) {
            if (!current.contains(model.id())) {
                builder.model(model);
            }
        }
        return builder;
    }

    /** Creates the models that are missing; the library's must exist (see {@link #installAll}). */
    public static List<Model> install(ModelDesign design) {
        List<Model> created = new ArrayList<>();
        for (Model model : all()) {
            if (design.getModel(model.id()).isEmpty()) {
                created.add(design.createModel(model));
            }
        }
        return List.copyOf(created);
    }

    /** Every predefined value object: the library's, these, and the media ones, in dependency order. */
    public static Schema.Builder installAll(Schema.Builder builder) {
        return MediaObjects.install(install(ValueObjectLibrary.install(builder)));
    }

    /** Every predefined value object: the library's, these, and the media ones, in dependency order. */
    public static List<Model> installAll(ModelDesign design) {
        List<Model> created = new ArrayList<>(ValueObjectLibrary.install(design));
        created.addAll(install(design));
        created.addAll(MediaObjects.install(design));
        return List.copyOf(created);
    }

    private static String celList(String... values) {
        return Arrays.stream(values).map(v -> "'" + v + "'").collect(Collectors.joining(", ", "[", "]"));
    }
}

package io.joshuasalcedo.schema.predefined;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The currencies of the {@code currency} value object, with what the Java {@code FiatCurrency} and
 * {@code CryptoCoin} enums know about each: name, symbol and decimal places. Generated from those enums (do not edit
 * by hand), so a {@code money} amount can be rounded and shown the way the Java {@code Money} does.
 * <pre>{@code
 * Currencies.of("PHP").orElseThrow().symbol();                      // "₱"
 * Currencies.round(new BigDecimal("1234.567"), "PHP");               // 1234.57
 * Currencies.round(new BigDecimal("0.123456789"), "BTC");           // 0.12345679
 * }</pre>
 */
public final class Currencies {

    /**
     * One currency.
     *
     * @param constant      the enum constant used as the {@code code} choice, e.g. {@code PHP} or {@code UNKNOWN}
     * @param code          the ISO 4217 or ticker code, e.g. {@code PHP}, {@code XXX}, {@code BTC}
     * @param name          e.g. "Philippine Peso"
     * @param symbol        e.g. "₱"
     * @param decimalPlaces the places an amount is rounded to (half-even), e.g. 2, 0 for JPY, 8 for BTC
     * @param crypto        true for a {@code CryptoCoin}, false for a {@code FiatCurrency}
     */
    public record Currency(String constant, String code, String name, String symbol, int decimalPlaces,
                           boolean crypto) {

        /**
         * The {@code type} the {@code currency} value object stores for it.
         *
         * @return {@code CryptoCoin} or {@code FiatCurrency}
         */
        public String type() {
            return crypto ? "CryptoCoin" : "FiatCurrency";
        }
    }

    private static final Map<String, Currency> BY_CONSTANT = table(
            entry("UNKNOWN", "XXX", "Unknown Currency", "?", 0, false),
            entry("AED", "AED", "UAE Dirham", "د.إ", 2, false),
            entry("AFN", "AFN", "Afghani", "؋", 2, false),
            entry("ALL", "ALL", "Lek", "L", 2, false),
            entry("AMD", "AMD", "Armenian Dram", "֏", 2, false),
            entry("ANG", "ANG", "Netherlands Antillean Guilder", "ƒ", 2, false),
            entry("AOA", "AOA", "Kwanza", "Kz", 2, false),
            entry("ARS", "ARS", "Argentine Peso", "$", 2, false),
            entry("AUD", "AUD", "Australian Dollar", "A$", 2, false),
            entry("AWG", "AWG", "Aruban Florin", "ƒ", 2, false),
            entry("AZN", "AZN", "Azerbaijanian Manat", "₼", 2, false),
            entry("BAM", "BAM", "Convertible Mark", "KM", 2, false),
            entry("BBD", "BBD", "Barbados Dollar", "Bds$", 2, false),
            entry("BDT", "BDT", "Taka", "৳", 2, false),
            entry("BGN", "BGN", "Bulgarian Lev", "лв", 2, false),
            entry("BHD", "BHD", "Bahraini Dinar", ".د.ب", 3, false),
            entry("BIF", "BIF", "Burundi Franc", "FBu", 0, false),
            entry("BMD", "BMD", "Bermudian Dollar", "$", 2, false),
            entry("BND", "BND", "Brunei Dollar", "B$", 2, false),
            entry("BOB", "BOB", "Boliviano", "Bs.", 2, false),
            entry("BRL", "BRL", "Brazilian Real", "R$", 2, false),
            entry("BSD", "BSD", "Bahamian Dollar", "B$", 2, false),
            entry("BTN", "BTN", "Ngultrum", "Nu.", 2, false),
            entry("BWP", "BWP", "Pula", "P", 2, false),
            entry("BYN", "BYN", "Belarussian Ruble", "Br", 2, false),
            entry("BZD", "BZD", "Belize Dollar", "BZ$", 2, false),
            entry("CAD", "CAD", "Canadian Dollar", "C$", 2, false),
            entry("CDF", "CDF", "Congolese Franc", "FC", 2, false),
            entry("CHF", "CHF", "Swiss Franc", "CHF", 2, false),
            entry("CLP", "CLP", "Chilean Peso", "$", 0, false),
            entry("CNY", "CNY", "Chinese Yuan", "¥", 2, false),
            entry("COP", "COP", "Colombian Peso", "$", 2, false),
            entry("CRC", "CRC", "Costa Rican Colon", "₡", 2, false),
            entry("CUP", "CUP", "Cuban Peso", "₱", 2, false),
            entry("CVE", "CVE", "Cabo Verde Escudo", "$", 2, false),
            entry("CZK", "CZK", "Czech Koruna", "Kč", 2, false),
            entry("DJF", "DJF", "Djibouti Franc", "Fdj", 0, false),
            entry("DKK", "DKK", "Danish Krone", "kr", 2, false),
            entry("DOP", "DOP", "Dominican Peso", "RD$", 2, false),
            entry("DZD", "DZD", "Algerian Dinar", "د.ج", 2, false),
            entry("EGP", "EGP", "Egyptian Pound", "E£", 2, false),
            entry("ERN", "ERN", "Nakfa", "Nfk", 2, false),
            entry("ETB", "ETB", "Ethiopian Birr", "Br", 2, false),
            entry("EUR", "EUR", "Euro", "€", 2, false),
            entry("FJD", "FJD", "Fiji Dollar", "FJ$", 2, false),
            entry("FKP", "FKP", "Falkland Islands Pound", "£", 2, false),
            entry("GBP", "GBP", "Pound Sterling", "£", 2, false),
            entry("GEL", "GEL", "Lari", "₾", 2, false),
            entry("GHS", "GHS", "Ghana Cedi", "GH₵", 2, false),
            entry("GIP", "GIP", "Gibraltar Pound", "£", 2, false),
            entry("GMD", "GMD", "Dalasi", "D", 2, false),
            entry("GNF", "GNF", "Guinea Franc", "FG", 0, false),
            entry("GTQ", "GTQ", "Quetzal", "Q", 2, false),
            entry("GYD", "GYD", "Guyana Dollar", "G$", 2, false),
            entry("HKD", "HKD", "Hong Kong Dollar", "HK$", 2, false),
            entry("HNL", "HNL", "Lempira", "L", 2, false),
            entry("HTG", "HTG", "Gourde", "G", 2, false),
            entry("HUF", "HUF", "Forint", "Ft", 2, false),
            entry("IDR", "IDR", "Rupiah", "Rp", 2, false),
            entry("ILS", "ILS", "New Israeli Sheqel", "₪", 2, false),
            entry("INR", "INR", "Indian Rupee", "₹", 2, false),
            entry("IQD", "IQD", "Iraqi Dinar", "ع.د", 3, false),
            entry("IRR", "IRR", "Iranian Rial", "﷼", 2, false),
            entry("ISK", "ISK", "Iceland Krona", "kr", 0, false),
            entry("JMD", "JMD", "Jamaican Dollar", "J$", 2, false),
            entry("JOD", "JOD", "Jordanian Dinar", "د.ا", 3, false),
            entry("JPY", "JPY", "Japanese Yen", "¥", 0, false),
            entry("KES", "KES", "Kenyan Shilling", "KSh", 2, false),
            entry("KGS", "KGS", "Som", "лв", 2, false),
            entry("KHR", "KHR", "Riel", "៛", 2, false),
            entry("KMF", "KMF", "Comoro Franc", "CF", 0, false),
            entry("KPW", "KPW", "North Korean Won", "₩", 2, false),
            entry("KRW", "KRW", "South Korean Won", "₩", 0, false),
            entry("KWD", "KWD", "Kuwaiti Dinar", "د.ك", 3, false),
            entry("KYD", "KYD", "Cayman Islands Dollar", "CI$", 2, false),
            entry("KZT", "KZT", "Tenge", "₸", 2, false),
            entry("LAK", "LAK", "Kip", "₭", 2, false),
            entry("LBP", "LBP", "Lebanese Pound", "L£", 2, false),
            entry("LKR", "LKR", "Sri Lanka Rupee", "Rs", 2, false),
            entry("LRD", "LRD", "Liberian Dollar", "L$", 2, false),
            entry("LSL", "LSL", "Loti", "M", 2, false),
            entry("LYD", "LYD", "Libyan Dinar", "ل.د", 3, false),
            entry("MAD", "MAD", "Moroccan Dirham", "MAD", 2, false),
            entry("MDL", "MDL", "Moldovan Leu", "L", 2, false),
            entry("MGA", "MGA", "Malagasy Ariary", "Ar", 2, false),
            entry("MKD", "MKD", "Denar", "ден", 2, false),
            entry("MMK", "MMK", "Kyat", "K", 2, false),
            entry("MNT", "MNT", "Tugrik", "₮", 2, false),
            entry("MOP", "MOP", "Pataca", "MOP$", 2, false),
            entry("MRU", "MRU", "Ouguiya", "UM", 2, false),
            entry("MUR", "MUR", "Mauritius Rupee", "₨", 2, false),
            entry("MVR", "MVR", "Rufiyaa", "Rf", 2, false),
            entry("MWK", "MWK", "Kwacha", "MK", 2, false),
            entry("MXN", "MXN", "Mexican Peso", "MX$", 2, false),
            entry("MYR", "MYR", "Malaysian Ringgit", "RM", 2, false),
            entry("MZN", "MZN", "Mozambique Metical", "MT", 2, false),
            entry("NAD", "NAD", "Namibia Dollar", "N$", 2, false),
            entry("NGN", "NGN", "Naira", "₦", 2, false),
            entry("NIO", "NIO", "Cordoba Oro", "C$", 2, false),
            entry("NOK", "NOK", "Norwegian Krone", "kr", 2, false),
            entry("NPR", "NPR", "Nepalese Rupee", "₨", 2, false),
            entry("NZD", "NZD", "New Zealand Dollar", "NZ$", 2, false),
            entry("OMR", "OMR", "Rial Omani", "ر.ع.", 3, false),
            entry("PAB", "PAB", "Balboa", "B/.", 2, false),
            entry("PEN", "PEN", "Nuevo Sol", "S/.", 2, false),
            entry("PGK", "PGK", "Kina", "K", 2, false),
            entry("PHP", "PHP", "Philippine Peso", "₱", 2, false),
            entry("PKR", "PKR", "Pakistan Rupee", "₨", 2, false),
            entry("PLN", "PLN", "Zloty", "zł", 2, false),
            entry("PYG", "PYG", "Guarani", "₲", 0, false),
            entry("QAR", "QAR", "Qatari Rial", "ر.ق", 2, false),
            entry("RON", "RON", "Romanian Leu", "lei", 2, false),
            entry("RSD", "RSD", "Serbian Dinar", "дин.", 2, false),
            entry("RUB", "RUB", "Russian Ruble", "₽", 2, false),
            entry("RWF", "RWF", "Rwanda Franc", "RF", 0, false),
            entry("SAR", "SAR", "Saudi Riyal", "ر.س", 2, false),
            entry("SBD", "SBD", "Solomon Islands Dollar", "SI$", 2, false),
            entry("SCR", "SCR", "Seychelles Rupee", "₨", 2, false),
            entry("SDG", "SDG", "Sudanese Pound", "ج.س.", 2, false),
            entry("SEK", "SEK", "Swedish Krona", "kr", 2, false),
            entry("SGD", "SGD", "Singapore Dollar", "S$", 2, false),
            entry("SHP", "SHP", "Saint Helena Pound", "£", 2, false),
            entry("SLE", "SLE", "Leone", "Le", 2, false),
            entry("SOS", "SOS", "Somali Shilling", "Sh", 2, false),
            entry("SRD", "SRD", "Surinam Dollar", "$", 2, false),
            entry("SSP", "SSP", "South Sudanese Pound", "£", 2, false),
            entry("STN", "STN", "Dobra", "Db", 2, false),
            entry("SVC", "SVC", "El Salvador Colon", "₡", 2, false),
            entry("SYP", "SYP", "Syrian Pound", "£S", 2, false),
            entry("SZL", "SZL", "Lilangeni", "E", 2, false),
            entry("THB", "THB", "Baht", "฿", 2, false),
            entry("TJS", "TJS", "Somoni", "SM", 2, false),
            entry("TMT", "TMT", "Turkmenistan New Manat", "T", 2, false),
            entry("TND", "TND", "Tunisian Dinar", "د.ت", 3, false),
            entry("TOP", "TOP", "Pa'anga", "T$", 2, false),
            entry("TRY", "TRY", "Turkish Lira", "₺", 2, false),
            entry("TTD", "TTD", "Trinidad and Tobago Dollar", "TT$", 2, false),
            entry("TWD", "TWD", "New Taiwan Dollar", "NT$", 2, false),
            entry("TZS", "TZS", "Tanzanian Shilling", "TSh", 2, false),
            entry("UAH", "UAH", "Hryvnia", "₴", 2, false),
            entry("UGX", "UGX", "Uganda Shilling", "USh", 0, false),
            entry("USD", "USD", "US Dollar", "$", 2, false),
            entry("UYU", "UYU", "Peso Uruguayo", "$U", 2, false),
            entry("UZS", "UZS", "Uzbekistan Sum", "сўм", 2, false),
            entry("VED", "VED", "Bolivar Digital", "Bs.D", 2, false),
            entry("VES", "VES", "Bolivar Soberano", "Bs.S", 2, false),
            entry("VND", "VND", "Dong", "₫", 0, false),
            entry("VUV", "VUV", "Vatu", "VT", 0, false),
            entry("WST", "WST", "Tala", "WS$", 2, false),
            entry("XAF", "XAF", "CFA Franc BEAC", "FCFA", 0, false),
            entry("XCD", "XCD", "East Caribbean Dollar", "EC$", 2, false),
            entry("XOF", "XOF", "CFA Franc BCEAO", "CFA", 0, false),
            entry("XPF", "XPF", "CFP Franc", "₣", 0, false),
            entry("YER", "YER", "Yemeni Rial", "﷼", 2, false),
            entry("ZAR", "ZAR", "South African Rand", "R", 2, false),
            entry("ZMW", "ZMW", "Zambian Kwacha", "ZK", 2, false),
            entry("ZWL", "ZWL", "Zimbabwe Dollar", "Z$", 2, false),
            entry("BTC", "BTC", "Bitcoin", "₿", 8, true),
            entry("ETH", "ETH", "Ethereum", "Ξ", 18, true),
            entry("USDT", "USDT", "Tether", "₮", 6, true),
            entry("BNB", "BNB", "Binance CryptoCoin", "BNB", 8, true),
            entry("SOL", "SOL", "Solana", "SOL", 9, true),
            entry("XRP", "XRP", "Ripple", "XRP", 6, true),
            entry("ADA", "ADA", "Cardano", "₳", 6, true),
            entry("DOGE", "DOGE", "Dogecoin", "Ð", 8, true),
            entry("MATIC", "MATIC", "Polygon", "MATIC", 18, true),
            entry("DOT", "DOT", "Polkadot", "DOT", 10, true),
            entry("AVAX", "AVAX", "Avalanche", "AVAX", 9, true),
            entry("LINK", "LINK", "Chainlink", "LINK", 18, true),
            entry("UNI", "UNI", "Uniswap", "UNI", 18, true),
            entry("LTC", "LTC", "Litecoin", "Ł", 8, true),
            entry("ATOM", "ATOM", "Cosmos", "ATOM", 6, true),
            entry("USDC", "USDC", "USD CryptoCoin", "$", 2, true)    );

    private Currencies() {
    }

    /**
     * Every currency: the fiat currencies, then the crypto coins, in enum order.
     *
     * @return the currencies
     */
    public static List<Currency> all() {
        return List.copyOf(BY_CONSTANT.values());
    }

    /**
     * A currency by its enum constant (the {@code code} the {@code currency} value object stores).
     *
     * @param constant e.g. {@code PHP}; any case
     * @return the currency, or empty when there is none by that name
     */
    public static Optional<Currency> of(String constant) {
        return constant == null ? Optional.empty()
                : Optional.ofNullable(BY_CONSTANT.get(constant.strip().toUpperCase(java.util.Locale.ROOT)));
    }

    /**
     * Rounds an amount half-even to the currency's decimal places, as the Java {@code Money} constructor does.
     *
     * @param amount   the amount
     * @param constant the currency's constant
     * @return the rounded amount; {@code amount} unchanged for an unknown currency
     */
    public static BigDecimal round(BigDecimal amount, String constant) {
        return of(constant).map(c -> amount.setScale(c.decimalPlaces(), RoundingMode.HALF_EVEN)).orElse(amount);
    }

    private static Currency entry(String constant, String code, String name, String symbol, int places,
                                  boolean crypto) {
        return new Currency(constant, code, name, symbol, places, crypto);
    }

    private static Map<String, Currency> table(Currency... currencies) {
        Map<String, Currency> map = new LinkedHashMap<>();
        for (Currency c : currencies) {
            map.put(c.constant(), c);
        }
        return java.util.Collections.unmodifiableMap(map);
    }
}

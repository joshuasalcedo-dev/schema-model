package io.joshuasalcedo.schema.predefined;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The countries of the {@code country} choices (Address, PostalCode), with what the Java {@code Country} enum knows
 * about each: ISO 3166-1 alpha-2 code, display name and continent. Generated from that enum (do not edit by hand).
 * <pre>{@code
 * Countries.of("PHILIPPINES").orElseThrow().code();   // "PH"
 * Countries.byCode("gb").orElseThrow().constant();    // "UNITED_KINGDOM"
 * }</pre>
 */
public final class Countries {

    /**
     * One country.
     *
     * @param constant    the enum constant used as the choice, e.g. {@code PHILIPPINES}
     * @param code        the ISO 3166-1 alpha-2 code, e.g. {@code PH}; {@code XX} for {@code UNKNOWN}
     * @param displayName e.g. "Philippines"
     * @param continent   the continent constant, e.g. {@code ASIA}
     */
    public record Country(String constant, String code, String displayName, String continent) {
    }

    private static final Map<String, Country> BY_CONSTANT = table(
            entry("AFGHANISTAN", "AF", "Afghanistan", "ASIA"),
            entry("ALAND_ISLANDS", "AX", "Åland Islands", "EUROPE"),
            entry("ALBANIA", "AL", "Albania", "EUROPE"),
            entry("ALGERIA", "DZ", "Algeria", "AFRICA"),
            entry("AMERICAN_SAMOA", "AS", "American Samoa", "OCEANIA"),
            entry("ANDORRA", "AD", "Andorra", "EUROPE"),
            entry("ANGOLA", "AO", "Angola", "AFRICA"),
            entry("ANGUILLA", "AI", "Anguilla", "NORTH_AMERICA"),
            entry("ANTARCTICA", "AQ", "Antarctica", "ANTARCTICA"),
            entry("ANTIGUA_AND_BARBUDA", "AG", "Antigua & Barbuda", "NORTH_AMERICA"),
            entry("ARGENTINA", "AR", "Argentina", "SOUTH_AMERICA"),
            entry("ARMENIA", "AM", "Armenia", "ASIA"),
            entry("ARUBA", "AW", "Aruba", "NORTH_AMERICA"),
            entry("AUSTRALIA", "AU", "Australia", "OCEANIA"),
            entry("AUSTRIA", "AT", "Austria", "EUROPE"),
            entry("AZERBAIJAN", "AZ", "Azerbaijan", "ASIA"),
            entry("BAHAMAS", "BS", "Bahamas", "NORTH_AMERICA"),
            entry("BAHRAIN", "BH", "Bahrain", "ASIA"),
            entry("BANGLADESH", "BD", "Bangladesh", "ASIA"),
            entry("BARBADOS", "BB", "Barbados", "NORTH_AMERICA"),
            entry("BELARUS", "BY", "Belarus", "EUROPE"),
            entry("BELGIUM", "BE", "Belgium", "EUROPE"),
            entry("BELIZE", "BZ", "Belize", "NORTH_AMERICA"),
            entry("BENIN", "BJ", "Benin", "AFRICA"),
            entry("BERMUDA", "BM", "Bermuda", "NORTH_AMERICA"),
            entry("BHUTAN", "BT", "Bhutan", "ASIA"),
            entry("BOLIVIA", "BO", "Bolivia", "SOUTH_AMERICA"),
            entry("BOSNIA_AND_HERZEGOVINA", "BA", "Bosnia & Herzegovina", "EUROPE"),
            entry("BOTSWANA", "BW", "Botswana", "AFRICA"),
            entry("BOUVET_ISLAND", "BV", "Bouvet Island", "ANTARCTICA"),
            entry("BRAZIL", "BR", "Brazil", "SOUTH_AMERICA"),
            entry("BRITISH_INDIAN_OCEAN_TERRITORY", "IO", "British Indian Ocean Territory", "ASIA"),
            entry("BRITISH_VIRGIN_ISLANDS", "VG", "British Virgin Islands", "NORTH_AMERICA"),
            entry("BRUNEI", "BN", "Brunei", "ASIA"),
            entry("BULGARIA", "BG", "Bulgaria", "EUROPE"),
            entry("BURKINA_FASO", "BF", "Burkina Faso", "AFRICA"),
            entry("BURUNDI", "BI", "Burundi", "AFRICA"),
            entry("CAMBODIA", "KH", "Cambodia", "ASIA"),
            entry("CAMEROON", "CM", "Cameroon", "AFRICA"),
            entry("CANADA", "CA", "Canada", "NORTH_AMERICA"),
            entry("CAPE_VERDE", "CV", "Cape Verde", "AFRICA"),
            entry("CARIBBEAN_NETHERLANDS", "BQ", "Caribbean Netherlands", "NORTH_AMERICA"),
            entry("CAYMAN_ISLANDS", "KY", "Cayman Islands", "NORTH_AMERICA"),
            entry("CENTRAL_AFRICAN_REPUBLIC", "CF", "Central African Republic", "AFRICA"),
            entry("CHAD", "TD", "Chad", "AFRICA"),
            entry("CHILE", "CL", "Chile", "SOUTH_AMERICA"),
            entry("CHINA", "CN", "China", "ASIA"),
            entry("CHRISTMAS_ISLAND", "CX", "Christmas Island", "OCEANIA"),
            entry("COCOS_KEELING_ISLANDS", "CC", "Cocos (Keeling) Islands", "OCEANIA"),
            entry("COLOMBIA", "CO", "Colombia", "SOUTH_AMERICA"),
            entry("COMOROS", "KM", "Comoros", "AFRICA"),
            entry("CONGO_BRAZZAVILLE", "CG", "Congo - Brazzaville", "AFRICA"),
            entry("CONGO_KINSHASA", "CD", "Congo - Kinshasa", "AFRICA"),
            entry("COOK_ISLANDS", "CK", "Cook Islands", "OCEANIA"),
            entry("COSTA_RICA", "CR", "Costa Rica", "NORTH_AMERICA"),
            entry("COTE_DIVOIRE", "CI", "Côte d’Ivoire", "AFRICA"),
            entry("CROATIA", "HR", "Croatia", "EUROPE"),
            entry("CUBA", "CU", "Cuba", "NORTH_AMERICA"),
            entry("CURACAO", "CW", "Curaçao", "NORTH_AMERICA"),
            entry("CYPRUS", "CY", "Cyprus", "ASIA"),
            entry("CZECHIA", "CZ", "Czechia", "EUROPE"),
            entry("DENMARK", "DK", "Denmark", "EUROPE"),
            entry("DJIBOUTI", "DJ", "Djibouti", "AFRICA"),
            entry("DOMINICA", "DM", "Dominica", "NORTH_AMERICA"),
            entry("DOMINICAN_REPUBLIC", "DO", "Dominican Republic", "NORTH_AMERICA"),
            entry("ECUADOR", "EC", "Ecuador", "SOUTH_AMERICA"),
            entry("EGYPT", "EG", "Egypt", "AFRICA"),
            entry("EL_SALVADOR", "SV", "El Salvador", "NORTH_AMERICA"),
            entry("EQUATORIAL_GUINEA", "GQ", "Equatorial Guinea", "AFRICA"),
            entry("ERITREA", "ER", "Eritrea", "AFRICA"),
            entry("ESTONIA", "EE", "Estonia", "EUROPE"),
            entry("ESWATINI", "SZ", "Eswatini", "AFRICA"),
            entry("ETHIOPIA", "ET", "Ethiopia", "AFRICA"),
            entry("FALKLAND_ISLANDS", "FK", "Falkland Islands", "SOUTH_AMERICA"),
            entry("FAROE_ISLANDS", "FO", "Faroe Islands", "EUROPE"),
            entry("FIJI", "FJ", "Fiji", "OCEANIA"),
            entry("FINLAND", "FI", "Finland", "EUROPE"),
            entry("FRANCE", "FR", "France", "EUROPE"),
            entry("FRENCH_GUIANA", "GF", "French Guiana", "SOUTH_AMERICA"),
            entry("FRENCH_POLYNESIA", "PF", "French Polynesia", "OCEANIA"),
            entry("FRENCH_SOUTHERN_TERRITORIES", "TF", "French Southern Territories", "ANTARCTICA"),
            entry("GABON", "GA", "Gabon", "AFRICA"),
            entry("GAMBIA", "GM", "Gambia", "AFRICA"),
            entry("GEORGIA", "GE", "Georgia", "ASIA"),
            entry("GERMANY", "DE", "Germany", "EUROPE"),
            entry("GHANA", "GH", "Ghana", "AFRICA"),
            entry("GIBRALTAR", "GI", "Gibraltar", "EUROPE"),
            entry("GREECE", "GR", "Greece", "EUROPE"),
            entry("GREENLAND", "GL", "Greenland", "NORTH_AMERICA"),
            entry("GRENADA", "GD", "Grenada", "NORTH_AMERICA"),
            entry("GUADELOUPE", "GP", "Guadeloupe", "NORTH_AMERICA"),
            entry("GUAM", "GU", "Guam", "OCEANIA"),
            entry("GUATEMALA", "GT", "Guatemala", "NORTH_AMERICA"),
            entry("GUERNSEY", "GG", "Guernsey", "EUROPE"),
            entry("GUINEA", "GN", "Guinea", "AFRICA"),
            entry("GUINEA_BISSAU", "GW", "Guinea-Bissau", "AFRICA"),
            entry("GUYANA", "GY", "Guyana", "SOUTH_AMERICA"),
            entry("HAITI", "HT", "Haiti", "NORTH_AMERICA"),
            entry("HEARD_AND_MCDONALD_ISLANDS", "HM", "Heard & McDonald Islands", "ANTARCTICA"),
            entry("HONDURAS", "HN", "Honduras", "NORTH_AMERICA"),
            entry("HONG_KONG_SAR_CHINA", "HK", "Hong Kong SAR China", "ASIA"),
            entry("HUNGARY", "HU", "Hungary", "EUROPE"),
            entry("ICELAND", "IS", "Iceland", "EUROPE"),
            entry("INDIA", "IN", "India", "ASIA"),
            entry("INDONESIA", "ID", "Indonesia", "ASIA"),
            entry("IRAN", "IR", "Iran", "ASIA"),
            entry("IRAQ", "IQ", "Iraq", "ASIA"),
            entry("IRELAND", "IE", "Ireland", "EUROPE"),
            entry("ISLE_OF_MAN", "IM", "Isle of Man", "EUROPE"),
            entry("ISRAEL", "IL", "Israel", "ASIA"),
            entry("ITALY", "IT", "Italy", "EUROPE"),
            entry("JAMAICA", "JM", "Jamaica", "NORTH_AMERICA"),
            entry("JAPAN", "JP", "Japan", "ASIA"),
            entry("JERSEY", "JE", "Jersey", "EUROPE"),
            entry("JORDAN", "JO", "Jordan", "ASIA"),
            entry("KAZAKHSTAN", "KZ", "Kazakhstan", "ASIA"),
            entry("KENYA", "KE", "Kenya", "AFRICA"),
            entry("KIRIBATI", "KI", "Kiribati", "OCEANIA"),
            entry("KUWAIT", "KW", "Kuwait", "ASIA"),
            entry("KYRGYZSTAN", "KG", "Kyrgyzstan", "ASIA"),
            entry("LAOS", "LA", "Laos", "ASIA"),
            entry("LATVIA", "LV", "Latvia", "EUROPE"),
            entry("LEBANON", "LB", "Lebanon", "ASIA"),
            entry("LESOTHO", "LS", "Lesotho", "AFRICA"),
            entry("LIBERIA", "LR", "Liberia", "AFRICA"),
            entry("LIBYA", "LY", "Libya", "AFRICA"),
            entry("LIECHTENSTEIN", "LI", "Liechtenstein", "EUROPE"),
            entry("LITHUANIA", "LT", "Lithuania", "EUROPE"),
            entry("LUXEMBOURG", "LU", "Luxembourg", "EUROPE"),
            entry("MACAO_SAR_CHINA", "MO", "Macao SAR China", "ASIA"),
            entry("MADAGASCAR", "MG", "Madagascar", "AFRICA"),
            entry("MALAWI", "MW", "Malawi", "AFRICA"),
            entry("MALAYSIA", "MY", "Malaysia", "ASIA"),
            entry("MALDIVES", "MV", "Maldives", "ASIA"),
            entry("MALI", "ML", "Mali", "AFRICA"),
            entry("MALTA", "MT", "Malta", "EUROPE"),
            entry("MARSHALL_ISLANDS", "MH", "Marshall Islands", "OCEANIA"),
            entry("MARTINIQUE", "MQ", "Martinique", "NORTH_AMERICA"),
            entry("MAURITANIA", "MR", "Mauritania", "AFRICA"),
            entry("MAURITIUS", "MU", "Mauritius", "AFRICA"),
            entry("MAYOTTE", "YT", "Mayotte", "AFRICA"),
            entry("MEXICO", "MX", "Mexico", "NORTH_AMERICA"),
            entry("MICRONESIA", "FM", "Micronesia", "OCEANIA"),
            entry("MOLDOVA", "MD", "Moldova", "EUROPE"),
            entry("MONACO", "MC", "Monaco", "EUROPE"),
            entry("MONGOLIA", "MN", "Mongolia", "ASIA"),
            entry("MONTENEGRO", "ME", "Montenegro", "EUROPE"),
            entry("MONTSERRAT", "MS", "Montserrat", "NORTH_AMERICA"),
            entry("MOROCCO", "MA", "Morocco", "AFRICA"),
            entry("MOZAMBIQUE", "MZ", "Mozambique", "AFRICA"),
            entry("MYANMAR_BURMA", "MM", "Myanmar (Burma)", "ASIA"),
            entry("NAMIBIA", "NA", "Namibia", "AFRICA"),
            entry("NAURU", "NR", "Nauru", "OCEANIA"),
            entry("NEPAL", "NP", "Nepal", "ASIA"),
            entry("NETHERLANDS", "NL", "Netherlands", "EUROPE"),
            entry("NEW_CALEDONIA", "NC", "New Caledonia", "OCEANIA"),
            entry("NEW_ZEALAND", "NZ", "New Zealand", "OCEANIA"),
            entry("NICARAGUA", "NI", "Nicaragua", "NORTH_AMERICA"),
            entry("NIGER", "NE", "Niger", "AFRICA"),
            entry("NIGERIA", "NG", "Nigeria", "AFRICA"),
            entry("NIUE", "NU", "Niue", "OCEANIA"),
            entry("NORFOLK_ISLAND", "NF", "Norfolk Island", "OCEANIA"),
            entry("NORTH_KOREA", "KP", "North Korea", "ASIA"),
            entry("NORTH_MACEDONIA", "MK", "North Macedonia", "EUROPE"),
            entry("NORTHERN_MARIANA_ISLANDS", "MP", "Northern Mariana Islands", "OCEANIA"),
            entry("NORWAY", "NO", "Norway", "EUROPE"),
            entry("OMAN", "OM", "Oman", "ASIA"),
            entry("PAKISTAN", "PK", "Pakistan", "ASIA"),
            entry("PALAU", "PW", "Palau", "OCEANIA"),
            entry("PALESTINIAN_TERRITORIES", "PS", "Palestinian Territories", "ASIA"),
            entry("PANAMA", "PA", "Panama", "NORTH_AMERICA"),
            entry("PAPUA_NEW_GUINEA", "PG", "Papua New Guinea", "OCEANIA"),
            entry("PARAGUAY", "PY", "Paraguay", "SOUTH_AMERICA"),
            entry("PERU", "PE", "Peru", "SOUTH_AMERICA"),
            entry("PHILIPPINES", "PH", "Philippines", "ASIA"),
            entry("PITCAIRN_ISLANDS", "PN", "Pitcairn Islands", "OCEANIA"),
            entry("POLAND", "PL", "Poland", "EUROPE"),
            entry("PORTUGAL", "PT", "Portugal", "EUROPE"),
            entry("PUERTO_RICO", "PR", "Puerto Rico", "NORTH_AMERICA"),
            entry("QATAR", "QA", "Qatar", "ASIA"),
            entry("REUNION", "RE", "Réunion", "AFRICA"),
            entry("ROMANIA", "RO", "Romania", "EUROPE"),
            entry("RUSSIA", "RU", "Russia", "EUROPE"),
            entry("RWANDA", "RW", "Rwanda", "AFRICA"),
            entry("SAINT_BARTHELEMY", "BL", "St. Barthélemy", "NORTH_AMERICA"),
            entry("SAINT_HELENA", "SH", "St. Helena", "AFRICA"),
            entry("SAINT_KITTS_AND_NEVIS", "KN", "St. Kitts & Nevis", "NORTH_AMERICA"),
            entry("SAINT_LUCIA", "LC", "St. Lucia", "NORTH_AMERICA"),
            entry("SAINT_MARTIN", "MF", "St. Martin", "NORTH_AMERICA"),
            entry("SAINT_PIERRE_AND_MIQUELON", "PM", "St. Pierre & Miquelon", "NORTH_AMERICA"),
            entry("SAINT_VINCENT_AND_GRENADINES", "VC", "St. Vincent & Grenadines", "NORTH_AMERICA"),
            entry("SAMOA", "WS", "Samoa", "OCEANIA"),
            entry("SAN_MARINO", "SM", "San Marino", "EUROPE"),
            entry("SAO_TOME_AND_PRINCIPE", "ST", "São Tomé & Príncipe", "AFRICA"),
            entry("SAUDI_ARABIA", "SA", "Saudi Arabia", "ASIA"),
            entry("SENEGAL", "SN", "Senegal", "AFRICA"),
            entry("SERBIA", "RS", "Serbia", "EUROPE"),
            entry("SEYCHELLES", "SC", "Seychelles", "AFRICA"),
            entry("SIERRA_LEONE", "SL", "Sierra Leone", "AFRICA"),
            entry("SINGAPORE", "SG", "Singapore", "ASIA"),
            entry("SINT_MAARTEN", "SX", "Sint Maarten", "NORTH_AMERICA"),
            entry("SLOVAKIA", "SK", "Slovakia", "EUROPE"),
            entry("SLOVENIA", "SI", "Slovenia", "EUROPE"),
            entry("SOLOMON_ISLANDS", "SB", "Solomon Islands", "OCEANIA"),
            entry("SOMALIA", "SO", "Somalia", "AFRICA"),
            entry("SOUTH_AFRICA", "ZA", "South Africa", "AFRICA"),
            entry("SOUTH_GEORGIA_AND_SOUTH_SANDWICH_ISLANDS", "GS", "South Georgia & South Sandwich Islands", "ANTARCTICA"),
            entry("SOUTH_KOREA", "KR", "South Korea", "ASIA"),
            entry("SOUTH_SUDAN", "SS", "South Sudan", "AFRICA"),
            entry("SPAIN", "ES", "Spain", "EUROPE"),
            entry("SRI_LANKA", "LK", "Sri Lanka", "ASIA"),
            entry("SUDAN", "SD", "Sudan", "AFRICA"),
            entry("SURINAME", "SR", "Suriname", "SOUTH_AMERICA"),
            entry("SVALBARD_AND_JAN_MAYEN", "SJ", "Svalbard & Jan Mayen", "EUROPE"),
            entry("SWEDEN", "SE", "Sweden", "EUROPE"),
            entry("SWITZERLAND", "CH", "Switzerland", "EUROPE"),
            entry("SYRIA", "SY", "Syria", "ASIA"),
            entry("TAIWAN", "TW", "Taiwan", "ASIA"),
            entry("TAJIKISTAN", "TJ", "Tajikistan", "ASIA"),
            entry("TANZANIA", "TZ", "Tanzania", "AFRICA"),
            entry("THAILAND", "TH", "Thailand", "ASIA"),
            entry("TIMOR_LESTE", "TL", "Timor-Leste", "ASIA"),
            entry("TOGO", "TG", "Togo", "AFRICA"),
            entry("TOKELAU", "TK", "Tokelau", "OCEANIA"),
            entry("TONGA", "TO", "Tonga", "OCEANIA"),
            entry("TRINIDAD_AND_TOBAGO", "TT", "Trinidad & Tobago", "NORTH_AMERICA"),
            entry("TUNISIA", "TN", "Tunisia", "AFRICA"),
            entry("TURKIYE", "TR", "Türkiye", "EUROPE"),
            entry("TURKMENISTAN", "TM", "Turkmenistan", "ASIA"),
            entry("TURKS_AND_CAICOS_ISLANDS", "TC", "Turks & Caicos Islands", "NORTH_AMERICA"),
            entry("TUVALU", "TV", "Tuvalu", "OCEANIA"),
            entry("UGANDA", "UG", "Uganda", "AFRICA"),
            entry("UKRAINE", "UA", "Ukraine", "EUROPE"),
            entry("UNITED_ARAB_EMIRATES", "AE", "United Arab Emirates", "ASIA"),
            entry("UNITED_KINGDOM", "GB", "United Kingdom", "EUROPE"),
            entry("UNITED_STATES", "US", "United States", "NORTH_AMERICA"),
            entry("URUGUAY", "UY", "Uruguay", "SOUTH_AMERICA"),
            entry("US_OUTLYING_ISLANDS", "UM", "U.S. Outlying Islands", "OCEANIA"),
            entry("US_VIRGIN_ISLANDS", "VI", "U.S. Virgin Islands", "NORTH_AMERICA"),
            entry("UZBEKISTAN", "UZ", "Uzbekistan", "ASIA"),
            entry("VANUATU", "VU", "Vanuatu", "OCEANIA"),
            entry("VATICAN_CITY", "VA", "Vatican City", "EUROPE"),
            entry("VENEZUELA", "VE", "Venezuela", "SOUTH_AMERICA"),
            entry("VIETNAM", "VN", "Vietnam", "ASIA"),
            entry("WALLIS_AND_FUTUNA", "WF", "Wallis & Futuna", "OCEANIA"),
            entry("WESTERN_SAHARA", "EH", "Western Sahara", "AFRICA"),
            entry("YEMEN", "YE", "Yemen", "ASIA"),
            entry("ZAMBIA", "ZM", "Zambia", "AFRICA"),
            entry("ZIMBABWE", "ZW", "Zimbabwe", "AFRICA"),
            entry("UNKNOWN", "XX", "Unknown", "UNKNOWN")
    );

    private Countries() {
    }

    /**
     * Every country, in enum order.
     *
     * @return the countries
     */
    public static List<Country> all() {
        return List.copyOf(BY_CONSTANT.values());
    }

    /**
     * A country by its enum constant (the choice stored in a record).
     *
     * @param constant e.g. {@code PHILIPPINES}; any case
     * @return the country, or empty when there is none by that name
     */
    public static Optional<Country> of(String constant) {
        return constant == null ? Optional.empty()
                : Optional.ofNullable(BY_CONSTANT.get(constant.strip().toUpperCase(Locale.ROOT)));
    }

    /**
     * A country by its ISO 3166-1 alpha-2 code.
     *
     * @param code e.g. {@code PH}; any case
     * @return the country, or empty when no country has that code
     */
    public static Optional<Country> byCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String upper = code.strip().toUpperCase(Locale.ROOT);
        return BY_CONSTANT.values().stream().filter(c -> c.code().equals(upper)).findFirst();
    }

    private static Country entry(String constant, String code, String displayName, String continent) {
        return new Country(constant, code, displayName, continent);
    }

    private static Map<String, Country> table(Country... countries) {
        Map<String, Country> map = new LinkedHashMap<>();
        for (Country c : countries) {
            map.put(c.constant(), c);
        }
        return Collections.unmodifiableMap(map);
    }
}

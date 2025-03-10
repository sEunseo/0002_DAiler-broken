/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fissy.dialer.smartdial.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceManager;

import com.fissy.dialer.smartdial.map.CompositeSmartDialMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Smart Dial utility class to find prefixes of contacts. It contains both methods to find supported
 * prefix combinations for contact names, and also methods to find supported prefix combinations for
 * contacts' phone numbers. Each contact name is separated into several tokens, such as first name,
 * middle name, family name etc. Each phone number is also separated into country code, NANP area
 * code, and local number if such separation is possible.
 */
public class SmartDialPrefix {

    /**
     * The number of starting and ending tokens in a contact's name considered for initials. For
     * example, if both constants are set to 2, and a contact's name is "Albert Ben Charles Daniel Ed
     * Foster", the first two tokens "Albert" "Ben", and last two tokens "Ed" "Foster" can be replaced
     * by their initials in contact name matching. Users can look up this contact by combinations of
     * his initials such as "AF" "BF" "EF" "ABF" "BEF" "ABEF" etc, but can not use combinations such
     * as "CF" "DF" "ACF" "ADF" etc.
     */
    private static final int LAST_TOKENS_FOR_INITIALS = 2;

    private static final int FIRST_TOKENS_FOR_INITIALS = 2;

    /**
     * The country code of the user's sim card obtained by calling getSimCountryIso
     */
    private static final String PREF_USER_SIM_COUNTRY_CODE =
            "DialtactsActivity_user_sim_country_code";

    private static final String PREF_USER_SIM_COUNTRY_CODE_DEFAULT = null;

    private static String userSimCountryCode = PREF_USER_SIM_COUNTRY_CODE_DEFAULT;
    /**
     * Indicates whether user is in NANP regions.
     */
    private static boolean userInNanpRegion = false;
    /**
     * Set of country names that use NANP code.
     */
    private static Set<String> nanpCountries = null;
    /**
     * Set of supported country codes in front of the phone number.
     */
    private static Set<String> countryCodes = null;

    /**
     * Initializes the Nanp settings, and finds out whether user is in a NANP region.
     */
    public static void initializeNanpSettings(Context context) {
        final TelephonyManager manager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (manager != null) {
            userSimCountryCode = manager.getSimCountryIso();
        }

        final SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        if (userSimCountryCode != null) {
            prefs.edit().putString(PREF_USER_SIM_COUNTRY_CODE, userSimCountryCode).apply();
        } else {
            userSimCountryCode =
                    prefs.getString(PREF_USER_SIM_COUNTRY_CODE, PREF_USER_SIM_COUNTRY_CODE_DEFAULT);
        }
        userInNanpRegion = isCountryNanp(userSimCountryCode);
    }

    /**
     * Parses a contact's name into a list of separated tokens.
     *
     * @param contactName Contact's name stored in string.
     * @return A list of name tokens, for example separated first names, last name, etc.
     */
    public static ArrayList<String> parseToIndexTokens(Context context, String contactName) {
        final int length = contactName.length();
        final ArrayList<String> result = new ArrayList<>();
        char c;
        final StringBuilder currentIndexToken = new StringBuilder();
        for (int i = 0; i < length; i++) {
            c = CompositeSmartDialMap.normalizeCharacter(context, contactName.charAt(i));
            if (CompositeSmartDialMap.isValidDialpadCharacter(context, c)) {
                currentIndexToken.append(CompositeSmartDialMap.getDialpadIndex(context, c));
            } else {
                if (currentIndexToken.length() != 0) {
                    result.add(currentIndexToken.toString());
                }
                currentIndexToken.delete(0, currentIndexToken.length());
            }
        }

        if (currentIndexToken.length() != 0) {
            result.add(currentIndexToken.toString());
        }
        return result;
    }

    /**
     * Generates a list of strings that any prefix of any string in the list can be used to look up
     * the contact's name.
     *
     * @param index The contact's name in string.
     * @return A List of strings, whose prefix can be used to look up the contact.
     */
    public static ArrayList<String> generateNamePrefixes(Context context, String index) {
        final ArrayList<String> result = new ArrayList<>();

        final ArrayList<String> indexTokens = parseToIndexTokens(context, index);

        if (indexTokens.size() > 0) {
            final StringBuilder fullNameToken = new StringBuilder();
            for (int i = indexTokens.size() - 1; i >= 0; i--) {
                fullNameToken.insert(0, indexTokens.get(i));
                result.add(fullNameToken.toString());
            }

            ArrayList<String> fullNames = new ArrayList<>();
            fullNames.add(indexTokens.get(indexTokens.size() - 1));
            final int recursiveNameStart = result.size();
            int recursiveNameEnd = result.size();
            String initial;
            for (int i = indexTokens.size() - 2; i >= 0; i--) {
                if ((i >= indexTokens.size() - LAST_TOKENS_FOR_INITIALS)
                        || (i < FIRST_TOKENS_FOR_INITIALS)) {
                    initial = indexTokens.get(i).substring(0, 1);

                    for (int j = 0; j < fullNames.size(); ++j) {
                        result.add(initial + fullNames.get(j));
                    }
                    for (int j = recursiveNameStart; j < recursiveNameEnd; ++j) {
                        result.add(initial + result.get(j));
                    }
                    recursiveNameEnd = result.size();
                    final String currentFullName = fullNames.get(fullNames.size() - 1);
                    fullNames.add(indexTokens.get(i) + currentFullName);
                }
            }
        }

        return result;
    }

    /**
     * Computes a list of number strings based on tokens of a given phone number. Any prefix of any
     * string in the list can be used to look up the phone number. The list include the full phone
     * number, the national number if there is a country code in the phone number, and the local
     * number if there is an area code in the phone number following the NANP format. For example, if
     * a user has phone number +41 71 394 8392, the list will contain 41713948392 and 713948392. Any
     * prefix to either of the strings can be used to look up the phone number. If a user has a phone
     * number +1 555-302-3029 (NANP format), the list will contain 15553023029, 5553023029, and
     * 3023029.
     *
     * @param number String of user's phone number.
     * @return A list of strings where any prefix of any entry can be used to look up the number.
     */
    public static ArrayList<String> parseToNumberTokens(Context context, String number) {
        final ArrayList<String> result = new ArrayList<>();
        if (!TextUtils.isEmpty(number)) {
            result.add(SmartDialNameMatcher.normalizeNumber(context, number));

            final PhoneNumberTokens phoneNumberTokens = parsePhoneNumber(context, number);

            if (phoneNumberTokens.countryCodeOffset != 0) {
                result.add(
                        SmartDialNameMatcher.normalizeNumber(
                                context, number, phoneNumberTokens.countryCodeOffset));
            }

            if (phoneNumberTokens.nanpCodeOffset != 0) {
                result.add(
                        SmartDialNameMatcher.normalizeNumber(
                                context, number, phoneNumberTokens.nanpCodeOffset));
            }
        }
        return result;
    }

    /**
     * Parses a phone number to find out whether it has country code and NANP area code.
     *
     * @param number Raw phone number.
     * @return a PhoneNumberToken instance with country code, NANP code information.
     */
    public static PhoneNumberTokens parsePhoneNumber(Context context, String number) {
        String countryCode = "";
        int countryCodeOffset = 0;
        int nanpNumberOffset = 0;

        if (!TextUtils.isEmpty(number)) {
            String normalizedNumber = SmartDialNameMatcher.normalizeNumber(context, number);
            if (number.charAt(0) == '+') {
                for (int i = 1; i <= 1 + 3; i++) {
                    if (number.length() <= i) {
                        break;
                    }
                    countryCode = number.substring(1, i);
                    if (isValidCountryCode(countryCode)) {
                        countryCodeOffset = i;
                        break;
                    }
                }
            } else {
                if ((normalizedNumber.length() == 11)
                        && (normalizedNumber.charAt(0) == '1')
                        && (userInNanpRegion)) {
                    countryCode = "1";
                    countryCodeOffset = number.indexOf(normalizedNumber.charAt(1));
                    if (countryCodeOffset == -1) {
                        countryCodeOffset = 0;
                    }
                }
            }

            if (userInNanpRegion) {
                String areaCode = "";
                if (countryCode.equals("") && normalizedNumber.length() == 10) {
                    areaCode = normalizedNumber.substring(0, 3);
                } else if (countryCode.equals("1") && normalizedNumber.length() == 11) {
                    areaCode = normalizedNumber.substring(1, 4);
                }
                if (!areaCode.equals("")) {
                    final int areaCodeIndex = number.indexOf(areaCode);
                    if (areaCodeIndex != -1) {
                        nanpNumberOffset = number.indexOf(areaCode) + 3;
                    }
                }
            }
        }
        return new PhoneNumberTokens(countryCode, countryCodeOffset, nanpNumberOffset);
    }

    /**
     * Checkes whether a country code is valid.
     */
    private static boolean isValidCountryCode(String countryCode) {
        if (countryCodes == null) {
            countryCodes = initCountryCodes();
        }
        return countryCodes.contains(countryCode);
    }

    private static Set<String> initCountryCodes() {
        final HashSet<String> result = new HashSet<>();
        result.add("1");
        result.add("7");
        result.add("20");
        result.add("27");
        result.add("30");
        result.add("31");
        result.add("32");
        result.add("33");
        result.add("34");
        result.add("36");
        result.add("39");
        result.add("40");
        result.add("41");
        result.add("43");
        result.add("44");
        result.add("45");
        result.add("46");
        result.add("47");
        result.add("48");
        result.add("49");
        result.add("51");
        result.add("52");
        result.add("53");
        result.add("54");
        result.add("55");
        result.add("56");
        result.add("57");
        result.add("58");
        result.add("60");
        result.add("61");
        result.add("62");
        result.add("63");
        result.add("64");
        result.add("65");
        result.add("66");
        result.add("81");
        result.add("82");
        result.add("84");
        result.add("86");
        result.add("90");
        result.add("91");
        result.add("92");
        result.add("93");
        result.add("94");
        result.add("95");
        result.add("98");
        result.add("211");
        result.add("212");
        result.add("213");
        result.add("216");
        result.add("218");
        result.add("220");
        result.add("221");
        result.add("222");
        result.add("223");
        result.add("224");
        result.add("225");
        result.add("226");
        result.add("227");
        result.add("228");
        result.add("229");
        result.add("230");
        result.add("231");
        result.add("232");
        result.add("233");
        result.add("234");
        result.add("235");
        result.add("236");
        result.add("237");
        result.add("238");
        result.add("239");
        result.add("240");
        result.add("241");
        result.add("242");
        result.add("243");
        result.add("244");
        result.add("245");
        result.add("246");
        result.add("247");
        result.add("248");
        result.add("249");
        result.add("250");
        result.add("251");
        result.add("252");
        result.add("253");
        result.add("254");
        result.add("255");
        result.add("256");
        result.add("257");
        result.add("258");
        result.add("260");
        result.add("261");
        result.add("262");
        result.add("263");
        result.add("264");
        result.add("265");
        result.add("266");
        result.add("267");
        result.add("268");
        result.add("269");
        result.add("290");
        result.add("291");
        result.add("297");
        result.add("298");
        result.add("299");
        result.add("350");
        result.add("351");
        result.add("352");
        result.add("353");
        result.add("354");
        result.add("355");
        result.add("356");
        result.add("357");
        result.add("358");
        result.add("359");
        result.add("370");
        result.add("371");
        result.add("372");
        result.add("373");
        result.add("374");
        result.add("375");
        result.add("376");
        result.add("377");
        result.add("378");
        result.add("379");
        result.add("380");
        result.add("381");
        result.add("382");
        result.add("385");
        result.add("386");
        result.add("387");
        result.add("389");
        result.add("420");
        result.add("421");
        result.add("423");
        result.add("500");
        result.add("501");
        result.add("502");
        result.add("503");
        result.add("504");
        result.add("505");
        result.add("506");
        result.add("507");
        result.add("508");
        result.add("509");
        result.add("590");
        result.add("591");
        result.add("592");
        result.add("593");
        result.add("594");
        result.add("595");
        result.add("596");
        result.add("597");
        result.add("598");
        result.add("599");
        result.add("670");
        result.add("672");
        result.add("673");
        result.add("674");
        result.add("675");
        result.add("676");
        result.add("677");
        result.add("678");
        result.add("679");
        result.add("680");
        result.add("681");
        result.add("682");
        result.add("683");
        result.add("685");
        result.add("686");
        result.add("687");
        result.add("688");
        result.add("689");
        result.add("690");
        result.add("691");
        result.add("692");
        result.add("800");
        result.add("808");
        result.add("850");
        result.add("852");
        result.add("853");
        result.add("855");
        result.add("856");
        result.add("870");
        result.add("878");
        result.add("880");
        result.add("881");
        result.add("882");
        result.add("883");
        result.add("886");
        result.add("888");
        result.add("960");
        result.add("961");
        result.add("962");
        result.add("963");
        result.add("964");
        result.add("965");
        result.add("966");
        result.add("967");
        result.add("968");
        result.add("970");
        result.add("971");
        result.add("972");
        result.add("973");
        result.add("974");
        result.add("975");
        result.add("976");
        result.add("977");
        result.add("979");
        result.add("992");
        result.add("993");
        result.add("994");
        result.add("995");
        result.add("996");
        result.add("998");
        return result;
    }

    /**
     * Indicates whether the given country uses NANP numbers
     *
     * @param country ISO 3166 country code (case doesn't matter)
     * @return True if country uses NANP numbers (e.g. US, Canada), false otherwise
     * @see <a href="https://en.wikipedia.org/wiki/North_American_Numbering_Plan">
     * https://en.wikipedia.org/wiki/North_American_Numbering_Plan</a>
     */
    @VisibleForTesting
    public static boolean isCountryNanp(String country) {
        if (TextUtils.isEmpty(country)) {
            return false;
        }
        if (nanpCountries == null) {
            nanpCountries = initNanpCountries();
        }
        return nanpCountries.contains(country.toUpperCase());
    }

    private static Set<String> initNanpCountries() {
        final HashSet<String> result = new HashSet<>();
        result.add("US"); // United States
        result.add("CA"); // Canada
        result.add("AS"); // American Samoa
        result.add("AI"); // Anguilla
        result.add("AG"); // Antigua and Barbuda
        result.add("BS"); // Bahamas
        result.add("BB"); // Barbados
        result.add("BM"); // Bermuda
        result.add("VG"); // British Virgin Islands
        result.add("KY"); // Cayman Islands
        result.add("DM"); // Dominica
        result.add("DO"); // Dominican Republic
        result.add("GD"); // Grenada
        result.add("GU"); // Guam
        result.add("JM"); // Jamaica
        result.add("PR"); // Puerto Rico
        result.add("MS"); // Montserrat
        result.add("MP"); // Northern Mariana Islands
        result.add("KN"); // Saint Kitts and Nevis
        result.add("LC"); // Saint Lucia
        result.add("VC"); // Saint Vincent and the Grenadines
        result.add("TT"); // Trinidad and Tobago
        result.add("TC"); // Turks and Caicos Islands
        result.add("VI"); // U.S. Virgin Islands
        return result;
    }

    /**
     * Class to record phone number parsing information.
     */
    public static class PhoneNumberTokens {

        /**
         * Country code of the phone number.
         */
        final String countryCode;

        /**
         * Offset of national number after the country code.
         */
        final int countryCodeOffset;

        /**
         * Offset of local number after NANP area code.
         */
        final int nanpCodeOffset;

        public PhoneNumberTokens(String countryCode, int countryCodeOffset, int nanpCodeOffset) {
            this.countryCode = countryCode;
            this.countryCodeOffset = countryCodeOffset;
            this.nanpCodeOffset = nanpCodeOffset;
        }
    }
}

/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.fissy.dialer.smartdial.map.CompositeSmartDialMap;
import com.fissy.dialer.smartdial.util.SmartDialPrefix.PhoneNumberTokens;

import java.util.ArrayList;

/**
 * {@link #SmartDialNameMatcher} contains utility functions to remove accents from accented
 * characters and normalize a phone number. It also contains the matching logic that determines if a
 * contact's display name matches a numeric query. The boolean variable {@link #ALLOW_INITIAL_MATCH}
 * controls the behavior of the matching logic and determines whether we allow matches like 57 -
 * (J)ohn (S)mith.
 */
public class SmartDialNameMatcher {
    // Whether or not we allow matches like 57 - (J)ohn (S)mith
    private static final boolean ALLOW_INITIAL_MATCH = true;

    // The maximum length of the initial we will match - typically set to 1 to minimize false
    // positives
    private static final int INITIAL_LENGTH_LIMIT = 1;

    private final ArrayList<SmartDialMatchPosition> matchPositions = new ArrayList<>();
    private String query;

    // Controls whether to treat an empty query as a match (with anything).
    private boolean shouldMatchEmptyQuery = false;

    public SmartDialNameMatcher(String query) {
        this.query = query;
    }

    /**
     * Strips a phone number of unnecessary characters (spaces, dashes, etc.)
     *
     * @param number Phone number we want to normalize
     * @return Phone number consisting of digits from 0-9
     */
    public static String normalizeNumber(Context context, String number) {
        return normalizeNumber(context, number, /* offset = */ 0);
    }

    /**
     * Strips a phone number of unnecessary characters (spaces, dashes, etc.)
     *
     * @param number Phone number we want to normalize
     * @param offset Offset to start from
     * @return Phone number consisting of digits from 0-9
     */
    public static String normalizeNumber(Context context, String number, int offset) {
        final StringBuilder s = new StringBuilder();
        for (int i = offset; i < number.length(); i++) {
            char ch = number.charAt(i);
            if (CompositeSmartDialMap.isValidDialpadNumericChar(context, ch)) {
                s.append(ch);
            }
        }
        return s.toString();
    }

    /**
     * Constructs empty highlight mask. Bit 0 at a position means there is no match, Bit 1 means there
     * is a match and should be highlighted in the TextView.
     *
     * @param builder StringBuilder object
     * @param length  Length of the desired mask.
     */
    private void constructEmptyMask(StringBuilder builder, int length) {
        for (int i = 0; i < length; ++i) {
            builder.append("0");
        }
    }

    /**
     * Replaces the 0-bit at a position with 1-bit, indicating that there is a match.
     *
     * @param builder  StringBuilder object.
     * @param matchPos Match Positions to mask as 1.
     */
    private void replaceBitInMask(StringBuilder builder, SmartDialMatchPosition matchPos) {
        for (int i = matchPos.start; i < matchPos.end; ++i) {
            builder.replace(i, i + 1, "1");
        }
    }

    /**
     * Matches a phone number against a query. Let the test application overwrite the NANP setting.
     *
     * @param phoneNumber - Raw phone number
     * @param query       - Normalized query (only contains numbers from 0-9)
     * @return {@literal null} if the number and the query don't match, a valid SmartDialMatchPosition
     * with the matching positions otherwise
     */
    @Nullable
    public SmartDialMatchPosition matchesNumber(Context context, String phoneNumber, String query) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return shouldMatchEmptyQuery ? new SmartDialMatchPosition(0, 0) : null;
        }
        StringBuilder builder = new StringBuilder();
        constructEmptyMask(builder, phoneNumber.length());

        // Try matching the number as is
        SmartDialMatchPosition matchPos =
                matchesNumberWithOffset(context, phoneNumber, query, /* offset = */ 0);
        if (matchPos == null) {
            PhoneNumberTokens phoneNumberTokens = SmartDialPrefix.parsePhoneNumber(context, phoneNumber);

            if (phoneNumberTokens.countryCodeOffset != 0) {
                matchPos =
                        matchesNumberWithOffset(
                                context, phoneNumber, query, phoneNumberTokens.countryCodeOffset);
            }
            if (matchPos == null && phoneNumberTokens.nanpCodeOffset != 0) {
                matchPos =
                        matchesNumberWithOffset(context, phoneNumber, query, phoneNumberTokens.nanpCodeOffset);
            }
        }
        if (matchPos != null) {
            replaceBitInMask(builder, matchPos);
        }
        return matchPos;
    }

    /**
     * Matches a phone number against the saved query, taking care of formatting characters and also
     * taking into account country code prefixes and special NANP number treatment.
     *
     * @param phoneNumber - Raw phone number
     * @return {@literal null} if the number and the query don't match, a valid SmartDialMatchPosition
     * with the matching positions otherwise
     */
    public SmartDialMatchPosition matchesNumber(Context context, String phoneNumber) {
        return matchesNumber(context, phoneNumber, query);
    }

    /**
     * Matches a phone number against a query, taking care of formatting characters
     *
     * @param phoneNumber - Raw phone number
     * @param query       - Normalized query (only contains numbers from 0-9)
     * @param offset      - The position in the number to start the match against (used to ignore leading
     *                    prefixes/country codes)
     * @return {@literal null} if the number and the query don't match, a valid SmartDialMatchPosition
     * with the matching positions otherwise
     */
    private SmartDialMatchPosition matchesNumberWithOffset(
            Context context, String phoneNumber, String query, int offset) {
        if (TextUtils.isEmpty(phoneNumber) || TextUtils.isEmpty(query)) {
            return shouldMatchEmptyQuery ? new SmartDialMatchPosition(offset, offset) : null;
        }
        int queryAt = 0;
        int numberAt = offset;
        for (int i = offset; i < phoneNumber.length(); i++) {
            if (queryAt == query.length()) {
                break;
            }
            char ch = phoneNumber.charAt(i);
            if (CompositeSmartDialMap.isValidDialpadNumericChar(context, ch)) {
                if (ch != query.charAt(queryAt)) {
                    return null;
                }
                queryAt++;
            } else {
                if (queryAt == 0) {
                    // Found a separator before any part of the query was matched, so advance the
                    // offset to avoid prematurely highlighting separators before the rest of the
                    // query.
                    // E.g. don't highlight the first '-' if we're matching 1-510-111-1111 with
                    // '510'.
                    // However, if the current offset is 0, just include the beginning separators
                    // anyway, otherwise the highlighting ends up looking weird.
                    // E.g. if we're matching (510)-111-1111 with '510', we should include the
                    // first '('.
                    if (offset != 0) {
                        offset++;
                    }
                }
            }
            numberAt++;
        }
        return new SmartDialMatchPosition(offset, numberAt);
    }

    /**
     * This function iterates through each token in the display name, trying to match the query to the
     * numeric equivalent of the token.
     *
     * <p>A token is defined as a range in the display name delimited by characters that have no latin
     * alphabet equivalents (e.g. spaces - ' ', periods - ',', underscores - '_' or chinese characters
     * - '王'). Transliteration from non-latin characters to latin character will be done on a best
     * effort basis - e.g. 'Ü' - 'u'.
     *
     * <p>For example, the display name "Phillips Thomas Jr" contains three tokens: "phillips",
     * "thomas", and "jr".
     *
     * <p>A match must begin at the start of a token. For example, typing 846(Tho) would match
     * "Phillips Thomas", but 466(hom) would not.
     *
     * <p>Also, a match can extend across tokens. For example, typing 37337(FredS) would match (Fred
     * S)mith.
     *
     * @param displayName The normalized(no accented characters) display name we intend to match
     *                    against.
     * @param query       The string of digits that we want to match the display name to.
     * @param matchList   An array list of {@link SmartDialMatchPosition}s that we add matched positions
     *                    to.
     * @return Returns true if a combination of the tokens in displayName match the query string
     * contained in query. If the function returns true, matchList will contain an ArrayList of
     * match positions (multiple matches correspond to initial matches).
     */
    private boolean matchesCombination(
            Context context,
            String displayName,
            String query,
            ArrayList<SmartDialMatchPosition> matchList) {
        StringBuilder builder = new StringBuilder();
        constructEmptyMask(builder, displayName.length());
        final int nameLength = displayName.length();
        final int queryLength = query.length();

        if (nameLength < queryLength) {
            return false;
        }

        if (queryLength == 0) {
            return false;
        }

        // The current character index in displayName
        // E.g. 3 corresponds to 'd' in "Fred Smith"
        int nameStart = 0;

        // The current character in the query we are trying to match the displayName against
        int queryStart = 0;

        // The start position of the current token we are inspecting
        int tokenStart = 0;

        // The number of non-alphabetic characters we've encountered so far in the current match.
        // E.g. if we've currently matched 3733764849 to (Fred Smith W)illiam, then the
        // seperatorCount should be 2. This allows us to correctly calculate offsets for the match
        // positions
        int seperatorCount = 0;

        ArrayList<SmartDialMatchPosition> partial = new ArrayList<>();
        // Keep going until we reach the end of displayName
        while (nameStart < nameLength && queryStart < queryLength) {
            char ch = displayName.charAt(nameStart);
            // Strip diacritics from accented characters if any
            ch = CompositeSmartDialMap.normalizeCharacter(context, ch);
            if (CompositeSmartDialMap.isValidDialpadCharacter(context, ch)) {
                if (CompositeSmartDialMap.isValidDialpadAlphabeticChar(context, ch)) {
                    ch = CompositeSmartDialMap.getDialpadNumericCharacter(context, ch);
                }
                if (ch != query.charAt(queryStart)) {
                    // Failed to match the current character in the query.

                    // Case 1: Failed to match the first character in the query. Skip to the next
                    // token since there is no chance of this token matching the query.

                    // Case 2: Previous characters in the query matched, but the current character
                    // failed to match. This happened in the middle of a token. Skip to the next
                    // token since there is no chance of this token matching the query.

                    // Case 3: Previous characters in the query matched, but the current character
                    // failed to match. This happened right at the start of the current token. In
                    // this case, we should restart the query and try again with the current token.
                    // Otherwise, we would fail to match a query like "964"(yog) against a name
                    // Yo-Yoghurt because the query match would fail on the 3rd character, and
                    // then skip to the end of the "Yoghurt" token.

                    if (queryStart == 0
                            || CompositeSmartDialMap.isValidDialpadCharacter(
                            context,
                            CompositeSmartDialMap.normalizeCharacter(
                                    context, displayName.charAt(nameStart - 1)))) {
                        // skip to the next token, in the case of 1 or 2.
                        while (nameStart < nameLength
                                && CompositeSmartDialMap.isValidDialpadCharacter(
                                context,
                                CompositeSmartDialMap.normalizeCharacter(
                                        context, displayName.charAt(nameStart)))) {
                            nameStart++;
                        }
                        nameStart++;
                    }

                    // Restart the query and set the correct token position
                    queryStart = 0;
                    seperatorCount = 0;
                    tokenStart = nameStart;
                } else {
                    if (queryStart == queryLength - 1) {

                        // As much as possible, we prioritize a full token match over a sub token
                        // one so if we find a full token match, we can return right away
                        matchList.add(
                                new SmartDialMatchPosition(tokenStart, queryLength + tokenStart + seperatorCount));
                        for (SmartDialMatchPosition match : matchList) {
                            replaceBitInMask(builder, match);
                        }
                        return true;
                    } else if (ALLOW_INITIAL_MATCH && queryStart < INITIAL_LENGTH_LIMIT) {
                        // we matched the first character.
                        // branch off and see if we can find another match with the remaining
                        // characters in the query string and the remaining tokens
                        // find the next separator in the query string
                        int j;
                        for (j = nameStart; j < nameLength; j++) {
                            if (!CompositeSmartDialMap.isValidDialpadCharacter(
                                    context,
                                    CompositeSmartDialMap.normalizeCharacter(context, displayName.charAt(j)))) {
                                break;
                            }
                        }
                        // this means there is at least one character left after the separator
                        if (j < nameLength - 1) {
                            final String remainder = displayName.substring(j + 1);
                            final ArrayList<SmartDialMatchPosition> partialTemp = new ArrayList<>();
                            if (matchesCombination(
                                    context, remainder, query.substring(queryStart + 1), partialTemp)) {

                                // store the list of possible match positions
                                SmartDialMatchPosition.advanceMatchPositions(partialTemp, j + 1);
                                partialTemp.add(0, new SmartDialMatchPosition(nameStart, nameStart + 1));
                                // we found a partial token match, store the data in a
                                // temp buffer and return it if we end up not finding a full
                                // token match
                                partial = partialTemp;
                            }
                        }
                    }
                    nameStart++;
                    queryStart++;
                    // we matched the current character in the name against one in the query,
                    // continue and see if the rest of the characters match
                }
            } else {
                // found a separator, we skip this character and continue to the next one
                nameStart++;
                if (queryStart == 0) {
                    // This means we found a separator before the start of a token,
                    // so we should increment the token's start position to reflect its true
                    // start position
                    tokenStart = nameStart;
                } else {
                    // Otherwise this separator was found in the middle of a token being matched,
                    // so increase the separator count
                    seperatorCount++;
                }
            }
        }
        // if we have no complete match at this point, then we attempt to fall back to the partial
        // token match(if any). If we don't allow initial matching (ALLOW_INITIAL_MATCH = false)
        // then partial will always be empty.
        if (!partial.isEmpty()) {
            matchList.addAll(partial);
            for (SmartDialMatchPosition match : matchList) {
                replaceBitInMask(builder, match);
            }
            return true;
        }
        return false;
    }

    /**
     * This function iterates through each token in the display name, trying to match the query to the
     * numeric equivalent of the token.
     *
     * <p>A token is defined as a range in the display name delimited by characters that have no latin
     * alphabet equivalents (e.g. spaces - ' ', periods - ',', underscores - '_' or chinese characters
     * - '王'). Transliteration from non-latin characters to latin character will be done on a best
     * effort basis - e.g. 'Ü' - 'u'.
     *
     * <p>For example, the display name "Phillips Thomas Jr" contains three tokens: "phillips",
     * "thomas", and "jr".
     *
     * <p>A match must begin at the start of a token. For example, typing 846(Tho) would match
     * "Phillips Thomas", but 466(hom) would not.
     *
     * <p>Also, a match can extend across tokens. For example, typing 37337(FredS) would match (Fred
     * S)mith.
     *
     * @param displayName The normalized(no accented characters) display name we intend to match
     *                    against.
     * @return Returns true if a combination of the tokens in displayName match the query string
     * contained in query. If the function returns true, matchList will contain an ArrayList of
     * match positions (multiple matches correspond to initial matches).
     */
    public boolean matches(Context context, String displayName) {
        matchPositions.clear();
        return matchesCombination(context, displayName, query, matchPositions);
    }

    public ArrayList<SmartDialMatchPosition> getMatchPositions() {
        // Return a clone of mMatchPositions so that the caller can use it without
        // worrying about it changing
        return new ArrayList<>(matchPositions);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setShouldMatchEmptyQuery(boolean matches) {
        shouldMatchEmptyQuery = matches;
    }
}

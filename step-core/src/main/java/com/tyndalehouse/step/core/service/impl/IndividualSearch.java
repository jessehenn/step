package com.tyndalehouse.step.core.service.impl;

import static com.tyndalehouse.step.core.utils.StringUtils.isBlank;
import static com.tyndalehouse.step.core.utils.StringUtils.isNotBlank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.crosswire.jsword.index.lucene.LuceneIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tyndalehouse.step.core.exceptions.StepInternalException;
import com.tyndalehouse.step.core.utils.StringUtils;

/**
 * Represents an individual search
 * 
 * @author chrisburrell
 */
public class IndividualSearch {
    private static final char RELATED_WORDS = '~';
    private static final char SIMILAR_FORMS = '*';

    private static final Pattern IN_VERSIONS = Pattern
            .compile("in ?\\(([^)]+)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern MAIN_RANGE = Pattern.compile("(\\+\\[[^\\]]+\\])");
    private static final Pattern SUB_RANGE = Pattern.compile("\\{([^}]+)\\}");
    private static final Pattern ORIGINAL_FILTER = Pattern.compile(" where original is \\(([^)]+)\\)");

    private static final Logger LOGGER = LoggerFactory.getLogger(IndividualSearch.class);
    private static final String TEXT = "t=";
    private static final String SUBJECT = "s=";
    private static final String ORIGINAL = "o";

    private static final String TIMELINE_DESCRIPTION = "d=";
    private static final String TIMELINE_REFERENCE = "dr=";

    private SearchType type;
    private String query;
    private String[] versions;
    private boolean amendedQuery;
    private String subRange;
    private String mainRange;
    private String[] originalFilter;

    /**
     * Initialises the search from the query string
     * 
     * @param query the query that is being sent to the app to search for
     */
    public IndividualSearch(final String query) {
        if (query.startsWith(TEXT)) {
            this.type = SearchType.TEXT;
            matchVersions(query.substring(TEXT.length()));
        } else if (query.startsWith(SUBJECT)) {
            parseSubjectSearch(query.substring(SUBJECT.length()));
        } else if (query.startsWith(ORIGINAL)) {
            parseOriginalsearch(query.substring(ORIGINAL.length()));
        } else if (query.startsWith(TIMELINE_DESCRIPTION)) {
            this.type = SearchType.TIMELINE_DESCRIPTION;
            matchVersions(query.substring(TIMELINE_DESCRIPTION.length()));
        } else if (query.startsWith(TIMELINE_REFERENCE)) {
            this.type = SearchType.TIMELINE_REFERENCE;
            matchVersions(query.substring(TIMELINE_REFERENCE.length()));
        } else {
            // LOGGER.warn("Unknown search type for query [{}]", query);

            // default to JSword and hope for the best, but warn
            matchVersions(query);
            this.type = SearchType.TEXT;
        }
        if (isBlank(this.query)) {
            // return straight away
            throw new StepInternalException("Unable to search, as query provided was blank.");
        }
    }

    /**
     * Parses the query to be the correct original search
     * 
     * @param parseableQuery the query entered by the user, without the first character (o)
     */
    private void parseOriginalsearch(final String parseableQuery) {
        int length = 1;

        final char specifier = parseableQuery.charAt(length);
        switch (parseableQuery.charAt(0)) {
            case 'm':
                this.type = SearchType.ORIGINAL_MEANING;
                break;
            case 't':
                this.type = SearchType.ORIGINAL_TRANSLATED_AS;
                break;
            case 'g':
                if (specifier == RELATED_WORDS) {
                    this.type = SearchType.ORIGINAL_GREEK_RELATED;
                    length++;
                } else if (specifier == SIMILAR_FORMS) {
                    this.type = SearchType.ORIGINAL_GREEK_FORMS;
                    length++;
                } else {
                    this.type = SearchType.ORIGINAL_GREEK_EXACT;
                }
                break;
            case 'h':
                if (parseableQuery.charAt(length) == '~') {
                    this.type = SearchType.ORIGINAL_HEBREW_RELATED;
                    length++;
                } else if (specifier == SIMILAR_FORMS) {
                    this.type = SearchType.ORIGINAL_HEBREW_FORMS;
                    length++;
                } else {
                    this.type = SearchType.ORIGINAL_HEBREW_EXACT;
                }
                break;
            case 'f':
                break;
            default:
                throw new StepInternalException("Unsupported search for query o" + parseableQuery);
        }

        matchOriginalFilter(parseableQuery.substring(length + 1));
        matchVersions(this.query);

        // finally we can try and match our sub-range for the original word
        matchSubRange();

        matchMainRange();
    }

    /**
     * Matches the original filter, 'where original is' pattern
     * 
     * @param parseableQuery query
     */
    private void matchOriginalFilter(final String parseableQuery) {
        this.query = parseableQuery;
        final String originalFilter = matchFirstGroupAndRemove(ORIGINAL_FILTER);
        if (isNotBlank(originalFilter)) {
            this.originalFilter = originalFilter.split(",");
        }
    }

    /**
     * Matches a main range such as +[Gen-Rev]
     */
    private void matchMainRange() {
        this.mainRange = matchFirstGroupAndRemove(MAIN_RANGE);
    }

    /**
     * Matches a sub-range in the form of {range}
     */
    private void matchSubRange() {
        this.subRange = matchFirstGroupAndRemove(SUB_RANGE);
    }

    /**
     * Matches the first group and removes the entire match from the string
     * 
     * @param pattern the pattern to use for matching the query
     * @return the string that was matched
     */
    private String matchFirstGroupAndRemove(final Pattern pattern) {
        final Matcher matcher = pattern.matcher(this.query);

        if (matcher.find()) {
            this.query = this.query.replace(matcher.group(), "").trim();
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * matches a version in the query of type "xyz in (KJV)"
     * 
     * @param textQuery the query without the prefix
     */
    private void matchVersions(final String textQuery) {
        final Matcher capturedVersions = IN_VERSIONS.matcher(textQuery);

        if (!capturedVersions.find()) {
            throw new StepInternalException("Unable to match query string to find versions " + textQuery);
        }

        final String versionGroup = capturedVersions.group(1);
        this.versions = versionGroup.split("[, ]+");
        for (int i = 0; i < this.versions.length; i++) {
            this.versions[i] = this.versions[i].trim();
        }

        this.query = textQuery.substring(0, capturedVersions.start() - 1).trim();
    }

    /**
     * Constructs the syntax for the subject search
     * 
     * @param parsedSubject the parsed and well-formed search query, containing prefix, etc.
     */
    private void parseSubjectSearch(final String parsedSubject) {
        // fill in the query and versions
        matchVersions(parsedSubject);

        // amend the query
        final StringBuilder subjectQuery = new StringBuilder(this.query.length() + 32);
        final String[] keys = StringUtils.split(this.query);

        for (int i = 0; i < keys.length; i++) {
            subjectQuery.append(LuceneIndex.FIELD_HEADING);
            subjectQuery.append(':');
            subjectQuery.append(keys[i]);

            if (i + 1 < keys.length) {
                subjectQuery.append(" AND ");
            }
        }
        this.type = SearchType.SUBJECT;

        this.query = subjectQuery.toString();
    }

    /**
     * @return the type
     */
    public SearchType getType() {
        return this.type;
    }

    /**
     * @return the query
     */
    public String getQuery() {
        return this.query;
    }

    /**
     * @return the versions
     */
    public String[] getVersions() {
        return this.versions;
    }

    /**
     * @param query the query to set
     */
    public void setQuery(final String query) {
        // record the fact the query has been amended
        this.amendedQuery = true;
        this.query = query;
    }

    /**
     * @return the amendedQuery
     */
    public boolean isAmendedQuery() {
        return this.amendedQuery;
    }

    /**
     * @return the subRange
     */
    public String getSubRange() {
        return this.subRange;
    }

    /**
     * @return the mainRange
     */
    public String getMainRange() {
        return this.mainRange;
    }

    /**
     * @param versions overwrites the versions
     */
    public void setVersions(final String[] versions) {
        this.versions = versions;

    }

    /**
     * @return the originalFilter
     */
    public String[] getOriginalFilter() {
        return this.originalFilter;
    }
}
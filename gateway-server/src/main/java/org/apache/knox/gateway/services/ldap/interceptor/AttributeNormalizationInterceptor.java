/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.services.ldap.interceptor;

import com.google.common.annotations.VisibleForTesting;
import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.entry.Attribute;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Value;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.AttributeType;
import org.apache.directory.api.ldap.model.schema.MatchingRule;
import org.apache.directory.api.ldap.model.schema.normalizers.DeepTrimToLowerNormalizer;
import org.apache.directory.server.core.api.filtering.EntryFilter;
import org.apache.directory.server.core.api.filtering.EntryFilteringCursor;
import org.apache.directory.server.core.api.interceptor.BaseInterceptor;
import org.apache.directory.server.core.api.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.api.interceptor.context.SearchOperationContext;
import org.apache.knox.gateway.services.ldap.LdapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interceptor that lowercases the values of case-insensitive attributes in the entries it returns,
 * so that clients see one canonical form regardless of how the backing directory cased them.
 * <p>
 * The values of three kinds of attributes are lowercased: attributes whose equality matching rule in
 * the schema ignores case (e.g. {@code caseIgnoreMatch}, {@code caseIgnoreIA5Match}), DN-valued
 * attributes such as {@code member} and {@code memberOf}, name-and-optional-UID attributes such as
 * {@code uniqueMember}, and {@code objectClass}. The entry DN is lowercased as well.
 * <p>
 * It acts on the results of the interceptors listed after it, so list it before any interceptor
 * whose output it should normalize: interceptors that compare {@code objectClass} values or parse
 * group names from {@code memberOf} DNs must see the backend's original values. Search results are
 * normalized lazily, as the cursor is read.
 */
public class AttributeNormalizationInterceptor extends BaseInterceptor {

    // RFC 4517 Name and Optional UID: a DN optionally followed by #'<binary digits>'B. The UID suffix
    // is syntax, not text, so only the DN part may be lowercased.
    private static final Pattern NAME_AND_OPTIONAL_UID = Pattern.compile("^(.*?)(#'[01]*'B)?$", Pattern.DOTALL);

    private final EntryFilter normalizingFilter = new EntryFilter() {
        @Override
        public boolean accept(SearchOperationContext operation, Entry result) throws LdapException {
            normalize(result);
            return true;
        }

        @Override
        public String toString(String tabs) {
            return tabs + "AttributeNormalizationFilter";
        }
    };

    public AttributeNormalizationInterceptor(String name) {
        super(name);
    }

    @Override
    public Entry lookup(LookupOperationContext ctx) throws LdapException {
        return normalize(next(ctx));
    }

    @Override
    public EntryFilteringCursor search(SearchOperationContext ctx) throws LdapException {
        final EntryFilteringCursor cursor = next(ctx);
        cursor.addEntryFilter(normalizingFilter);
        return cursor;
    }

    @VisibleForTesting
    Entry normalize(Entry entry) throws LdapException {
        if (entry == null) {
            return null;
        }
        lowercaseDn(entry);
        for (Attribute attribute : new ArrayList<>(entry.getAttributes())) {
            final AttributeType attributeType = resolveAttributeType(attribute);
            if (!shouldNormalize(attributeType)) {
                continue;
            }
            final Attribute normalized = lowercase(attribute, attributeType);
            if (normalized != null) {
                entry.put(normalized);
            }
        }
        return entry;
    }

    private void lowercaseDn(Entry entry) throws LdapException {
        final Dn dn = entry.getDn();
        if (dn == null || dn.isEmpty()) {
            return;
        }
        final String name = dn.getName();
        final String lowered = name.toLowerCase(Locale.ROOT);
        if (!name.equals(lowered)) {
            entry.setDn(new Dn(schemaManager, lowered));
        }
    }

    private AttributeType resolveAttributeType(Attribute attribute) {
        final AttributeType attributeType = attribute.getAttributeType();
        return attributeType != null ? attributeType : schemaManager.getAttributeType(attribute.getId());
    }

    private boolean shouldNormalize(AttributeType attributeType) {
        if (attributeType == null) {
            return false;
        }
        if (SchemaConstants.OBJECT_CLASS_AT_OID.equals(attributeType.getOid())
                || LdapUtils.isDnValued(attributeType)
                || isNameAndOptionalUid(attributeType)) {
            return true;
        }
        final MatchingRule equality = attributeType.getEquality();
        return equality != null && equality.getNormalizer() instanceof DeepTrimToLowerNormalizer;
    }

    private static boolean isNameAndOptionalUid(AttributeType attributeType) {
        return SchemaConstants.NAME_AND_OPTIONAL_UID_SYNTAX.equals(attributeType.getSyntaxOid());
    }

    private static String lowercase(String value, boolean nameAndOptionalUid) {
        if (nameAndOptionalUid) {
            final Matcher matcher = NAME_AND_OPTIONAL_UID.matcher(value);
            if (matcher.matches()) {
                final String uid = matcher.group(2);
                return matcher.group(1).toLowerCase(Locale.ROOT) + (uid == null ? "" : uid);
            }
        }
        return value.toLowerCase(Locale.ROOT);
    }

    /**
     * Returns a lowercased copy of the attribute, or null when every value is already lowercase
     * or the attribute holds binary values.
     */
    private Attribute lowercase(Attribute attribute, AttributeType attributeType) throws LdapException {
        final boolean nameAndOptionalUid = isNameAndOptionalUid(attributeType);
        final List<String> values = new ArrayList<>(attribute.size());
        boolean changed = false;
        for (Value value : attribute) {
            if (!value.isHumanReadable()) {
                return null;
            }
            final String original = value.getString();
            final String lowered = lowercase(original, nameAndOptionalUid);
            changed |= !original.equals(lowered);
            values.add(lowered);
        }
        if (!changed) {
            return null;
        }
        return new DefaultAttribute(attribute.getUpId(), attributeType, values.toArray(new String[0]));
    }
}

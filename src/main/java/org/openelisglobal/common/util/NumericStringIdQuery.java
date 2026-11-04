package org.openelisglobal.common.util;

import jakarta.persistence.TypedQuery;
import java.util.Collection;
import org.hibernate.query.Query;
import org.hibernate.type.CustomType;
import org.hibernate.type.Type;
import org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType;

/**
 * Explicit bindings for legacy numeric columns represented as Java String IDs.
 */
public final class NumericStringIdQuery {
    private static final Type ID_TYPE = new CustomType(new LIMSStringNumberUserType());

    private NumericStringIdQuery() {
    }

    public static <T> TypedQuery<T> bind(TypedQuery<T> query, String parameter, String id) {
        query.unwrap(Query.class).setParameter(parameter, id, ID_TYPE);
        return query;
    }

    public static <T> TypedQuery<T> bindMany(TypedQuery<T> query, String parameter, Collection<String> ids) {
        query.unwrap(Query.class).setParameterList(parameter, ids, ID_TYPE);
        return query;
    }
}

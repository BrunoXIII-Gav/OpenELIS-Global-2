package org.openelisglobal.test.valueholder;

import jakarta.persistence.*;
import java.sql.Timestamp;
import org.hibernate.annotations.Type;
import org.openelisglobal.common.valueholder.BaseObject;

/**
 * Verified provenance for a legacy definition whose creation origin was not
 * recorded.
 */
@Entity
@Table(name = "test_creation_origin", schema = "clinlims")
public class TestCreationOrigin extends BaseObject<String> {
    @Id
    @Type(type = "org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType")
    @Column(name = "test_id", columnDefinition = "NUMERIC(10,0)")
    public String id;
    @Column(name = "user_created", nullable = false)
    public boolean userCreated;
    @Column(name = "actor_id", length = 40)
    public String actorId;
    @Column(name = "verified_at", nullable = false)
    public Timestamp verifiedAt;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }
}

package org.openelisglobal.authorization;

import static org.junit.Assert.assertNotNull;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.authorization.valueholder.RoleModulePermissionLabUnitScope;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;

public class AuthorizationOrmValidationTest {

    @Test
    public void roleModulePermissionMappingsLoadSuccessfully() {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(RoleModulePermission.class);
        configuration.addAnnotatedClass(RoleModulePermissionLabUnitScope.class);
        configuration.addAnnotatedClass(RoleFieldRestriction.class);
        configuration.addAnnotatedClass(RoleFieldTagRestriction.class);
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");

        SessionFactory sessionFactory = configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build());

        assertNotNull(sessionFactory.getMetamodel().entity(RoleModulePermission.class));
        assertNotNull(sessionFactory.getMetamodel().entity(RoleModulePermissionLabUnitScope.class));
        assertNotNull(sessionFactory.getMetamodel().entity(RoleFieldRestriction.class));
        assertNotNull(sessionFactory.getMetamodel().entity(RoleFieldTagRestriction.class));
        sessionFactory.close();
    }
}

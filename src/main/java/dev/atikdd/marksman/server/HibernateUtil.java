package dev.atikdd.marksman.server;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;

class HibernateUtil {

    private static SessionFactory sessionFactory;

    static SessionFactory getSessionFactory() {
        if (sessionFactory == null) {
            try {
                Configuration configuration = new Configuration();
                configuration.configure();
                configuration.addAnnotatedClass(PlayerEntity.class);

                StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                        .applySettings(configuration.getProperties())
                        .build();

                sessionFactory = configuration.buildSessionFactory(registry);

            } catch (Exception e) {
                System.err.println("Ошибка при создании SessionFactory: " + e.getMessage());
                e.printStackTrace();
                throw new ExceptionInInitializerError(e);
            }
        }
        return sessionFactory;
    }

    static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}

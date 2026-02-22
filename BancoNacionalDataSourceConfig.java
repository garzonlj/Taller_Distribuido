package com.universidad.transferencias.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del DataSource para PostgreSQL (Banco Nacional)
 * 
 * Esta configuración:
 * 1. Crea un DataSource específico para PostgreSQL
 * 2. Configura el EntityManagerFactory con el dialecto correcto
 * 3. Crea un TransactionManager para gestionar transacciones PostgreSQL
 * 4. Vincula los repositorios del paquete 'repository.nacional' a esta configuración
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.universidad.transferencias.repository.nacional",
    entityManagerFactoryRef = "nacionalEntityManagerFactory",
    transactionManagerRef = "nacionalTransactionManager"
)
public class BancoNacionalDataSourceConfig {
    
    /**
     * DataSource primario para PostgreSQL
     * @Primary indica que este es el DataSource principal de la aplicación
     */
    @Primary
    @Bean(name = "nacionalDataSource")
    @ConfigurationProperties(prefix = "spring.datasource-nacional")
    public DataSource nacionalDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    /**
     * EntityManagerFactory para PostgreSQL
     * Configura JPA para trabajar con la base de datos PostgreSQL
     */
    @Primary
    @Bean(name = "nacionalEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean nacionalEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("nacionalDataSource") DataSource dataSource) {
        
        // Configuración de propiedades de Hibernate para PostgreSQL
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", true);
        properties.put("hibernate.format_sql", true);
        
        return builder
                .dataSource(dataSource)
                .packages("com.universidad.transferencias.model")
                .persistenceUnit("nacional")
                .properties(properties)
                .build();
    }
    
    /**
     * TransactionManager para PostgreSQL
     * Gestiona las transacciones locales de esta base de datos
     */
    @Primary
    @Bean(name = "nacionalTransactionManager")
    public PlatformTransactionManager nacionalTransactionManager(
            @Qualifier("nacionalEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

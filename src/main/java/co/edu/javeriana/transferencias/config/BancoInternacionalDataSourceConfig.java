package main.java.co.edu.javeriana.transferencias.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del DataSource para MySQL (Banco Internacional)
 * 
 * IMPORTANTE: Esta configuración NO usa @Primary porque solo puede haber
 * un DataSource primario en la aplicación (PostgreSQL en este caso)
 * 
 * Esta configuración:
 * 1. Crea un DataSource específico para MySQL
 * 2. Configura el EntityManagerFactory con el dialecto MySQL
 * 3. Crea un TransactionManager para gestionar transacciones MySQL
 * 4. Vincula los repositorios del paquete 'repository.internacional' a esta configuración
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
    basePackages = "com.universidad.transferencias.repository.internacional",
    entityManagerFactoryRef = "internacionalEntityManagerFactory",
    transactionManagerRef = "internacionalTransactionManager"
)
public class BancoInternacionalDataSourceConfig {
    
    /**
     * DataSource secundario para MySQL
     * SIN @Primary - solo puede haber uno primario
     */
    @Bean(name = "internacionalDataSource")
    @ConfigurationProperties(prefix = "spring.datasource-internacional")
    public DataSource internacionalDataSource() {
        return DataSourceBuilder.create().build();
    }
    
    /**
     * EntityManagerFactory para MySQL
     * Configura JPA para trabajar con la base de datos MySQL
     * 
     * NOTA: El dialecto es diferente al de PostgreSQL
     */
    @Bean(name = "internacionalEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean internacionalEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("internacionalDataSource") DataSource dataSource) {
        
        // Configuración de propiedades de Hibernate para MySQL
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.show_sql", true);
        properties.put("hibernate.format_sql", true);
        
        // MySQL tiene algunas particularidades con InnoDB
        properties.put("hibernate.jdbc.batch_size", 20);
        properties.put("hibernate.order_inserts", true);
        properties.put("hibernate.order_updates", true);
        
        return builder
                .dataSource(dataSource)
                .packages("com.universidad.transferencias.model")
                .persistenceUnit("internacional")
                .properties(properties)
                .build();
    }
    
    /**
     * TransactionManager para MySQL
     * Gestiona las transacciones locales de esta base de datos
     * 
     * IMPORTANTE: Este TransactionManager es independiente del de PostgreSQL
     * No pueden coordinarse automáticamente - por eso necesitamos el patrón SAGA
     */
    @Bean(name = "internacionalTransactionManager")
    public PlatformTransactionManager internacionalTransactionManager(
            @Qualifier("internacionalEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}

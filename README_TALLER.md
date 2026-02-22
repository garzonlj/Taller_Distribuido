# Taller: Transacciones Distribuidas PostgreSQL + MySQL

## 📋 Diferencias Clave entre PostgreSQL y MySQL

### 1. Auto-incremento
**PostgreSQL:**
```sql
id BIGSERIAL PRIMARY KEY
-- o
id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY
```

**MySQL:**
```sql
id BIGINT AUTO_INCREMENT PRIMARY KEY
```

### 2. Tipos de Datos
| Concepto | PostgreSQL | MySQL |
|----------|------------|-------|
| Texto variable | VARCHAR | VARCHAR |
| Booleano | BOOLEAN | BOOLEAN o TINYINT(1) |
| Timestamp | TIMESTAMP | TIMESTAMP o DATETIME |
| Auto-incremento | SERIAL, BIGSERIAL | AUTO_INCREMENT |

### 3. Funciones de Fecha/Hora
**PostgreSQL:**
```sql
fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

**MySQL:**
```sql
fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
```

### 4. Configuración de Hibernate

**PostgreSQL (application.yml):**
```yaml
spring:
  datasource-nacional:
    jdbc-url: jdbc:postgresql://localhost:5432/banco_nacional
    driver-class-name: org.postgresql.Driver

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

**MySQL (application.yml):**
```yaml
spring:
  datasource-internacional:
    jdbc-url: jdbc:mysql://localhost:3306/banco_internacional?useSSL=false&serverTimezone=UTC
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

### 5. Dependencias Maven

Asegúrese de incluir ambos drivers en `pom.xml`:

```xml
<!-- PostgreSQL Driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- MySQL Driver -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

## 🚀 Inicio Rápido

### Prerequisitos
- Java 17+
- Maven 3.8+
- Docker Desktop
- IDE (IntelliJ IDEA recomendado)


## 🎯 Desafío Principal: Transacciones Distribuidas sin XA

### ¿Por qué no XA/2PC?

**Problema:** PostgreSQL y MySQL tienen implementaciones de XA incompatibles e incompletas:
- PostgreSQL: Soporte limitado de XA, requiere configuración compleja
- MySQL: XA funciona pero con limitaciones en InnoDB
- Complejidad: Requiere un transaction manager externo (Atomikos, Bitronix)
- Performance: XA tiene alto overhead de latencia

### Solución: Patrón SAGA

El patrón Saga divide una transacción distribuida en:
1. **Transacciones locales** en cada base de datos
2. **Transacciones compensatorias** que revierten cambios en caso de fallo

```
┌─────────────────────────────────────────────┐
│           SAGA TRANSACTION                  │
├─────────────────────────────────────────────┤
│ 1. BEGIN                                    │
│ 2. Debitar PostgreSQL (Banco Nacional)      │
│    └─ SUCCESS → Continuar                   │
│    └─ FAIL → Marcar FALLIDA                 │
│                                             │
│ 3. Acreditar MySQL (Banco Internacional)    │
│    └─ SUCCESS → Marcar COMPLETADA           │
│    └─ FAIL → COMPENSAR (revertir débito)    │
│                └─ Marcar REVERTIDA          │
└─────────────────────────────────────────────┘
```

## 📊 Estructura del Proyecto

```
transferencias-distribuidas/
├── docker-compose.yml          # Orquestación de contenedores
├── pom.xml                     # Dependencias Maven
├── src/
│   └── main/
│       ├── java/
│       │   └── co/edu/javeriana/transferencias/
│       │       ├── config/
│       │       │   ├── BancoNacionalDataSourceConfig.java    # PostgreSQL
│       │       │   └── BancoInternacionalDataSourceConfig.java # MySQL
│       │       ├── model/
│       │       │   ├── Cuenta.java
│       │       │   ├── Movimiento.java
│       │       │   └── EstadoTransferencia.java
│       │       ├── repository/
│       │       │   ├── nacional/                # Repositorios PostgreSQL
│       │       │   │   ├── CuentaNacionalRepository.java
│       │       │   │   └── MovimientoNacionalRepository.java
│       │       │   └── internacional/           # Repositorios MySQL
│       │       │       ├── CuentaInternacionalRepository.java
│       │       │       └── MovimientoInternacionalRepository.java
│       │       ├── service/
│       │       │   ├── BancoNacionalService.java
│       │       │   ├── BancoInternacionalService.java
│       │       │   └── TransferenciaService.java   # ⭐ SAGA Orchestrator
│       │       ├── controller/
│       │       │   └── TransferenciaController.java
│       │       └── dto/
│       │           ├── TransferenciaRequest.java
│       │           └── TransferenciaResponse.java
│       └── resources/
│           ├── application.yml
│           ├── schema-nacional.sql           # PostgreSQL
│           ├── data-nacional.sql
│           ├── schema-internacional.sql      # MySQL
│           ├── data-internacional.sql
│           └── static/
│               ├── index.html
│               ├── styles.css
│               └── app.js
```

## 🔍 Puntos Clave de Implementación

### 1. Configuración de DataSources

Cada base de datos necesita:
- ✅ DataSource bean único
- ✅ EntityManagerFactory específico
- ✅ PlatformTransactionManager específico
- ✅ @EnableJpaRepositories con su propio basePackages

### 2. Locks Pesimistas

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT c FROM Cuenta c WHERE c.numeroCuenta = :numeroCuenta")
Optional<Cuenta> findByNumeroCuentaWithLock(String numeroCuenta);
```

**¿Por qué?** Previene race conditions cuando dos transferencias intentan debitar la misma cuenta simultáneamente.

### 3. Transaction Managers Específicos

```java
@Transactional(transactionManager = "nacionalTransactionManager")
public void debitar(String cuenta, BigDecimal monto, String ref) {
    // Operación en PostgreSQL
}

@Transactional(transactionManager = "internacionalTransactionManager")
public void acreditar(String cuenta, BigDecimal monto, String ref) {
    // Operación en MySQL
}
```

## 🧪 Escenarios de Prueba

### Caso 1: Transferencia Exitosa ✅
```
Origen: BN-001 (PostgreSQL)
Destino: BI-001 (MySQL)
Monto: $100

Resultado esperado:
- BN-001 saldo: $5000 → $4900
- BI-001 saldo: $8000 → $8100
- Estado: COMPLETADA
```

### Caso 2: Saldo Insuficiente ❌
```
Origen: BN-003 (saldo $2,500)
Destino: BI-001
Monto: $5,000

Resultado esperado:
- Falla en paso 1 (débito)
- BN-003 saldo sin cambios
- BI-001 saldo sin cambios
- Estado: FALLIDA
```

### Caso 3: Fallo en Acreditación 🔄
```
Origen: BN-002
Destino: BI-002
Monto: $500
(Simular fallo en MySQL)

Resultado esperado:
- Débito exitoso en PostgreSQL
- Falla acreditación en MySQL
- COMPENSACIÓN: Débito revertido
- BN-002 saldo restaurado
- Estado: REVERTIDA
```

## 🎓 Conceptos de Arquitectura

### Consistencia Eventual

Este taller implementa **consistencia eventual**:
- ❌ No hay garantía de consistencia INMEDIATA
- ✅ El sistema eventualmente llega a un estado consistente
- 🔄 Las compensaciones garantizan que no quede dinero "perdido"

### Trade-offs

| Aspecto | XA/2PC | SAGA |
|---------|--------|------|
| Consistencia | Fuerte | Eventual |
| Disponibilidad | Baja | Alta |
| Performance | Lenta | Rápida |
| Complejidad | Alta (infraestructura) | Media (lógica) |
| Escalabilidad | Limitada | Excelente |

## 🐛 Troubleshooting

### Error: "Communications link failure"
**Causa:** La app inicia antes que las bases de datos  
**Solución:** Verificar healthchecks en docker-compose.yml

### Error: "Access denied for user"
**Causa:** Credenciales incorrectas  
**Solución:** Verificar variables de entorno en docker-compose.yml y application.yml

### Error: "Table 'cuenta' doesn't exist"
**Causa:** Scripts SQL no se ejecutaron  
**Solución:** 
```bash
docker-compose down -v  # Eliminar volúmenes
docker-compose up -d    # Recrear todo
```

### Error: "Dialect not found"
**Causa:** Falta configurar el dialecto correcto  
**Solución:** Verificar que EntityManagerFactory tenga:
- PostgreSQL: `org.hibernate.dialect.PostgreSQLDialect`
- MySQL: `org.hibernate.dialect.MySQLDialect`

## 📚 Recursos Útiles

### Documentación Oficial
- [Spring Data JPA - Multiple Databases](https://spring.io/guides/gs/accessing-data-jpa/)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/15/)
- [MySQL 8.0 Reference Manual](https://dev.mysql.com/doc/refman/8.0/en/)
- [Saga Pattern - Microservices.io](https://microservices.io/patterns/data/saga.html)

### Herramientas Recomendadas
- **Datagrip**: Cliente universal de bases de datos
- **Postman**: Para probar la API REST
- **Docker Desktop**: Gestión de contenedores
- **IntelliJ IDEA**: IDE con excelente soporte Spring

## 🏆 Criterios de Éxito

Para considerar el taller completado exitosamente:

- [ ] Ambos DataSources configurados y funcionando
- [ ] Transferencias exitosas entre PostgreSQL y MySQL
- [ ] Compensaciones funcionan correctamente ante fallos
- [ ] Locks previenen condiciones de carrera
- [ ] Frontend permite realizar transferencias
- [ ] Sistema completo se despliega con `docker-compose up`
- [ ] README documenta decisiones de diseño
- [ ] Capturas de pantalla de pruebas

## 💡 Consejos

1. **Empieza simple:** Primero haz que funcione, luego optimiza
2. **Logs son tu amigo:** Usa @Slf4j generosamente
3. **Prueba compensaciones:** Simula fallos para verificar que las reversiones funcionan
4. **Verifica en las BDs:** No confíes solo en logs, consulta las tablas directamente
5. **Commits frecuentes:** Haz commit después de cada parte funcional

---

**Última actualización:** Febrero 2026  
**Contacto:** jrafael.ocampo@javeriana.edu.co  
**Curso:** Arquitectura de Software - Pontificia Universidad Javeriana

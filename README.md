# About this project

This is an exploratory project which implements multitenancy in:
+ JPA/JTA based on Hibernate Multitenancy
+ JAX-RS based on security (identified user in Basic Auth)

It contains a test where XA transaction (having JAX-RS + JPA + JMS + JTA) attempt to rollback distributed transaction on constraint violation.

Another test attempts to break JAX-RS security on an HTTP request sent for a user with denied security role.

## How to configure datasources and Hibernate multi-tenancy

### Start PostgreSQL database

(Make sure CURL and Java is installed on your system and available in PATH)
```console
mkdir 9.6
curl --output 9.6/postgres.zip -x za-sophos.sb-sk.local:3128 -X GET https://get.enterprisedb.com/postgresql/postgresql-9.6.11-1-windows-x64-binaries.zip
jar -xf 9.6/postgres.zip
```

**Notice:** If the database could not be downloaded by the command, select one [from](https://www.enterprisedb.com/download-postgresql-binaries) .

Start the PostgreSQL database standalone (not a service) on Windows console.
```console
set PGDATA=%cd%\pgsql\data
cd %cd%\pgsql\bin
pg_ctl.exe status
pg_ctl.exe start
```

### Create Database

Go to `pgAdmin4` and create database name `mt`, tables for `schema1` and `schema2`.

```sql
CREATE DATABASE mt;
```

Go to the database `mt` and create schemas with tables:

```sql
CREATE SCHEMA schema1;
CREATE SCHEMA schema2;
CREATE TABLE schema1.mytable
(
    id bigint NOT NULL,
    name character varying(255),
    CONSTRAINT "A_pkey" PRIMARY KEY (id)
);
CREATE TABLE schema2.mytable
(
    id bigint NOT NULL,
    name character varying(255),
    CONSTRAINT "A_pkey" PRIMARY KEY (id)
);
```

### Run WildFly 14.0.1.Final

```console
$WILDFLY_HOME/bin/standalone -b=0.0.0.0 -Djboss.socket.binding.port-offset=0 --server-config=standalone-full.xml
```
**Notice:** The ports are not shifted, otherwise the management port would not be `9990` - see next.

### Run the build

```console
mvn package -DskipTests
```

### Run CLI Tool

Suppose we are `cd $WILDFLY_HOME/bin` .

```console
jboss-cli -c controller=127.0.0.1:9990 --connect
```
**Notice:** You may want to add `--user=*** --password=***` if you added a management user with credentials before in the WildFly console.
 
Keep the command prompt open and add WildFly module in the next steps...

#### Create WildFly Module of Hibernate Multitenancy
Replace the `/path/to` with basedir in the project.

```console
module add --name=org.multitenancy --resources=/path/to/application/schema-backing-object/target/schema-backing-object-1.0-SNAPSHOT.jar,/path/to/application/hibernate-multitenancy/target/hibernate-multitenancy-1.0-SNAPSHOT.jar --dependencies=org.hibernate,javax.enterprise.api
```

Finally, the module would become (I added more dependencies for sake of CDI access - but JBoss module is not working with CDI bound to application context - therefore used ThreadLocal):
```xml
<?xml version='1.0' encoding='UTF-8'?>

<module xmlns="urn:jboss:module:1.1" name="org.multitenancy">

    <resources>
        <resource-root path="schema-backing-object-1.0-SNAPSHOT.jar"/>
		<resource-root path="hibernate-multitenancy-1.0-SNAPSHOT.jar"/>
    </resources>

    <dependencies>
        <module name="org.hibernate"/>
        <module name="javax.enterprise.api"/>

        <module name="javax.api"/>
        <module name="javax.annotation.api"/>
        <module name="javax.ejb.api"/>
        <module name="javax.resource.api"/>
        <module name="javax.inject.api"/>
        <module name="javax.interceptor.api"/>
        <module name="javax.validation.api"/>
        <module name="org.hibernate.validator"/>
        <module name="javax.xml.stream.api"/>
    </dependencies>
</module>
```

One may say that `ThreadLocal` is not secure because it holds previously set data and the particular Thread holds the same data in a new request context.
This is true and therefore the `webapp` has CDI event handler `SchemaHolderLifecycle`. This handler invalidates the schema from a non-public to `PUBLIC`.

This might be still very dangerous in large and messy applications which are very hard to understand and control by the developer, especially those applications using EJB and MDB.
Some additional efforts have to be spent on a proper clean up.

### Create PostgreSQL module in WildFly

```console
curl --output ~/postgresql-42.1.1.jar -x za-sophos.sb-sk.local:3128 -X GET https://jdbc.postgresql.org/download/postgresql-42.1.1.jar
```


```console
jboss-cli -c controller=127.0.0.1:9990 --command="module add --name=org.postgres --resources=~/postgresql-42.1.1.jar --dependencies=javax.api,javax.transaction.api"
```
**Notice:** You may want to add `--user=*** --password=***` if you added a management user with credentials before in the WildFly console.

Delete the driver `postgresql-42.1.1.jar` from your user home dir.


### Configuring datasource

Make sure you have created PostgreSQL database `mt` with username `postgres` and password `admin`.

Let's create XA datasource:

```console
jboss-cli -c controller=127.0.0.1:9990 --connect
[standalone@127.0.0.1:9990 /] /subsystem=datasources/jdbc-driver=postgres:add(driver-name="postgres",driver-module-name="org.postgres",driver-xa-datasource-class-name=org.postgresql.xa.PGXADataSource)
[standalone@127.0.0.1:9990 /] reload
[standalone@127.0.0.1:9990 /] batch
[standalone@127.0.0.1:9990 / #] /subsystem=datasources/xa-data-source="mt":add(jndi-name="java:jboss/datasources/mt",user-name=postgres,password=admin,xa-datasource-class=org.postgresql.xa.PGXADataSource,driver-name="postgres",check-valid-connection-sql="select 1",min-pool-size=1,max-pool-size=20,valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker,exception-sorter-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter,validate-on-match=false,background-validation=true,background-validation-millis=25000,idle-timeout-minutes=1)
[standalone@127.0.0.1:9990 / #] /subsystem=datasources/xa-data-source="mt"/xa-datasource-properties=ServerName:add(value="127.0.0.1")
[standalone@127.0.0.1:9990 / #] /subsystem=datasources/xa-data-source="mt"/xa-datasource-properties=PortNumber:add(value="5432")
[standalone@127.0.0.1:9990 / #] /subsystem=datasources/xa-data-source="mt"/xa-datasource-properties=DatabaseName:add(value="postgres")
[standalone@127.0.0.1:9990 / #] run-batch
The batch executed successfully
[standalone@127.0.0.1:9990 /] xa-data-source enable --name=mt
operation-requires-reload: true
process-state:             reload-required
[standalone@127.0.0.1:9990 /] reload
[standalone@127.0.0.1:9990 /] exit
```

The XA datasource has no way to specify connection properties. So this would not work:

```console
/subsystem=datasources/xa-data-source="mt"/xa-datasource-properties=hibernate.multiTenancy:add(value="SCHEMA")
/subsystem=datasources/xa-data-source="mt"/xa-datasource-properties=hibernate.tenant_identifier_resolver:add(value="com.github.tibor17.holder.SchemaResolver")
/subsystem=datasources/xa-data-source="mt"/xa-datasource-properties=hibernate.multi_tenant_connection_provider:add(value="com.github.tibor17.core.MultiTenantProvider")
```

and another alternative is to specify these hardware properties in `persistence.xml` but it is still a dirty design:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<persistence xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			 xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/persistence http://xmlns.jcp.org/xml/ns/persistence/persistence_2_2.xsd"
			 version="2.2" xmlns="http://xmlns.jcp.org/xml/ns/persistence">
	<persistence-unit name="mt" transaction-type="JTA">
		<jta-data-source>java:jboss/datasources/mt</jta-data-source>
		<class>com.github.tibor17.web.MyEntity</class>
		<properties>
			<property name="hibernate.multiTenancy" value="SCHEMA"/>
			<property name="hibernate.tenant_identifier_resolver" value="com.github.tibor17.holder.SchemaResolver"/>
			<property name="hibernate.multi_tenant_connection_provider" value="com.github.tibor17.core.MultiTenantProvider"/>
		</properties>
	</persistence-unit>
</persistence>
```

A better approach is to use non-XA datasource where connection
properties can be specified in the datasource but you have to make sure
that you really do not need to have XA transactions (that e.g. JMS
and JPA appears in one transaction).

Here is the CLI configuring the non-XA datasource (yet without connection validation)

```console
jboss-cli -c controller=127.0.0.1:9990 --command="/subsystem=datasources/data-source=mt/:add(connection-url=jdbc:postgresql://127.0.0.1:5432/mt,driver-name=postgres,jndi-name=java:jboss/datasources/mt,initial-pool-size=1,max-pool-size=20,min-pool-size=1,password=admin,user-name=postgres)"
jboss-cli -c controller=127.0.0.1:9990 --command="data-source enable --name=mt"
```


and connection properties controlling HB multi-tenancy:

```console
$ jboss-cli -c controller=127.0.0.1:9990 --connect
[standalone@127.0.0.1:9990 /] batch
[standalone@127.0.0.1:9990 /] cd /subsystem=datasources/data-source="mt"
[standalone@127.0.0.1:9990 data-source=mt] ./connection-properties=hibernate.multiTenancy:add(value="SCHEMA")
[standalone@127.0.0.1:9990 data-source=mt] ./connection-properties=hibernate.tenant_identifier_resolver:add(value="com.github.tibor17.holder.SchemaResolver")
[standalone@127.0.0.1:9990 data-source=mt] ./connection-properties=hibernate.multi_tenant_connection_provider:add(value="com.github.tibor17.core.MultiTenantProvider")
[standalone@127.0.0.1:9990 / #] run-batch
The batch executed successfully
[standalone@127.0.0.1:9990 /] reload
[standalone@127.0.0.1:9990 /] exit
```


In this we are interested in a test with JPA and JMS in one XA transaction.
Use the first config approach with XA datasource.

## Configuring in-vm JMS

```console
jboss-cli -c controller=127.0.0.1:9990 --connect --command='/subsystem=messaging-activemq/server=default/jms-queue=TestQ:add(entries=["java:/jms/queue/test java:/jboss/exported/jms/queue/TestQ"])'
```


## How to configure application Roles. Add an Application Users and Basic Authentication.

Start the `jboss-cli.sh` or `jboss-cli.bat` command and connect to the server.

```console
[standalone@127.0.0.1:9990 /] /subsystem=undertow/server=default-server/host=default-host/setting=http-invoker:write-attribute(name=security-realm, value="CarsApplicationRealm")
{
    "outcome" => "success",
    "response-headers" => {
        "operation-requires-reload" => true,
        "process-state" => "reload-required"
    }
}
```

Let's create the first application user `schema1` used by HTTP Basic Authentication:

```console
d:\wildfly-14.0.1.Final\bin>add-user.bat

What type of user do you wish to add?
 a) Management User (mgmt-users.properties)
 b) Application User (application-users.properties)
(a): b

Enter the details of the new user to add.
Using realm 'ApplicationRealm' as discovered from the existing property files.
Username : schema1
Password recommendations are listed below. To modify these restrictions edit the add-user.properties configuration file.
 - The password should be different from the username
 - The password should not be one of the following restricted values {root, admin, administrator}
 - The password should contain at least 8 characters, 1 alphabetic character(s), 1 digit(s), 1 non-alphanumeric symbol(s)
Password :
WFLYDM0098: The password should be different from the username
Are you sure you want to use the password entered yes/no? y
Re-enter Password :
What groups do you want this user to belong to? (Please enter a comma separated list, or leave blank for none)[  ]: personal-cars
About to add user 'schema1' for realm 'ApplicationRealm'
Is this correct yes/no? y
Added user 'schema1' to file 'D:\wildfly-14.0.1.Final\standalone\configuration\application-users.properties'
Added user 'schema1' to file 'D:\wildfly-14.0.1.Final\domain\configuration\application-users.properties'
Added user 'schema1' with groups personal-cars to file 'D:\wildfly-14.0.1.Final\standalone\configuration\application-roles.properties'
Added user 'schema1' with groups personal-cars to file 'D:\wildfly-14.0.1.Final\domain\configuration\application-roles.properties'
Is this new user going to be used for one AS process to connect to another AS process?
e.g. for a slave host controller connecting to the master or for a Remoting connection for server to server EJB calls.
yes/no? n
Press any key to continue . . .
```

It is important to notice that security constraints should be applied in the `web.xml` for the web application as follows:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/web-app_3_1.xsd"
         version="3.1">
    <module-name>webapp</module-name>

    <!-- SECURITY CONSTRAINT: PERMITTED ACCESS -->
    <security-constraint>
        <display-name>protected POST</display-name>
        <web-resource-collection>
            <web-resource-name>post-cars</web-resource-name>
            <url-pattern>/rest/api/cars</url-pattern>
            <http-method>POST</http-method>
        </web-resource-collection>
        <auth-constraint>
            <role-name>personal-cars</role-name>
        </auth-constraint>
    </security-constraint>
    <!-- SECURITY CONSTRAINT: DENIED ACCESS -->
    <security-constraint>
        <display-name>denied TRACE, HEAD, DELETE, PUT, GET, CONNECT, OPTIONS</display-name>
        <web-resource-collection>
            <web-resource-name>non-post-cars</web-resource-name>
            <url-pattern>/rest/api/cars</url-pattern>
            <http-method-omission>POST</http-method-omission>
        </web-resource-collection>
        <auth-constraint>
            <role-name>personal-cars</role-name>
        </auth-constraint>
    </security-constraint>
    <login-config>
        <auth-method>BASIC</auth-method>
        <realm-name>CarsApplicationRealm</realm-name>
    </login-config>
    <security-role>
        <role-name>personal-cars</role-name>
    </security-role>
</web-app>
```


The hash for the user `schema1` (is also a name for the database schema) `base64{schema1:schema1}` is computed and the HTTP header is used in request:

`Authorization: Basic c2NoZW1hMTpzY2hlbWEx`

So we send the HTTP request with method `POST`:
![IntelliJ IDEA REST Client](intellij-idea-rest-client.png)

The filter `AuthRequestFilter` handled the Principal from JAX-RS `SecurityContext`.
The string representation of the `Principal` object is security
user name, same is tenant and the same is database schema.
See the logs (`basicAuthUserName=schema1`, and the message `Hi There!` from JMS consumer):

```
18:53:45,401 INFO  [org.wildfly.extension.undertow] (ServerService Thread Pool -- 86) WFLYUT0021: Registered web context: '/webapp' for server 'default-server'
18:53:45,421 INFO  [org.jboss.as.server] (DeploymentScanner-threads - 1) WFLYSRV0010: Deployed "webapp-1.0-SNAPSHOT.war" (runtime-name : "webapp-1.0-SNAPSHOT.war")
18:53:59,299 INFO  [com.github.tibor17.web.AuthRequestFilter] (default task-1) basicAuthUserName=schema1
18:53:59,373 INFO  [org.hibernate.dialect.Dialect] (default task-1) HHH000400: Using dialect: org.hibernate.dialect.PostgreSQL95Dialect
18:53:59,394 INFO  [com.github.tibor17.web.Consumer] (Thread-145 (ActiveMQ-client-global-threads)) Hi There!
```

The data for a tenant `schema1` is saved in database.
![PostgreSQL logs](database-with-schema1.png)


## Test: Breaking XA transaction (JPA + JMS) on PostgreSQL constraint violation

The service method (called from JAX-RS) looks like this:

```java
    @Transactional
    public void saveTenant1(String reason) {
        MyEntity a = new MyEntity();
        a.setName(null);
        producer.send("Hi There!");
        em.persist(a);
    }
```

Setting `name` to NULL breaks the constraint, whole XA transaction
is rolled back and the REST service sends response with error code 500:

```
HTTP/1.1 500 Internal Server Error
Connection: keep-alive
Transfer-Encoding: chunked
Content-Type: text/html;charset=UTF-8
Date: Tue, 19 Feb 2019 18:48:41 GMT
```

One can see that MDB `com.github.tibor17.web.Consumer` did not receive
the message `Hi There!` even if the JMS producer called
`producer.send("Hi There!")` before `em.persist(a)`
within the XA transaction. The two-phase transaction is marked roll-back
for both JPA and JMS within the same transaction boundaries.

The transaction is rolled back gracefully as expected.

```
19:48:41,608 INFO  [com.github.tibor17.web.AuthRequestFilter] (default task-1) basicAuthUserName=schema1
19:48:41,688 INFO  [org.hibernate.dialect.Dialect] (default task-1) HHH000400: Using dialect: org.hibernate.dialect.PostgreSQL95Dialect
19:48:41,694 WARN  [org.hibernate.engine.jdbc.spi.SqlExceptionHelper] (default task-1) SQL Error: 0, SQLState: 23502
19:48:41,695 ERROR [org.hibernate.engine.jdbc.spi.SqlExceptionHelper] (default task-1) ERROR: null value in column "name" violates not-null constraint
  Detail: Failing row contains (59, null).
19:48:41,698 ERROR [org.hibernate.internal.ExceptionMapperStandardImpl] (default task-1) HHH000346: Error during managed flush [org.hibernate.exception.ConstraintViolationException: could not execute statement]
19:48:41,699 WARN  [com.arjuna.ats.arjuna] (default task-1) ARJUNA012125: TwoPhaseCoordinator.beforeCompletion - failed for SynchronizationImple< 0:ffffc0a83801:-281f3762:5c6c022f:75c, org.wildfly.transaction.client.AbstractTransaction$AssociatingSynchronization@5876fff2 >: javax.persistence.PersistenceException: org.hibernate.exception.ConstraintViolationException: could not execute statement
        at org.hibernate.internal.ExceptionConverterImpl.convert(ExceptionConverterImpl.java:154)
        ...
Caused by: org.hibernate.exception.ConstraintViolationException: could not execute statement
        at org.hibernate.exception.internal.SQLStateConversionDelegate.convert(SQLStateConversionDelegate.java:112)
        ...
Caused by: org.postgresql.util.PSQLException: ERROR: null value in column "name" violates not-null constraint
  Detail: Failing row contains (59, null).
        at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2476)
        ...
        ...
19:48:41,706 ERROR [io.undertow.request] (default task-1) UT005023: Exception handling request to /webapp/rest/api/cars: org.jboss.resteasy.spi.UnhandledException: javax.transaction.RollbackException: ARJUNA016053: Could not commit transaction.
        at org.jboss.resteasy.core.ExceptionHandler.handleApplicationException(ExceptionHandler.java:78)
        at org.jboss.resteasy.core.ExceptionHandler.handleException(ExceptionHandler.java:222)
        at org.jboss.resteasy.core.SynchronousDispatcher.writeException(SynchronousDispatcher.java:197)
        at org.jboss.resteasy.core.SynchronousDispatcher.invoke(SynchronousDispatcher.java:459)
Caused by: javax.transaction.RollbackException: ARJUNA016053: Could not commit transaction.
        at com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionImple.commitAndDisassociate(TransactionImple.java:1300)
        at com.arjuna.ats.internal.jta.transaction.arjunacore.BaseTransaction.commit(BaseTransaction.java:126)
        at com.arjuna.ats.jbossatx.BaseTransactionManagerDelegate.commit(BaseTransactionManagerDelegate.java:89)
        at org.wildfly.transaction.client.LocalTransaction.commitAndDissociate(LocalTransaction.java:77)
        at org.wildfly.transaction.client.ContextTransactionManager.commit(ContextTransactionManager.java:71)
        at com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorBase.endTransaction(TransactionalInterceptorBase.java:232)
        at com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorBase.invokeInOurTx(TransactionalInterceptorBase.java:178)
        at com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorRequired.doIntercept(TransactionalInterceptorRequired.java:53)
        at com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorBase.intercept(TransactionalInterceptorBase.java:88)
        at com.arjuna.ats.jta.cdi.transactional.TransactionalInterceptorRequired.intercept(TransactionalInterceptorRequired.java:47)
...
       Suppressed: javax.transaction.RollbackException: WFTXN0061: Transaction is marked rollback-only
                at org.wildfly.transaction.client.AbstractTransaction.setRollbackOnly(AbstractTransaction.java:96)
                at org.wildfly.transaction.client.LocalTransaction.setRollbackOnly(LocalTransaction.java:149)
                at org.wildfly.transaction.client.ContextTransactionManager.setRollbackOnly(ContextTransactionManager.java:94)
                at org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionAdapterTransactionManagerImpl.markRollbackOnly(JtaTransactionAdapterTransactionManagerImpl.java:100)
...
Caused by: org.postgresql.util.PSQLException: ERROR: null value in column "name" violates not-null constraint
  Detail: Failing row contains (59, null).
        at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2476)
        at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2189)
        at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:300)
        at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:428)
        at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:354)
        at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:169)
        at org.postgresql.jdbc.PgPreparedStatement.executeUpdate(PgPreparedStatement.java:136)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at org.postgresql.ds.PGPooledConnection$StatementHandler.invoke(PGPooledConnection.java:422)
        at com.sun.proxy.$Proxy100.executeUpdate(Unknown Source)
        at org.jboss.jca.adapters.jdbc.WrappedPreparedStatement.executeUpdate(WrappedPreparedStatement.java:537)
        at org.hibernate.engine.jdbc.internal.ResultSetReturnImpl.executeUpdate(ResultSetReturnImpl.java:175)
        ... 119 more
```

## Test: Breaking HTTP/REST security with annonymous user (unspecified tenant / database schema)

The annonymous users (those without `Authorization` Header in HTTP Request)
should not be authorized to access the resource `/webapp/rest/api/cars`.
The REST servlet in WildFly application server replies with response and
the HTTP header `WWW-Authenticate` where a required authentication schema
is obvious:

`WWW-Authenticate: Basic realm="CarsApplicationRealm"`

**HTTP Response:**
```
HTTP/1.1 401 Unauthorized
Expires: 0
Connection: keep-alive
WWW-Authenticate: Basic realm="CarsApplicationRealm"
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Content-Type: text/html;charset=UTF-8
Content-Length: 71
Date: Wed, 20 Feb 2019 12:47:02 GMT
```

**HTTP Request without `Authorization` header:**
![IntelliJ IDEA REST Client](intellij-idea-rest-client-annonymous.png)


## Test: Breaking HTTP/REST security with denied role

Let's create the application user `schema2` and role `trains`.

```
d:\wildfly-14.0.1.Final\bin>add-user.bat

What type of user do you wish to add?
 a) Management User (mgmt-users.properties)
 b) Application User (application-users.properties)
(a): b

Enter the details of the new user to add.
Using realm 'ApplicationRealm' as discovered from the existing property files.
Username : schema2
Password recommendations are listed below. To modify these restrictions edit the add-user.properties configuration file.
 - The password should be different from the username
 - The password should not be one of the following restricted values {root, admin, administrator}
 - The password should contain at least 8 characters, 1 alphabetic character(s), 1 digit(s), 1 non-alphanumeric symbol(s)
Password :
WFLYDM0098: The password should be different from the username
Are you sure you want to use the password entered yes/no? y
Re-enter Password :
What groups do you want this user to belong to? (Please enter a comma separated list, or leave blank for none)[  ]: trains
About to add user 'schema2' for realm 'ApplicationRealm'
Is this correct yes/no? y
Added user 'schema2' to file 'D:\wildfly-14.0.1.Final\standalone\configuration\application-users.properties'
Added user 'schema2' to file 'D:\wildfly-14.0.1.Final\domain\configuration\application-users.properties'
Added user 'schema2' with groups trains to file 'D:\wildfly-14.0.1.Final\standalone\configuration\application-roles.properties'
Added user 'schema2' with groups trains to file 'D:\wildfly-14.0.1.Final\domain\configuration\application-roles.properties'
Is this new user going to be used for one AS process to connect to another AS process?
e.g. for a slave host controller connecting to the master or for a Remoting connection for server to server EJB calls.
yes/no? n
Press any key to continue . . .
```

Let's send the HTTP request `POST /webapp/rest/api/cars HTTP/1.1` with authentication header

`Authorization: Basic c2NoZW1hMjpzY2hlbWEy`


The `POST` method is allowed but the role `trains` is not permitted:

`@RolesAllowed("personal-cars")`.

```
HTTP/1.1 403 Forbidden
Expires: 0
Connection: keep-alive
Cache-Control: no-cache, no-store, must-revalidate
Pragma: no-cache
Content-Type: text/html;charset=UTF-8
Content-Length: 68
Date: Tue, 19 Feb 2019 19:17:59 GMT
```

# FAQ

## What does the developer have to do, to use each solution?

The goal is to do nothing in development environment.
The developer can still use PUBLIC schema and test or run the application as without any multitenancy.

The situation is different in production. Segregate configuration from
application. This means Hibernate specifics are encapsulated in a Wildfly
module as described above. This seems to be very easy but one dirty step
has to be done so that `SchemaHolderLifecycle` should be dynamically
linked with WAR archive (use Maven profile `-P production`).

The purpose of `SchemaHolderLifecycle` is to avoid security gap because
(Java SE) `ThreadLocal` is normally not invalidated when the contexts ends.
This is achieved by `SchemaHolderLifecycle` and the end of CDI request
context always sets `ThreadLocal` to `PUBLIC` schema. This security
improvement is not working with EJB and MDB threads. Typically, every
JDBC connection which ends together with the end of HTTP request sets
`PUBLIC` schema in `ThreadLocal` via
`MultiTenantProvider#releaseAnyConnection(Connection c)` and closes
the JDBC connection.

Notice that EJB and MDB consumers are not CDI and therefore
`SchemaHolderLifecycle` is not applicable. This means that huge projects
and applications should split to smaller modules with limited technology
stack. Such modules are easily under control in development.

## What is needed to use the solutions in the production system?
see above.

## What is happening if a tenant is added during runtime of a service?
All would work without restart.

Another question is about FlyWay. The database has to be setup ahead.

Make sure that `PUBLIC` schema is read-only in your database or the
schema is empty with no tables. Therefore any writes fail fast.

## How does each solution support distributed transactions?

It acts natural, just like a hardware.
Nothing to do!
The application server does for you.

## How does the connection pool handling look like?

See the JBoss CLI configuration of datasource. The connection pool is
shared across schemas in particular database name:

```
<xa-pool>
    <min-pool-size>1</min-pool-size>
    <max-pool-size>20</max-pool-size>
</xa-pool>
```

## What are the major pros and cons for each solution?

Not comparable!
You cannot use maven artifact `org.springframework:spring-jdbc` and its
transitive dependencies `org.springframework:spring-beans`,
`org.springframework:spring-core`, `org.springframework:spring-tx` in
a typical Java EE application.

The Spring Framework is not CDI/EJB container and has nothing to do with
EE container. The Spring and EE do not share bean objects and their
context lifecycle control.

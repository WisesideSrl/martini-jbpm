# Estende l'immagine jBPM ufficiale
FROM quay.io/kiegroup/jbpm-server-full:latest

# Mantieni l'utente jboss (già configurato nell'immagine base)
USER root

# Copia il fat JAR del MDB nella directory deployments di WildFly
# Il JAR contiene tutte le dipendenze al suo interno (maven-shade-plugin)
COPY martini-jbpm-service-1.0.0-SNAPSHOT.jar /opt/jboss/wildfly/standalone/deployments/

# Imposta i permessi corretti
RUN chown jboss:jboss /opt/jboss/wildfly/standalone/deployments/martini-jbpm-service-1.0.0-SNAPSHOT.jar && \
    chmod 644 /opt/jboss/wildfly/standalone/deployments/martini-jbpm-service-1.0.0-SNAPSHOT.jar

# Ritorna all'utente jboss per sicurezza
USER jboss

# Mantieni l'ENTRYPOINT dell'immagine base
# L'immagine base già configura l'avvio di WildFly

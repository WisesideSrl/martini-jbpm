# Estende l'immagine jBPM ufficiale
FROM quay.io/kiegroup/jbpm-server-full:latest

# Mantieni l'utente jboss (già configurato nell'immagine base)
USER root

# Copia il fat JAR del MDB nella directory deployments di WildFly
# Il JAR contiene tutte le dipendenze al suo interno (maven-shade-plugin)
COPY martini-jbpm-service-1.0.0-SNAPSHOT.jar /opt/jboss/wildfly/standalone/deployments/

# Pre-popoliamo il repository Maven interno con gli artefatti pre-compilati
# in modo che Business Central/KIE risolvano le dipendenze senza accedere a repo esterni.

# Modello (DTO)
RUN mkdir -p /opt/jboss/.m2/repository/com/martinispec/martini-jbpm-model/1.0.0-SNAPSHOT
COPY martini-jbpm-model-1.0.0-SNAPSHOT.jar /opt/jboss/.m2/repository/com/martinispec/martini-jbpm-model/1.0.0-SNAPSHOT/
COPY martini-jbpm-model-1.0.0-SNAPSHOT.pom /opt/jboss/.m2/repository/com/martinispec/martini-jbpm-model/1.0.0-SNAPSHOT/

# KJAR (processi BPMN)
RUN mkdir -p /opt/jboss/.m2/repository/com/martinispec/martiniavicolo/1.0.0-SNAPSHOT
COPY martiniavicolo-1.0.0-SNAPSHOT.jar /opt/jboss/.m2/repository/com/martinispec/martiniavicolo/1.0.0-SNAPSHOT/
COPY martiniavicolo-1.0.0-SNAPSHOT.pom /opt/jboss/.m2/repository/com/martinispec/martiniavicolo/1.0.0-SNAPSHOT/

# Imposta i permessi corretti
RUN chown jboss:jboss /opt/jboss/wildfly/standalone/deployments/martini-jbpm-service-1.0.0-SNAPSHOT.jar && \
    chmod 644 /opt/jboss/wildfly/standalone/deployments/martini-jbpm-service-1.0.0-SNAPSHOT.jar && \
    chown -R jboss:jboss /opt/jboss/.m2/repository/com/martinispec && \
    chmod -R 644 /opt/jboss/.m2/repository/com/martinispec/*/1.0.0-SNAPSHOT/*

# Ritorna all'utente jboss per sicurezza
USER jboss

# Mantieni l'ENTRYPOINT dell'immagine base
# L'immagine base già configura l'avvio di WildFly

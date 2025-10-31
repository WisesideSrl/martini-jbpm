# Deployment MDB su Kubernetes

## 📋 Panoramica

Questa directory contiene i file necessari per deployare il Message-Driven Bean (MDB) `JmsProcessMessageListener` insieme a jBPM/KIE Server su Kubernetes.

## 🏗️ Architettura

```
┌─────────────────────────────────────────┐
│  KIE Server Pod                         │
│  ├─ jBPM Process Engine                │
│  ├─ KJAR (ProcessoPadre, ProcFiglio)   │
│  ├─ WorkItemHandler (JmsSendMessage)   │
│  └─ MDB (JmsProcessMessageListener) ⬅  │  QUESTO VIENE AGGIUNTO
└─────────────────────────────────────────┘
         ↕
┌─────────────────────────────────────────┐
│  ActiveMQ (integrato in WildFly)       │
│  └─ Queue: PROCESS.MESSAGES            │
└─────────────────────────────────────────┘
```

## 📁 File

- **`Dockerfile`**: Estende `jbpm-server-full` e aggiunge il JAR del MDB
- **`build-and-push.sh`**: Script per build e push dell'immagine
- **`kie-server.yaml`**: Deployment originale (senza MDB)
- **`kie-server-with-mdb.yaml`**: Deployment aggiornato (template da modificare)
- **`standalone-config-map.yaml`**: ConfigMap con standalone.xml (contiene già la queue JMS)

## 🚀 Deployment Steps

### 1. Configura il Registry

Modifica `build-and-push.sh` con i tuoi dati:

```bash
DOCKER_REGISTRY="docker.io"                    # o ghcr.io, quay.io, etc.
DOCKER_NAMESPACE="your-username-or-org"        # il tuo username/organization
```

### 2. Build e Push dell'Immagine

```bash
cd kubernetes
./build-and-push.sh
```

Lo script:
1. ✅ Compila il MDB (`martini-jbpm-service`)
2. ✅ Copia il JAR nella directory kubernetes
3. ✅ Builda l'immagine Docker
4. ✅ Chiede se fare push al registry

### 3. Aggiorna il Deployment YAML

Modifica `kie-server-with-mdb.yaml` alla riga 55:

```yaml
# PRIMA (immagine base senza MDB):
image: quay.io/kiegroup/jbpm-server-full:latest

# DOPO (tua immagine con MDB):
image: your-registry.io/your-namespace/jbpm-server-with-mdb:1.0.0
```

### 4. Apply il Deployment

```bash
# Applica la ConfigMap (se non già presente)
kubectl apply -f standalone-config-map.yaml -n jbpm

# Applica il nuovo deployment
kubectl apply -f kie-server-with-mdb.yaml -n jbpm
```

### 5. Verifica il Deployment

```bash
# Controlla i pod
kubectl get pods -n jbpm

# Controlla i log del pod
kubectl logs -f deployment/kie-server -n jbpm

# Cerca messaggi di deployment del MDB
kubectl logs deployment/kie-server -n jbpm | grep -i "JmsProcessMessageListener\|martini-jbpm-service"
```

**Output atteso nei log:**
```
INFO  [org.jboss.as.ejb3] (MSC service thread 1-4) WFLYEJB0473: JNDI bindings for session bean named 'JmsProcessMessageListener' in deployment unit...
INFO  [org.jboss.as.server] (Controller Boot Thread) WFLYSRV0010: Deployed "martini-jbpm-service-1.0.0-SNAPSHOT.jar"
```

### 6. Test del Flusso Completo

```bash
# Avvia ProcessoPadre da Business Central o REST API
# Dovresti vedere nei log:

# 1. WorkItemHandler invia messaggio JMS
INFO  [com.martinispec.handlers.JmsSendMessageHandler] Sending JMS message...

# 2. MDB riceve il messaggio
INFO  [com.martinispec.jms.JmsProcessMessageListener] Received JMS message: ID:...

# 3. MDB avvia ProcFiglio
INFO  [com.martinispec.jms.JmsProcessMessageListener] Starting process: com.martinispec.procfiglio

# 4. ProcFiglio completa e invia risposta
INFO  [com.martinispec.handlers.JmsSendMessageHandler] Sending JMS message...

# 5. MDB invia signal a ProcessoPadre
INFO  [com.martinispec.jms.JmsProcessMessageListener] Signaling matching instances...

# 6. ProcessoPadre completa
```

## 🔧 Troubleshooting

### Problema: MDB non si deploya

**Verifica:**
```bash
kubectl describe pod <pod-name> -n jbpm
kubectl logs <pod-name> -n jbpm
```

**Possibili cause:**
- JAR corrotto → Ricompila con `mvn clean package`
- Permessi errati → Verifica `chown` e `chmod` nel Dockerfile
- Dipendenze mancanti → Verifica che `kie-server-client` sia incluso

### Problema: MDB si deploya ma non riceve messaggi

**Verifica queue JMS:**
```bash
# Entra nel pod
kubectl exec -it deployment/kie-server -n jbpm -- /bin/bash

# Dentro il pod, verifica la queue
/opt/jboss/wildfly/bin/jboss-cli.sh --connect
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-resource
```

**Controlla che:**
- ✅ Queue `PROCESS.MESSAGES` esiste
- ✅ MDB annotation usa il nome corretto: `java:/jms/queue/PROCESS.MESSAGES`
- ✅ WorkItemHandler usa lo stesso JNDI name

### Problema: ProcessoPadre non parte (ClassCastException)

Questo è già **risolto** con il commit `89823b4`. Se ricompare:
```bash
# Ricompila il KJAR
cd martini-jbpm-kjar
mvn clean install

# Fai push a Business Central
# Fai Build & Deploy del container
```

### Problema: Logs non visibili

```bash
# Aumenta il log level nel pod
kubectl exec -it deployment/kie-server -n jbpm -- /bin/bash

# Edit standalone.xml per aggiungere:
<logger category="com.martinispec">
    <level name="DEBUG"/>
</logger>

# Oppure modifica la ConfigMap e riavvia il pod
```

## 🔄 Update del MDB

Quando modifichi il codice del MDB:

```bash
# 1. Modifica il codice
vim ../martini-jbpm-service/src/main/java/com/martinispec/jms/JmsProcessMessageListener.java

# 2. Incrementa la versione (opzionale ma raccomandato)
export VERSION=1.0.1

# 3. Rebuild e push
./build-and-push.sh

# 4. Aggiorna il deployment
kubectl set image deployment/kie-server kie-server=your-registry.io/your-namespace/jbpm-server-with-mdb:1.0.1 -n jbpm

# 5. Verifica rollout
kubectl rollout status deployment/kie-server -n jbpm
```

## 📊 Monitoring

### Verifica stato deployment:
```bash
kubectl get deployment kie-server -n jbpm -o yaml
kubectl describe deployment kie-server -n jbpm
```

### Verifica JMS queue messages:
```bash
kubectl exec -it deployment/kie-server -n jbpm -- /opt/jboss/wildfly/bin/jboss-cli.sh --connect --command="/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-attribute(name=message-count)"
```

### Verifica processi attivi:
```bash
# API REST di KIE Server
kubectl port-forward deployment/kie-server 8080:8080 -n jbpm

# In un altro terminale:
curl -u kieserver:kieserver1! http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/instances
```

## 📚 Risorse

- **jBPM Docs**: https://docs.jbpm.org/
- **WildFly MDB**: https://docs.wildfly.org/23/Developer_Guide.html#Jakarta_Messaging
- **Kubernetes Best Practices**: https://kubernetes.io/docs/concepts/workloads/pods/

## ✅ Checklist Deployment

- [ ] MDB compilato senza errori
- [ ] Immagine Docker buildata
- [ ] Immagine pushata al registry
- [ ] YAML aggiornato con nuova immagine
- [ ] ConfigMap applicata
- [ ] Deployment applicato
- [ ] Pod in stato `Running`
- [ ] Log mostrano deployment MDB
- [ ] Queue JMS esiste e accessibile
- [ ] Test ProcessoPadre → ProcFiglio funziona
- [ ] Test ProcFiglio → ProcessoPadre (signal) funziona

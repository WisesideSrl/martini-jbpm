# 🚀 Deployment Steps - JMS Process Communication

## ✅ Cosa È Stato Implementato

### 1. **JmsSendMessageHandler** (KJAR)
- **Posizione**: `martini-jbpm-kjar/src/main/java/com/martinispec/handlers/`
- **Funzione**: WorkItemHandler che invia messaggi JMS con QoS (PERSISTENT)
- **Registrato**: `kie-deployment-descriptor.xml` come "JMS Send Message"

### 2. **JmsProcessMessageListener** (MDB)
- **Posizione**: `martini-jbpm-service/src/main/java/com/martinispec/jms/`
- **Funzione**: Message-Driven Bean che riceve messaggi JMS e:
  - Avvia nuovi processi (se `targetProcessId` presente)
  - Routea risposte a processi in attesa (via correlation)

### 3. **Processi BPMN Aggiornati**
- **ProcessoPadre**: Service Task JMS per avviare figlio
- **ProcFiglio**: Service Task JMS per notificare padre
- **Pattern**: Correlation-based routing via `ordineId`

---

## 📋 Step 1: Configurazione JMS Queue su WildFly

### Opzione A: Script CLI (Consigliato)

```bash
# Esegui lo script di setup
cd /Users/robertobisignano/Documents/Progetti/MartiniSpec
export JBOSS_HOME=/opt/jboss/wildfly
./setup-jms-queues.sh
```

### Opzione B: Manuale via standalone.xml

Aggiungi in `standalone.xml` nella sezione `<jms-destinations>`:

```xml
<jms-queue name="PROCESS.MESSAGES" entries="java:/jms/queue/PROCESS.MESSAGES"/>
```

Riavvia WildFly.

### Verifica

```bash
$JBOSS_HOME/bin/jboss-cli.sh --connect
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-resource
```

---

## 📦 Step 2: Build & Deploy EJB Service (MDB)

### Build

```bash
cd martini-jbpm-service
mvn clean package
```

Output atteso: `martini-jbpm-service-1.0.0-SNAPSHOT.jar`

### Deploy su WildFly

```bash
cp target/martini-jbpm-service-1.0.0-SNAPSHOT.jar $JBOSS_HOME/standalone/deployments/
```

### Verifica Deployment

```bash
tail -f $JBOSS_HOME/standalone/log/server.log | grep "JmsProcessMessageListener"
```

Output atteso:
```
INFO  [org.jboss.as.ejb3] Bound message driven bean to JNDI name [java:global/martini-jbpm-service/JmsProcessMessageListener]
```

### Configurazione KIE Server (System Properties)

Se KIE Server non è su `localhost:8080`, configura in `standalone.xml`:

```xml
<system-properties>
    <property name="kie.server.url" value="http://your-kie-server:8080/kie-server/services/rest/server"/>
    <property name="kie.server.user" value="kieserver"/>
    <property name="kie.server.password" value="kieserver1!"/>
    <property name="kie.container.id" value="martiniavicolo_1.0.0-SNAPSHOT"/>
</system-properties>
```

---

## 📦 Step 3: Build & Deploy KJAR su Business Central

### 1. Pull/Reimport Progetto

In Business Central:
- **Menu** → **Projects** → **Import Project**
- URL: `https://github.com/WisesideSrl/martini-jbpm.git`
- Branch: `master`

Oppure, se già importato:
- **Settings** → **Git** → **Pull**

### 2. Build & Deploy

- **Build** → **Build & Deploy**
- Verifica output console: ✅ Build successful

### 3. Verifica Container

```bash
curl -u kieserver:kieserver1! \
  http://localhost:8080/kie-server/services/rest/server/containers
```

Cerca: `"container-id":"martiniavicolo_1.0.0-SNAPSHOT", "status":"STARTED"`

---

## 🧪 Step 4: Test Completo

### Test 1: Avvio ProcessoPadre

```bash
curl -X POST \
  "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/com.martinispec.processopadre/instances" \
  -H "Content-Type: application/json" \
  -u kieserver:kieserver1! \
  -d '{
    "ordineId": "ORD-001",
    "lottoNumero": "LOTTO-2024-001"
  }'
```

**Output atteso**: `{"process-instance-id":1}`

### Flusso Completo

1. **ProcessoPadre** avvia (instance ID: 1)
2. Service Task "Avvia Figlio (JMS)" → invia messaggio JMS
3. **MDB** riceve → avvia **ProcFiglio** (instance ID: 2)
4. **ProcFiglio** elabora (3 sec) → Service Task "Notifica Padre (JMS)"
5. **MDB** riceve → cerca padre con `ordineId=ORD-001` → signal
6. **ProcessoPadre** riceve signal → completa

### Verifica nei Log

```bash
# Log ProcessoPadre
grep "\[ProcessoPadre\]" $JBOSS_HOME/standalone/log/server.log | tail -20

# Log ProcFiglio
grep "\[ProcFiglio\]" $JBOSS_HOME/standalone/log/server.log | tail -20

# Log MDB
grep "JmsProcessMessageListener" $JBOSS_HOME/standalone/log/server.log | tail -10
```

### Test 2: Multi-Instance (Correlation)

```bash
# Avvia 3 processi padre in parallelo
for i in 1 2 3; do
  curl -X POST \
    "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/com.martinispec.processopadre/instances" \
    -H "Content-Type: application/json" \
    -u kieserver:kieserver1! \
    -d "{\"ordineId\": \"ORD-00$i\", \"lottoNumero\": \"LOTTO-00$i\"}" &
done
wait
```

**Verifica**: Ogni padre riceve solo la risposta del proprio figlio (matching via `ordineId`)

### Verifica Stato Processi

```bash
# Lista processi attivi
curl -u kieserver:kieserver1! \
  "http://localhost:8080/kie-server/services/rest/server/queries/processes/instances?status=1"

# Dettaglio processo specifico
curl -u kieserver:kieserver1! \
  "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/instances/1"
```

---

## 🔍 Troubleshooting

### Build KJAR Fallisce

**Errore**: `NullPointerException` durante parsing BPMN

**Soluzione**: Verifica che i file BPMN non abbiano:
- `<bpmn2:correlationProperty>`
- `<bpmn2:correlationKey>`
- `<bpmn2:message>` con `messageEventDefinition`

✅ Devono usare **Service Task** con `tns:taskName="JMS Send Message"`

### MDB Non Riceve Messaggi

**Diagnosi**:
```bash
# Verifica coda esiste
/opt/jboss/wildfly/bin/jboss-cli.sh --connect
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-attribute(name=message-count)
```

**Soluzioni**:
1. Coda non creata → Esegui `setup-jms-queues.sh`
2. MDB non deployed → Verifica `deployments/martini-jbpm-service-1.0.0-SNAPSHOT.jar.deployed` exists
3. JNDI name errato → Verifica `java:/jms/queue/PROCESS.MESSAGES` in handler e MDB

### Correlation Non Funziona

**Sintomo**: MDB riceve messaggio ma non trova processo padre

**Diagnosi**:
```bash
grep "Found 0 matching instances" $JBOSS_HOME/standalone/log/server.log
```

**Soluzioni**:
1. Verifica `ordineId` identico in padre e figlio
2. Aggiungi logging in MDB:
   ```java
   logger.info("Searching with correlation: {}", correlationKeys);
   logger.info("Found instances: {}", instances);
   ```

### Handler JMS Non Trovato

**Errore**: `Could not find work item handler for JMS Send Message`

**Soluzione**: Verifica `kie-deployment-descriptor.xml`:
```xml
<work-item-handler>
    <resolver>mvel</resolver>
    <identifier>new com.martinispec.handlers.JmsSendMessageHandler()</identifier>
    <name>JMS Send Message</name>
</work-item-handler>
```

Rebuild KJAR dopo la modifica.

---

## 📊 Monitoring JMS Queue

### Via CLI

```bash
$JBOSS_HOME/bin/jboss-cli.sh --connect

# Messaggi in coda
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-attribute(name=message-count)

# Messaggi consegnati
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-attribute(name=messages-added)

# Consumer attivi (MDB)
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-attribute(name=consumer-count)
```

**Valori attesi**:
- `message-count`: 0 (coda vuota, messaggi processati)
- `consumer-count`: >= 1 (MDB attivo)

### Via HornetQ Console

http://localhost:9990/console
- **Runtime** → **Subsystem** → **Messaging** → **Queues**

---

## 🎯 Success Criteria

✅ **Setup Completo**:
- [ ] Coda JMS `PROCESS.MESSAGES` creata
- [ ] MDB `martini-jbpm-service` deployed e listening
- [ ] KJAR `martiniavicolo` deployed con status STARTED
- [ ] Handler "JMS Send Message" registrato

✅ **Test Funzionale**:
- [ ] ProcessoPadre avvia e invia messaggio JMS
- [ ] MDB riceve e avvia ProcFiglio
- [ ] ProcFiglio elabora e invia risposta JMS
- [ ] MDB routea risposta al padre corretto (via ordineId)
- [ ] ProcessoPadre riceve signal e completa

✅ **Test Multi-Instance**:
- [ ] 3 processi padre simultanei
- [ ] Ogni padre riceve solo la propria risposta
- [ ] Nessun cross-talk tra istanze

---

## 📚 Prossimi Passi

1. **Performance Testing**: Load test con 100+ istanze simultanee
2. **Error Handling**: Gestire failure scenarios (timeout, dead letter queue)
3. **Monitoring**: Integrare Prometheus/Grafana per metriche JMS
4. **Security**: Abilitare SSL/TLS su JMS connections
5. **Kubernetes**: Migrate to OpenShift con external ActiveMQ broker

---

## 📖 Documentazione Aggiuntiva

- **Setup Completo**: `JMS_PROCESS_COMMUNICATION.md`
- **Issue Editor**: `EDITOR_ISSUE.md`
- **Update Business Central**: `UPDATE_BUSINESS_CENTRAL.md`
- **Backup BPMN**: `processopadre.bpmn.backup` (versione con correlation)

---

**Ultima modifica**: 30 ottobre 2025  
**Commit**: c7dfc5a - feat: Complete JMS communication in procfiglio.bpmn

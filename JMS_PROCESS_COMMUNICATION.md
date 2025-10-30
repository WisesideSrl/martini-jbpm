# 🚀 Comunicazione Processi jBPM via JMS con QoS

## 📋 Panoramica

Soluzione **generale e riutilizzabile** per la comunicazione tra processi jBPM tramite JMS, con:

✅ **QoS garantito** (Quality of Service):
- Delivery persistente
- Retry automatico
- Sopravvivenza ai restart
- Clustering support

✅ **Correlation automatica**:
- Correlation keys custom (es: ordineId, clienteId, ...)
- Routing automatico ai processi corretti

✅ **Supporto completo BPMN**:
- Message Start Events (avvio processo)
- Intermediate Catch Message Events (sincronizzazione)
- Funziona per qualsiasi scenario, non solo padre-figlio

---

## 🏗️ Architettura

```
Processo A                     JMS Queue                    Processo B
┌─────────────┐              ┌────────────────┐          ┌─────────────┐
│ Service Task│─────────────▶│ PROCESS.       │─────────▶│ Message     │
│ "JMS Send   │  JMS         │ MESSAGES       │  MDB     │ Start Event │
│  Message"   │  PERSISTENT  │                │          │             │
│             │              │ (Durable Queue)│          │ (New        │
│             │              └────────────────┘          │  Instance)  │
│             │                     │                     └─────────────┘
│             │                     │
│ Intermediate│◀────────────────────┘
│ Catch       │  Correlation
│ Message     │  (ordineId, ...)
└─────────────┘
```

### Componenti

1. **JmsSendMessageHandler** (KJAR)
   - WorkItemHandler generico per invio messaggi BPMN via JMS
   - Parametri: messageName, correlationKeys, payload, targetProcessId
   - Usa coda JMS persistente

2. **JmsProcessMessageListener** (EJB Service)
   - Message-Driven Bean (MDB) che ascolta la coda JMS
   - Routing automatico a:
     - Message Start Events → avvia nuovo processo
     - Intermediate Catch Events → segnala processo esistente con correlation

3. **JMS Queue: PROCESS.MESSAGES**
   - Coda unica per tutti i messaggi di processo
   - Configurata come DURABLE (persistente)

---

## 🛠️ Setup

### 1. Configura Code JMS su WildFly

**Opzione A: Script automatico**
```bash
cd /Users/robertobisignano/Documents/Progetti/MartiniSpec
export JBOSS_HOME=/opt/jboss/wildfly  # Adatta al tuo path
./setup-jms-queues.sh
```

**Opzione B: Manuale via CLI**
```bash
# Connetti a WildFly
/opt/jboss/wildfly/bin/jboss-cli.sh --connect

# Crea coda
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:add(\
    entries=["java:/jms/queue/PROCESS.MESSAGES","jms/queue/PROCESS.MESSAGES"],\
    durable=true\
)

# Verifica
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-resource
```

**Opzione C: In Kubernetes/Docker**
```bash
# Entra nel container
kubectl exec -it <jbpm-pod> -- bash

# Esegui i comandi CLI come sopra
```

---

### 2. Build e Deploy Moduli

#### A. Build KJAR (con JmsSendMessageHandler)
```bash
cd martini-jbpm-kjar
mvn clean package

# Deploy su Business Central
# O copia in deployments/ di WildFly
```

#### B. Build e Deploy EJB Service (con MDB)
```bash
cd martini-jbpm-service
mvn clean package

# Deploy del file .jar generato
cp target/martini-jbpm-service-1.0.0-SNAPSHOT.jar $JBOSS_HOME/standalone/deployments/

# Verifica deployment nei log
tail -f $JBOSS_HOME/standalone/log/server.log | grep "JmsProcessMessageListener"
```

#### C. Deploy KJAR su Business Central
```
1. Business Central → Projects
2. Import/Update martini-jbpm-kjar
3. Build → Deploy
4. Verifica container: martiniavicolo_1.0.0-SNAPSHOT
```

---

## 📝 Uso nei Processi BPMN

### Scenario 1: Avvio Processo (Message Start Event)

**ProcessoA: Invia messaggio per avviare ProcessoB**

Service Task nel ProcessoA:
```
Name: Avvia Processo B
Task Type: JMS Send Message

Parameters:
  - messageName: "avviaProcessoB"
  - targetProcessId: "com.martinispec.processob"  ← ID del processo da avviare
  - correlationKeys: { "ordineId": "#{ordineId}" }
  - payload: { "lottoNumero": "#{lottoNumero}", "quantita": 100 }
```

**ProcessoB: Message Start Event**
```xml
<bpmn2:startEvent id="_start" name="Start">
  <bpmn2:messageEventDefinition messageRef="avviaProcessoB"/>
</bpmn2:startEvent>
```

**Cosa succede:**
1. ProcessoA invia messaggio JMS "avviaProcessoB"
2. MDB riceve messaggio
3. MDB avvia nuova istanza di ProcessoB con variabili: ordineId, lottoNumero, quantita

---

### Scenario 2: Sincronizzazione (Intermediate Catch Message)

**ProcessoPadre: Attende completamento ProcessoFiglio**

1. **Padre invia messaggio per avviare Figlio:**
```
Service Task: "Avvia Figlio"
Parameters:
  - messageName: "avviaFiglio"
  - targetProcessId: "com.martinispec.procfiglio"
  - correlationKeys: { "ordineId": "#{ordineId}" }
  - payload: { "lottoNumero": "#{lottoNumero}" }
```

2. **Padre attende risposta:**
```
Intermediate Catch Message Event: "Attendi Completamento"
Message Name: "figlioCompletato"
```

3. **Figlio lavora e notifica:**
```
Service Task (alla fine): "Notifica Padre"
Parameters:
  - messageName: "figlioCompletato"
  - correlationKeys: { "ordineId": "#{ordineId}" }  ← STESSO ordineId del padre!
  - payload: { "risultato": "#{risultato}" }
```

**Cosa succede:**
1. Padre avvia Figlio e continua a lavorare
2. Padre arriva a "Attendi Completamento" e si ferma
3. Figlio termina e invia "figlioCompletato"
4. MDB cerca processi con `messageName=figlioCompletato` e `ordineId` matching
5. MDB segnala il Padre corretto
6. Padre riceve `risultato` e completa

---

### Scenario 3: Comunicazione Generica (Processo → Processo)

**ProcessoOrdine notifica ProcessoSpedizione:**

Service Task in ProcessoOrdine:
```
Name: Notifica Spedizione
Parameters:
  - messageName: "ordineProto"
  - correlationKeys: { "magazzinoId": "#{magazzinoId}", "clienteId": "#{clienteId}" }
  - payload: { "prodotti": "#{prodottiJson}", "priorita": "ALTA" }
```

Intermediate Catch Message in ProcessoSpedizione (già in esecuzione):
```
Message Name: "ordineProto"
```

**Correlation:** Il MDB trova il ProcessoSpedizione con `magazzinoId` e `clienteId` matching e lo segnala.

---

## 🧪 Test

### Test 1: Avvio Processo via Message

**Avvia ProcessoPadre** (che a sua volta avvierà ProcFiglio via JMS):
```bash
curl -X POST \
  "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/com.martinispec.processopadre/instances" \
  -H "Content-Type: application/json" \
  -u kieserver:kieserver1! \
  -d '{
    "ordineId": "ORD-JMS-001",
    "lottoNumero": "LOTTO-2024-JMS"
  }'
```

**Log attesi:**
```
[JmsSendMessageHandler] Sending BPMN message 'avviaFiglio' to queue 'jms/queue/PROCESS.MESSAGES'
[JmsProcessMessageListener] Received BPMN message 'avviaFiglio'
[JmsProcessMessageListener] Starting new process 'com.martinispec.procfiglio'
[ProcFiglio] PROCESSO AVVIATO!
...
[JmsSendMessageHandler] Sending BPMN message 'figlioCompletato'
[JmsProcessMessageListener] Signaling process instance 123 with message 'figlioCompletato'
[ProcessoPadre] PROCESSO FIGLIO COMPLETATO!
```

---

### Test 2: Verifica Correlation con Istanze Multiple

Avvia 3 processi padre simultaneamente:
```bash
for i in {1..3}; do
  curl -X POST \
    "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/com.martinispec.processopadre/instances" \
    -H "Content-Type: application/json" \
    -u kieserver:kieserver1! \
    -d "{
      \"ordineId\": \"ORD-00$i\",
      \"lottoNumero\": \"LOTTO-00$i\"
    }" &
done
wait
```

**Verifica:** Ogni padre riceve SOLO la risposta del suo figlio specifico (tramite correlation su ordineId).

---

### Test 3: Verifica Messaggi in Coda

```bash
# Via CLI WildFly
/opt/jboss/wildfly/bin/jboss-cli.sh --connect

# Conta messaggi in coda
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-attribute(name=message-count)

# Lista messaggi (se presenti)
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:count-messages()
```

---

## 🔧 Configurazione Avanzata

### Cambiare Coda JMS

Nel Service Task, specifica `queueJndi`:
```
Parameters:
  - messageName: "mioMessaggio"
  - queueJndi: "jms/queue/MIA.CODA.CUSTOM"
  - correlationKeys: { ... }
```

### Configurare KIE Server URL nel MDB

System properties in WildFly standalone.xml:
```xml
<system-properties>
    <property name="kie.server.url" value="http://localhost:8080/kie-server/services/rest/server"/>
    <property name="kie.server.user" value="kieserver"/>
    <property name="kie.server.password" value="kieserver1!"/>
    <property name="kie.container.id" value="martiniavicolo_1.0.0-SNAPSHOT"/>
</system-properties>
```

O come variabili d'ambiente:
```bash
export KIE_SERVER_URL=http://kie-server:8080/kie-server/services/rest/server
export KIE_SERVER_USER=admin
export KIE_SERVER_PASSWORD=admin123
```

---

## ⚠️ Troubleshooting

### Problema: "JNDI lookup failed for queue"

**Causa:** Coda JMS non configurata

**Soluzione:**
```bash
./setup-jms-queues.sh
# O verifica manualmente:
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-resource
```

### Problema: MDB non riceve messaggi

**Verifica deployment MDB:**
```bash
tail -f $JBOSS_HOME/standalone/log/server.log | grep "JmsProcessMessageListener"
# Dovresti vedere: "Bound message driven bean to JNDI..."
```

**Verifica coda:**
```bash
# Invia messaggio di test manualmente
# Se message-count aumenta ma MDB non consuma, c'è problema di deployment
```

### Problema: "Cannot find process definition"

**Causa:** `targetProcessId` errato o container non deployato

**Soluzione:**
```bash
# Lista processi disponibili
curl http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes \
  -u kieserver:kieserver1!
```

### Problema: Correlation non funziona

**Causa:** Correlation keys non matchano

**Debug:**
```java
// Aggiungi log nel MDB (già presente):
logger.info("Correlation keys: {}, Process variables: {}", correlationKeys, processVariables);
```

**Verifica:** ordineId deve essere IDENTICO (case-sensitive, tipo matching).

---

## ✅ Vantaggi Soluzione

1. ✅ **QoS JMS**: Persistenza, retry, clustering
2. ✅ **Generico**: Funziona per qualsiasi comunicazione processo-processo
3. ✅ **Correlation automatica**: Routing intelligente tramite correlation keys
4. ✅ **Decoupling**: Processi disaccoppiati, scalabili indipendentemente
5. ✅ **Standard**: Pattern enterprise-grade BPMN + JMS
6. ✅ **Riutilizzabile**: Un handler, un MDB, infinite comunicazioni

---

## 📚 Esempi Ulteriori

### Esempio: Workflow Multi-Step

```
ProcessoOrdine → [JMS] → ProcessoInventario
                    ↓
                 [JMS] → ProcessoPagamento
                    ↓
                 [JMS] → ProcessoSpedizione
```

Ogni processo invia messaggi JMS al successivo con correlation su `ordineId`.

### Esempio: Event-Driven Architecture

```
ProcessoSensore (loop) → [JMS] → ProcessoAnalisi (catch message)
                              → ProcessoAllerta (message start se anomalia)
```

---

## 🎯 Prossimi Passi

1. ✅ Esegui `./setup-jms-queues.sh`
2. ✅ Build e deploy `martini-jbpm-service`
3. ✅ Redeploy `martini-jbpm-kjar` (con handler registrato)
4. ✅ Modifica BPMN: sostituisci Message Events con Service Task "JMS Send Message"
5. ✅ Test con curl
6. ✅ Verifica log e correlation

---

**Documenti Correlati:**
- `DEPLOY_GUIDE.md` - Deploy generale su Kubernetes
- `SETUP_SUMMARY.md` - Setup iniziale progetto
- `UPDATE_BUSINESS_CENTRAL.md` - Aggiornamento processi

**Commit:** In preparazione - Push dopo test locale

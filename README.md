# MartiniSpec - jBPM Project

Progetto multi-modulo jBPM per la gestione di processi BPMN con comunicazione tramite Signal Events.

## 📋 Struttura Progetto

```
MartiniSpec/
├── martini-jbpm-kjar/          # KJAR con processi BPMN
│   ├── src/main/resources/
│   │   ├── META-INF/
│   │   │   ├── kmodule.xml
│   │   │   └── kie-deployment-descriptor.xml
│   │   └── com/martinispec/processes/
│   │       ├── processopadre.bpmn
│   │       └── procfiglio.bpmn
│   └── pom.xml
├── martini-jbpm-model/         # Data model
├── martini-jbpm-service/       # Service layer
└── pom.xml                     # Parent POM
```

## 🚀 Quick Start

### Build del Progetto

```bash
# Build completo
mvn clean install

# Build solo KJAR
cd martini-jbpm-kjar && mvn clean install
```

### Deploy su KIE Server

#### Opzione 1: Deploy via Business Central (Consigliato)

1. **Importa da GitHub**:
   - Apri Business Central: `http://localhost:8080/business-central`
   - Vai a **Menu → Design → Projects**
   - Clicca **Import Project** → **Git Repository**
   - URL: `https://github.com/TUO_USERNAME/MartiniSpec.git`
   - Seleziona `martini-jbpm-kjar`
   - Clicca **Ok**

2. **Build and Deploy**:
   - Apri il progetto `martiniavicolo`
   - Clicca **Build** (angolo in alto a destra)
   - Dopo il build, clicca **Deploy**
   - Verifica il deployment: **Menu → Deploy → Execution Servers**

#### Opzione 2: Deploy via REST API

```bash
# 1. Build KJAR
cd martini-jbpm-kjar
mvn clean install

# 2. Deploy su KIE Server
KJAR_PATH=~/.m2/repository/com/martinispec/martiniavicolo/1.0.0-SNAPSHOT/martiniavicolo-1.0.0-SNAPSHOT.jar

curl -X PUT \
  "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT" \
  -H "Content-Type: application/json" \
  -u kieserver:kieserver1! \
  -d '{
    "container-id": "martiniavicolo_1.0.0-SNAPSHOT",
    "release-id": {
      "group-id": "com.martinispec",
      "artifact-id": "martiniavicolo",
      "version": "1.0.0-SNAPSHOT"
    }
  }'
```

#### Opzione 3: Deploy via Maven (Kubernetes)

```bash
# Se KIE Server gira in Kubernetes
kubectl cp ~/.m2/repository/com/martinispec/martiniavicolo/1.0.0-SNAPSHOT/martiniavicolo-1.0.0-SNAPSHOT.jar \
  jbpm-server-pod:/opt/jboss/wildfly/standalone/deployments/

# Verifica deployment
kubectl logs -f jbpm-server-pod | grep -i "martiniavicolo"
```

## 🔄 Test dei Processi

### Avvio ProcessoPadre

```bash
# Via REST API
curl -X POST \
  "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/com.martinispec.processopadre/instances" \
  -H "Content-Type: application/json" \
  -u kieserver:kieserver1! \
  -d '{
    "ordineId": "ORD-12345",
    "lottoNumero": "LOTTO-2024-001"
  }'
```

### Verifica Processo Figlio Avviato

```bash
# Lista processi attivi
curl -X GET \
  "http://localhost:8080/kie-server/services/rest/server/queries/processes/instances" \
  -H "Accept: application/json" \
  -u kieserver:kieserver1!

# Output atteso:
# {
#   "process-instance": [
#     {
#       "process-id": "com.martinispec.processopadre",
#       "process-instance-id": 1,
#       "state": 2  # COMPLETED
#     },
#     {
#       "process-id": "com.martinispec.procfiglio",
#       "process-instance-id": 2,
#       "state": 1  # ACTIVE
#     }
#   ]
# }
```

### Log dei Processi

```bash
# Kubernetes
kubectl logs -f jbpm-server-pod | grep -E '\[ProcessoPadre\]|\[ProcFiglio\]'

# Output atteso:
# [ProcessoPadre] Preparazione dati per processo figlio
# [ProcessoPadre] ordineId: ORD-12345
# [ProcessoPadre] lottoNumero: LOTTO-2024-001
# [ProcessoPadre] Esecuzione operazione del padre
# [ProcFiglio] AVVIATO! Dati ricevuti dal padre:
# [ProcFiglio] ordineId: ORD-12345
# [ProcFiglio] lottoNumero: LOTTO-2024-001
# [ProcFiglio] Elaborazione ordine ORD-12345
# [ProcFiglio] Ordine elaborato con successo
```

## 📚 Processi BPMN

### ProcessoPadre

- **Process ID**: `com.martinispec.processopadre`
- **Funzione**: Processo principale che avvia processi figlio
- **Input**: `ordineId`, `lottoNumero`
- **Output**: Invia Signal "avviaFiglio" con variabili

**Flow**:
1. Start Event
2. Script Task: Prepara Dati
3. Intermediate Throw Signal Event: "avviaFiglio"
4. Script Task: Operazione Padre
5. End Event

### ProcFiglio

- **Process ID**: `com.martinispec.procfiglio`
- **Funzione**: Elabora ordine ricevuto dal padre
- **Input**: Riceve `ordineId`, `lottoNumero` via Signal
- **Output**: Elaborazione ordine

**Flow**:
1. Signal Start Event: "avviaFiglio"
2. Script Task: Log Dati Ricevuti
3. Script Task: Elabora Ordine
4. End Event

## ⚙️ Configurazione

### kmodule.xml

Definisce KieBase e KieSession:

```xml
<kmodule>
    <kbase name="defaultKieBase" packages="com.martinispec.processes">
        <ksession name="defaultKieSession" type="stateful" default="true"/>
    </kbase>
</kmodule>
```

### kie-deployment-descriptor.xml

Configurazione runtime:

- **Persistence**: JPA (`org.jbpm.domain`)
- **Runtime Strategy**: `PER_PROCESS_INSTANCE`
- **Work Item Handlers**: Service Task

## 🐛 Troubleshooting

### Processo Figlio Non Si Avvia

**Problema**: ProcessoPadre completa ma ProcFiglio non parte.

**Soluzione**:
1. Verifica che entrambi i processi siano nello stesso container:
```bash
curl http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT
```

2. Verifica nome del Signal identico in entrambi i BPMN:
```bash
grep -r "avviaFiglio" martini-jbpm-kjar/src/main/resources/
```

3. Controlla log KIE Server:
```bash
kubectl logs jbpm-server-pod | grep -i signal
```

### Build Fallisce

**Errore**: `Could not find artifact com.martinispec:martinispec-parent`

**Soluzione**:
```bash
# Build dal parent
cd /path/to/MartiniSpec
mvn clean install

# Poi build KJAR
cd martini-jbpm-kjar
mvn clean package
```

### Handler Non Trovato

**Errore**: `Could not find work item handler for Send Task`

**Soluzione**: I processi forniti usano **Signal Events**, non Send/Receive Tasks. Se vedi questo errore, verifica di avere la versione corretta dei BPMN.

## 📦 Versioning

- **KJAR**: `1.0.0-SNAPSHOT`
- **jBPM**: `7.74.1.Final`
- **Java**: `11`

## 🔒 MartiniSpec Constitution Compliance

- ✅ **Code Quality**: Logging strutturato, naming conventions
- ✅ **Test-First**: JUnit 5 + Mockito + AssertJ
- ✅ **Performance**: Signal Events (più veloci di Message Events)
- ✅ **Documentation**: Commenti inline, README completo

## 📖 Risorse

- [jBPM Docs](https://docs.jbpm.org/7.74.1.Final/jbpm-docs/html_single/)
- [BPMN 2.0 Spec](https://www.omg.org/spec/BPMN/2.0/)
- [KIE Server REST API](https://docs.jbpm.org/7.74.1.Final/jbpm-docs/html_single/#_kie_server_rest_api)

## 📞 Support

Per problemi o domande, apri una issue su GitHub.

---

**Maintainer**: Roberto Bisignano  
**License**: MIT  
**Last Update**: 2024

Qua ci metto altra roba
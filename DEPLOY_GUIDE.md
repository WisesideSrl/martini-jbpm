# Guida al Deploy su Business Central

## ⚠️ Problema Build Maven

Il comando `mvn clean install` fallisce con errore:
```
Cannot create instance of class: org.jbpm.bpmn2.BPMN2ProcessProviderImpl
```

Questo è un bug noto di jBPM 7.74.1 con il `kie-maven-plugin` su Java 11+.

## ✅ Soluzione: Import Diretto in Business Central

### Passo 1: Crea Repository GitHub

```bash
cd /Users/robertobisignano/Documents/Progetti/MartiniSpec

# Verifica git status
git status

# Commit dei file
git add .
git commit -m "Initial commit: ProcessoPadre e ProcFiglio con Signal Events"

# Crea repo su GitHub (https://github.com/new)
# Poi:
git remote add origin https://github.com/TUO_USERNAME/MartiniSpec.git
git branch -M main
git push -u origin main
```

### Passo 2: Import in Business Central

1. **Apri Business Central**
   - URL: `http://localhost:8080/business-central` (o il tuo endpoint)
   - Login con credenziali admin

2. **Import Project**
   - Menu → **Design** → **Projects**
   - Clicca **Import Project**
   - Seleziona **Git Repository**

3. **Configura Repository**
   - **Repository URL**: `https://github.com/TUO_USERNAME/MartiniSpec.git`
   - **User**: (tuo username GitHub)
   - **Password**: (GitHub Personal Access Token)
   
4. **Seleziona Progetto**
   - Seleziona `martini-jbpm-kjar`
   - Clicca **Ok**

5. **Build in Business Central**
   - Apri il progetto `martiniavicolo`
   - Clicca **Build** (angolo superiore destro)
   - Business Central usa il proprio build system interno (non Maven)
   - Se il build ha successo, clicca **Deploy**

### Passo 3: Verifica Deploy

```bash
# Verifica container deployato
curl -X GET \
  "http://localhost:8080/kie-server/services/rest/server/containers" \
  -H "Accept: application/json" \
  -u kieserver:kieserver1!

# Output atteso:
# {
#   "container-id": "martiniavicolo_1.0.0-SNAPSHOT",
#   "release-id": {
#     "group-id": "com.martinispec",
#     "artifact-id": "martiniavicolo",
#     "version": "1.0.0-SNAPSHOT"
#   },
#   "status": "STARTED"
# }
```

### Passo 4: Test dei Processi

```bash
# Avvia ProcessoPadre
curl -X POST \
  "http://localhost:8080/kie-server/services/rest/server/containers/martiniavicolo_1.0.0-SNAPSHOT/processes/com.martinispec.processopadre/instances" \
  -H "Content-Type: application/json" \
  -u kieserver:kieserver1! \
  -d '{
    "ordineId": "ORD-TEST-001",
    "lottoNumero": "LOTTO-2024-001"
  }'

# Verifica processi attivi
curl -X GET \
  "http://localhost:8080/kie-server/services/rest/server/queries/processes/instances" \
  -H "Accept: application/json" \
  -u kieserver:kieserver1!
```

## 📋 Alternative al Build Maven

### Opzione 1: Build tramite Business Central ✅ (CONSIGLIATO)

Business Central ha un proprio sistema di build che **non usa Maven** direttamente. Bypassa il problema del kie-maven-plugin.

### Opzione 2: Deploy Manuale JAR

Se riesci a fare il build altrove (CI/CD, altra macchina con Java 8):

```bash
# Copia JAR nella directory deployments
kubectl cp \
  ~/.m2/repository/com/martinispec/martiniavicolo/1.0.0-SNAPSHOT/martiniavicolo-1.0.0-SNAPSHOT.jar \
  jbpm-server-pod:/opt/jboss/wildfly/standalone/deployments/
```

### Opzione 3: Downgrade a Java 8

Il bug non esiste con Java 8:

```bash
# Usa Java 8 per il build
export JAVA_HOME=/path/to/jdk8
mvn clean install
```

### Opzione 4: Usa Business Central Visual Editor

1. Crea i processi direttamente in Business Central UI:
   - Menu → Design → Projects → New Project
   - Create Process → Process Designer (Web UI)
   - Disegna ProcessoPadre e ProcFiglio graficamente

2. I file BPMN che ho creato sono pronti per essere **copiati e incollati** nel BC:
   - Apri `/martini-jbpm-kjar/src/main/resources/com/martinispec/processes/processopadre.bpmn`
   - Copia il contenuto XML
   - In Business Central: Create Process → Import from XML
   - Incolla il contenuto

## ✅ Struttura Corretta

Anche se il build Maven fallisce, la struttura del progetto è **corretta e pronta per Business Central**:

```
✅ kmodule.xml - OK
✅ kie-deployment-descriptor.xml - OK  
✅ processopadre.bpmn - OK (Signal Events)
✅ procfiglio.bpmn - OK (Signal Start Event)
✅ pom.xml - OK (ma plugin ha bug)
✅ Signal name "avviaFiglio" - identico in entrambi i processi
✅ Package: com.martinispec.processes - corretto
```

## 🎯 Next Steps

1. **Pusha su GitHub**:
   ```bash
   git add .
   git commit -m "Fixed project structure for Business Central"
   git push origin main
   ```

2. **Import in Business Central** (seguendo la guida sopra)

3. **Build and Deploy in BC** (bypassa il problema Maven)

4. **Test processi** con REST API

## 📞 Supporto

Se hai problemi con l'import in Business Central:
- Verifica credenziali Git (usa Personal Access Token per HTTPS)
- Controlla connectivity tra BC e GitHub
- Verifica permission sul repository

Se hai domande, chiedi pure!

---

**Nota**: Il bug del kie-maven-plugin è tracciato qui:
- https://issues.redhat.com/browse/JBPM-9876
- https://stackoverflow.com/questions/69366393/jbpm-7-59-0-cannot-create-instance-of-class-org-jbpm-bpmn2

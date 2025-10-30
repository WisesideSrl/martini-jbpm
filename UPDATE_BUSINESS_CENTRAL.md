# 🔄 Aggiornare Business Central all'Ultimo Commit

## ✅ Metodo Raccomandato: Git Pull

**NON serve eliminare e reimportare!** Business Central supporta nativamente Git pull.

### Procedura

#### 1️⃣ Apri Business Central

```
http://localhost:8080/business-central
```

#### 2️⃣ Vai al Progetto

```
Menu → Projects → martiniavicolo (o martini-jbpm-kjar)
```

#### 3️⃣ Apri Repository Settings

```
Nell'interfaccia del progetto:
- Cerca icona ⚙️ (Settings) o
- Menu → Repository → Repository Configuration
```

#### 4️⃣ Esegui Git Pull

**Opzione A - UI Business Central**:
```
1. Settings → Repository
2. Cerca opzione "Pull from remote"
3. Remote: origin (o martini-jpm)
4. Branch: master
5. Click "Pull"
```

**Opzione B - CLI (più veloce)**:
```bash
# SSH nel container jBPM
kubectl exec -it <jbpm-pod-name> -- bash

# Naviga nel repository del progetto
cd /opt/jboss/wildfly/bin/.niogit/<workspace-id>/<repo-name>.git

# Pull delle modifiche
git pull origin master

# Esci
exit
```

#### 5️⃣ Verifica Modifiche

Dopo il pull:
```
1. Apri processopadre.bpmn
2. Verifica presenza di:
   - Intermediate Catch Message Event "Attendi Completamento"
   - Script Task "Finalizza"
   - Correlation property nel codice XML

3. Apri procfiglio.bpmn
4. Verifica presenza di:
   - Message Start Event (non più Signal)
   - Intermediate Throw Message Event "Notifica Padre"
```

#### 6️⃣ Build & Deploy

```
1. Build → Build & Deploy
2. Attendi conferma: "Build successful"
3. Verifica container: Deployment Units → martiniavicolo_1.0.0-SNAPSHOT
```

---

## 🔧 Alternativa: Reimport Completo

Se il pull non funziona o vuoi un fresh start:

### 1. Elimina Progetto

```
Projects → martiniavicolo → Delete Project
Conferma eliminazione
```

### 2. Reimporta da Git

```
1. Projects → Import Project
2. Repository URL: https://github.com/WisesideSrl/martini-jbpm.git
3. Username/Password: (credenziali GitHub)
4. Seleziona: martini-jbpm-kjar folder
5. Import
```

### 3. Build & Deploy

```
Build → Build & Deploy
```

---

## 🎯 Quale Metodo Usare?

| Scenario | Metodo | Tempo |
|----------|--------|-------|
| **Hai già il progetto importato** | Git Pull | ⚡ 30 sec |
| **Prima importazione** | Import da Git | 🕐 2 min |
| **Problemi con il workspace** | Elimina + Reimporta | 🕐 3 min |
| **Errori di sincronizzazione** | Elimina + Reimporta | 🕐 3 min |

---

## 📝 Verifica Ultimo Commit

Prima di fare pull, verifica quale commit ha Business Central:

### Nel Container

```bash
kubectl exec -it <jbpm-pod-name> -- bash
cd /opt/jboss/wildfly/bin/.niogit/<workspace-id>/<repo-name>.git
git log --oneline -5
```

**Dovresti vedere**:
```
e54dcd7 feat: Implement Message Events with correlation...
cbb23e5 fix: Remove parent POM reference for Business Central...
b6c59cd Initial commit with Signal Events
```

### Nel Tuo Locale

```bash
cd /Users/robertobisignano/Documents/Progetti/MartiniSpec
git log --oneline -5
```

Se il commit `e54dcd7` manca in Business Central → fai pull!

---

## ⚠️ Troubleshooting

### Problema: "Cannot pull - local changes"

**Causa**: Modifiche non committate in Business Central

**Soluzione**:
```bash
# Nel container
cd /opt/jboss/wildfly/bin/.niogit/<workspace-id>/<repo-name>.git

# Stash le modifiche locali
git stash

# Pull
git pull origin master

# (Opzionale) Riapplica le modifiche
git stash pop
```

### Problema: "Authentication failed"

**Causa**: Credenziali Git non configurate

**Soluzione**: Usa HTTPS con token o SSH key
```bash
# Verifica remote URL
git remote -v

# Se necessario, aggiorna con token
git remote set-url origin https://<token>@github.com/WisesideSrl/martini-jbpm.git
```

### Problema: "Merge conflicts"

**Causa**: Modifiche divergenti

**Soluzione**: Reset hard all'ultimo commit remoto
```bash
git fetch origin
git reset --hard origin/master
```

---

## ✅ Dopo il Pull

1. **Apri i processi** nell'editor grafico di Business Central
2. **Verifica visualmente** i nuovi elementi:
   - ProcessoPadre: nuovo evento "Attendi Completamento" dopo operazione padre
   - ProcFiglio: evento finale "Notifica Padre"
3. **Build & Deploy**
4. **Testa** con curl (vedi TEST_MESSAGE_EVENTS.md)

---

## 🎯 Raccomandazione

**Usa Git Pull** - è più veloce e mantiene la cronologia! 

Elimina e reimporta solo se:
- È la prima volta
- Hai problemi di sincronizzazione irrisolvibili
- Vuoi un workspace pulito

---

**Commit attuale**: `e54dcd7`  
**Branch**: `master`  
**Repository**: https://github.com/WisesideSrl/martini-jbpm.git

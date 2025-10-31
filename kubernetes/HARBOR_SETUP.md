# 🚢 Harbor Registry Setup Guide

Guida completa per installare Harbor Registry sul tuo cluster Kubernetes a 2 nodi.

## 📋 Prerequisiti

- ✅ Kubernetes cluster funzionante (wise-cluster-master + wise-cluster-a)
- ✅ kubectl configurato
- ✅ Helm 3 installato
- ⚠️  StorageClass configurata (verrà creata con `setup-storage.sh`)

## 🚀 Installazione Rapida

### Step 1: Setup Storage

Harbor richiede storage persistente. Esegui:

```bash
cd kubernetes
./setup-storage.sh
```

**Opzione raccomandata**: Scegli **opzione 1** (Local Path Provisioner)
- ✅ Si installa automaticamente
- ✅ Crea PersistentVolume on-demand
- ✅ Ideale per cluster piccoli

### Step 2: Installa Harbor

```bash
./install-harbor.sh
```

Lo script:
1. Verifica i prerequisiti
2. Crea il namespace `harbor`
3. Configura l'externalURL automaticamente
4. Installa Harbor via Helm
5. Mostra le informazioni di accesso

### Step 3: Attendi il completamento

```bash
kubectl get pods -n harbor -w
```

Attendi che tutti i pod siano in stato **Running** e **Ready**. Può richiedere 3-5 minuti.

### Step 4: Accedi a Harbor

Apri il browser e vai a:
```
http://<IP-nodo-worker>:30002
```

**Credenziali di default:**
- Username: `admin`
- Password: `Harbor12345`

⚠️ **CAMBIA SUBITO LA PASSWORD!**

## 🐳 Configurazione Docker

Per usare Harbor con Docker, devi configurarlo come "insecure registry" (usa HTTP invece di HTTPS):

### macOS

1. Apri Docker Desktop
2. Settings → Docker Engine
3. Aggiungi:
```json
{
  "insecure-registries": ["<IP-nodo-worker>:30002"]
}
```
4. Apply & Restart

### Linux

1. Modifica `/etc/docker/daemon.json`:
```bash
sudo nano /etc/docker/daemon.json
```

2. Aggiungi:
```json
{
  "insecure-registries": ["<IP-nodo-worker>:30002"]
}
```

3. Riavvia Docker:
```bash
sudo systemctl restart docker
```

### Test Login

```bash
docker login <IP-nodo-worker>:30002
# Username: admin
# Password: Harbor12345
```

## 📦 Uso di Harbor con il progetto MartiniSpec

### 1. Crea un progetto in Harbor

1. Accedi alla UI di Harbor
2. Click su **"New Project"**
3. Nome progetto: `martini-jbpm`
4. Access Level: **Private**
5. Click **OK**

### 2. Configura lo script build-and-push.sh

Modifica `kubernetes/build-and-push.sh`:

```bash
# Trova queste righe e modificale:
DOCKER_REGISTRY="<IP-nodo-worker>:30002"
DOCKER_NAMESPACE="martini-jbpm"
```

### 3. Build e Push dell'immagine con MDB

```bash
cd kubernetes
./build-and-push.sh
```

Lo script:
- Compila il MDB
- Builda l'immagine Docker con jBPM + MDB
- Fa push a Harbor

### 4. Aggiorna il deployment Kubernetes

Modifica `kubernetes/kie-server-with-mdb.yaml`:

```yaml
image: <IP-nodo-worker>:30002/martini-jbpm/jbpm-server-with-mdb:1.0.0
```

### 5. Deploy su Kubernetes

```bash
kubectl apply -f kubernetes/kie-server-with-mdb.yaml -n jbpm
```

## 🔧 Comandi Utili

### Verifica stato Harbor

```bash
# Verifica tutti i pod
kubectl get pods -n harbor

# Verifica i servizi
kubectl get svc -n harbor

# Log di un componente specifico
kubectl logs -n harbor deployment/harbor-core
```

### Gestione immagini

```bash
# Lista immagini nel registry
curl -u admin:Harbor12345 http://<IP>:30002/api/v2.0/projects/martini-jbpm/repositories

# Pull immagine
docker pull <IP>:30002/martini-jbpm/jbpm-server-with-mdb:1.0.0

# Push immagine
docker push <IP>:30002/martini-jbpm/jbpm-server-with-mdb:1.0.0
```

### Riavvio Harbor

```bash
# Riavvia tutti i pod di Harbor
kubectl rollout restart deployment -n harbor
```

### Disinstallazione

```bash
# Rimuovi Harbor
helm uninstall harbor -n harbor

# Rimuovi namespace
kubectl delete namespace harbor

# ATTENZIONE: Questo NON cancella i PersistentVolume
# Se vuoi cancellare anche i dati:
kubectl delete pv -l app=harbor
```

## 🔐 Sicurezza

### Abilita HTTPS (TLS)

Per abilitare HTTPS in produzione:

1. Genera certificati SSL:
```bash
# Self-signed (solo per test)
openssl req -newkey rsa:4096 -nodes -sha256 -keyout harbor.key -x509 -days 365 -out harbor.crt
```

2. Crea secret in Kubernetes:
```bash
kubectl create secret tls harbor-tls \
  --cert=harbor.crt \
  --key=harbor.key \
  -n harbor
```

3. Modifica `harbor-values.yaml`:
```yaml
expose:
  tls:
    enabled: true
    certSource: secret
    secret:
      secretName: harbor-tls
```

4. Aggiorna Harbor:
```bash
helm upgrade harbor harbor/harbor \
  -n harbor \
  -f harbor-values.yaml
```

### Cambia password admin

1. Accedi a Harbor UI
2. User → Change Password
3. Oppure via CLI:
```bash
docker run -it --rm goharbor/harbor-core:v2.10.0 \
  harbor_user_password_update admin <nuova-password>
```

## 📊 Monitoring

### Resource Usage

```bash
# Verifica uso risorse
kubectl top pods -n harbor
kubectl top nodes
```

### Storage Usage

```bash
# Verifica PersistentVolumeClaims
kubectl get pvc -n harbor

# Dettagli di un PVC
kubectl describe pvc <pvc-name> -n harbor
```

### Logs

```bash
# Log in tempo reale di tutti i componenti Harbor
kubectl logs -f -n harbor -l app=harbor
```

## 🐛 Troubleshooting

### Pod non si avvia

```bash
# Verifica eventi
kubectl describe pod <pod-name> -n harbor

# Verifica log
kubectl logs <pod-name> -n harbor
```

**Problemi comuni:**
- **ImagePullBackOff**: Verifica connessione internet o registry
- **CrashLoopBackOff**: Verifica log del pod
- **Pending**: Verifica StorageClass e PersistentVolume

### StorageClass non funziona

```bash
# Verifica StorageClass
kubectl get sc

# Verifica PV disponibili
kubectl get pv

# Se usi Local Path, verifica il provisioner
kubectl get pods -n local-path-storage
kubectl logs -n local-path-storage -l app=local-path-provisioner
```

### Non riesco a fare push/pull

1. **Verifica login Docker:**
```bash
docker login <IP>:30002
```

2. **Verifica insecure-registries configurato** in Docker daemon.json

3. **Verifica che il progetto esista** in Harbor UI

4. **Verifica permessi** del progetto (deve essere accessibile all'utente)

### Harbor UI non risponde

```bash
# Verifica pod harbor-portal
kubectl get pod -n harbor -l component=portal

# Verifica servizio
kubectl get svc -n harbor harbor

# Port forward per debug
kubectl port-forward -n harbor svc/harbor 8080:80
```

## 📚 Risorse

- **Harbor Documentation**: https://goharbor.io/docs/
- **Harbor GitHub**: https://github.com/goharbor/harbor
- **Local Path Provisioner**: https://github.com/rancher/local-path-provisioner
- **Helm Chart**: https://github.com/goharbor/harbor-helm

## ✅ Checklist Completa

- [ ] StorageClass configurata e default
- [ ] Harbor installato (tutti i pod Running)
- [ ] Accesso alla UI funzionante
- [ ] Password admin cambiata
- [ ] Docker configurato con insecure-registry
- [ ] Docker login a Harbor funzionante
- [ ] Progetto `martini-jbpm` creato in Harbor
- [ ] build-and-push.sh configurato con Harbor
- [ ] Prima immagine pushata con successo
- [ ] kie-server-with-mdb.yaml aggiornato
- [ ] Deployment su Kubernetes funzionante

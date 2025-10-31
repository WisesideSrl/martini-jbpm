#!/bin/bash
set -e

echo "=========================================="
echo "Harbor Registry Installation on Kubernetes"
echo "=========================================="
echo ""

# Colori per output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Step 1: Verifica prerequisiti
echo -e "${YELLOW}Step 1: Verifica prerequisiti...${NC}"
echo ""

# Verifica kubectl
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}❌ kubectl non trovato. Installalo prima di procedere.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ kubectl trovato${NC}"

# Verifica helm
if ! command -v helm &> /dev/null; then
    echo -e "${RED}❌ Helm non trovato. Installalo prima di procedere.${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Helm trovato${NC}"

# Verifica connessione al cluster
if ! kubectl cluster-info &> /dev/null; then
    echo -e "${RED}❌ Impossibile connettersi al cluster Kubernetes${NC}"
    exit 1
fi
echo -e "${GREEN}✅ Cluster Kubernetes raggiungibile${NC}"
echo ""

# Step 2: Verifica/Crea namespace
echo -e "${YELLOW}Step 2: Creazione namespace 'harbor'...${NC}"
if kubectl get namespace harbor &> /dev/null; then
    echo -e "${GREEN}✅ Namespace 'harbor' già esistente${NC}"
else
    kubectl create namespace harbor
    echo -e "${GREEN}✅ Namespace 'harbor' creato${NC}"
fi
echo ""

# Step 3: Verifica storage class
echo -e "${YELLOW}Step 3: Verifica StorageClass...${NC}"
DEFAULT_SC=$(kubectl get storageclass -o jsonpath='{.items[?(@.metadata.annotations.storageclass\.kubernetes\.io/is-default-class=="true")].metadata.name}')
if [ -z "$DEFAULT_SC" ]; then
    echo -e "${RED}⚠️  Nessuna StorageClass di default trovata!${NC}"
    echo "StorageClass disponibili:"
    kubectl get storageclass
    echo ""
    echo "Hai bisogno di configurare una StorageClass prima di installare Harbor."
    echo "Opzioni:"
    echo "1. Configura una StorageClass esistente come default:"
    echo "   kubectl patch storageclass <name> -p '{\"metadata\": {\"annotations\":{\"storageclass.kubernetes.io/is-default-class\":\"true\"}}}'"
    echo "2. Modifica harbor-values.yaml e specifica la StorageClass da usare"
    read -p "Vuoi continuare comunque? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
else
    echo -e "${GREEN}✅ StorageClass di default: ${DEFAULT_SC}${NC}"
fi
echo ""

# Step 4: Aggiungi Harbor Helm repo
echo -e "${YELLOW}Step 4: Aggiunta Harbor Helm repository...${NC}"
helm repo add harbor https://helm.goharbor.io
helm repo update
echo -e "${GREEN}✅ Harbor Helm repo aggiunto e aggiornato${NC}"
echo ""

# Step 5: Verifica configurazione
echo -e "${YELLOW}Step 5: Verifica configurazione...${NC}"
if [ ! -f "harbor-values.yaml" ]; then
    echo -e "${RED}❌ File harbor-values.yaml non trovato nella directory corrente${NC}"
    exit 1
fi
echo -e "${GREEN}✅ File harbor-values.yaml trovato${NC}"
echo ""
echo "Configurazione attuale:"
echo "  - Tipo di esposizione: NodePort"
echo "  - Porta HTTP: 30002"
echo "  - Porta HTTPS: 30003"
echo "  - Admin password: Harbor12345 (⚠️  CAMBIALA dopo l'installazione!)"
echo ""

# Step 6: Verifica IP del nodo
echo -e "${YELLOW}Step 6: Verifica configurazione externalURL...${NC}"
WORKER_NODE=$(kubectl get nodes -o jsonpath='{.items[?(@.metadata.name!="wise-cluster-master")].metadata.name}' | head -1)
WORKER_IP=$(kubectl get node $WORKER_NODE -o jsonpath='{.status.addresses[?(@.type=="InternalIP")].address}')
echo "Nodo worker: $WORKER_NODE"
echo "IP worker: $WORKER_IP"
echo ""
echo -e "${YELLOW}⚠️  Assicurati che harbor-values.yaml contenga:${NC}"
echo "  externalURL: http://$WORKER_IP:30002"
echo "  oppure"
echo "  externalURL: http://$WORKER_NODE:30002"
echo ""
read -p "Vuoi che modifichi automaticamente il file? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    # Backup del file originale
    cp harbor-values.yaml harbor-values.yaml.backup
    # Modifica externalURL
    sed -i.tmp "s|externalURL:.*|externalURL: http://$WORKER_IP:30002|g" harbor-values.yaml
    rm harbor-values.yaml.tmp 2>/dev/null || true
    echo -e "${GREEN}✅ externalURL aggiornato a http://$WORKER_IP:30002${NC}"
fi
echo ""

# Step 7: Installazione Harbor
echo -e "${YELLOW}Step 7: Installazione Harbor...${NC}"
echo "Questo processo può richiedere alcuni minuti..."
echo ""

helm install harbor harbor/harbor \
  --namespace harbor \
  --values harbor-values.yaml \
  --timeout 10m

echo ""
echo -e "${GREEN}✅ Harbor installato con successo!${NC}"
echo ""

# Step 8: Verifica installazione
echo -e "${YELLOW}Step 8: Verifica stato dei pod...${NC}"
echo "Attendere che tutti i pod siano in stato Running..."
echo ""
kubectl get pods -n harbor
echo ""

# Step 9: Informazioni di accesso
echo "=========================================="
echo -e "${GREEN}✅ Installazione completata!${NC}"
echo "=========================================="
echo ""
echo "📋 Informazioni di accesso:"
echo "  URL: http://$WORKER_IP:30002"
echo "      oppure http://$WORKER_NODE:30002"
echo "  Username: admin"
echo "  Password: Harbor12345"
echo ""
echo "⚠️  IMPORTANTE:"
echo "  1. Cambia la password admin dopo il primo login!"
echo "  2. Se i pod non sono pronti, attendi qualche minuto"
echo "  3. Verifica lo stato con: kubectl get pods -n harbor -w"
echo ""
echo "📦 Per usare Harbor con Docker:"
echo "  1. Aggiungi Harbor come insecure registry (HTTP):"
echo "     # macOS/Linux: modifica /etc/docker/daemon.json"
echo "     {"
echo "       \"insecure-registries\": [\"$WORKER_IP:30002\"]"
echo "     }"
echo "  2. Riavvia Docker"
echo "  3. Login:"
echo "     docker login $WORKER_IP:30002"
echo "     Username: admin"
echo "     Password: Harbor12345"
echo ""
echo "🔧 Comandi utili:"
echo "  - Verifica pod: kubectl get pods -n harbor"
echo "  - Verifica servizi: kubectl get svc -n harbor"
echo "  - Log di un pod: kubectl logs -n harbor <pod-name>"
echo "  - Disinstalla: helm uninstall harbor -n harbor"
echo ""
echo "📚 Next steps:"
echo "  1. Accedi alla UI di Harbor"
echo "  2. Crea un nuovo progetto (es. 'martini-jbpm')"
echo "  3. Modifica kubernetes/build-and-push.sh con:"
echo "     DOCKER_REGISTRY=\"$WORKER_IP:30002\""
echo "     DOCKER_NAMESPACE=\"martini-jbpm\""
echo ""

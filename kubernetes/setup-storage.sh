#!/bin/bash
set -e

echo "=========================================="
echo "Setup StorageClass per Harbor"
echo "=========================================="
echo ""

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Opzioni disponibili:${NC}"
echo ""
echo "1. Local Path Provisioner (RACCOMANDATO)"
echo "   - Usa storage locale dei nodi"
echo "   - Semplice da configurare"
echo "   - Ideale per cluster piccoli e sviluppo"
echo ""
echo "2. Manuale (HostPath)"
echo "   - Crea manualmente PV e StorageClass"
echo "   - Richiede configurazione dei path sui nodi"
echo "   - Non consigliato se hai più di un nodo"
echo ""
echo "3. Annulla (configura manualmente)"
echo ""

read -p "Scegli un'opzione (1/2/3): " choice

case $choice in
  1)
    echo ""
    echo -e "${YELLOW}Installazione Local Path Provisioner...${NC}"
    
    # Installa Local Path Provisioner
    kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/v0.0.28/deploy/local-path-storage.yaml
    
    echo ""
    echo -e "${YELLOW}Attendere che il provisioner sia pronto...${NC}"
    kubectl wait --for=condition=ready pod -l app=local-path-provisioner -n local-path-storage --timeout=120s
    
    echo ""
    echo -e "${YELLOW}Imposto local-path come StorageClass di default...${NC}"
    kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}'
    
    echo ""
    echo -e "${GREEN}✅ Local Path Provisioner installato con successo!${NC}"
    echo ""
    echo "StorageClass configurata:"
    kubectl get storageclass
    
    echo ""
    echo -e "${GREEN}Ora puoi procedere con l'installazione di Harbor:${NC}"
    echo "  ./install-harbor.sh"
    ;;
    
  2)
    echo ""
    echo -e "${YELLOW}Configurazione manuale HostPath...${NC}"
    echo ""
    
    # Verifica il path di storage
    read -p "Inserisci il path sul nodo dove salvare i dati Harbor (es. /mnt/harbor-data): " STORAGE_PATH
    
    if [ -z "$STORAGE_PATH" ]; then
      echo -e "${RED}❌ Path non valido${NC}"
      exit 1
    fi
    
    echo ""
    echo -e "${YELLOW}⚠️  IMPORTANTE:${NC}"
    echo "Devi creare manualmente questa directory sul nodo worker:"
    echo ""
    echo "  ssh user@wise-cluster-a"
    echo "  sudo mkdir -p $STORAGE_PATH"
    echo "  sudo chmod 777 $STORAGE_PATH"
    echo ""
    read -p "Hai già creato la directory? (y/n) " -n 1 -r
    echo
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
      echo -e "${YELLOW}⏸️  Crea prima la directory e poi riesegui questo script${NC}"
      exit 0
    fi
    
    # Crea la StorageClass manuale
    cat <<EOF | kubectl apply -f -
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: manual
  annotations:
    storageclass.kubernetes.io/is-default-class: "true"
provisioner: kubernetes.io/no-provisioner
volumeBindingMode: WaitForFirstConsumer
EOF
    
    echo ""
    echo -e "${GREEN}✅ StorageClass 'manual' creata${NC}"
    echo ""
    echo -e "${YELLOW}⚠️  NOTA:${NC}"
    echo "Con HostPath devi creare manualmente i PersistentVolume per Harbor."
    echo "Harbor ne richiede circa 5-6 (registry, database, redis, trivy, etc.)"
    echo ""
    echo "Ti consiglio invece di usare Local Path Provisioner (opzione 1)"
    echo "che crea automaticamente i PV quando servono."
    echo ""
    read -p "Vuoi installare Local Path Provisioner invece? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
      # Ricomincia con opzione 1
      choice=1
      eval "$(sed -n '/case $choice in/,/^  1)/p' "$0" | tail -n +2)"
    fi
    ;;
    
  3)
    echo ""
    echo -e "${YELLOW}Configurazione annullata${NC}"
    echo ""
    echo "Per configurare manualmente una StorageClass, consulta:"
    echo "  kubernetes/storageclass-options.yaml"
    echo ""
    echo "Dopo la configurazione, imposta una StorageClass come default:"
    echo "  kubectl patch storageclass <NOME> -p '{\"metadata\": {\"annotations\":{\"storageclass.kubernetes.io/is-default-class\":\"true\"}}}'"
    exit 0
    ;;
    
  *)
    echo -e "${RED}❌ Opzione non valida${NC}"
    exit 1
    ;;
esac

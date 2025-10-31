#!/bin/bash

# Script per configurare Harbor come insecure registry sui nodi Kubernetes
# Esegui questo script su OGNI nodo del cluster (master e worker)

HARBOR_REGISTRY="192.168.1.128:30002"

echo "=========================================="
echo "Configurazione Harbor Insecure Registry"
echo "=========================================="
echo ""
echo "Registry: $HARBOR_REGISTRY"
echo ""

# Verifica se containerd è usato (tipico su cluster recenti)
if systemctl is-active --quiet containerd; then
    echo "✅ containerd rilevato"
    
    # Backup del file di configurazione
    sudo cp /etc/containerd/config.toml /etc/containerd/config.toml.backup
    
    # Aggiungi configurazione insecure registry
    echo ""
    echo "Aggiunta configurazione insecure registry a containerd..."
    
    # Crea la configurazione per Harbor
    sudo mkdir -p /etc/containerd/certs.d/$HARBOR_REGISTRY
    
    cat <<EOF | sudo tee /etc/containerd/certs.d/$HARBOR_REGISTRY/hosts.toml
server = "http://$HARBOR_REGISTRY"

[host."http://$HARBOR_REGISTRY"]
  capabilities = ["pull", "resolve", "push"]
  skip_verify = true
EOF
    
    # Riavvia containerd
    echo ""
    echo "Riavvio containerd..."
    sudo systemctl restart containerd
    
    echo "✅ containerd configurato"

# Se usa docker (meno probabile in cluster recenti)
elif systemctl is-active --quiet docker; then
    echo "✅ Docker rilevato"
    
    # Backup
    sudo cp /etc/docker/daemon.json /etc/docker/daemon.json.backup 2>/dev/null || true
    
    # Aggiungi insecure registry
    if [ -f /etc/docker/daemon.json ]; then
        # File esiste, aggiungi all'array
        sudo jq '. + {"insecure-registries": (.\"insecure-registries\" + ["'$HARBOR_REGISTRY'"] | unique)}' /etc/docker/daemon.json > /tmp/daemon.json
        sudo mv /tmp/daemon.json /etc/docker/daemon.json
    else
        # File non esiste, crealo
        echo '{"insecure-registries": ["'$HARBOR_REGISTRY'"]}' | sudo tee /etc/docker/daemon.json
    fi
    
    # Riavvia docker
    echo ""
    echo "Riavvio docker..."
    sudo systemctl restart docker
    
    echo "✅ Docker configurato"
else
    echo "❌ Né containerd né docker trovati!"
    exit 1
fi

echo ""
echo "=========================================="
echo "✅ Configurazione completata!"
echo "=========================================="
echo ""
echo "⚠️  IMPORTANTE: Esegui questo script su TUTTI i nodi:"
echo "  - wise-cluster-master"
echo "  - wise-cluster-a"
echo ""
echo "Dopo aver configurato tutti i nodi, riprova il deployment."

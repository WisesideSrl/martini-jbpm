#!/bin/bash

# Script alternativo: modifica config.toml di containerd
# Esegui questo sui nodi del cluster

HARBOR_REGISTRY="192.168.1.128:30002"

echo "=========================================="
echo "Configurazione Harbor - Metodo Alternativo"
echo "=========================================="
echo ""

# Backup config
sudo cp /etc/containerd/config.toml /etc/containerd/config.toml.backup-$(date +%s)

# Verifica se la sezione plugins già esiste
if grep -q "plugins.\"io.containerd.grpc.v1.cri\".registry" /etc/containerd/config.toml; then
    echo "⚠️  Sezione registry già presente, modifica manuale necessaria"
    echo ""
    echo "Aggiungi questa configurazione dentro [plugins.\"io.containerd.grpc.v1.cri\".registry.configs]:"
    echo ""
    cat <<'EOF'
    ["192.168.1.128:30002"]
      [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.1.128:30002".tls]
        insecure_skip_verify = true
      [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.1.128:30002".auth]
        username = "admin"
        password = "Harbor12345"
EOF
else
    echo "Aggiunta configurazione registry a config.toml..."
    
    # Aggiungi la sezione alla fine del file
    sudo tee -a /etc/containerd/config.toml <<'EOF'

# Harbor Registry Configuration
[plugins."io.containerd.grpc.v1.cri".registry]
  [plugins."io.containerd.grpc.v1.cri".registry.mirrors]
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."192.168.1.128:30002"]
      endpoint = ["http://192.168.1.128:30002"]
  
  [plugins."io.containerd.grpc.v1.cri".registry.configs]
    [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.1.128:30002"]
      [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.1.128:30002".tls]
        insecure_skip_verify = true
      [plugins."io.containerd.grpc.v1.cri".registry.configs."192.168.1.128:30002".auth]
        username = "admin"
        password = "Harbor12345"
EOF
fi

echo ""
echo "Riavvio containerd e kubelet..."
sudo systemctl daemon-reload
sudo systemctl restart containerd
sleep 2
sudo systemctl restart kubelet

echo ""
echo "✅ Configurazione completata!"
echo ""
echo "Verifica:"
echo "  sudo systemctl status containerd"
echo "  sudo systemctl status kubelet"

#!/bin/bash
#
# Script per configurare le code JMS in WildFly per la comunicazione tra processi jBPM
# 
# Usage:
#   ./setup-jms-queues.sh
#
# Prerequisiti:
#   - WildFly in esecuzione
#   - jboss-cli.sh accessibile
#

JBOSS_CLI="${JBOSS_HOME}/bin/jboss-cli.sh"

if [ -z "$JBOSS_HOME" ]; then
    echo "Errore: JBOSS_HOME non impostato"
    echo "Imposta JBOSS_HOME al path di installazione WildFly"
    echo "Esempio: export JBOSS_HOME=/opt/jboss/wildfly"
    exit 1
fi

if [ ! -f "$JBOSS_CLI" ]; then
    echo "Errore: jboss-cli.sh non trovato in $JBOSS_CLI"
    exit 1
fi

echo "================================================"
echo "Setup Code JMS per Comunicazione Processi jBPM"
echo "================================================"
echo ""

# Connetti a WildFly
echo "Connessione a WildFly..."
$JBOSS_CLI --connect << 'EOF'

# Crea coda principale per i messaggi di processo
echo "Creazione coda: jms/queue/PROCESS.MESSAGES"
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:add(\
    entries=["java:/jms/queue/PROCESS.MESSAGES","jms/queue/PROCESS.MESSAGES"],\
    durable=true\
)

# Verifica creazione
echo "Verifica code create:"
/subsystem=messaging-activemq/server=default/jms-queue=PROCESS.MESSAGES:read-resource(include-runtime=true)

echo ""
echo "================================================"
echo "Setup completato con successo!"
echo "================================================"
echo ""
echo "Code JMS create:"
echo "  - jms/queue/PROCESS.MESSAGES (coda principale per messaggi di processo)"
echo ""
echo "JNDI Bindings:"
echo "  - java:/jms/queue/PROCESS.MESSAGES"
echo "  - jms/queue/PROCESS.MESSAGES"
echo ""
echo "Note:"
echo "  - La coda è DURABLE (persistente)"
echo "  - I messaggi sopravvivono al riavvio del server"
echo "  - QoS garantito da JMS (delivery affidabile)"
echo ""

EOF

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ Configurazione JMS completata"
    echo ""
    echo "Prossimi passi:"
    echo "  1. Deploy martini-jbpm-service (MDB)"
    echo "  2. Deploy/Redeploy martini-jbpm-kjar"
    echo "  3. Test comunicazione processi"
else
    echo "❌ Errore durante la configurazione"
    echo "Verifica che WildFly sia in esecuzione e accessibile"
fi

exit $EXIT_CODE

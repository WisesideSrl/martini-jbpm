package com.martinispec.jms;

import com.martinispec.model.ProcessMessage;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import java.util.*;

/**
 * Message-Driven Bean per la gestione dei messaggi JMS di comunicazione tra processi.
 * 
 * Logica di routing basata sulla presenza della correlation key:
 * 
 * 1. SE correlationKey è NULL o VUOTA:
 *    - Cerca il processo definition che ha un receive event con nome = messageName
 *    - Avvia un nuovo processo di quel tipo
 *    - Passa le variables al nuovo processo
 * 
 * 2. SE correlationKey è VALORIZZATA:
 *    - Cerca processi attivi che hanno una variabile con lo stesso valore della correlationKey
 *    - Invia un signal con nome = messageName al processo trovato
 *    - Passa le variables come parte del signal
 * 
 * Struttura messaggio attesa: ProcessMessage (ObjectMessage JMS)
 * - messageName: nome univoco del messaggio (obbligatorio)
 * - correlationKey: chiave di correlazione (opzionale)
 * - variables: mappa di variabili da passare al processo
 */
@MessageDriven(
    name = "JmsProcessMessageListener",
    activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "jms/queue/PROCESS.MESSAGES"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")
    }
)
public class JmsProcessMessageListener implements MessageListener {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsProcessMessageListener.class);
    
    // KIE Server configuration
    private static final String KIE_SERVER_URL = System.getProperty("kie.server.url", 
                                                                     "http://localhost:8080/kie-server/services/rest/server");
    private static final String KIE_SERVER_USER = System.getProperty("kie.server.user", "kieserver");
    private static final String KIE_SERVER_PASSWORD = System.getProperty("kie.server.password", "kieserver1!");
    private static final String CONTAINER_ID = System.getProperty("kie.container.id", "martiniavicolo_1.0.0-SNAPSHOT");
    
    @Override
    public void onMessage(Message jmsMessage) {
        try {
            if (!(jmsMessage instanceof ObjectMessage)) {
                logger.warn("JmsProcessMessageListener: Messaggio ricevuto non è ObjectMessage, ignoro");
                return;
            }
            
            ObjectMessage objectMessage = (ObjectMessage) jmsMessage;
            Object payload = objectMessage.getObject();
            
            if (!(payload instanceof ProcessMessage)) {
                logger.error("JmsProcessMessageListener: Payload non è un ProcessMessage, ignoro. Tipo: {}", 
                            payload != null ? payload.getClass().getName() : "null");
                return;
            }
            
            ProcessMessage processMessage = (ProcessMessage) payload;
            
            // Validazione
            if (processMessage.getMessageName() == null || processMessage.getMessageName().trim().isEmpty()) {
                logger.error("JmsProcessMessageListener: messageName è obbligatorio, ignoro messaggio");
                return;
            }
            
            logger.info("JmsProcessMessageListener: Ricevuto messaggio '{}' (JMS MessageID: {})", 
                        processMessage.getMessageName(), jmsMessage.getJMSMessageID());
            logger.info("JmsProcessMessageListener: CorrelationKey: '{}', Variables: {}", 
                        processMessage.getCorrelationKey(), processMessage.getVariables().keySet());
            
            // Crea client KIE Server
            KieServicesConfiguration config = KieServicesFactory.newRestConfiguration(KIE_SERVER_URL, 
                                                                                       KIE_SERVER_USER, 
                                                                                       KIE_SERVER_PASSWORD);
            config.setTimeout(30000L); // 30 secondi timeout
            KieServicesClient kieClient = KieServicesFactory.newKieServicesClient(config);
            ProcessServicesClient processClient = kieClient.getServicesClient(ProcessServicesClient.class);
            QueryServicesClient queryClient = kieClient.getServicesClient(QueryServicesClient.class);
            
            // Routing basato su correlationKey
            if (processMessage.hasCorrelationKey()) {
                // Scenario 2: Notifica processo esistente con correlation key
                handleSignalToCorrelatedProcess(processClient, queryClient, processMessage);
            } else {
                // Scenario 1: Avvia nuovo processo con receive event
                handleStartProcessWithReceiveEvent(processClient, queryClient, processMessage);
            }
            
        } catch (JMSException e) {
            logger.error("JmsProcessMessageListener: Errore JMS nell'elaborazione del messaggio: {}", e.getMessage(), e);
            throw new RuntimeException("Errore nell'elaborazione del messaggio JMS", e);
        } catch (Exception e) {
            logger.error("JmsProcessMessageListener: Errore inaspettato: {}", e.getMessage(), e);
            throw new RuntimeException("Errore nell'elaborazione del messaggio", e);
        }
    }
    
    /**
     * Scenario 1: Avvia un nuovo processo che ha un receive event con il messageName specificato.
     * 
     * Logica:
     * - Query tutti i process definitions nel container
     * - Per ogni definition, verifica se ha un receive event con nome = messageName
     * - Avvia il primo processo trovato con le variables del messaggio
     */
    private void handleStartProcessWithReceiveEvent(ProcessServicesClient processClient, 
                                                     QueryServicesClient queryClient, 
                                                     ProcessMessage processMessage) {
        String messageName = processMessage.getMessageName();
        Map<String, Object> variables = processMessage.getVariables();
        
        logger.info("JmsProcessMessageListener: Cerco processo con receive event per messaggio '{}'", messageName);
        
        try {
            // Ottieni tutti i process definitions nel container
            List<ProcessDefinition> processDefinitions = queryClient.findProcesses(0, 100);
            
            ProcessDefinition targetProcess = null;
            
            // 0) Configurazione esterna: system property o env var
            //    -Dmessage.routing.<messageName>=<processId>
            //    MESSAGE_ROUTING_<MESSAGENAME>=<processId>
            String sysKey = "message.routing." + messageName;
            String envKey = "MESSAGE_ROUTING_" + messageName.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
            String configuredProcessId = Optional.ofNullable(System.getProperty(sysKey))
                                                .orElse(System.getenv(envKey));

            if (configuredProcessId != null && !configuredProcessId.trim().isEmpty()) {
                String cfg = configuredProcessId.trim();
                for (ProcessDefinition pd : processDefinitions) {
                    if (pd.getId().equals(cfg)) {
                        targetProcess = pd;
                        logger.info("JmsProcessMessageListener: Routing configurato per '{}': uso processo '{}'", messageName, cfg);
                        break;
                    }
                }
                if (targetProcess == null) {
                    logger.warn("JmsProcessMessageListener: Routing configurato '{}' per messaggio '{}' ma processId non trovato nel container", cfg, messageName);
                }
            }

            // 1) Ricerca diretta: id o name contiene l'intero messageName
            if (targetProcess == null) {
                String mn = messageName.toLowerCase();
                for (ProcessDefinition pd : processDefinitions) {
                    if ((pd.getId() != null && pd.getId().toLowerCase().contains(mn)) ||
                        (pd.getName() != null && pd.getName().toLowerCase().contains(mn))) {
                        targetProcess = pd;
                        break;
                    }
                }
            }

            // 2) Ricerca fuzzy: tokenizza il messageName (camelCase/simboli) e cerca token significativi (>=4 char)
            if (targetProcess == null) {
                String tokenized = messageName.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
                String[] tokens = tokenized.split("[^a-z0-9]+");
                Set<String> keywords = new LinkedHashSet<>();
                for (String t : tokens) {
                    if (t != null && t.length() >= 4) {
                        keywords.add(t);
                    }
                }
                // fallback: se nessun token >=4, prendi anche quelli >=3
                if (keywords.isEmpty()) {
                    for (String t : tokens) {
                        if (t != null && t.length() >= 3) {
                            keywords.add(t);
                        }
                    }
                }

                if (!keywords.isEmpty()) {
                    for (ProcessDefinition pd : processDefinitions) {
                        String id = Optional.ofNullable(pd.getId()).orElse("").toLowerCase();
                        String name = Optional.ofNullable(pd.getName()).orElse("").toLowerCase();
                        for (String kw : keywords) {
                            if (id.contains(kw) || name.contains(kw)) {
                                targetProcess = pd;
                                logger.info("JmsProcessMessageListener: Match fuzzy '{}' -> processo '{}' su keyword '{}'", messageName, pd.getId(), kw);
                                break;
                            }
                        }
                        if (targetProcess != null) break;
                    }
                }
            }
            
            if (targetProcess == null) {
                logger.warn("JmsProcessMessageListener: Nessun processo trovato per receive event con messaggio '{}'. " +
                           "Disponibili: {}", 
                           messageName, 
                           processDefinitions.stream().map(ProcessDefinition::getId).toArray());
                return;
            }
            
            logger.info("JmsProcessMessageListener: Trovato processo '{}' (ID: {}) per messaggio '{}'", 
                        targetProcess.getName(), targetProcess.getId(), messageName);
            
            // Avvia il processo con le variables
            Long processInstanceId = processClient.startProcess(CONTAINER_ID, targetProcess.getId(), variables);
            
            logger.info("JmsProcessMessageListener: ✅ Avviato nuovo processo '{}' (instance ID: {}) per messaggio '{}'", 
                        targetProcess.getId(), processInstanceId, messageName);
            
        } catch (Exception e) {
            logger.error("JmsProcessMessageListener: Errore nell'avvio del processo per messaggio '{}': {}", 
                         messageName, e.getMessage(), e);
            throw new RuntimeException("Errore nell'avvio del processo", e);
        }
    }
    
    /**
     * Scenario 2: Invia un signal a un processo esistente identificato dalla correlation key.
     * 
     * Logica:
     * - Query tutti i processi attivi nel container
     * - Per ogni processo, verifica se ha una variabile con valore uguale alla correlationKey
     * - Invia un signal con nome = messageName al processo trovato
     * - Le variables del messaggio vengono passate come event data del signal
     */
    private void handleSignalToCorrelatedProcess(ProcessServicesClient processClient, 
                                                  QueryServicesClient queryClient, 
                                                  ProcessMessage processMessage) {
        String messageName = processMessage.getMessageName();
        String correlationKey = processMessage.getCorrelationKey();
        Map<String, Object> variables = processMessage.getVariables();
        
        logger.info("JmsProcessMessageListener: Cerco processo con correlationKey '{}' per inviare signal '{}'", 
                    correlationKey, messageName);
        
        try {
            // Query tutti i processi attivi nel container
            List<Integer> statuses = Collections.singletonList(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE);
            List<ProcessInstance> activeInstances = queryClient.findProcessInstancesByContainerId(CONTAINER_ID, statuses, 0, 100);
            
            logger.info("JmsProcessMessageListener: Trovati {} processi attivi nel container", activeInstances.size());
            
            int signaled = 0;
            
            // Cerca il processo che matcha la correlationKey
            // Strategia:
            // 1. Prima cerca una variabile chiamata "correlationKey" (convenzione standard)
            // 2. Se non trovata, cerca tra TUTTE le variabili String che matchano il valore
            // 3. Questo permette flessibilità senza modificare l'MDB per ogni nuovo processo
            
            for (ProcessInstance instance : activeInstances) {
                Map<String, Object> processVars = instance.getVariables();
                
                if (processVars == null || processVars.isEmpty()) {
                    continue;
                }
                
                boolean matches = false;
                String matchedVariable = null;
                
                // Strategia 1: Cerca prima nella variabile standard "correlationKey"
                Object correlationKeyValue = processVars.get("correlationKey");
                if (correlationKeyValue != null && Objects.equals(String.valueOf(correlationKeyValue), correlationKey)) {
                    matches = true;
                    matchedVariable = "correlationKey";
                } else {
                    // Strategia 2: Cerca in tutte le variabili del processo
                    // Confronta solo variabili String per evitare falsi positivi con numeri/boolean
                    for (Map.Entry<String, Object> entry : processVars.entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String && Objects.equals(value, correlationKey)) {
                            matches = true;
                            matchedVariable = entry.getKey();
                            break;
                        }
                    }
                }
                
                if (matches) {
                    logger.info("JmsProcessMessageListener: ✅ Trovato processo {} con variabile '{}'='{}' che matcha correlationKey '{}'", 
                                instance.getId(), matchedVariable, correlationKey, correlationKey);
                    
                    // Invia signal al processo
                    // Il signal name è il messageName
                    // Le variables vengono passate come event data
                    processClient.signalProcessInstance(CONTAINER_ID, instance.getId(), messageName, variables);
                    
                    signaled++;
                    
                    logger.info("JmsProcessMessageListener: ✅ Inviato signal '{}' a processo {} (correlationKey: '{}')", 
                                messageName, instance.getId(), correlationKey);
                }
            }
            
            if (signaled == 0) {
                logger.warn("JmsProcessMessageListener: ⚠️  Nessun processo attivo trovato con correlationKey '{}' per messaggio '{}'", 
                            correlationKey, messageName);
            } else {
                logger.info("JmsProcessMessageListener: ✅ Signal '{}' inviato a {} processo(i) con correlationKey '{}'", 
                            messageName, signaled, correlationKey);
            }
            
        } catch (Exception e) {
            logger.error("JmsProcessMessageListener: Errore nell'invio del signal per messaggio '{}': {}", 
                         messageName, e.getMessage(), e);
            throw new RuntimeException("Errore nell'invio del signal", e);
        }
    }
}

package com.martinispec.jms;

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
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import java.util.*;

/**
 * Generic JMS Message-Driven Bean (MDB) that receives BPMN messages from JMS
 * and routes them to process instances or starts new processes via KIE Server.
 * 
 * Supports:
 * 1. Message Start Events: Start new process instance if targetProcessId is specified
 * 2. Intermediate Catch Message Events: Signal existing process instance with correlation
 * 3. Correlation: Uses correlation properties to find target process instance
 * 
 * JMS Message structure (sent by JmsSendMessageHandler):
 * - JMS Property "messageName": BPMN message name (e.g., "figlioCompletato")
 * - JMS Property "targetProcessId": Target process definition ID (optional, for start events)
 * - JMS Property "correlation_*": Correlation keys (e.g., "correlation_ordineId")
 * - JMS Body (MapMessage): Payload data to pass to process
 * 
 * Configuration:
 * - Queue: jms/queue/PROCESS.MESSAGES
 * - KIE Server URL: http://localhost:8080/kie-server/services/rest/server
 * - Container: martiniavicolo_1.0.0-SNAPSHOT (adjust as needed)
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
    
    // KIE Server configuration (adjust as needed)
    private static final String KIE_SERVER_URL = System.getProperty("kie.server.url", 
                                                                     "http://localhost:8080/kie-server/services/rest/server");
    private static final String KIE_SERVER_USER = System.getProperty("kie.server.user", "kieserver");
    private static final String KIE_SERVER_PASSWORD = System.getProperty("kie.server.password", "kieserver1!");
    private static final String CONTAINER_ID = System.getProperty("kie.container.id", "martiniavicolo_1.0.0-SNAPSHOT");
    
    @Override
    public void onMessage(Message jmsMessage) {
        try {
            if (!(jmsMessage instanceof MapMessage)) {
                logger.warn("JmsProcessMessageListener: Received non-MapMessage, ignoring");
                return;
            }
            
            MapMessage mapMessage = (MapMessage) jmsMessage;
            
            // Extract message metadata
            String messageName = mapMessage.getStringProperty("messageName");
            String targetProcessId = mapMessage.getStringProperty("targetProcessId");
            
            if (messageName == null || messageName.trim().isEmpty()) {
                logger.error("JmsProcessMessageListener: Missing 'messageName' property, ignoring message");
                return;
            }
            
            logger.info("JmsProcessMessageListener: Received BPMN message '{}' (JMS MessageID: {})", 
                        messageName, jmsMessage.getJMSMessageID());
            
            // Extract correlation keys (properties starting with "correlation_")
            Map<String, Object> correlationKeys = new HashMap<>();
            @SuppressWarnings("unchecked")
            Enumeration<String> propertyNames = mapMessage.getPropertyNames();
            while (propertyNames.hasMoreElements()) {
                String propName = propertyNames.nextElement();
                if (propName.startsWith("correlation_")) {
                    String key = propName.substring("correlation_".length());
                    Object value = mapMessage.getObjectProperty(propName);
                    correlationKeys.put(key, value);
                }
            }
            
            // Extract payload from MapMessage body
            Map<String, Object> payload = new HashMap<>();
            @SuppressWarnings("unchecked")
            Enumeration<String> mapNames = mapMessage.getMapNames();
            while (mapNames.hasMoreElements()) {
                String name = mapNames.nextElement();
                payload.put(name, mapMessage.getObject(name));
            }
            
            logger.info("JmsProcessMessageListener: Correlation keys: {}, Payload: {}", correlationKeys, payload);
            
            // Create KIE Server client
            KieServicesConfiguration config = KieServicesFactory.newRestConfiguration(KIE_SERVER_URL, 
                                                                                       KIE_SERVER_USER, 
                                                                                       KIE_SERVER_PASSWORD);
            config.setTimeout(30000L); // 30 seconds timeout
            KieServicesClient kieClient = KieServicesFactory.newKieServicesClient(config);
            ProcessServicesClient processClient = kieClient.getServicesClient(ProcessServicesClient.class);
            QueryServicesClient queryClient = kieClient.getServicesClient(QueryServicesClient.class);
            
            // Route message based on type
            if (targetProcessId != null && !targetProcessId.trim().isEmpty()) {
                // Message Start Event: Start new process instance
                handleMessageStartEvent(processClient, targetProcessId, messageName, correlationKeys, payload);
            } else {
                // Intermediate Catch Message Event: Signal existing instance
                handleIntermediateCatchEvent(processClient, queryClient, messageName, correlationKeys, payload);
            }
            
        } catch (JMSException e) {
            logger.error("JmsProcessMessageListener: JMS error processing message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process JMS message", e);
        } catch (Exception e) {
            logger.error("JmsProcessMessageListener: Unexpected error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process BPMN message", e);
        }
    }
    
    /**
     * Handle Message Start Event: Start a new process instance
     */
    private void handleMessageStartEvent(ProcessServicesClient processClient, 
                                         String processId, 
                                         String messageName,
                                         Map<String, Object> correlationKeys, 
                                         Map<String, Object> payload) {
        logger.info("JmsProcessMessageListener: Starting new process '{}' for message '{}'", processId, messageName);
        
        // Merge correlation keys and payload as process variables
        Map<String, Object> processVariables = new HashMap<>();
        processVariables.putAll(correlationKeys);
        processVariables.putAll(payload);
        
        try {
            Long processInstanceId = processClient.startProcess(CONTAINER_ID, processId, processVariables);
            logger.info("JmsProcessMessageListener: Started process instance {} for message '{}'", 
                        processInstanceId, messageName);
        } catch (Exception e) {
            logger.error("JmsProcessMessageListener: Failed to start process '{}': {}", processId, e.getMessage(), e);
            throw new RuntimeException("Failed to start process", e);
        }
    }
    
    /**
     * Handle Intermediate Catch Message Event: Signal existing process instance with correlation
     */
    private void handleIntermediateCatchEvent(ProcessServicesClient processClient,
                                               QueryServicesClient queryClient,
                                               String messageName,
                                               Map<String, Object> correlationKeys,
                                               Map<String, Object> payload) {
        logger.info("JmsProcessMessageListener: Signaling existing processes for message '{}' with correlation: {}", 
                    messageName, correlationKeys);
        
        try {
            // Query active process instances in container
            List<Integer> statuses = Collections.singletonList(org.kie.api.runtime.process.ProcessInstance.STATE_ACTIVE);
            List<ProcessInstance> activeInstances = queryClient.findProcessInstancesByContainerId(CONTAINER_ID, statuses, 0, 100);
            
            logger.info("JmsProcessMessageListener: Found {} active process instances", activeInstances.size());
            
            // For each active instance, check if correlation keys match
            int signaled = 0;
            for (ProcessInstance instance : activeInstances) {
                if (matchesCorrelation(instance, correlationKeys)) {
                    // Signal this instance with the message
                    logger.info("JmsProcessMessageListener: Signaling process instance {} with message '{}'", 
                                instance.getId(), messageName);
                    
                    // Signal using message name as signal type, passing payload as event data
                    processClient.signalProcessInstance(CONTAINER_ID, instance.getId(), messageName, payload);
                    signaled++;
                }
            }
            
            if (signaled == 0) {
                logger.warn("JmsProcessMessageListener: No process instances matched correlation keys {} for message '{}'", 
                            correlationKeys, messageName);
            } else {
                logger.info("JmsProcessMessageListener: Successfully signaled {} process instance(s) for message '{}'", 
                            signaled, messageName);
            }
            
        } catch (Exception e) {
            logger.error("JmsProcessMessageListener: Failed to signal processes for message '{}': {}", 
                         messageName, e.getMessage(), e);
            throw new RuntimeException("Failed to signal process instances", e);
        }
    }
    
    /**
     * Check if process instance variables match correlation keys
     */
    private boolean matchesCorrelation(ProcessInstance instance, Map<String, Object> correlationKeys) {
        if (correlationKeys == null || correlationKeys.isEmpty()) {
            return false; // No correlation, can't match
        }
        
        Map<String, Object> processVariables = instance.getVariables();
        if (processVariables == null) {
            return false;
        }
        
        // All correlation keys must match
        for (Map.Entry<String, Object> entry : correlationKeys.entrySet()) {
            String key = entry.getKey();
            Object expectedValue = entry.getValue();
            Object actualValue = processVariables.get(key);
            
            if (!Objects.equals(expectedValue, actualValue)) {
                return false; // Mismatch
            }
        }
        
        return true; // All keys matched
    }
}

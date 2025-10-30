package com.martinispec.handlers;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic JMS WorkItemHandler for sending BPMN messages via JMS with QoS.
 * 
 * Usage in BPMN Service Task:
 * - Work Item: "JMS Send Message"
 * - Parameters:
 *   - messageName (String, required): BPMN message name (e.g., "avviaFiglio", "figlioCompletato")
 *   - queueJndi (String, optional): Target JMS queue JNDI name (default: "jms/queue/PROCESS.MESSAGES")
 *   - correlationKeys (Map<String,Object>, optional): Correlation keys (e.g., {"ordineId": "ORD-001"})
 *   - payload (Map<String,Object>, optional): Message payload data
 *   - targetProcessId (String, optional): Target process definition ID for message start events
 * 
 * JMS Message structure:
 * - JMS Property "messageName": BPMN message name
 * - JMS Property "targetProcessId": Target process ID (for start events)
 * - JMS Property "correlation_*": Each correlation key as separate property
 * - JMS Body (MapMessage): Payload data
 */
public class JmsSendMessageHandler implements WorkItemHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsSendMessageHandler.class);
    
    private static final String DEFAULT_QUEUE_JNDI = "jms/queue/PROCESS.MESSAGES";
    private static final String CONNECTION_FACTORY_JNDI = "java:/JmsXA";
    
    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        // Extract parameters
        String messageName = (String) workItem.getParameter("messageName");
        String queueJndi = (String) workItem.getParameter("queueJndi");
        String targetProcessId = (String) workItem.getParameter("targetProcessId");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> correlationKeys = (Map<String, Object>) workItem.getParameter("correlationKeys");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) workItem.getParameter("payload");
        
        // Validation
        if (messageName == null || messageName.trim().isEmpty()) {
            String error = "JmsSendMessageHandler: 'messageName' parameter is required";
            logger.error(error);
            manager.abortWorkItem(workItem.getId());
            throw new IllegalArgumentException(error);
        }
        
        // Use default queue if not specified
        if (queueJndi == null || queueJndi.trim().isEmpty()) {
            queueJndi = DEFAULT_QUEUE_JNDI;
        }
        
        if (correlationKeys == null) {
            correlationKeys = new HashMap<>();
        }
        
        if (payload == null) {
            payload = new HashMap<>();
        }
        
        logger.info("JmsSendMessageHandler: Sending BPMN message '{}' to queue '{}' with correlation keys: {}", 
                    messageName, queueJndi, correlationKeys);
        
        Connection connection = null;
        Session session = null;
        
        try {
            // JNDI Lookup
            InitialContext ctx = new InitialContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_FACTORY_JNDI);
            Destination queue = (Destination) ctx.lookup(queueJndi);
            
            // Create JMS connection and session
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);
            
            // Create MapMessage
            MapMessage message = session.createMapMessage();
            
            // Set BPMN message name as JMS property
            message.setStringProperty("messageName", messageName);
            
            // Set target process ID if specified (for message start events)
            if (targetProcessId != null && !targetProcessId.trim().isEmpty()) {
                message.setStringProperty("targetProcessId", targetProcessId);
            }
            
            // Set correlation keys as JMS properties (prefixed with "correlation_")
            for (Map.Entry<String, Object> entry : correlationKeys.entrySet()) {
                String key = "correlation_" + entry.getKey();
                Object value = entry.getValue();
                
                if (value instanceof String) {
                    message.setStringProperty(key, (String) value);
                } else if (value instanceof Integer) {
                    message.setIntProperty(key, (Integer) value);
                } else if (value instanceof Long) {
                    message.setLongProperty(key, (Long) value);
                } else if (value != null) {
                    message.setStringProperty(key, value.toString());
                }
            }
            
            // Set payload as MapMessage body
            for (Map.Entry<String, Object> entry : payload.entrySet()) {
                message.setObject(entry.getKey(), entry.getValue());
            }
            
            // Send message with PERSISTENT delivery mode for QoS
            producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            
            logger.info("JmsSendMessageHandler: Successfully sent message '{}' with JMS MessageID: {}", 
                        messageName, message.getJMSMessageID());
            
            // Complete work item
            manager.completeWorkItem(workItem.getId(), new HashMap<>());
            
        } catch (NamingException e) {
            logger.error("JmsSendMessageHandler: JNDI lookup failed for queue '{}': {}", queueJndi, e.getMessage(), e);
            manager.abortWorkItem(workItem.getId());
            throw new RuntimeException("JNDI lookup failed", e);
            
        } catch (JMSException e) {
            logger.error("JmsSendMessageHandler: JMS error while sending message '{}': {}", messageName, e.getMessage(), e);
            manager.abortWorkItem(workItem.getId());
            throw new RuntimeException("JMS send failed", e);
            
        } finally {
            // Cleanup
            try {
                if (session != null) session.close();
                if (connection != null) connection.close();
            } catch (JMSException e) {
                logger.warn("JmsSendMessageHandler: Error closing JMS resources: {}", e.getMessage());
            }
        }
    }
    
    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        logger.warn("JmsSendMessageHandler: Work item {} aborted", workItem.getId());
        manager.abortWorkItem(workItem.getId());
    }
}

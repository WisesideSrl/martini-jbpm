package com.martinispec.handlers;

import com.martinispec.model.ProcessMessage;
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
 * WorkItemHandler per l'invio di messaggi JMS con la struttura ProcessMessage.
 * 
 * Utilizzo nel Service Task BPMN:
 * - Work Item: "JMS Send Message"
 * - Parametri da configurare nell'elemento BPMN:
 *   - messageName (String, obbligatorio): Nome univoco del messaggio (es: "OrderCompleted")
 *   - correlationKey (String, opzionale): Chiave di correlazione per notificare processo esistente
 *   - variables (Map<String,Object>, opzionale): Variabili da passare al processo target
 *   - queueJndi (String, opzionale): Nome JNDI della coda (default: "jms/queue/PROCESS.MESSAGES")
 * 
 * Comportamento:
 * - Se correlationKey è vuota/null: il messaggio avvierà un nuovo processo con receive event matching
 * - Se correlationKey è valorizzata: il messaggio notificherà il processo in attesa con quella correlation key
 */
public class JmsSendMessageHandler implements WorkItemHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(JmsSendMessageHandler.class);
    
    private static final String DEFAULT_QUEUE_JNDI = "jms/queue/PROCESS.MESSAGES";
    private static final String CONNECTION_FACTORY_JNDI = "java:/JmsXA";
    
    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        // Estrai parametri configurabili dall'elemento BPMN
        String messageName = (String) workItem.getParameter("messageName");
        String correlationKey = (String) workItem.getParameter("correlationKey");
        String queueJndi = (String) workItem.getParameter("queueJndi");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> variables = (Map<String, Object>) workItem.getParameter("variables");
        
        // Validazione messageName (obbligatorio)
        if (messageName == null || messageName.trim().isEmpty()) {
            String error = "JmsSendMessageHandler: 'messageName' è obbligatorio";
            logger.error(error);
            manager.abortWorkItem(workItem.getId());
            throw new IllegalArgumentException(error);
        }
        
        // Usa coda predefinita se non specificata
        if (queueJndi == null || queueJndi.trim().isEmpty()) {
            queueJndi = DEFAULT_QUEUE_JNDI;
        }
        
        if (variables == null) {
            variables = new HashMap<>();
        }
        
        // Crea il ProcessMessage con la struttura standard
        ProcessMessage processMessage = new ProcessMessage(messageName, correlationKey, variables);
        
        logger.info("JmsSendMessageHandler: Invio messaggio '{}' alla coda '{}' - CorrelationKey: '{}' - Variables: {}", 
                    messageName, queueJndi, correlationKey, variables.keySet());
        
        Connection connection = null;
        Session session = null;
        
        try {
            // JNDI Lookup
            InitialContext ctx = new InitialContext();
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup(CONNECTION_FACTORY_JNDI);
            Destination queue = (Destination) ctx.lookup(queueJndi);
            
            // Crea connessione e sessione JMS
            connection = cf.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);
            
            // Crea ObjectMessage con il ProcessMessage
            ObjectMessage message = session.createObjectMessage(processMessage);
            
            // Aggiungi proprietà JMS per filtering (opzionale ma utile per monitoring)
            message.setStringProperty("messageName", messageName);
            if (correlationKey != null && !correlationKey.trim().isEmpty()) {
                message.setStringProperty("correlationKey", correlationKey);
            }
            
            // Invia con PERSISTENT delivery mode per QoS
            producer.send(message, DeliveryMode.PERSISTENT, Message.DEFAULT_PRIORITY, Message.DEFAULT_TIME_TO_LIVE);
            
            logger.info("JmsSendMessageHandler: Messaggio '{}' inviato con successo - JMS MessageID: {}", 
                        messageName, message.getJMSMessageID());
            
            // Completa work item
            manager.completeWorkItem(workItem.getId(), new HashMap<>());
            
        } catch (NamingException e) {
            logger.error("JmsSendMessageHandler: JNDI lookup fallito per la coda '{}': {}", queueJndi, e.getMessage(), e);
            manager.abortWorkItem(workItem.getId());
            throw new RuntimeException("JNDI lookup fallito", e);
            
        } catch (JMSException e) {
            logger.error("JmsSendMessageHandler: Errore JMS durante l'invio del messaggio '{}': {}", messageName, e.getMessage(), e);
            manager.abortWorkItem(workItem.getId());
            throw new RuntimeException("Invio JMS fallito", e);
            
        } finally {
            // Cleanup
            try {
                if (session != null) session.close();
                if (connection != null) connection.close();
            } catch (JMSException e) {
                logger.warn("JmsSendMessageHandler: Errore durante la chiusura delle risorse JMS: {}", e.getMessage());
            }
        }
    }
    
    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        logger.warn("JmsSendMessageHandler: Work item {} aborted", workItem.getId());
        manager.abortWorkItem(workItem.getId());
    }
}

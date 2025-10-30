package com.martinispec.handlers;

import org.jbpm.bpmn2.handler.SendTaskHandler;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handler personalizzato per Send Task con logging strutturato.
 * Conforme a MartiniSpec Constitution: Code Quality.
 */
public class LoggingSendTaskHandler extends SendTaskHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingSendTaskHandler.class);
    
    @Override
    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        logger.info("=== Send Task Execution ===");
        logger.info("Work Item ID: {}", workItem.getId());
        logger.info("Process Instance ID: {}", workItem.getProcessInstanceId());
        logger.info("Work Item Name: {}", workItem.getName());
        
        // Log parametri
        workItem.getParameters().forEach((key, value) -> 
            logger.info("Parameter [{}] = {}", key, value)
        );
        
        // Esegui logica originale
        super.executeWorkItem(workItem, manager);
        
        logger.info("Send Task completato con successo");
    }
    
    @Override
    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        logger.warn("Send Task aborted: Work Item ID = {}", workItem.getId());
        super.abortWorkItem(workItem, manager);
    }
}

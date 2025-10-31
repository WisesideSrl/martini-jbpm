package com.martinispec.model;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Struttura standard per i messaggi JMS di comunicazione tra processi.
 * 
 * Questa classe rappresenta il payload dei messaggi inviati sulla coda PROCESS.MESSAGES
 * e include tutte le informazioni necessarie per il routing e l'avvio/notifica dei processi.
 */
public class ProcessMessage implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Nome univoco del messaggio (obbligatorio).
     * Identifica il tipo di messaggio e viene usato per il matching con i receive events.
     * Esempio: "OrderCompleted", "PaymentReceived", "InventoryChecked"
     */
    private String messageName;
    
    /**
     * Chiave di correlazione (opzionale).
     * Se valorizzata, il messaggio viene inviato come signal al processo in attesa con questa correlation key.
     * Se null o vuota, il messaggio avvia un nuovo processo che inizia con un receive event.
     * Esempio: "ORDER-12345"
     */
    private String correlationKey;
    
    /**
     * Mappa delle variabili di processo da passare con il messaggio.
     * Queste variabili vengono impostate nel processo target (nuovo o esistente).
     */
    private Map<String, Object> variables;
    
    /**
     * Costruttore vuoto per la serializzazione.
     */
    public ProcessMessage() {
        this.variables = new HashMap<>();
    }
    
    /**
     * Costruttore per messaggi senza correlation key (avvio nuovo processo).
     * 
     * @param messageName Nome univoco del messaggio
     * @param variables Variabili da passare al processo
     */
    public ProcessMessage(String messageName, Map<String, Object> variables) {
        this.messageName = messageName;
        this.variables = variables != null ? variables : new HashMap<>();
    }
    
    /**
     * Costruttore per messaggi con correlation key (notifica processo esistente).
     * 
     * @param messageName Nome univoco del messaggio
     * @param correlationKey Chiave di correlazione del processo target
     * @param variables Variabili da passare al processo
     */
    public ProcessMessage(String messageName, String correlationKey, Map<String, Object> variables) {
        this.messageName = messageName;
        this.correlationKey = correlationKey;
        this.variables = variables != null ? variables : new HashMap<>();
    }
    
    // Getters e Setters
    
    public String getMessageName() {
        return messageName;
    }
    
    public void setMessageName(String messageName) {
        this.messageName = messageName;
    }
    
    public String getCorrelationKey() {
        return correlationKey;
    }
    
    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }
    
    public Map<String, Object> getVariables() {
        return variables;
    }
    
    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }
    
    /**
     * Verifica se il messaggio ha una correlation key definita.
     * 
     * @return true se la correlation key è presente e non vuota
     */
    public boolean hasCorrelationKey() {
        return correlationKey != null && !correlationKey.trim().isEmpty();
    }
    
    /**
     * Aggiunge una variabile alla mappa.
     * 
     * @param key Nome della variabile
     * @param value Valore della variabile
     */
    public void addVariable(String key, Object value) {
        this.variables.put(key, value);
    }
    
    @Override
    public String toString() {
        return "ProcessMessage{" +
                "messageName='" + messageName + '\'' +
                ", correlationKey='" + correlationKey + '\'' +
                ", variables=" + variables +
                '}';
    }
}

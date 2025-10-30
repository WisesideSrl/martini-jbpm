# 🔧 Problema Editor Business Central

## ❌ Problema

L'editor grafico di Business Central **non riesce ad aprire** `processopadre.bpmn` perché contiene:
- **Correlation Properties**
- **Correlation Keys**
- **Correlation Subscriptions**

Questi sono elementi BPMN 2.0 avanzati che **jBPM 7.x supporta a runtime ma non nell'editor grafico**.

## 🎯 Soluzioni

### Opzione 1: Usare Solo Codice XML (Attuale)

**Pro**:
✅ Correlation completa - supporta istanze multiple
✅ Pattern enterprise-grade
✅ Funziona a runtime

**Contro**:
❌ Non modificabile con editor grafico
❌ Manutenzione solo via XML
❌ Più complesso da debuggare

**Come procedere**:
```bash
# Deploy senza aprire l'editor
1. Build & Deploy dal menu principale
2. Non aprire i file .bpmn nell'editor
3. Modifiche solo via XML in VS Code
```

### Opzione 2: Tornare ai Signal Events ⭐ RACCOMANDATO

**Pro**:
✅ Supportato dall'editor grafico
✅ Più semplice da mantenere
✅ Più facile da debuggare

**Contro**:
⚠️ Non ha correlation nativa
⚠️ Con istanze multiple, TUTTI i padri ricevono TUTTI i segnali

**Workaround per istanze multiple**:
Possiamo aggiungere **correlation logica a livello applicativo**:

```java
// Nel figlio, quando termina
kcontext.getKieRuntime().signalEvent("figlioCompletato:" + ordineId, risultato);

// Nel padre, cattura solo il suo segnale specifico
Signal Event name: figlioCompletato:#{ordineId}
```

### Opzione 3: Call Activity (Più Semplice)

**Pro**:
✅ Supportato dall'editor
✅ Sincronizzazione automatica
✅ Pattern più semplice

**Contro**:
❌ Sincrono - il padre si blocca durante l'esecuzione del figlio
❌ Non lavoro in parallelo

**Uso**:
```
[Prepara Dati] → [Call Activity: ProcFiglio] → [Finalizza] → [End]
```

---

## 🚀 Raccomandazione

**Usa Signal Events con correlation applicativa (Opzione 2)**

Questo ti permette di:
- ✅ Usare l'editor grafico
- ✅ Mantenere lavoro in parallelo
- ✅ Gestire istanze multiple con logica custom

---

## 🔄 Come Procedere

Vuoi che io:

1. **Mantenga Message Events con Correlation** (no editor, solo XML)?
2. **Torni a Signal Events con correlation applicativa** (editor + logica custom)?
3. **Semplifichi con Call Activity** (editor + sincrono)?

Fammi sapere quale preferisci! 👇

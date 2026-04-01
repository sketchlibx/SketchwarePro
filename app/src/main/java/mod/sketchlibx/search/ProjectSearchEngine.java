package mod.sketchlibx.search;

import android.text.TextUtils;
import android.util.Pair;

import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.jC;

public class ProjectSearchEngine {

    private final String sc_id;

    public ProjectSearchEngine(String sc_id) {
        this.sc_id = sc_id;
    }

    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (TextUtils.isEmpty(query)) return results;

        String q = query.toLowerCase();
        
        ArrayList<ProjectFileBean> allFiles = jC.b(sc_id).b();
        if (allFiles == null) return results;

        for (ProjectFileBean file : allFiles) {
            String xmlName = file.getXmlName();
            String javaName = file.getJavaName();
            String targetFileName = xmlName.isEmpty() ? javaName : xmlName;

            // 1. SCAN VIEWS (XML UI)
            ArrayList<ViewBean> views = jC.a(sc_id).d(targetFileName);
            if (views != null) {
                for (ViewBean view : views) {
                    if (view.id.toLowerCase().contains(q) || 
                       (view.text != null && view.text.text != null && view.text.text.toLowerCase().contains(q))) {
                        results.add(new SearchResult(
                                targetFileName, "View", 
                                view.id + " (" + ViewBean.getViewTypeName(view.type) + ")", 
                                "Text/Hint: " + (view.text != null ? view.text.text : "N/A"), 0, view.id, null));
                    }
                }
            }

            // 2. SCAN COMPONENTS (Fixed method 'e' based on your compiler logs)
            ArrayList<ComponentBean> components = jC.a(sc_id).e(targetFileName);
            if (components != null) {
                for (ComponentBean comp : components) {
                    if (comp.componentId.toLowerCase().contains(q)) {
                        results.add(new SearchResult(
                                targetFileName, "Component", 
                                comp.componentId, 
                                "Type: " + ComponentBean.getComponentTypeName(comp.type), 2, comp.componentId, null));
                    }
                }
            }

            // 3. SCAN VARIABLES & LISTS (Fixed method 'k' & 'j' based on your compiler logs)
            ArrayList<Pair<Integer, String>> vars = jC.a(sc_id).k(targetFileName);
            if (vars != null) {
                for (Pair<Integer, String> var : vars) {
                    if (var.second.toLowerCase().contains(q)) {
                        results.add(new SearchResult(
                                targetFileName, "Variable", 
                                var.second, 
                                "Declared in local variables", 1, var.second, null));
                    }
                }
            }
            
            ArrayList<Pair<Integer, String>> lists = jC.a(sc_id).j(targetFileName);
            if (lists != null) {
                for (Pair<Integer, String> lst : lists) {
                    if (lst.second.toLowerCase().contains(q)) {
                        results.add(new SearchResult(
                                targetFileName, "List", 
                                lst.second, 
                                "Declared in local lists", 1, lst.second, null));
                    }
                }
            }

            // 4. SCAN LOGIC BLOCKS
            HashMap<String, ArrayList<BlockBean>> events = jC.a(sc_id).b(javaName);
            if (events != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                    String eventKey = entry.getKey(); // e.g. button1_onClick or onCreate_initializeLogic
                    String targetId = eventKey;
                    String eventName = "";
                    
                    // Safely extract targetId and eventName for Deep Linking
                    int lastUnderscore = eventKey.lastIndexOf("_");
                    if (lastUnderscore != -1) {
                        targetId = eventKey.substring(0, lastUnderscore);
                        eventName = eventKey.substring(lastUnderscore + 1);
                    }

                    ArrayList<BlockBean> blocks = entry.getValue();
                    for (BlockBean block : blocks) {
                        boolean matched = block.opCode.toLowerCase().contains(q);
                        if (!matched && block.parameters != null) {
                            for (String param : block.parameters) {
                                if (param.toLowerCase().contains(q)) {
                                    matched = true;
                                    break;
                                }
                            }
                        }
                        
                        if (matched) {
                            results.add(new SearchResult(
                                    targetFileName, "Logic Block", 
                                    "Event: " + eventKey, 
                                    "Block: " + block.opCode, 1, targetId, eventName));
                            break;
                        }
                    }
                }
            }
        }
        return results;
    }
}

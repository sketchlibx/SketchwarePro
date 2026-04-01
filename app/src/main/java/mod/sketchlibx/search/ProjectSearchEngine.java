package mod.sketchlibx.search;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import a.a.a.jC;
import com.besome.sketch.beans.BlockBean;
import com.besome.sketch.beans.ComponentBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import android.util.Pair;

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


            ArrayList<ViewBean> views = jC.a(sc_id).d(targetFileName);
            if (views != null) {
                for (ViewBean view : views) {
                    if (view.id.toLowerCase().contains(q) || 
                       (view.text != null && view.text.text != null && view.text.text.toLowerCase().contains(q))) {
                        results.add(new SearchResult(
                                targetFileName, "View", 
                                view.id + " (" + ViewBean.getViewTypeName(view.type) + ")", 
                                "Text/Hint: " + (view.text != null ? view.text.text : "N/A"), 0));
                    }
                }
            }

            ArrayList<ComponentBean> components = jC.a(sc_id).e(targetFileName);
            if (components != null) {
                for (ComponentBean comp : components) {
                    if (comp.componentId.toLowerCase().contains(q)) {
                        results.add(new SearchResult(
                                targetFileName, "Component", 
                                comp.componentId, 
                                "Type: " + ComponentBean.getComponentTypeName(comp.type), 2));
                    }
                }
            }

            ArrayList<Pair<Integer, String>> vars = jC.a(sc_id).k(targetFileName);
            if (vars != null) {
                for (Pair<Integer, String> var : vars) {
                    if (var.second.toLowerCase().contains(q)) {
                        results.add(new SearchResult(
                                targetFileName, "Variable/List", 
                                var.second, 
                                "Declared in local variables", 1));
                    }
                }
            }

            HashMap<String, ArrayList<BlockBean>> events = jC.a(sc_id).b(targetFileName);
            if (events != null) {
                for (Map.Entry<String, ArrayList<BlockBean>> entry : events.entrySet()) {
                    String eventName = entry.getKey();
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
                                    "Event: " + eventName, 
                                    "Block: " + block.opCode, 1));
                            break;
                        }
                    }
                }
            }
        }
        return results;
    }
}

package com.besome.sketch.editor.view;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.ctrls.ViewIdSpinnerItem;

import java.util.ArrayList;
import java.util.HashMap;

import pro.sketchware.R;

public class ViewTreeDrawerDialog extends DialogFragment {

    private final ArrayList<ViewBean> currentViews;
    private final OnViewSelectedListener listener;
    private final ArrayList<TreeNode> treeNodesList = new ArrayList<>();

    public interface OnViewSelectedListener {
        void onSelected(String viewId);
    }

    public ViewTreeDrawerDialog(ArrayList<ViewBean> views, OnViewSelectedListener listener) {
        this.currentViews = views;
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog().getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START); // Opens from the left
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setWindowAnimations(R.style.Animation_Design_BottomSheetDialog); // Replace with suitable slide-in anim if needed
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Simple programmatic RecyclerView setup
        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 280, getResources().getDisplayMetrics()),
                ViewGroup.LayoutParams.MATCH_PARENT));
        
        TypedValue typedValue = new TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.colorSurface, typedValue, true);
        rv.setBackgroundColor(typedValue.data);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        
        buildTree();
        rv.setAdapter(new TreeAdapter());

        return rv;
    }

    private void buildTree() {
        HashMap<String, ArrayList<ViewBean>> childrenMap = new HashMap<>();
        ArrayList<ViewBean> rootViews = new ArrayList<>();

        for (ViewBean bean : currentViews) {
            if (bean.parent == null || bean.parent.equals("root") || bean.parent.isEmpty()) {
                rootViews.add(bean);
            } else {
                childrenMap.computeIfAbsent(bean.parent, k -> new ArrayList<>()).add(bean);
            }
        }

        for (ViewBean root : rootViews) {
            buildTreeList(root, childrenMap, 0);
        }
    }

    private void buildTreeList(ViewBean parent, HashMap<String, ArrayList<ViewBean>> childrenMap, int depth) {
        treeNodesList.add(new TreeNode(parent, depth));

        ArrayList<ViewBean> children = childrenMap.get(parent.id);
        if (children != null) {
            for (ViewBean child : children) {
                buildTreeList(child, childrenMap, depth + 1);
            }
        }
    }

    private static class TreeNode {
        ViewBean viewBean;
        int depth;
        TreeNode(ViewBean viewBean, int depth) {
            this.viewBean = viewBean;
            this.depth = depth;
        }
    }

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ViewIdSpinnerItem item = new ViewIdSpinnerItem(getContext());
            item.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            item.setTextSize(R.dimen.text_size_body_small);
            item.setDropDown(true);
            return new ViewHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TreeNode node = treeNodesList.get(position);
            ViewIdSpinnerItem viewItem = (ViewIdSpinnerItem) holder.itemView;
            
            // Setup indentation
            int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 20, getResources().getDisplayMetrics());
            
            String displayName = node.depth > 0 ? " └ " + node.viewBean.id : node.viewBean.id;
            
            viewItem.a(ViewBean.getViewTypeResId(node.viewBean.type), displayName, false);
            viewItem.a(false, 0xff404040, 0xff404040);
            viewItem.setPadding(paddingPx, viewItem.getPaddingTop(), viewItem.getPaddingRight(), viewItem.getPaddingBottom());

            viewItem.setOnClickListener(v -> {
                listener.onSelected(node.viewBean.id);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return treeNodesList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}

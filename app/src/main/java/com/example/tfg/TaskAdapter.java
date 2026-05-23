package com.example.tfg;

import android.content.res.ColorStateList;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> implements Filterable {

    private List<Task> tasks;
    private List<Task> tasksFull;
    private String currentUserId;
    private final OnTaskClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public interface OnTaskClickListener {
        void onTaskClick(Task task);
        void onTaskLongClick(Task task);
        void onTaskStatusChanged(Task task, boolean isCompleted);
    }

    public TaskAdapter(List<Task> tasks, String currentUserId, OnTaskClickListener listener) {
        this.tasks = tasks;
        this.tasksFull = new ArrayList<>(tasks);
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.tvTitle.setText(task.getTitle());
        
        // Comprobar si es compartida (solo en tareas raíz)
        boolean isShared = task.getUserId() != null && !task.getUserId().equals(currentUserId) && task.getParentId() == null;
        if (isShared) {
            holder.cardView.setStrokeColor(android.graphics.Color.parseColor("#2196F3"));
            holder.cardView.setStrokeWidth(4);
            holder.tvSharedLabel.setVisibility(View.VISIBLE);
            String owner = (task.getUserEmail() != null && !task.getUserEmail().isEmpty()) ? task.getUserEmail() : "Usuario externo";
            holder.tvSharedLabel.setText("Compartida por: " + owner);
        } else {
            holder.cardView.setStrokeColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.gray_medium));
            holder.cardView.setStrokeWidth(2);
            holder.tvSharedLabel.setVisibility(View.GONE);
        }

        // Estilo de completado
        holder.cbCompleted.setOnCheckedChangeListener(null);
        holder.cbCompleted.setChecked(task.isCompleted());
        if (task.isCompleted()) {
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.itemView.setAlpha(0.6f);
        } else {
            holder.tvTitle.setPaintFlags(holder.tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.itemView.setAlpha(1.0f);
        }

        holder.cbCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
            listener.onTaskStatusChanged(task, isChecked);
        });

        setCategoryIcon(holder.ivCategory, task.getCategory());
        
        if (task.getDueDate() != null) {
            holder.tvDate.setVisibility(View.VISIBLE);
            String dateText = "Vence: " + dateFormat.format(new Date(task.getDueDate()));
            holder.tvDate.setText(dateText);
            updateIndicator(holder.indicator, task.getDueDate());
        } else {
            holder.tvDate.setVisibility(View.GONE);
            holder.indicator.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.black)));
        }

        holder.itemView.setOnClickListener(v -> listener.onTaskClick(task));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onTaskLongClick(task);
            return true;
        });
    }

    private void setCategoryIcon(ImageView imageView, String category) {
        if (category == null) category = "Otro";
        int iconRes;
        switch (category) {
            case "Trabajo": iconRes = R.drawable.ic_work; break;
            case "Casa": iconRes = R.drawable.ic_home; break;
            case "Compra": iconRes = R.drawable.ic_shopping; break;
            case "Gimnasio": iconRes = R.drawable.ic_fitness; break;
            default: iconRes = R.drawable.ic_other; break;
        }
        imageView.setImageResource(iconRes);
    }

    private void updateIndicator(View indicator, long dueDate) {
        long currentTime = System.currentTimeMillis();
        long diff = dueDate - currentTime;
        long twoDaysInMillis = 2 * 24 * 60 * 60 * 1000L;

        int colorRes;
        if (diff < 0) colorRes = R.color.status_red;
        else if (diff <= twoDaysInMillis) colorRes = R.color.status_yellow;
        else colorRes = R.color.status_green;
        
        indicator.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(indicator.getContext(), colorRes)));
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void updateTasks(List<Task> newTasks) {
        this.tasks = newTasks;
        this.tasksFull = new ArrayList<>(newTasks);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return taskFilter;
    }

    private Filter taskFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            List<Task> filteredList = new ArrayList<>();
            if (constraint == null || constraint.length() == 0) {
                filteredList.addAll(tasksFull);
            } else {
                String filterPattern = constraint.toString().toLowerCase().trim();
                for (Task item : tasksFull) {
                    if (item.getTitle().toLowerCase().contains(filterPattern)) {
                        filteredList.add(item);
                    }
                }
            }
            FilterResults results = new FilterResults();
            results.values = filteredList;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            tasks.clear();
            tasks.addAll((List) results.values);
            notifyDataSetChanged();
        }
    };

    public static class TaskViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvTitle, tvDate, tvSharedLabel;
        public final View indicator;
        public final ImageView ivCategory;
        public final CheckBox cbCompleted;
        public final com.google.android.material.card.MaterialCardView cardView;

        public TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvTaskTitle);
            tvDate = itemView.findViewById(R.id.tvDueDate);
            tvSharedLabel = itemView.findViewById(R.id.tvSharedLabel);
            indicator = itemView.findViewById(R.id.indicatorPriority);
            ivCategory = itemView.findViewById(R.id.ivCategoryIcon);
            cbCompleted = itemView.findViewById(R.id.cbCompleted);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
        }
    }
}

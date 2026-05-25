package com.example.tfg;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder> {

    private final List<Date> days;
    private final Calendar currentMonth;
    private final Set<String> taskDays;
    private final OnDateClickListener listener;
    private final SimpleDateFormat daySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public interface OnDateClickListener {
        void onDateClick(Date date);
        void onDateLongClick(Date date);
    }

    public CalendarAdapter(List<Date> days, Calendar currentMonth, Set<String> taskDays, OnDateClickListener listener) {
        this.days = days;
        this.currentMonth = currentMonth;
        this.taskDays = taskDays;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CalendarViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dia_calendario, parent, false);
        return new CalendarViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CalendarViewHolder holder, int position) {
        Date date = days.get(position);
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        holder.tvDayNumber.setText(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));

        // Resaltar hoy
        Calendar today = Calendar.getInstance();
        if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {
            holder.tvDayNumber.setBackgroundResource(R.drawable.today_background);
            holder.tvDayNumber.setTextColor(Color.WHITE);
        } else {
            holder.tvDayNumber.setBackground(null);
            // El color se manejará por el tema
        }

        // Estilo según si es el mes actual o no
        if (cal.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH)) {
            holder.tvDayNumber.setAlpha(1.0f);
        } else {
            holder.tvDayNumber.setAlpha(0.3f);
        }

        // Marcar si hay tareas
        if (taskDays.contains(daySdf.format(date))) {
            holder.dotTask.setVisibility(View.VISIBLE);
        } else {
            holder.dotTask.setVisibility(View.INVISIBLE);
        }

        holder.itemView.setOnClickListener(v -> listener.onDateClick(date));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onDateLongClick(date);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return days.size();
    }

    public static class CalendarViewHolder extends RecyclerView.ViewHolder {
        public final TextView tvDayNumber;
        public final View dotTask;

        public CalendarViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayNumber = itemView.findViewById(R.id.tvDayNumber);
            dotTask = itemView.findViewById(R.id.dotTask);
        }
    }
}

package com.example.tfg;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CalendarActivity extends AppCompatActivity {

    private TextView tvMonthYear, tvSelectedDate;
    private RecyclerView rvCalendar, rvTasksDay;
    private Calendar currentMonth = Calendar.getInstance();
    private List<Task> allTasks = new ArrayList<>();
    private List<Task> filteredTasks = new ArrayList<>();
    private TaskAdapter taskAdapter;
    private FirebaseFirestore db;
    private String userId, userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();
        userEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        Toolbar toolbar = findViewById(R.id.toolbarCalendar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Aplicar color personalizado
        int savedColor = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getInt("ToolbarColor", android.graphics.Color.BLACK);
        toolbar.setBackgroundColor(savedColor);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(savedColor);
        }

        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        rvCalendar = findViewById(R.id.rvCalendar);
        rvTasksDay = findViewById(R.id.rvTasksDay);

        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        
        taskAdapter = new TaskAdapter(filteredTasks, userId, new TaskAdapter.OnTaskClickListener() {
            @Override public void onTaskClick(Task task) { 
                android.content.Intent intent = new android.content.Intent(CalendarActivity.this, MainActivity.class);
                intent.putExtra("OPEN_TASK", task);
                intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
            @Override public void onTaskLongClick(Task task) { }
            @Override public void onTaskStatusChanged(Task task, boolean isCompleted) {
                db.collection("tasks").document(task.getId()).update("completed", isCompleted);
            }
        });
        rvTasksDay.setLayoutManager(new LinearLayoutManager(this));
        rvTasksDay.setAdapter(taskAdapter);

        findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, -1);
            updateCalendar();
        });

        findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            currentMonth.add(Calendar.MONTH, 1);
            updateCalendar();
        });

        loadTasks();
    }

    private void loadTasks() {
        db.collection("tasks").whereEqualTo("userId", userId).get().addOnSuccessListener(ownerTasks -> {
            allTasks.clear();
            for (QueryDocumentSnapshot doc : ownerTasks) {
                Task t = doc.toObject(Task.class);
                t.setId(doc.getId());
                allTasks.add(t);
            }
            db.collection("tasks").whereArrayContains("sharedWith", userEmail).get().addOnSuccessListener(sharedTasks -> {
                for (QueryDocumentSnapshot doc : sharedTasks) {
                    Task t = doc.toObject(Task.class);
                    t.setId(doc.getId());
                    boolean exists = false;
                    for(Task et : allTasks) if(et.getId().equals(t.getId())) exists = true;
                    if(!exists) allTasks.add(t);
                }
                updateCalendar();
            });
        });
    }

    private void updateCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        tvMonthYear.setText(sdf.format(currentMonth.getTime()));

        List<Date> days = new ArrayList<>();
        Calendar cal = (Calendar) currentMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 2;
        if (firstDayOfWeek < 0) firstDayOfWeek += 7;
        cal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);

        while (days.size() < 42) {
            days.add(cal.getTime());
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        Set<String> taskDays = new HashSet<>();
        SimpleDateFormat daySdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (Task t : allTasks) {
            if (t.getDueDate() != null) {
                taskDays.add(daySdf.format(new Date(t.getDueDate())));
            }
        }

        CalendarAdapter adapter = new CalendarAdapter(days, currentMonth, taskDays, date -> {
            showTasksForDay(date);
        });
        rvCalendar.setAdapter(adapter);
    }

    private void showTasksForDay(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        tvSelectedDate.setText("Tareas para el " + sdf.format(date));
        
        filteredTasks.clear();
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date);
        
        for (Task t : allTasks) {
            if (t.getDueDate() != null) {
                Calendar cal2 = Calendar.getInstance();
                cal2.setTimeInMillis(t.getDueDate());
                if (cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)) {
                    filteredTasks.add(t);
                }
            }
        }
        taskAdapter.notifyDataSetChanged();
    }
}

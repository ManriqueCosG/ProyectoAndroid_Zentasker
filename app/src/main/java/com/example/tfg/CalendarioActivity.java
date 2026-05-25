package com.example.tfg;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
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

public class CalendarioActivity extends AppCompatActivity {

    private TextView tvMonthYear, tvSelectedDate;
    private TareaAdapter taskAdapter;
    private FirebaseFirestore db;
    private String userId, userEmail;
    
    private SharedPreferences prefs;
    private Calendar selectedCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private String[] categories = {"Trabajo", "Casa", "Compra", "Gimnasio", "Otro"};
    private List<Tarea> allTasks = new ArrayList<>();
    private List<Tarea> filteredTasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendario);

        db = FirebaseFirestore.getInstance();
        userId = FirebaseAuth.getInstance().getUid();
        userEmail = FirebaseAuth.getInstance().getCurrentUser() != null ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : "";

        Toolbar toolbar = findViewById(R.id.toolbarCalendar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        applyToolbarColor(toolbar);
        
        tvMonthYear = findViewById(R.id.tvMonthYear);
        tvSelectedDate = findViewById(R.id.tvSelectedDate);
        RecyclerView rvCalendar = findViewById(R.id.rvCalendar);
        RecyclerView rvTasksDay = findViewById(R.id.rvTasksDay);

        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        
        taskAdapter = new TareaAdapter(filteredTasks, userId, new TareaAdapter.OnTaskClickListener() {
            @Override public void onTaskClick(Tarea task) { 
                Intent intent = new Intent(CalendarioActivity.this, MainActivity.class);
                intent.putExtra("OPEN_TASK", task);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            }
            @Override public void onTaskLongClick(Tarea task) { }
            @Override public void onTaskStatusChanged(Tarea task, boolean isCompleted) {
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

    private Calendar currentMonth = Calendar.getInstance();

    private void loadTasks() {
        db.collection("tasks").whereEqualTo("userId", userId).get().addOnSuccessListener(ownerTasks -> {
            allTasks.clear();
            for (QueryDocumentSnapshot doc : ownerTasks) {
                Tarea t = doc.toObject(Tarea.class);
                t.setId(doc.getId());
                allTasks.add(t);
            }
            db.collection("tasks").whereArrayContains("sharedWith", userEmail).get().addOnSuccessListener(sharedTasks -> {
                for (QueryDocumentSnapshot doc : sharedTasks) {
                    Tarea t = doc.toObject(Tarea.class);
                    t.setId(doc.getId());
                    boolean exists = false;
                    for(Tarea et : allTasks) if(et.getId().equals(t.getId())) { exists = true; break; }
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
        for (Tarea t : allTasks) {
            if (t.getDueDate() != null) {
                taskDays.add(daySdf.format(new Date(t.getDueDate())));
            }
        }

        CalendarAdapter adapter = new CalendarAdapter(days, currentMonth, taskDays, new CalendarAdapter.OnDateClickListener() {
            @Override
            public void onDateClick(Date date) {
                showTasksForDay(date);
            }

            @Override
            public void onDateLongClick(Date date) {
                showAddTaskDialog(date);
            }
        });
        RecyclerView rvCalendar = findViewById(R.id.rvCalendar);
        rvCalendar.setAdapter(adapter);
    }

    private void showTasksForDay(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        tvSelectedDate.setText("Tareas para el " + sdf.format(date));
        
        filteredTasks.clear();
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date);
        
        for (Tarea t : allTasks) {
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

    private void showAddTaskDialog(Date date) {
        selectedCalendar.setTime(date);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nueva Tarea");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_anadir_tarea, null);
        final EditText input = viewInflated.findViewById(R.id.etTaskName);
        final EditText etDesc = viewInflated.findViewById(R.id.etTaskDescription);
        final EditText etDate = viewInflated.findViewById(R.id.etDueDate);
        final EditText etTime = viewInflated.findViewById(R.id.etDueTime);
        final com.google.android.material.textfield.TextInputLayout tilDueTime = viewInflated.findViewById(R.id.tilDueTime);
        final Spinner spinner = viewInflated.findViewById(R.id.spinnerCategory);
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(4); // Por defecto "Otro"

        etDate.setText(dateFormat.format(date));
        etDate.setOnClickListener(v -> showDatePicker(etDate, etTime, tilDueTime));
        etTime.setOnClickListener(v -> showTimePicker(etTime));

        builder.setView(viewInflated);

        builder.setPositiveButton("Añadir", (dialog, which) -> {
            String taskTitle = input.getText().toString().trim();
            String taskDesc = etDesc.getText().toString().trim();
            String category = spinner.getSelectedItem().toString();
            Long dueDate = selectedCalendar.getTimeInMillis();
            boolean hasTime = !etTime.getText().toString().isEmpty();
            if (!taskTitle.isEmpty()) {
                saveTaskToFirestore(taskTitle, taskDesc, category, dueDate, hasTime);
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showDatePicker(EditText etDate, EditText etTime, View tilTime) {
        new android.app.DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedCalendar.set(Calendar.YEAR, year);
            selectedCalendar.set(Calendar.MONTH, month);
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            etDate.setText(dateFormat.format(selectedCalendar.getTime()));
            if (tilTime != null) tilTime.setVisibility(View.VISIBLE);
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), 
           selectedCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(EditText etTime) {
        new android.app.TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedCalendar.set(Calendar.MINUTE, minute);
            selectedCalendar.set(Calendar.SECOND, 0);
            etTime.setText(timeFormat.format(selectedCalendar.getTime()));
        }, selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), true).show();
    }

    private void saveTaskToFirestore(String title, String description, String category, Long dueDate, boolean hasTime) {
        Tarea newTask = new Tarea(title, description, category, userId, null, dueDate);
        newTask.setUserEmail(userEmail);
        newTask.setHasTime(hasTime);

        db.collection("tasks").add(newTask)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(CalendarioActivity.this, "Tarea añadida", Toast.LENGTH_SHORT).show();
                    loadTasks();
                })
                .addOnFailureListener(e -> Toast.makeText(CalendarioActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        // Ocultar búsqueda y ordenación en el calendario si no son útiles
        menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_sort).setVisible(false);
        menu.findItem(R.id.action_calendar).setVisible(false);
        menu.findItem(R.id.action_view_info).setVisible(false);
        
        MenuItem darkModeItem = menu.findItem(R.id.action_dark_mode);
        if (darkModeItem != null) {
            darkModeItem.setChecked(prefs.getBoolean("DarkMode", false));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_dark_mode) {
            toggleDarkMode();
            return true;
        } else if (id == R.id.color_default) {
            saveToolbarColor(ContextCompat.getColor(this, R.color.black));
            return true;
        } else if (id == R.id.color_red) {
            saveToolbarColor(Color.parseColor("#D32F2F"));
            return true;
        } else if (id == R.id.color_green) {
            saveToolbarColor(Color.parseColor("#388E3C"));
            return true;
        } else if (id == R.id.color_purple) {
            saveToolbarColor(Color.parseColor("#7B1FA2"));
            return true;
        } else if (id == R.id.color_black) {
            saveToolbarColor(Color.BLACK);
            return true;
        } else if (id == R.id.action_logout) {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveToolbarColor(int color) {
        prefs.edit().putInt("ToolbarColor", color).apply();
        applyToolbarColor(findViewById(R.id.toolbarCalendar));
    }

    private void toggleDarkMode() {
        boolean isDark = !prefs.getBoolean("DarkMode", false);
        prefs.edit().putBoolean("DarkMode", isDark).apply();
        if (isDark) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void applyToolbarColor(Toolbar toolbar) {
        int color = prefs.getInt("ToolbarColor", Color.BLACK);
        toolbar.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color);
        }
    }
}

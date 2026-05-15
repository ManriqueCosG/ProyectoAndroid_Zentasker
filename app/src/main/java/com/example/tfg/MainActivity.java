package com.example.tfg;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private List<Task> currentTasks = new ArrayList<>();
    private Stack<Task> taskNavigationStack = new Stack<>();
    private TaskAdapter adapter;
    private Toolbar toolbar;
    private ImageView ivTaskInfo;
    private ProgressBar progressBar;
    
    private View dashboardCard;
    private TextView tvWelcome, tvSummary;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CollectionReference tasksRef;
    private String userId;
    private String userEmail;
    
    private Calendar selectedCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private String[] categories = {"Trabajo", "Casa", "Compra", "Gimnasio", "Otro"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        userId = user.getUid();
        userEmail = user.getEmail();
        db = FirebaseFirestore.getInstance();
        tasksRef = db.collection("tasks");

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        ivTaskInfo = findViewById(R.id.ivTaskInfo);
        ivTaskInfo.setOnClickListener(v -> showTaskInfoSheet());

        progressBar = findViewById(R.id.progressBar);
        dashboardCard = findViewById(R.id.dashboardCard);
        tvWelcome = findViewById(R.id.tvWelcome);
        tvSummary = findViewById(R.id.tvSummary);
        
        updateToolbarTitle();

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TaskAdapter(currentTasks, new TaskAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Task task) {
                onTaskClicked(task);
            }

            @Override
            public void onTaskLongClick(Task task) {
                showEditTaskDialog(task);
            }

            @Override
            public void onTaskStatusChanged(Task task, boolean isCompleted) {
                updateTaskCompletionInFirestore(task, isCompleted);
            }
        });
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(v -> showAddTaskDialog());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!taskNavigationStack.isEmpty()) {
                    taskNavigationStack.pop();
                    loadTasks();
                } else {
                    finish();
                }
            }
        });

        loadTasks();
    }

    private void onTaskClicked(Task task) {
        taskNavigationStack.push(task);
        loadTasks();
    }

    private void loadTasks() {
        progressBar.setVisibility(View.VISIBLE);
        String parentId = taskNavigationStack.isEmpty() ? null : taskNavigationStack.peek().getId();
        
        Query query = tasksRef.whereEqualTo("userId", userId)
                             .whereEqualTo("parentId", parentId);

        query.get().addOnCompleteListener(task -> {
            progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                currentTasks.clear();
                int pendingCount = 0;
                for (QueryDocumentSnapshot document : task.getResult()) {
                    Task t = document.toObject(Task.class);
                    t.setId(document.getId());
                    currentTasks.add(t);
                    if (!t.isCompleted()) pendingCount++;
                }
                
                // Mover completadas al final
                Collections.sort(currentTasks, (t1, t2) -> Boolean.compare(t1.isCompleted(), t2.isCompleted()));
                
                adapter.updateTasks(currentTasks);
                updateToolbarTitle();
                updateDashboard(pendingCount);
            } else {
                Log.e(TAG, "Error al cargar tareas: ", task.getException());
                Toast.makeText(MainActivity.this, "Error al cargar: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDashboard(int pendingCount) {
        if (taskNavigationStack.isEmpty()) {
            dashboardCard.setVisibility(View.VISIBLE);
            String name = userEmail != null ? userEmail.split("@")[0] : "Usuario";
            tvWelcome.setText("Hola, " + name);
            tvSummary.setText("Tienes " + pendingCount + " tareas pendientes");
        } else {
            dashboardCard.setVisibility(View.GONE);
        }
    }

    private void updateToolbarTitle() {
        if (getSupportActionBar() != null) {
            if (taskNavigationStack.isEmpty()) {
                getSupportActionBar().setTitle("Mis Tareas");
                getSupportActionBar().setSubtitle(userEmail);
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
                ivTaskInfo.setVisibility(View.GONE);
            } else {
                Task currentParent = taskNavigationStack.peek();
                getSupportActionBar().setTitle(currentParent.getTitle());
                getSupportActionBar().setSubtitle(null);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                ivTaskInfo.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showTaskInfoSheet() {
        if (taskNavigationStack.isEmpty()) return;
        Task task = taskNavigationStack.peek();

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_task_info, null);

        TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
        TextView tvDesc = view.findViewById(R.id.tvSheetDescription);
        TextView tvDate = view.findViewById(R.id.tvSheetDate);

        tvTitle.setText(task.getTitle());
        tvDesc.setText(task.getDescription() != null && !task.getDescription().isEmpty() 
                ? task.getDescription() : "Sin descripción");
        
        if (task.getDueDate() != null) {
            tvDate.setText("Vencimiento: " + dateFormat.format(new Date(task.getDueDate())));
            tvDate.setVisibility(View.VISIBLE);
        } else {
            tvDate.setVisibility(View.GONE);
        }

        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        if (!taskNavigationStack.isEmpty()) {
            taskNavigationStack.pop();
            loadTasks();
            return true;
        }
        return super.onSupportNavigateUp();
    }

    private void showAddTaskDialog() {
        selectedCalendar = Calendar.getInstance();
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nueva Tarea");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        final EditText input = viewInflated.findViewById(R.id.etTaskName);
        final EditText etDesc = viewInflated.findViewById(R.id.etTaskDescription);
        final EditText etDate = viewInflated.findViewById(R.id.etDueDate);
        final Spinner spinner = viewInflated.findViewById(R.id.spinnerCategory);
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(4); // Por defecto "Otro"

        etDate.setOnClickListener(v -> showDatePicker(etDate));

        builder.setView(viewInflated);

        builder.setPositiveButton("Añadir", (dialog, which) -> {
            String taskTitle = input.getText().toString().trim();
            String taskDesc = etDesc.getText().toString().trim();
            String category = spinner.getSelectedItem().toString();
            Long dueDate = etDate.getText().toString().isEmpty() ? null : selectedCalendar.getTimeInMillis();
            if (!taskTitle.isEmpty()) {
                saveTaskToFirestore(taskTitle, taskDesc, category, dueDate);
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditTaskDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Tarea");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_add_task, null);
        final EditText input = viewInflated.findViewById(R.id.etTaskName);
        final EditText etDesc = viewInflated.findViewById(R.id.etTaskDescription);
        final EditText etDate = viewInflated.findViewById(R.id.etDueDate);
        final Spinner spinner = viewInflated.findViewById(R.id.spinnerCategory);
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        input.setText(task.getTitle());
        etDesc.setText(task.getDescription());
        
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(task.getCategory())) {
                spinner.setSelection(i);
                break;
            }
        }

        if (task.getDueDate() != null) {
            selectedCalendar.setTimeInMillis(task.getDueDate());
            etDate.setText(dateFormat.format(new Date(task.getDueDate())));
        }

        etDate.setOnClickListener(v -> showDatePicker(etDate));

        builder.setView(viewInflated);

        builder.setPositiveButton("Guardar", (dialog, which) -> {
            String taskTitle = input.getText().toString().trim();
            String taskDesc = etDesc.getText().toString().trim();
            String category = spinner.getSelectedItem().toString();
            Long dueDate = etDate.getText().toString().isEmpty() ? null : selectedCalendar.getTimeInMillis();
            if (!taskTitle.isEmpty()) {
                updateTaskInFirestore(task.getId(), taskTitle, taskDesc, category, dueDate);
            }
        });
        
        builder.setNeutralButton("Eliminar", (dialog, which) -> {
            deleteTaskFromFirestore(task.getId());
        });
        
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showDatePicker(EditText etDate) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedCalendar.set(Calendar.YEAR, year);
            selectedCalendar.set(Calendar.MONTH, month);
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            etDate.setText(dateFormat.format(selectedCalendar.getTime()));
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), 
           selectedCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void saveTaskToFirestore(String title, String description, String category, Long dueDate) {
        String parentId = taskNavigationStack.isEmpty() ? null : taskNavigationStack.peek().getId();
        Task newTask = new Task(title, description, category, userId, parentId, dueDate);

        tasksRef.add(newTask)
                .addOnSuccessListener(documentReference -> {
                    if (parentId != null) {
                        updateParentProgress(parentId, 1, 0);
                    }
                    loadTasks();
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void updateTaskInFirestore(String taskId, String title, String description, String category, Long dueDate) {
        tasksRef.document(taskId)
                .update("title", title, "description", description, "category", category, "dueDate", dueDate)
                .addOnSuccessListener(aVoid -> {
                    if (!taskNavigationStack.isEmpty() && taskNavigationStack.peek().getId().equals(taskId)) {
                        taskNavigationStack.peek().setTitle(title);
                        taskNavigationStack.peek().setDescription(description);
                        taskNavigationStack.peek().setCategory(category);
                        taskNavigationStack.peek().setDueDate(dueDate);
                    }
                    loadTasks();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show());
    }

    private void updateTaskCompletionInFirestore(Task task, boolean isCompleted) {
        tasksRef.document(task.getId())
                .update("completed", isCompleted)
                .addOnSuccessListener(aVoid -> {
                    task.setCompleted(isCompleted);
                    if (task.getParentId() != null) {
                        updateParentProgress(task.getParentId(), 0, isCompleted ? 1 : -1);
                    }
                    loadTasks();
                });
    }

    private void updateParentProgress(String parentId, int totalChange, int completedChange) {
        db.runTransaction(transaction -> {
            DocumentReference parentRef = tasksRef.document(parentId);
            Task parent = transaction.get(parentRef).toObject(Task.class);
            if (parent != null) {
                int newTotal = parent.getTotalSubtasks() + totalChange;
                int newCompleted = parent.getCompletedSubtasks() + completedChange;
                
                boolean isCompleted = (newTotal > 0 && newCompleted == newTotal);
                
                transaction.update(parentRef, 
                    "totalSubtasks", newTotal,
                    "completedSubtasks", newCompleted,
                    "completed", isCompleted
                );
            }
            return null;
        });
    }

    private void deleteTaskFromFirestore(String taskId) {
        progressBar.setVisibility(View.VISIBLE);
        tasksRef.document(taskId).get().addOnSuccessListener(documentSnapshot -> {
            Task task = documentSnapshot.toObject(Task.class);
            if (task != null && task.getParentId() != null) {
                updateParentProgress(task.getParentId(), -1, task.isCompleted() ? -1 : 0);
            }
            deleteTaskRecursively(taskId);
        });
    }

    private void deleteTaskRecursively(String taskId) {
        tasksRef.whereEqualTo("parentId", taskId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                WriteBatch batch = db.batch();
                batch.delete(tasksRef.document(taskId));
                for (QueryDocumentSnapshot document : task.getResult()) {
                    batch.delete(document.getReference());
                }
                
                batch.commit().addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    loadTasks();
                }).addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Error al eliminar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            } else {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Error al buscar subtareas", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint("Buscar tareas...");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.getFilter().filter(newText);
                return false;
            }
        });
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_logout) {
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return true;
        } else if (id == R.id.sort_date) {
            Collections.sort(currentTasks, (t1, t2) -> {
                if (t1.getDueDate() == null) return 1;
                if (t2.getDueDate() == null) return -1;
                return t1.getDueDate().compareTo(t2.getDueDate());
            });
            adapter.notifyDataSetChanged();
            return true;
        } else if (id == R.id.sort_alpha) {
            Collections.sort(currentTasks, (t1, t2) -> t1.getTitle().compareToIgnoreCase(t2.getTitle()));
            adapter.notifyDataSetChanged();
            return true;
        } else if (id == R.id.sort_category) {
            Collections.sort(currentTasks, (t1, t2) -> t1.getCategory().compareTo(t2.getCategory()));
            adapter.notifyDataSetChanged();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

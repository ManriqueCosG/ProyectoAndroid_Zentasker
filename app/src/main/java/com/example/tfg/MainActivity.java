package com.example.tfg;

import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
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
    private List<Tarea> currentTasks = new ArrayList<>();
    private Stack<Tarea> taskNavigationStack = new Stack<>();
    private TareaAdapter adapter;
    private Toolbar toolbar;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    
    private View dashboardCard;
    private TextView tvWelcome, tvSummary;

    private View parentProgressContainer;
    private ProgressBar parentProgressBar;
    private TextView tvParentProgressPercent;
    
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private CollectionReference tasksRef;
    private String userId;
    private String userEmail;
    private ListenerRegistration notificationListener;
    
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_DARK_MODE = "DarkMode";
    private static final String KEY_TOOLBAR_COLOR = "ToolbarColor";

    private Calendar selectedCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private String[] categories = {"Trabajo", "Casa", "Compra", "Gimnasio", "Otro"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

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
        applyToolbarColor();
        
        progressBar = findViewById(R.id.progressBar);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        dashboardCard = findViewById(R.id.dashboardCard);
        tvWelcome = findViewById(R.id.tvWelcome);
        tvSummary = findViewById(R.id.tvSummary);

        parentProgressContainer = findViewById(R.id.parentProgressContainer);
        parentProgressBar = findViewById(R.id.parentProgressBar);
        tvParentProgressPercent = findViewById(R.id.tvParentProgressPercent);
        
        updateToolbarTitle();

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new TareaAdapter(currentTasks, userId, new TareaAdapter.OnTaskClickListener() {
            @Override
            public void onTaskClick(Tarea task) {
                onTaskClicked(task);
            }

            @Override
            public void onTaskLongClick(Tarea task) {
                showEditTaskDialog(task);
            }

            @Override
            public void onTaskStatusChanged(Tarea task, boolean isCompleted) {
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

        swipeRefreshLayout.setOnRefreshListener(this::loadTasks);
        swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.black));

        checkNotificationPermission();
        setupCompletionListener();
        loadTasks();
    }

    private void setupCompletionListener() {
        // Escuchar tareas propias que han sido completadas por otros
        // Nota: Solo nos interesan los cambios ocurridos DESPUÉS de abrir la app
        notificationListener = tasksRef.whereEqualTo("userId", userId)
                .whereEqualTo("completed", true)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    
                    // Si es la carga inicial (todos los documentos vienen como ADDED), la ignoramos
                    if (snapshots.getMetadata().isFromCache()) return;

                    for (com.google.firebase.firestore.DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == com.google.firebase.firestore.DocumentChange.Type.ADDED || 
                            dc.getType() == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            
                            Tarea task = dc.getDocument().toObject(Tarea.class);
                            task.setId(dc.getDocument().getId()); // Corregido: Asignar ID manualmente
                            
                            String lastModifiedBy = dc.getDocument().getString("lastModifiedBy");

                            if (task.isCompleted() && lastModifiedBy != null && !lastModifiedBy.equals(userId)) {
                                sendTaskCompletionNotification(task);
                            }
                        }
                    }
                });
    }

    private void sendTaskCompletionNotification(Tarea task) {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "completion_notifications";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    channelId, "Tareas Completadas", android.app.NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, 0, 
                new Intent(this, MainActivity.class), android.app.PendingIntent.FLAG_IMMUTABLE);

        String editorName = task.getLastModifiedByEmail() != null ? task.getLastModifiedByEmail() : "Un colaborador";

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("¡Tarea terminada!")
                .setContentText(editorName + " ha terminado: " + task.getTitle())
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(task.getId().hashCode(), builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void applyToolbarColor() {
        int color = prefs.getInt(KEY_TOOLBAR_COLOR, ContextCompat.getColor(this, R.color.black));
        toolbar.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // Importante para que onResume vea los nuevos datos
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Manejar navegación desde el calendario
        Tarea openTask = (Tarea) getIntent().getSerializableExtra("OPEN_TASK");
        if (openTask != null) {
            getIntent().removeExtra("OPEN_TASK");
            taskNavigationStack.clear();
            taskNavigationStack.push(openTask);
        }

        loadTasks();
    }

    private void onTaskClicked(Tarea task) {
        if (taskNavigationStack.isEmpty()) {
            taskNavigationStack.push(task);
            loadTasks();
        } else {
            // Todos los usuarios pueden ver y editar las notas de las subtareas
            Intent intent = new Intent(this, TareaEditorActivity.class);
            intent.putExtra("TASK_ID", task.getId());
            intent.putExtra("TASK_TITLE", task.getTitle());
            intent.putExtra("TASK_DESC", task.getDescription());
            startActivity(intent);
        }
    }

    private void loadTasks() {
        if (!swipeRefreshLayout.isRefreshing()) {
            progressBar.setVisibility(View.VISIBLE);
        }
        String parentId = taskNavigationStack.isEmpty() ? null : taskNavigationStack.peek().getId();
        
        if (parentId == null) {
            // Cargar tareas raíz (propias + compartidas)
            tasksRef.whereEqualTo("userId", userId)
                    .whereEqualTo("parentId", null)
                    .get().addOnCompleteListener(ownerTask -> {
                if (ownerTask.isSuccessful()) {
                    List<Tarea> combinedTasks = new ArrayList<>();
                    for (QueryDocumentSnapshot document : ownerTask.getResult()) {
                        Tarea t = document.toObject(Tarea.class);
                        t.setId(document.getId());
                        combinedTasks.add(t);
                    }

                    tasksRef.whereArrayContains("sharedWith", userEmail)
                            .whereEqualTo("parentId", null)
                            .get().addOnCompleteListener(sharedTask -> {
                                progressBar.setVisibility(View.GONE);
                                if (sharedTask.isSuccessful()) {
                                    for (QueryDocumentSnapshot document : sharedTask.getResult()) {
                                        Tarea t = document.toObject(Tarea.class);
                                        t.setId(document.getId());
                                        boolean exists = false;
                                        for(Tarea existing : combinedTasks) {
                                            if(existing.getId().equals(t.getId())) { exists = true; break; }
                                        }
                                        if(!exists) combinedTasks.add(t);
                                    }
                                    finishLoadingTasks(combinedTasks);
                                } else {
                                    finishLoadingTasks(combinedTasks);
                                }
                            });
                } else {
                    progressBar.setVisibility(View.GONE);
                }
            });
        } else {
            // Cargar subtareas: si ya estamos aquí es porque tenemos acceso al padre
            tasksRef.whereEqualTo("parentId", parentId)
                    .get().addOnCompleteListener(task -> {
                        progressBar.setVisibility(View.GONE);
                        if (task.isSuccessful()) {
                            List<Tarea> subtasks = new ArrayList<>();
                            for (QueryDocumentSnapshot document : task.getResult()) {
                                Tarea t = document.toObject(Tarea.class);
                                t.setId(document.getId());
                                // Aseguramos que la descripción viaje
                                subtasks.add(t);
                            }
                            finishLoadingTasks(subtasks);
                        }
                    });
        }
    }

    private void finishLoadingTasks(List<Tarea> tasks) {
        swipeRefreshLayout.setRefreshing(false);
        currentTasks.clear();
        currentTasks.addAll(tasks);
        
        int pendingCount = 0;
        for (Tarea t : currentTasks) {
            if (!t.isCompleted()) pendingCount++;
        }
        
        Collections.sort(currentTasks, (t1, t2) -> Boolean.compare(t1.isCompleted(), t2.isCompleted()));
        
        adapter.updateTasks(currentTasks);
        updateToolbarTitle();
        updateDashboard(pendingCount);
        scheduleTaskAlarms(currentTasks);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        if (!taskNavigationStack.isEmpty()) {
            Tarea parent = taskNavigationStack.peek();
            if (!parent.getUserId().equals(userId)) {
                fab.setVisibility(View.GONE);
            } else {
                fab.setVisibility(View.VISIBLE);
            }
            updateParentProgressUI(currentTasks);
        } else {
            fab.setVisibility(View.VISIBLE);
            parentProgressContainer.setVisibility(View.GONE);
        }
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

    private void updateParentProgressUI(List<Tarea> subtasks) {
        if (subtasks.isEmpty()) {
            parentProgressContainer.setVisibility(View.GONE);
            return;
        }

        parentProgressContainer.setVisibility(View.VISIBLE);
        int total = subtasks.size();
        int completed = 0;
        for (Tarea t : subtasks) {
            if (t.isCompleted()) completed++;
        }

        int progress = (total > 0) ? (completed * 100) / total : 0;
        parentProgressBar.setProgress(progress);
        tvParentProgressPercent.setText(progress + "%");

        if (progress == 100) {
            parentProgressBar.setProgressTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.status_green)));
        } else {
            parentProgressBar.setProgressTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(this, R.color.black)));
        }
    }

    private void updateToolbarTitle() {
        if (getSupportActionBar() != null) {
            applyToolbarColor();
            if (taskNavigationStack.isEmpty()) {
                getSupportActionBar().setTitle("Mis Tareas");
                getSupportActionBar().setSubtitle(userEmail);
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            } else {
                Tarea currentParent = taskNavigationStack.peek();
                getSupportActionBar().setTitle(currentParent.getTitle());
                getSupportActionBar().setSubtitle(null);
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            invalidateOptionsMenu(); // Actualizar visibilidad de "Ver Información"
        }
    }

    private void showCollaboratorsDialog() {
        if (taskNavigationStack.isEmpty()) return;
        Tarea task = taskNavigationStack.peek();
        
        StringBuilder collaborators = new StringBuilder();
        collaborators.append("Propietario: ").append(task.getUserEmail() != null ? task.getUserEmail() : "Desconocido").append("\n\n");
        
        if (task.getSharedWith() != null && !task.getSharedWith().isEmpty()) {
            collaborators.append("Colaboradores:\n");
            for (String email : task.getSharedWith()) {
                collaborators.append("- ").append(email).append("\n");
            }
        } else {
            collaborators.append("No hay colaboradores adicionales.");
        }

        new AlertDialog.Builder(this)
                .setTitle("Lista de Colaboradores")
                .setMessage(collaborators.toString())
                .setPositiveButton("Cerrar", null)
                .show();
    }

    private void showTaskInfoSheet() {
        if (taskNavigationStack.isEmpty()) return;
        Tarea task = taskNavigationStack.peek();

        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_info_tarea, null);

        TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
        TextView tvDesc = view.findViewById(R.id.tvSheetDescription);
        TextView tvDate = view.findViewById(R.id.tvSheetDate);

        tvTitle.setText(task.getTitle());
        
        android.text.SpannableStringBuilder infoCompleta = new android.text.SpannableStringBuilder();
        
        // 1. Añadir la descripción del diálogo si existe
        if (task.getDescription() != null && !task.getDescription().isEmpty()) {
            infoCompleta.append(task.getDescription());
        }
        
        // 2. Añadir las notas del editor debajo si existen
        if (task.getNotas() != null && !task.getNotas().isEmpty()) {
            if (infoCompleta.length() > 0) infoCompleta.append("\n\n---\nDetalles adicionales:\n\n");
            infoCompleta.append(android.text.Html.fromHtml(task.getNotas(), android.text.Html.FROM_HTML_MODE_LEGACY));
        }
        
        if (infoCompleta.length() > 0) {
            tvDesc.setText(infoCompleta);
        } else {
            tvDesc.setText("Sin descripción");
        }

        // 3. Mostrar colaboradores (Dueño + compartidos)
        StringBuilder collabInfo = new StringBuilder();
        collabInfo.append("Propietario: ").append(task.getUserEmail() != null ? task.getUserEmail() : "Desconocido");
        if (task.getSharedWith() != null && !task.getSharedWith().isEmpty()) {
            collabInfo.append("\nCompartida con:");
            for (String email : task.getSharedWith()) {
                collabInfo.append("\n - ").append(email);
            }
        }
        
        // Podemos usar el TextView de fecha o añadir uno nuevo en el layout.
        // Por simplicidad, lo añadiremos a la descripción si no hay fecha.
        if (task.getDueDate() != null) {
            tvDate.setText("Vencimiento: " + dateFormat.format(new Date(task.getDueDate())) + "\n\n" + collabInfo.toString());
            tvDate.setVisibility(View.VISIBLE);
        } else {
            tvDate.setText(collabInfo.toString());
            tvDate.setVisibility(View.VISIBLE);
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

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_anadir_tarea, null);
        final EditText input = viewInflated.findViewById(R.id.etTaskName);
        final EditText etDesc = viewInflated.findViewById(R.id.etTaskDescription);
        final EditText etDate = viewInflated.findViewById(R.id.etDueDate);
        final EditText etTime = viewInflated.findViewById(R.id.etDueTime);
        final com.google.android.material.textfield.TextInputLayout tilDueTime = viewInflated.findViewById(R.id.tilDueTime);
        final Spinner spinner = viewInflated.findViewById(R.id.spinnerCategory);
        final Spinner spinnerAssign = viewInflated.findViewById(R.id.spinnerAssign);
        final TextView tvAssignLabel = viewInflated.findViewById(R.id.tvAssignLabel);
        
        // Configurar spinner de asignación si hay colaboradores
        List<String> collaborators = new ArrayList<>();
        collaborators.add("Sin asignar");
        if (!taskNavigationStack.isEmpty()) {
            Tarea parent = taskNavigationStack.peek();
            if (parent.getSharedWith() != null && !parent.getSharedWith().isEmpty()) {
                collaborators.addAll(parent.getSharedWith());
                // También añadir al dueño si el actual no es el dueño
                if (parent.getUserEmail() != null && !collaborators.contains(parent.getUserEmail())) {
                    collaborators.add(parent.getUserEmail());
                }
                
                ArrayAdapter<String> assignAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, collaborators);
                assignAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerAssign.setAdapter(assignAdapter);
                spinnerAssign.setVisibility(View.VISIBLE);
                tvAssignLabel.setVisibility(View.VISIBLE);
            }
        }
        
        // Forzar limpieza de campos por seguridad
        input.setText("");
        etDesc.setText("");
        etDate.setText("");
        etTime.setText("");
        tilDueTime.setVisibility(View.GONE);
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(4); // Por defecto "Otro"

        etDate.setOnClickListener(v -> showDatePicker(etDate, etTime, tilDueTime));
        etTime.setOnClickListener(v -> showTimePicker(etTime));

        builder.setView(viewInflated);

        builder.setPositiveButton("Añadir", (dialog, which) -> {
            String taskTitle = input.getText().toString().trim();
            String taskDesc = etDesc.getText().toString().trim();
            String category = spinner.getSelectedItem().toString();
            String assignedTo = null;
            if (spinnerAssign.getVisibility() == View.VISIBLE && spinnerAssign.getSelectedItemPosition() > 0) {
                assignedTo = (String) spinnerAssign.getSelectedItem();
            }
            Long dueDate = etDate.getText().toString().isEmpty() ? null : selectedCalendar.getTimeInMillis();
            boolean hasTime = !etTime.getText().toString().isEmpty();
            if (!taskTitle.isEmpty()) {
                saveTaskToFirestore(taskTitle, taskDesc, category, dueDate, assignedTo, hasTime);
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditTaskDialog(Tarea task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Tarea");

        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_anadir_tarea, null);
        final EditText input = viewInflated.findViewById(R.id.etTaskName);
        final EditText etDesc = viewInflated.findViewById(R.id.etTaskDescription);
        final EditText etDate = viewInflated.findViewById(R.id.etDueDate);
        final EditText etTime = viewInflated.findViewById(R.id.etDueTime);
        final com.google.android.material.textfield.TextInputLayout tilDueTime = viewInflated.findViewById(R.id.tilDueTime);
        final Spinner spinner = viewInflated.findViewById(R.id.spinnerCategory);
        final Spinner spinnerAssign = viewInflated.findViewById(R.id.spinnerAssign);
        final TextView tvAssignLabel = viewInflated.findViewById(R.id.tvAssignLabel);
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerAdapter);

        // Configurar spinner de asignación si hay colaboradores (en la tarea actual o padre)
        List<String> collaborators = new ArrayList<>();
        collaborators.add("Sin asignar");
        
        Tarea taskContext = taskNavigationStack.isEmpty() ? task : taskNavigationStack.peek();
        boolean isOwner = task.getUserId().equals(userId);

        if (isOwner && taskContext.getSharedWith() != null && !taskContext.getSharedWith().isEmpty()) {
            collaborators.addAll(taskContext.getSharedWith());
            if (taskContext.getUserEmail() != null && !collaborators.contains(taskContext.getUserEmail())) {
                collaborators.add(taskContext.getUserEmail());
            }
            
            ArrayAdapter<String> assignAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, collaborators);
            assignAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerAssign.setAdapter(assignAdapter);
            spinnerAssign.setVisibility(View.VISIBLE);
            tvAssignLabel.setVisibility(View.VISIBLE);
            
            if (task.getAssignedTo() != null) {
                int pos = collaborators.indexOf(task.getAssignedTo());
                if (pos >= 0) spinnerAssign.setSelection(pos);
            }
        } else if (task.getAssignedTo() != null) {
            // Mostrar a quién está asignada pero sin permitir cambiarlo si no eres el dueño
            collaborators.add(task.getAssignedTo());
            ArrayAdapter<String> assignAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, collaborators);
            spinnerAssign.setAdapter(assignAdapter);
            spinnerAssign.setEnabled(false);
            spinnerAssign.setVisibility(View.VISIBLE);
            tvAssignLabel.setVisibility(View.VISIBLE);
        }

        input.setText(task.getTitle());
        etDesc.setText(task.getDescription());
        
        if (!isOwner) {
            input.setEnabled(false);
            etDesc.setEnabled(false);
            etDate.setEnabled(false);
            etTime.setEnabled(false);
            spinner.setEnabled(false);
            builder.setTitle("Detalles de Tarea (Solo Lectura)");
        }

        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equals(task.getCategory())) {
                spinner.setSelection(i);
                break;
            }
        }

        if (task.getDueDate() != null) {
            selectedCalendar.setTimeInMillis(task.getDueDate());
            etDate.setText(dateFormat.format(new Date(task.getDueDate())));
            if (task.isHasTime()) {
                etTime.setText(timeFormat.format(new Date(task.getDueDate())));
                tilDueTime.setVisibility(View.VISIBLE);
            } else {
                etTime.setText("");
                tilDueTime.setVisibility(View.GONE);
            }
        } else {
            etDate.setText("");
            etTime.setText("");
            tilDueTime.setVisibility(View.GONE);
        }

        etDate.setOnClickListener(v -> showDatePicker(etDate, etTime, tilDueTime));
        etTime.setOnClickListener(v -> showTimePicker(etTime));

        builder.setView(viewInflated);

        if (isOwner) {
            builder.setPositiveButton("Guardar", (dialog, which) -> {
                String taskTitle = input.getText().toString().trim();
                String taskDesc = etDesc.getText().toString().trim();
                String category = spinner.getSelectedItem().toString();
                String assignedTo = null;
                if (spinnerAssign.getVisibility() == View.VISIBLE && spinnerAssign.getSelectedItemPosition() > 0) {
                    assignedTo = (String) spinnerAssign.getSelectedItem();
                }
                Long dueDate = etDate.getText().toString().isEmpty() ? null : selectedCalendar.getTimeInMillis();
                boolean hasTime = !etTime.getText().toString().isEmpty();
                if (!taskTitle.isEmpty()) {
                    updateTaskInFirestore(task.getId(), taskTitle, taskDesc, category, dueDate, assignedTo, hasTime);
                }
            });

            builder.setNeutralButton("Opciones", (dialog, which) -> {
                showTaskOptions(task);
            });
        }
        
        builder.setNegativeButton(isOwner ? "Cancelar" : "Cerrar", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showTaskOptions(Tarea task) {
        String[] options = {"Compartir", "Eliminar"};
        new AlertDialog.Builder(this)
                .setTitle("Opciones de Tarea")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showShareDialog(task);
                    } else if (which == 1) {
                        deleteTaskFromFirestore(task.getId());
                    }
                })
                .show();
    }

    private void showShareDialog(Tarea task) {
        EditText etEmail = new EditText(this);
        etEmail.setHint("Correo electrónico del usuario");
        etEmail.setPadding(60, 40, 60, 40);

        new AlertDialog.Builder(this)
                .setTitle("Compartir Tarea")
                .setMessage("Introduce el correo del usuario con el que quieres compartir esta tarea:")
                .setView(etEmail)
                .setPositiveButton("Compartir", (dialog, which) -> {
                    String email = etEmail.getText().toString().trim();
                    if (!email.isEmpty() && email.contains("@")) {
                        shareTaskWithUser(task, email);
                    } else {
                        Toast.makeText(this, "Introduce un correo válido", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void shareTaskWithUser(Tarea task, String email) {
        WriteBatch batch = db.batch();
        DocumentReference taskRef = tasksRef.document(task.getId());
        batch.update(taskRef, "sharedWith", FieldValue.arrayUnion(email));

        // Propagar a subtareas
        tasksRef.whereEqualTo("parentId", task.getId()).get().addOnSuccessListener(querySnapshot -> {
            for (QueryDocumentSnapshot doc : querySnapshot) {
                batch.update(doc.getReference(), "sharedWith", FieldValue.arrayUnion(email));
            }
            batch.commit().addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Tarea y subtareas compartidas con " + email, Toast.LENGTH_SHORT).show();
                loadTasks();
            });
        });
    }

    private void showDatePicker(EditText etDate, EditText etTime, View tilTime) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedCalendar.set(Calendar.YEAR, year);
            selectedCalendar.set(Calendar.MONTH, month);
            selectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            etDate.setText(dateFormat.format(selectedCalendar.getTime()));
            if (tilTime != null) tilTime.setVisibility(View.VISIBLE);
        }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH), 
           selectedCalendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(EditText etTime) {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            selectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedCalendar.set(Calendar.MINUTE, minute);
            selectedCalendar.set(Calendar.SECOND, 0);
            etTime.setText(timeFormat.format(selectedCalendar.getTime()));
        }, selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), true).show();
    }

    private void saveTaskToFirestore(String title, String description, String category, Long dueDate, String assignedTo, boolean hasTime) {
        String parentId = taskNavigationStack.isEmpty() ? null : taskNavigationStack.peek().getId();
        List<String> sharedWith = taskNavigationStack.isEmpty() ? new ArrayList<>() : taskNavigationStack.peek().getSharedWith();
        
        Tarea newTask = new Tarea(title, description, category, userId, parentId, dueDate);
        newTask.setUserEmail(userEmail);
        newTask.setSharedWith(sharedWith); // Heredar compartidos del padre
        newTask.setAssignedTo(assignedTo);
        newTask.setHasTime(hasTime);

        tasksRef.add(newTask)
                .addOnSuccessListener(documentReference -> {
                    if (parentId != null) {
                        updateParentProgress(parentId, 1, 0);
                    }
                    loadTasks();
                })
                .addOnFailureListener(e -> Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void updateTaskInFirestore(String taskId, String title, String description, String category, Long dueDate, String assignedTo, boolean hasTime) {
        tasksRef.document(taskId)
                .update("title", title, 
                        "description", description, 
                        "category", category, 
                        "dueDate", dueDate,
                        "assignedTo", assignedTo,
                        "hasTime", hasTime)
                .addOnSuccessListener(aVoid -> {
                    // Actualizar en memoria si es el padre actual
                    if (!taskNavigationStack.isEmpty() && taskNavigationStack.peek().getId().equals(taskId)) {
                        taskNavigationStack.peek().setTitle(title);
                        taskNavigationStack.peek().setDescription(description);
                        taskNavigationStack.peek().setCategory(category);
                        taskNavigationStack.peek().setDueDate(dueDate);
                        taskNavigationStack.peek().setAssignedTo(assignedTo);
                        taskNavigationStack.peek().setHasTime(hasTime);
                    }
                    loadTasks();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error al actualizar", Toast.LENGTH_SHORT).show());
    }

    private void updateTaskCompletionInFirestore(Tarea task, boolean isCompleted) {
        tasksRef.document(task.getId())
                .update("completed", isCompleted, 
                        "lastModifiedBy", userId,
                        "lastModifiedByEmail", userEmail)
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
            Tarea parent = transaction.get(parentRef).toObject(Tarea.class);
            if (parent != null) {
                int newTotal = parent.getTotalSubtasks() + totalChange;
                int newCompleted = parent.getCompletedSubtasks() + completedChange;
                
                boolean isCompleted = (newTotal > 0 && newCompleted == newTotal);
                
                transaction.update(parentRef, 
                    "totalSubtasks", newTotal,
                    "completedSubtasks", newCompleted,
                    "completed", isCompleted,
                    "lastModifiedBy", userId,
                    "lastModifiedByEmail", userEmail
                );
            }
            return null;
        });
    }

    private void deleteTaskFromFirestore(String taskId) {
        progressBar.setVisibility(View.VISIBLE);
        tasksRef.document(taskId).get().addOnSuccessListener(documentSnapshot -> {
            Tarea task = documentSnapshot.toObject(Tarea.class);
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

    private void scheduleTaskAlarms(List<Tarea> tasks) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        
        long now = System.currentTimeMillis();

        for (Tarea task : tasks) {
            if (task.getId() == null || task.isCompleted() || task.getDueDate() == null) continue;
            
            long due = task.getDueDate();

            // 1. Alarma en el momento exacto
            if (due > now) {
                scheduleAlarm(alarmManager, task, due, RecordatorioReceiver.TYPE_EXACT);
            } else {
                // Tarea vencida: Notificar una vez si es la primera vez que la vemos
                // (Para simplificar, notificamos si está pendiente y vencida)
                // triggerImmediateNotification(task, RecordatorioReceiver.TYPE_EXPIRED);
            }

            // 2. Alarma "Queda poco" (30 min antes) - Solo si tiene hora especificada
            if (task.isHasTime()) {
                long soon = due - (30 * 60 * 1000); // 30 minutos antes
                if (soon > now) {
                    scheduleAlarm(alarmManager, task, soon, RecordatorioReceiver.TYPE_SOON);
                }
            }
        }
    }

    private void scheduleAlarm(AlarmManager am, Tarea task, long time, int type) {
        Intent intent = new Intent(this, RecordatorioReceiver.class);
        intent.putExtra("TASK_TITLE", task.getTitle());
        intent.putExtra("TASK_ID", task.getId());
        intent.putExtra(RecordatorioReceiver.EXTRA_TYPE, type);
        
        PendingIntent pi = PendingIntent.getBroadcast(
                this, 
                (task.getId() + type).hashCode(), 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, time, pi);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        MenuItem darkModeItem = menu.findItem(R.id.action_dark_mode);
        if (darkModeItem != null) {
            darkModeItem.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        }

        MenuItem infoItem = menu.findItem(R.id.action_view_info);
        if (infoItem != null) {
            infoItem.setVisible(!taskNavigationStack.isEmpty());
        }

        MenuItem colabItem = menu.findItem(R.id.action_colaboradores);
        if (colabItem != null) {
            colabItem.setVisible(!taskNavigationStack.isEmpty());
        }

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
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
            }
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_calendar) {
            startActivity(new Intent(this, CalendarioActivity.class));
            return true;
        } else if (id == R.id.action_view_info) {
            showTaskInfoSheet();
            return true;
        } else if (id == R.id.action_colaboradores) {
            showCollaboratorsDialog();
            return true;
        } else if (id == R.id.action_logout) {
            mAuth.signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return true;
        } else if (id == R.id.action_dark_mode) {
            boolean isDark = !item.isChecked();
            item.setChecked(isDark);
            prefs.edit().putBoolean(KEY_DARK_MODE, isDark).apply();
            if (isDark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
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

    private void saveToolbarColor(int color) {
        prefs.edit().putInt(KEY_TOOLBAR_COLOR, color).apply();
        applyToolbarColor();
    }
}

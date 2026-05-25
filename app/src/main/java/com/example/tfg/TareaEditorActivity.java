package com.example.tfg;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class TareaEditorActivity extends AppCompatActivity {

    private EditText etNote;
    private String taskId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tarea_editor);

        db = FirebaseFirestore.getInstance();
        etNote = findViewById(R.id.etNote);
        Toolbar toolbar = findViewById(R.id.toolbarEditor);
        
        taskId = getIntent().getStringExtra("TASK_ID");
        String title = getIntent().getStringExtra("TASK_TITLE");

        toolbar.setTitle(title != null ? title : "Nota de Subtarea");
        setSupportActionBar(toolbar);
        
        applyToolbarColor(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> saveAndExit());

        // Cargar datos en tiempo real desde Firestore
        loadTaskContent();

        findViewById(R.id.btnBold).setOnClickListener(v -> applyStyle(new StyleSpan(Typeface.BOLD)));
        findViewById(R.id.btnList).setOnClickListener(v -> insertBullet());
        
        findViewById(R.id.btnColorBlack).setOnClickListener(v -> applyColor(ContextCompat.getColor(this, R.color.black)));
        findViewById(R.id.btnColorRed).setOnClickListener(v -> applyColor(ContextCompat.getColor(this, R.color.status_red)));
        findViewById(R.id.btnColorGreen).setOnClickListener(v -> applyColor(ContextCompat.getColor(this, R.color.status_green)));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                saveAndExit();
            }
        });
    }

    private void applyStyle(Object style) {
        int start = etNote.getSelectionStart();
        int end = etNote.getSelectionEnd();
        if (start != end) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(etNote.getText());
            ssb.setSpan(style, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            etNote.setText(ssb);
            etNote.setSelection(end);
        }
    }

    private void applyColor(int color) {
        applyStyle(new ForegroundColorSpan(color));
    }

    private void insertBullet() {
        int selection = etNote.getSelectionStart();
        etNote.getText().insert(selection, "\n- ");
    }

    private void loadTaskContent() {
        db.collection("tasks").document(taskId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) return;
                    
                    String notasHtml = snapshot.getString("notas");
                    if (notasHtml != null && !notasHtml.isEmpty()) {
                        if (!etNote.hasFocus()) {
                            etNote.setText(Html.fromHtml(notasHtml, Html.FROM_HTML_MODE_LEGACY));
                        }
                    }
                });
    }

    private void saveAndExit() {
        String htmlContent = Html.toHtml(etNote.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        db.collection("tasks").document(taskId)
                .update("notas", htmlContent)
                .addOnSuccessListener(aVoid -> finish())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.action_search).setVisible(false);
        menu.findItem(R.id.action_sort).setVisible(false);
        menu.findItem(R.id.action_calendar).setVisible(false);
        menu.findItem(R.id.action_view_info).setVisible(true);
        menu.findItem(R.id.action_colaboradores).setVisible(true);
        
        MenuItem darkModeItem = menu.findItem(R.id.action_dark_mode);
        if (darkModeItem != null) {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            darkModeItem.setChecked(prefs.getBoolean("DarkMode", false));
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_view_info) {
            showTaskInfoSheet();
            return true;
        } else if (id == R.id.action_colaboradores) {
            showCollaboratorsDialog();
            return true;
        } else if (id == R.id.action_dark_mode) {
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
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putInt("ToolbarColor", color).apply();
        applyToolbarColor(findViewById(R.id.toolbarEditor));
    }

    private void toggleDarkMode() {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isDark = !prefs.getBoolean("DarkMode", false);
        prefs.edit().putBoolean("DarkMode", isDark).apply();
        if (isDark) AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        else AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
    }

    private void showCollaboratorsDialog() {
        db.collection("tasks").document(taskId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Tarea task = snapshot.toObject(Tarea.class);
                if (task != null) {
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
            }
        });
    }

    private void showTaskInfoSheet() {
        db.collection("tasks").document(taskId).get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Tarea task = snapshot.toObject(Tarea.class);
                if (task != null) {
                    com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
                    View view = getLayoutInflater().inflate(R.layout.layout_info_tarea, null);
                    
                    TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
                    TextView tvDesc = view.findViewById(R.id.tvSheetDescription);
                    
                    tvTitle.setText(task.getTitle());
                    
                    SpannableStringBuilder infoCompleta = new SpannableStringBuilder();
                    
                    // 1. Mostrar siempre la descripción corta del diálogo primero
                    if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                        infoCompleta.append(task.getDescription());
                    }
                    
                    // 2. Añadir las notas del editor (HTML) debajo
                    if (task.getNotas() != null && !task.getNotas().isEmpty()) {
                        if (infoCompleta.length() > 0) infoCompleta.append("\n\n---\nDetalles adicionales:\n\n");
                        infoCompleta.append(Html.fromHtml(task.getNotas(), Html.FROM_HTML_MODE_LEGACY));
                    }
                    
                    if (infoCompleta.length() > 0) {
                        tvDesc.setText(infoCompleta);
                    } else {
                        tvDesc.setText("Sin descripción ni notas");
                    }

                    // Mostrar colaboradores
                    TextView tvDate = view.findViewById(R.id.tvSheetDate);
                    StringBuilder collabInfo = new StringBuilder();
                    collabInfo.append("Propietario: ").append(task.getUserEmail() != null ? task.getUserEmail() : "Desconocido");
                    if (task.getSharedWith() != null && !task.getSharedWith().isEmpty()) {
                        collabInfo.append("\nCompartida con:");
                        for (String email : task.getSharedWith()) {
                            collabInfo.append("\n - ").append(email);
                        }
                    }
                    tvDate.setText(collabInfo.toString());
                    tvDate.setVisibility(View.VISIBLE);

                    bottomSheetDialog.setContentView(view);
                    bottomSheetDialog.show();
                }
            }
        });
    }

    private void applyToolbarColor(Toolbar toolbar) {
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        int color = prefs.getInt("ToolbarColor", Color.BLACK);
        toolbar.setBackgroundColor(color);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color);
        }
    }
}

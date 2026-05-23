package com.example.tfg;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import com.google.firebase.firestore.FirebaseFirestore;

public class TaskEditorActivity extends AppCompatActivity {

    private EditText etNote;
    private String taskId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_editor);

        db = FirebaseFirestore.getInstance();
        etNote = findViewById(R.id.etNote);
        Toolbar toolbar = findViewById(R.id.toolbarEditor);
        
        taskId = getIntent().getStringExtra("TASK_ID");
        String title = getIntent().getStringExtra("TASK_TITLE");

        toolbar.setTitle(title != null ? title : "Nota de Subtarea");
        setSupportActionBar(toolbar);
        
        // Aplicar color personalizado
        int savedColor = getSharedPreferences("AppPrefs", MODE_PRIVATE)
                .getInt("ToolbarColor", ContextCompat.getColor(this, R.color.black));
        toolbar.setBackgroundColor(savedColor);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(savedColor);
        }

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
                    
                    String description = snapshot.getString("description");
                    if (description != null && !description.isEmpty()) {
                        // Solo actualizamos si el usuario no tiene el foco (para no interrumpir)
                        if (!etNote.hasFocus()) {
                            etNote.setText(Html.fromHtml(description, Html.FROM_HTML_MODE_LEGACY));
                        }
                    }
                });
    }

    private void saveAndExit() {
        String htmlContent = Html.toHtml(etNote.getText(), Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE);
        db.collection("tasks").document(taskId)
                .update("description", htmlContent)
                .addOnSuccessListener(aVoid -> finish())
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }
}

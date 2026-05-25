package com.example.tfg;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class RecordatorioReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "task_reminders";
    public static final String EXTRA_TYPE = "REMINDER_TYPE";
    public static final int TYPE_EXACT = 1;
    public static final int TYPE_SOON = 2;
    public static final int TYPE_EXPIRED = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskTitle = intent.getStringExtra("TASK_TITLE");
        String taskId = intent.getStringExtra("TASK_ID");
        int type = intent.getIntExtra(EXTRA_TYPE, TYPE_EXACT);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios Zentasker",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                taskId != null ? (taskId + type).hashCode() : 0, 
                mainIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = "Tarea pendiente";
        String content = taskTitle;

        if (type == TYPE_SOON) {
            title = "¡Queda poco tiempo!";
            content = "Tu tarea vence pronto: " + taskTitle;
        } else if (type == TYPE_EXPIRED) {
            title = "¡Tarea vencida!";
            content = "El plazo ha terminado para: " + taskTitle;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (taskId != null) {
            notificationManager.notify((taskId + type).hashCode(), builder.build());
        }
    }
}

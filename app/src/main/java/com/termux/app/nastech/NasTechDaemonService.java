package com.termux.app.nastech;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * NasTech AI Terminal — Background Daemon Service
 *
 * A foreground service that:
 *   • Shows a persistent "NasTech AI Active" notification
 *   • Polls $NASTECH_HOME/daemon_cmd every 3 s for shell commands
 *   • Executes each command with bash and writes the output to daemon_out
 *   • Starts automatically on device boot (via NasTechBootReceiver)
 *   • Can be toggled on/off from NasTech Settings
 *
 * Command protocol (simple file-based IPC):
 *   Write command text to  $NASTECH_HOME/daemon_cmd
 *   Read result from       $NASTECH_HOME/daemon_out
 *   Both files are deleted after processing.
 */
public class NasTechDaemonService extends Service {

    private static final String TAG              = "NasTechDaemon";
    private static final String CHANNEL_ID       = "nastech_daemon";
    private static final int    NOTIF_ID         = 8472;
    private static final int    POLL_INTERVAL_SEC = 3;

    public static final String ACTION_START = "com.termux.nastech.DAEMON_START";
    public static final String ACTION_STOP  = "com.termux.nastech.DAEMON_STOP";

    private ScheduledExecutorService mScheduler;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("NasTech AI active"));
        Log.i(TAG, "NasTech daemon started");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startPoller();
        return START_STICKY; // OS restarts daemon if killed
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        if (mScheduler != null) mScheduler.shutdownNow();
        Log.i(TAG, "NasTech daemon stopped");
        super.onDestroy();
    }

    // ── Command poller ────────────────────────────────────────────────────────

    private void startPoller() {
        if (mScheduler != null && !mScheduler.isShutdown()) return;
        mScheduler = Executors.newSingleThreadScheduledExecutor();
        mScheduler.scheduleWithFixedDelay(this::pollCommandFile,
                0, POLL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void pollCommandFile() {
        try {
            File cmdFile = new File(NasTechManager.getNasTechHome(), "daemon_cmd");
            if (!cmdFile.exists()) return;

            String command = readFileString(cmdFile).trim();
            cmdFile.delete();

            if (command.isEmpty()) return;
            Log.d(TAG, "Daemon executing: " + command);

            // Update notification to show what we're running
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(NOTIF_ID,
                    buildNotification("Running: " + command.substring(0, Math.min(40, command.length()))));
            }

            String output = runBashCommand(command);

            // Write output
            File outFile = new File(NasTechManager.getNasTechHome(), "daemon_out");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(output.getBytes());
            }

            // Restore notification
            if (nm != null) nm.notify(NOTIF_ID, buildNotification("NasTech AI active"));

        } catch (Exception e) {
            Log.e(TAG, "Daemon poll error", e);
        }
    }

    private String runBashCommand(String command) {
        StringBuilder sb = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                NasTechManager.getTermuxBin("bash"), "-c", command);
            pb.environment().put("NASTECH_HOME", NasTechManager.getNasTechHome());
            pb.environment().put("GROQ_API_KEY",        NasTechManager.getGroqApiKey());
            pb.environment().put("GEMINI_API_KEY",      NasTechManager.getGeminiApiKey());
            pb.environment().put("OPENROUTER_API_KEY",  NasTechManager.getApiKey());
            pb.environment().put("HOME",
                System.getenv("HOME") != null ? System.getenv("HOME") : "");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }
            proc.waitFor(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            sb.append("error: ").append(e.getMessage());
        }
        return sb.toString();
    }

    /** Read a text file into a String — API 21 compatible (no java.nio.file). */
    private static String readFileString(File f) {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = fis.read(buf);
            return read > 0 ? new String(buf, 0, read) : "";
        } catch (Exception e) {
            return "";
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "NasTech AI Daemon",
                NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("NasTech AI background service");
            ch.setShowBadge(false);
            NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String status) {
        Intent tapIntent = new Intent(this, TermuxActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent tap = PendingIntent.getActivity(this, 0, tapIntent, flags);

        // Stop action
        Intent stopIntent = new Intent(this, NasTechDaemonService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⬡ NasTech AI Terminal")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(tap)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPi)
            .build();
    }
}

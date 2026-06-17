package com.termux.app.nastech;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * NasTech AI Terminal — Boot Receiver
 *
 * Starts NasTechDaemonService automatically after device boot so the
 * AI daemon is always running in the background without needing to open
 * the app first.
 *
 * Requires RECEIVE_BOOT_COMPLETED permission in AndroidManifest.xml.
 */
public class NasTechBootReceiver extends BroadcastReceiver {

    private static final String TAG = "NasTechBoot";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !"android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            return;
        }
        Log.i(TAG, "Boot completed — starting NasTech daemon");
        try {
            NasTechManager.init(context);
            Intent svc = new Intent(context, NasTechDaemonService.class);
            svc.setAction(NasTechDaemonService.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start daemon on boot", e);
        }
    }
}

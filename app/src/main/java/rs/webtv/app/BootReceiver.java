package rs.webtv.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Pokreće aplikaciju kada se uređaj uključi (ako je to uključeno u meniju). */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        boolean auto = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getBoolean(MainActivity.KEY_AUTOSTART, false);
        if (!auto) return;
        Intent i = new Intent(ctx, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }
}

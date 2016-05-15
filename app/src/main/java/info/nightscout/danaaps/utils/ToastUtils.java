package info.nightscout.danaaps.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * Created by mike on 13.05.2016.
 */
public class ToastUtils {

    public static void showToastInUiThread(final Context ctx,
                                           final String string) {

        Handler mainThread = new Handler(Looper.getMainLooper());
        mainThread.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ctx, string, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
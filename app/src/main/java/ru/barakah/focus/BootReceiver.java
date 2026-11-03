package ru.barakah.focus;

/** Refreshes existing state. Android alone binds an enabled AccessibilityService. */
public final class BootReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context,android.content.Intent intent) {
        String action=intent.getAction();
        if(!android.content.Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
            && !android.content.Intent.ACTION_BOOT_COMPLETED.equals(action)
            && !android.content.Intent.ACTION_USER_UNLOCKED.equals(action)) return;
        PendingResult pending=goAsync();
        AuthRuntime.WORK.execute(() -> {
            try {
                new PasswordStore(context); // Migrate the verifier on first unlock, without generating a new code.
                BlockService.refreshAfterBoot();
            } finally { pending.finish(); }
        });
    }
}

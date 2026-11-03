package ru.barakah.focus;

final class AuthRuntime {
    static final java.util.concurrent.ExecutorService WORK=java.util.concurrent.Executors.newSingleThreadExecutor();
    private static volatile long managementUntil;
    static void allowManagement() { managementUntil=android.os.SystemClock.elapsedRealtime()+60000; }
    static boolean managementAllowed() { return android.os.SystemClock.elapsedRealtime()<managementUntil; }
}

package ru.barakah.focus;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Optional system policy. Never provisions, wipes or changes accounts on the device. */
final class ManagedProtection {
    private final Context context;
    private final DevicePolicyManager manager;
    private final ComponentName admin;
    private final SharedPreferences prefs;
    ManagedProtection(Context context) {
        this.context=context; manager=context.getSystemService(DevicePolicyManager.class);
        admin=new ComponentName(context,AdminReceiver.class);
        prefs=context.getSharedPreferences("managed_protection",Context.MODE_PRIVATE);
    }
    boolean isOwner() { return manager.isDeviceOwnerApp(context.getPackageName()); }
    boolean isProtected() { return isOwner() && manager.isUninstallBlocked(admin,context.getPackageName()); }
    boolean hasRecovery() { return prefs.contains("recovery"); }
    static String newRecoveryCode() {
        byte[] bytes=new byte[16]; new SecureRandom().nextBytes(bytes);
        StringBuilder code=new StringBuilder();
        for(byte b:bytes) code.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
        return code.toString();
    }
    private static String hash(String code) throws Exception {
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(code.trim().getBytes(StandardCharsets.UTF_8));
        return android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP);
    }
    void enable(String recovery) throws Exception {
        if(!isOwner()) throw new IllegalStateException("Нет Device Owner. Обычные права администратора не подходят.");
        if(hasRecovery()) throw new IllegalStateException("Сначала сними предыдущую защиту по коду восстановления.");
        String pkg=context.getPackageName();
        boolean previousControl=Build.VERSION.SDK_INT>=30 && manager.getUserControlDisabledPackages(admin).contains(pkg);
        if(!prefs.edit().putString("recovery",hash(recovery))
            .putBoolean("previousUninstall",manager.isUninstallBlocked(admin,pkg))
            .putBoolean("previousControl",previousControl).commit()) throw new IllegalStateException("Не удалось сохранить восстановление");
        manager.setUninstallBlocked(admin,pkg,true);
        if(Build.VERSION.SDK_INT>=30) {
            List<String> packages=new ArrayList<>(manager.getUserControlDisabledPackages(admin));
            if(!packages.contains(pkg)) packages.add(pkg);
            manager.setUserControlDisabledPackages(admin,packages);
        }
        if(!isProtected()) throw new IllegalStateException("Android не подтвердил запрет удаления. Код восстановления сохранён.");
    }
    void recover(String code) throws Exception {
        if(!isOwner() || !hasRecovery()) throw new IllegalStateException("Нет активированной управляемой защиты");
        if(!MessageDigest.isEqual(hash(code).getBytes(StandardCharsets.UTF_8),prefs.getString("recovery","").getBytes(StandardCharsets.UTF_8)))
            throw new IllegalArgumentException("Неверный код");
        String pkg=context.getPackageName();
        if(Build.VERSION.SDK_INT>=30) {
            List<String> packages=new ArrayList<>(manager.getUserControlDisabledPackages(admin));
            if(!prefs.getBoolean("previousControl",false)) packages.remove(pkg);
            manager.setUserControlDisabledPackages(admin,packages);
        }
        manager.setUninstallBlocked(admin,pkg,prefs.getBoolean("previousUninstall",false));
        prefs.edit().clear().commit();
    }
}

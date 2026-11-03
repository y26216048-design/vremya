package ru.barakah.focus;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;

final class PasswordStore {
    enum Result { OK, WRONG, WAIT, MISSING }
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    private final Context context;
    PasswordStore(Context context) {
        this.context=context.getApplicationContext();
        Context early=this.context.createDeviceProtectedStorageContext();
        synchronized(LOCK) {
            SharedPreferences earlyPrefs=early.getSharedPreferences("password_guard",Context.MODE_PRIVATE);
            SharedPreferences selected=earlyPrefs;
            if(!earlyPrefs.contains("hash") && this.context.getSystemService(android.os.UserManager.class).isUserUnlocked()) {
                SharedPreferences previous=this.context.getSharedPreferences("password_guard",Context.MODE_PRIVATE);
                if(previous.contains("hash")) {
                    if(early.moveSharedPreferencesFrom(this.context,"password_guard")) selected=early.getSharedPreferences("password_guard",Context.MODE_PRIVATE);
                    else selected=previous; // Keep the existing verifier if migration fails; never replace the secret.
                }
            }
            prefs=selected;
        }
    }
    boolean hasPassword() { synchronized(LOCK) { return prefs.contains("hash") && prefs.contains("salt"); } }
    boolean needsSetup() { synchronized(LOCK) { return hasPassword() && !prefs.getBoolean("setup_complete",true); } }
    void completeSetup() {
        synchronized(LOCK) {
            if(!hasPassword()) throw new IllegalStateException("Сначала задайте код");
            if(!prefs.edit().putBoolean("setup_complete",true).commit()) throw new IllegalStateException("Не удалось сохранить настройку");
        }
    }
    void register(char[] code,char[] confirmation) throws Exception {
        synchronized(LOCK) {
            try {
                if(hasPassword()) throw new IllegalStateException("Код уже задан. Изменить его в приложении нельзя.");
                if(!context.getSystemService(android.os.UserManager.class).isUserUnlocked()) throw new IllegalStateException("Сначала разблокируйте телефон");
                if(code.length<8 || code.length>128) throw new IllegalArgumentException("Используйте от 8 до 128 символов");
                boolean onlySpaces=true; for(char ch:code) if(!Character.isWhitespace(ch)) onlySpaces=false;
                if(onlySpaces) throw new IllegalArgumentException("Код не может состоять только из пробелов");
                if(!Arrays.equals(code,confirmation)) throw new IllegalArgumentException("Коды не совпадают");
                String salt=PasswordCrypto.salt();
                if(!prefs.edit().putString("salt",salt).putString("hash",PasswordCrypto.hash(code,salt))
                    .putBoolean("setup_complete",false).remove("failures").remove("blockedUntil").commit())
                    throw new IllegalStateException("Не удалось сохранить защиту паролем");
            } finally { Arrays.fill(code,'\0'); Arrays.fill(confirmation,'\0'); }
        }
    }
    Result verify(char[] candidate) throws Exception {
        synchronized(LOCK) {
            try {
                if(!hasPassword()) return Result.MISSING;
                if(prefs.getLong("blockedUntil",0)>System.currentTimeMillis()) return Result.WAIT;
                if(PasswordCrypto.matches(candidate,prefs.getString("salt",""),prefs.getString("hash",""))) {
                    prefs.edit().remove("failures").remove("blockedUntil").commit(); return Result.OK;
                }
                int failures=prefs.getInt("failures",0)+1;
                SharedPreferences.Editor edit=prefs.edit().putInt("failures",failures>=5 ? 0 : failures);
                if(failures>=5) edit.putLong("blockedUntil",System.currentTimeMillis()+30000);
                edit.commit(); return Result.WRONG;
            } finally { Arrays.fill(candidate,'\0'); }
        }
    }
}

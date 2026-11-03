package ru.barakah.focus;

import java.util.Locale;

final class GuardRules {
    static final String APP_LABEL="Время";
    static final String SERVICE_LABEL="Время — защита приложений";
    static boolean ownLabel(String text) {
        return APP_LABEL.equals(text) || SERVICE_LABEL.equals(text) || "ru.barakah.focus".equals(text)
            || "Баракат времени".equals(text) || "Баракат — защита от Instagram и YouTube".equals(text);
    }
    static boolean settingsPackage(String name) { return "com.android.settings".equals(name); }
    static boolean installerPackage(String name) {
        return "com.google.android.packageinstaller".equals(name) || "com.android.packageinstaller".equals(name)
            || "com.samsung.android.packageinstaller".equals(name) || "com.google.android.permissioncontroller".equals(name)
            || "com.android.permissioncontroller".equals(name);
    }
    static boolean shouldGuard(String pkg,String screen,boolean ownLabel,boolean ownHeader,boolean appControls) {
        if(!ownLabel) return false;
        String name=screen==null ? "" : screen.toLowerCase(Locale.ROOT);
        if(installerPackage(pkg)) return name.contains("uninstall") || name.contains("uninstaller")
            || name.contains("deletepackage") || name.contains("alertdialog") || name.contains("installstart");
        if(!settingsPackage(pkg)) return false;
        boolean sensitive=name.contains("installedappdetails") || name.contains("appstorage") || name.contains("appinfo")
            || name.contains("storageuse") || name.contains("toggleaccessibilityservice")
            || name.contains("accessibilityservicedetails") || name.contains("apppermission");
        return sensitive || (ownHeader && appControls);
    }
}

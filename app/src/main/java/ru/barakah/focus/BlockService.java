package ru.barakah.focus;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;

public final class BlockService extends AccessibilityService {
    private static volatile BlockService running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windows;
    private long lastBlock;
    private PasswordStore passwords;
    private LinearLayout guardOverlay;
    private String pendingSystemPackage="",systemScreen="";
    private boolean systemWindowOwnLabel;
    private boolean systemScanQueued;
    private int systemScanRetries;
    private final Runnable scanSystem=() -> {
        systemScanQueued=false;
        if(passwords==null || !passwords.hasPassword() || passwords.needsSetup() || AuthRuntime.managementAllowed() || guardOverlay!=null) return;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) { retrySystemScan(); return; }
        try {
            String pkg=String.valueOf(root.getPackageName());
            if(!pkg.equals(pendingSystemPackage)) { retrySystemScan(); return; }
            boolean[] facts=new boolean[3];
            collectGuardFacts(root,facts,new int[]{0});
            if(GuardRules.shouldGuard(pkg,systemScreen,facts[0] || systemWindowOwnLabel,facts[1],facts[2])) showPasswordGuard();
            else retrySystemScan();
        } finally { root.recycle(); }
    };
    private String pendingBrowser;
    private boolean browserScanQueued;
    private final Runnable scanBrowser = () -> {
        browserScanQueued = false;
        String browser = pendingBrowser;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            if (!browser.equals(String.valueOf(root.getPackageName()))) return;
            for (String id : BlockRules.browserAddressIds(browser)) {
                java.util.List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByViewId(id);
                try {
                    for (AccessibilityNodeInfo node : nodes) {
                        String host = BlockRules.blockedHost(node.getText() == null ? null : node.getText().toString());
                        if (node.isVisibleToUser() && host != null) { block(); return; }
                    }
                } finally { for(AccessibilityNodeInfo node:nodes) node.recycle(); }
            }
        } finally { root.recycle(); }
    };

    public static boolean blocks(String name) {
        return BlockRules.blocksApp(name);
    }

    @Override protected void onServiceConnected() {
        running=this;
        windows = getSystemService(WindowManager.class);
        preparePassword();
        Log.i("BarakahBlock", "service_connected");
    }
    static void refreshAfterBoot() {
        BlockService service=running;
        if(service!=null) service.handler.post(service::preparePassword);
    }
    private void preparePassword() {
        passwords=new PasswordStore(this);
        AuthRuntime.WORK.execute(() -> {
            try {
                Log.i("BarakahBlock",passwords.hasPassword() ? "password_guard_ready" : "password_registration_required");
                handler.post(() -> { if(!systemScanQueued) { systemScanQueued=true; handler.post(scanSystem); } });
            } catch(Exception e) { Log.e("BarakahBlock","password_setup_failed"); }
        });
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        String name = event.getPackageName() == null ? "" : event.getPackageName().toString();
        if (blocks(name)) {
            block();
        } else if (BlockRules.browserAddressIds(name).length > 0) {
            pendingBrowser = name;
            if (!browserScanQueued) { browserScanQueued = true; handler.postDelayed(scanBrowser,100); }
        } else if(GuardRules.settingsPackage(name) || GuardRules.installerPackage(name)) {
            if(!pendingSystemPackage.equals(name)) { systemScreen=""; systemWindowOwnLabel=false; pendingSystemPackage=name; }
            if(event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getClassName()!=null) {
                systemScanRetries=0;
                String screen=event.getClassName().toString();
                if(!screen.startsWith("android.widget.") && !screen.startsWith("android.view.")) {
                    systemScreen=screen; systemWindowOwnLabel=false;
                    for(CharSequence value:event.getText()) if(GuardRules.ownLabel(String.valueOf(value).trim())) systemWindowOwnLabel=true;
                }
            }
            if(!systemScanQueued) { systemScanQueued=true; handler.postDelayed(scanSystem,60); }
        }
    }

    private void retrySystemScan() {
        if(++systemScanRetries<=8 && !systemScanQueued) { systemScanQueued=true; handler.postDelayed(scanSystem,120); }
    }

    private void collectGuardFacts(AccessibilityNodeInfo node,boolean[] facts,int[] count) {
        if(++count[0]>400) return;
        String value=node.getText()==null ? "" : node.getText().toString().trim();
        String description=node.getContentDescription()==null ? "" : node.getContentDescription().toString().trim();
        String id=node.getViewIdResourceName()==null ? "" : node.getViewIdResourceName();
        boolean own=GuardRules.ownLabel(value) || GuardRules.ownLabel(description);
        if(own && node.isVisibleToUser()) {
            facts[0]=true;
            if(id.endsWith("/entity_header_title") || id.endsWith("/app_name") || id.endsWith("/app_label")) facts[1]=true;
        }
        if(id.endsWith("/uninstall_button") || id.endsWith("/forcestop_button") || id.endsWith("/clear_data_button")) facts[2]=true;
        for(int i=0;i<node.getChildCount() && count[0]<400;i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null) { try { collectGuardFacts(child,facts,count); } finally { child.recycle(); } }
        }
    }

    private void showPasswordGuard() {
        if(guardOverlay!=null) return;
        if(!performGlobalAction(GLOBAL_ACTION_HOME)) performGlobalAction(GLOBAL_ACTION_BACK);
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
        int padding=(int)(24*getResources().getDisplayMetrics().density); card.setPadding(padding,padding,padding,padding);
        card.setBackgroundColor(0xfff3f7f1); card.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        TextView title=new TextView(this); title.setText("Введите пароль"); title.setTextSize(24); title.setTextColor(Color.BLACK); card.addView(title);
        TextView explanation=new TextView(this); explanation.setText("Для изменения настроек."); explanation.setTextSize(16); explanation.setTextColor(0xff526359); card.addView(explanation);
        EditText input=new EditText(this); input.setHint("Пароль"); input.setInputType(129); input.setSaveEnabled(false);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        input.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); card.addView(input);
        TextView result=new TextView(this); result.setTextColor(Color.BLACK); card.addView(result);
        Button enter=new Button(this); enter.setText("Разрешить · 60 с"); card.addView(enter);
        Button cancel=new Button(this); cancel.setText("Отмена"); card.addView(cancel);
        cancel.setOnClickListener(v -> { removeGuard(); performGlobalAction(GLOBAL_ACTION_HOME); });
        enter.setOnClickListener(v -> {
            char[] candidate=new char[input.length()]; input.getText().getChars(0,input.length(),candidate,0); input.getText().clear(); enter.setEnabled(false);
            AuthRuntime.WORK.execute(() -> {
                PasswordStore.Result verified;
                try { verified=passwords.verify(candidate); }
                catch(Exception e) { java.util.Arrays.fill(candidate,'\0'); verified=null; }
                final PasswordStore.Result answer=verified;
                handler.post(() -> {
                    if(guardOverlay!=card) return;
                    enter.setEnabled(true);
                    if(answer==PasswordStore.Result.OK) {
                        AuthRuntime.allowManagement(); removeGuard();
                        android.content.Intent intent=new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        try { startActivity(intent); } catch(RuntimeException ignored) { android.widget.Toast.makeText(this,"Доступ: 60 секунд",android.widget.Toast.LENGTH_SHORT).show(); }
                    } else result.setText(answer==PasswordStore.Result.WAIT ? "Повторите через 30 с." : "Неверный пароль");
                });
            });
        });
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_SECURE,PixelFormat.TRANSLUCENT);
        params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.setTitle("Время: пароль");
        guardOverlay=card;
        try { windows.addView(card,params); Log.i("BarakahBlock","management_prompt_shown"); }
        catch(RuntimeException e) { guardOverlay=null; android.widget.Toast.makeText(this,"Нужен пароль",android.widget.Toast.LENGTH_SHORT).show(); }
    }
    private void removeGuard() {
        if(guardOverlay!=null && windows!=null) {
            try { windows.removeView(guardOverlay); } catch(IllegalArgumentException ignored) {}
            guardOverlay=null;
        }
    }

    private void block() {
        long now = SystemClock.elapsedRealtime();
        if (lastBlock != 0 && now - lastBlock < 350) return;
        lastBlock = now;
        boolean home = performGlobalAction(GLOBAL_ACTION_HOME);
        if (!home) performGlobalAction(GLOBAL_ACTION_BACK);
        // Never log browsing addresses, page content or private URL query parameters.
        Log.i("BarakahBlock", "blocked home=" + home);
    }
    @Override public void onInterrupt() { removeGuard(); }
    @Override public void onDestroy() { if(running==this) running=null; handler.removeCallbacks(scanBrowser); handler.removeCallbacks(scanSystem); removeGuard(); super.onDestroy(); }
}

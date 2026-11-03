package ru.barakah.focus;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.*;

public final class MainActivity extends Activity {
    private PasswordStore passwords;
    private boolean resumed,unlocked;
    private TextView message;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        passwords=new PasswordStore(this);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; render(); }
    @Override protected void onPause() {
        resumed=false; unlocked=false;
        if(passwords.hasPassword()) showUnlock();
        super.onPause();
    }
    private LinearLayout page(String title) {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(24*getResources().getDisplayMetrics().density); root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(0xfff3f7f1); root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.addView(text(title,28)); ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        return root;
    }
    private TextView text(String value,int size) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(Color.BLACK); return view; }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
    private LinearLayout passwordPage() {
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(0xfff3f7f1);
        LinearLayout stage=new LinearLayout(this); stage.setOrientation(LinearLayout.VERTICAL); stage.setGravity(Gravity.CENTER);
        stage.setPadding(dp(24),dp(24),dp(24),dp(24));
        stage.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(24),dp(28),dp(24),dp(20));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE); background.setCornerRadius(dp(24)); background.setStroke(dp(1),0xffdfe9df);
        card.setBackground(background); card.setElevation(dp(3)); card.setFocusableInTouchMode(true); card.requestFocus();
        int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(48),dp(420));
        stage.addView(card,new LinearLayout.LayoutParams(width,LinearLayout.LayoutParams.WRAP_CONTENT));
        ImageView icon=new ImageView(this); icon.setImageResource(ru.barakah.focus.R.mipmap.ic_launcher);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLayout=new LinearLayout.LayoutParams(dp(52),dp(52)); iconLayout.gravity=Gravity.CENTER_HORIZONTAL; iconLayout.bottomMargin=dp(14); card.addView(icon,iconLayout);
        TextView brand=text(getString(ru.barakah.focus.R.string.app_name),26); brand.setTextColor(0xff24533b); brand.setGravity(Gravity.CENTER);
        brand.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); card.addView(brand);
        TextView title=text("Введите пароль",16); title.setTextColor(0xff526359); title.setGravity(Gravity.CENTER); title.setPadding(0,dp(6),0,0); card.addView(title);
        scroll.addView(stage); setContentView(scroll); return card;
    }
    private void render() {
        if(!passwords.hasPassword()) showRegistration();
        else if(passwords.needsSetup()) showSetup();
        else if(!unlocked) showUnlock(); else showControls();
    }
    private EditText codeField(LinearLayout root,String hint) {
        EditText field=new EditText(this); field.setHint(hint); field.setInputType(129); field.setSaveEnabled(false);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); root.addView(field); return field;
    }
    private void showRegistration() {
        LinearLayout root=page("Время · первый запуск");
        root.addView(text("\nКод задаётся один раз для этого устройства. Смены и восстановления кода в приложении нет.\n\nПопросите доверенного человека придумать код и сохранить его у себя. Не теряйте код.\n\nВажно: удаление приложения или очистка его данных может сбросить настройки — это не пожизненная системная защита.\n",17));
        EditText first=codeField(root,"Код — не менее 8 символов");
        EditText confirmation=codeField(root,"Повторите код");
        TextView error=text("",15); error.setTextColor(0xff9a3b19); root.addView(error);
        Button create=new Button(this); create.setText("Сохранить код и продолжить"); create.setAllCaps(false); root.addView(create);
        create.setOnClickListener(v -> {
            char[] code=new char[first.length()], repeated=new char[confirmation.length()];
            first.getText().getChars(0,first.length(),code,0); confirmation.getText().getChars(0,confirmation.length(),repeated,0);
            first.getText().clear(); confirmation.getText().clear(); create.setEnabled(false);
            AuthRuntime.WORK.execute(() -> {
                String problem=null;
                try { passwords.register(code,repeated); } catch(Exception e) { problem=e.getMessage(); }
                final String result=problem;
                runOnUiThread(() -> {
                    if(!resumed || isDestroyed()) return;
                    create.setEnabled(true);
                    if(result==null) render(); else error.setText(result);
                });
            });
        });
    }
    private boolean serviceEnabled() {
        android.view.accessibility.AccessibilityManager manager=getSystemService(android.view.accessibility.AccessibilityManager.class);
        for(android.accessibilityservice.AccessibilityServiceInfo item:manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            android.content.pm.ServiceInfo service=item.getResolveInfo().serviceInfo;
            if(getPackageName().equals(service.packageName) && BlockService.class.getName().equals(service.name)) return true;
        }
        return false;
    }
    private void showSetup() {
        LinearLayout root=page("Настройка защиты");
        root.addView(text("\nКод сохранён. Теперь включите службу «"+getString(ru.barakah.focus.R.string.service_label)+"» в специальных возможностях. Затем вернитесь сюда и завершите настройку.\n\nInstagram и официальный YouTube будут закрываться. ReVanced остаётся доступным.\n",17));
        root.addView(text("Служба локально проверяет адреса браузеров и название блокировщика в настройках. Данные не отправляются.\n\nЭто дополнительная защита, а не системный запрет удаления. Отключение службы и промежуток её запуска могут позволить обход.\n",15));
        Button enable=new Button(this); enable.setText("Открыть специальные возможности"); root.addView(enable);
        enable.setOnClickListener(v -> openAccessibility());
        root.addView(text("В специальных возможностях включите службу «"+getString(ru.barakah.focus.R.string.service_label)+"». Если Android требует, сначала разрешите ограниченные настройки в сведениях о приложении.\n",15));
        TextView setupStatus=text(serviceEnabled() ? "Служба включена" : "Служба пока выключена",16); root.addView(setupStatus);
        Button finishSetup=new Button(this); finishSetup.setText("Завершить настройку"); finishSetup.setAllCaps(false); root.addView(finishSetup);
        finishSetup.setOnClickListener(v -> {
            if(!serviceEnabled()) { setupStatus.setText("Сначала включите службу в специальных возможностях"); return; }
            try { passwords.completeSetup(); render(); } catch(RuntimeException e) { setupStatus.setText(e.getMessage()); }
        });
    }
    private void showUnlock() {
        LinearLayout root=passwordPage();
        EditText input=new EditText(this); input.setHint("Пароль"); input.setInputType(129); input.setSaveEnabled(false);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setTextSize(18); input.setSingleLine(true); input.setPadding(dp(16),0,dp(16),0);
        android.graphics.drawable.GradientDrawable fieldBackground=new android.graphics.drawable.GradientDrawable();
        fieldBackground.setColor(0xfff2f6f2); fieldBackground.setCornerRadius(dp(12)); fieldBackground.setStroke(dp(1),0xffd8e5d9); input.setBackground(fieldBackground);
        LinearLayout.LayoutParams fieldLayout=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(56)); fieldLayout.topMargin=dp(24); root.addView(input,fieldLayout);
        message=text("",15); message.setTextColor(0xff9a3b19); message.setGravity(Gravity.CENTER); message.setVisibility(View.GONE); root.addView(message);
        Button enter=new Button(this); enter.setText("Открыть"); enter.setAllCaps(false); enter.setTextSize(17); enter.setTextColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable buttonBackground=new android.graphics.drawable.GradientDrawable(); buttonBackground.setColor(0xff24533b); buttonBackground.setCornerRadius(dp(12));
        enter.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33ffffff),buttonBackground,null));
        LinearLayout.LayoutParams enterLayout=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(52)); enterLayout.topMargin=dp(16); root.addView(enter,enterLayout);
        enter.setOnClickListener(v -> {
            char[] candidate=new char[input.length()]; input.getText().getChars(0,input.length(),candidate,0); input.getText().clear();
            enter.setEnabled(false); TextView resultView=message;
            AuthRuntime.WORK.execute(() -> {
                PasswordStore.Result result;
                try { result=passwords.verify(candidate); }
                catch(Exception e) { java.util.Arrays.fill(candidate,'\0'); result=null; }
                final PasswordStore.Result answer=result;
                runOnUiThread(() -> {
                    if(!resumed || isDestroyed() || message!=resultView) return;
                    enter.setEnabled(true);
                    if(answer==PasswordStore.Result.OK) { unlocked=true; showControls(); }
                    else { resultView.setVisibility(View.VISIBLE); resultView.setText(answer==PasswordStore.Result.WAIT ? "Повторите через 30 с." : "Неверный пароль"); }
                });
            });
        });
        Button close=new Button(this); close.setText("Закрыть"); close.setAllCaps(false); close.setTextColor(0xff526359); close.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams closeLayout=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(48)); closeLayout.topMargin=dp(4); root.addView(close,closeLayout); close.setOnClickListener(v -> finish());
    }
    private void showControls() {
        LinearLayout root=page("Настройки");
        Button androidButton=new Button(this); androidButton.setText("Разрешить изменения · 60 с"); root.addView(androidButton);
        androidButton.setOnClickListener(v -> { AuthRuntime.allowManagement(); startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))); });
        Button service=new Button(this); service.setText("Настройки службы"); root.addView(service);
        service.setOnClickListener(v -> { AuthRuntime.allowManagement(); openAccessibility(); });
        Button lock=new Button(this); lock.setText("Закрыть доступ"); root.addView(lock); lock.setOnClickListener(v -> { unlocked=false; showUnlock(); });
    }
    private void openAccessibility() {
        Intent intent=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.putExtra(":settings:fragment_args_key",new ComponentName(this,BlockService.class).flattenToString());
        startActivity(intent);
    }
}

package ru.barakah.focus;

import java.net.URI;
import java.util.Locale;

/** Pure policy: never match a page title or a substring inside a search query. */
public final class BlockRules {
    private BlockRules() {}
    public static boolean blocksApp(String name) {
        return "com.instagram.android".equals(name) || "com.instagram.lite".equals(name)
            || "com.instapro.android".equals(name) || "com.instapro2.android".equals(name)
            || "com.instapro3.android".equals(name)
            || "com.google.android.youtube".equals(name);
    }
    public static String blockedHost(String address) {
        if (address == null) return null;
        String value = address.trim();
        if (value.isEmpty() || value.matches(".*\\s.*")) return null;
        try {
            if (value.startsWith("//")) value = "https:" + value;
            else if (!value.contains("://")) value = "https://" + value;
            URI uri = new URI(value);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return null;
            String host = uri.getHost();
            if (host == null) return null;
            host = host.toLowerCase(Locale.ROOT);
            while (host.endsWith(".")) host = host.substring(0,host.length()-1);
            for (String domain : new String[]{"youtube.com","youtu.be","youtube-nocookie.com","instagram.com","instagr.am"})
                if (host.equals(domain) || host.endsWith("." + domain)) return host;
        } catch (Exception ignored) {}
        return null;
    }
    public static String[] browserAddressIds(String name) {
        if (name == null) return new String[0];
        switch (name) {
            case "com.android.chrome": case "com.chrome.beta": case "com.chrome.dev": case "com.chrome.canary":
            case "com.brave.browser": case "com.microsoft.emmx": case "com.vivaldi.browser":
                return new String[]{name+":id/url_bar",name+":id/location_bar_edit_text"};
            case "com.sec.android.app.sbrowser": case "com.sec.android.app.sbrowser.beta":
                return new String[]{name+":id/location_bar_edit_text",name+":id/url_bar"};
            case "org.mozilla.firefox": case "org.mozilla.fenix":
                return new String[]{name+":id/mozac_browser_toolbar_url_view",name+":id/mozac_browser_toolbar_edit_url_view"};
            default: return new String[0];
        }
    }
}

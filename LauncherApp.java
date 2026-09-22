import java.io.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

// ================================================================
// LauncherApp.java
// ================================================================

// =====================================================================
//  ЛОГИКА (без JavaFX): JSON, настройки, Epic/EOS, Proton, запуск игр
// =====================================================================

/** Минимальный, но настоящий JSON-парсер (вместо хрупких регулярок). */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        j.ws();
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw j.err("лишние символы после значения");
        return v;
    }

    private IllegalArgumentException err(String m) {
        return new IllegalArgumentException("JSON: " + m + " (позиция " + i + ")");
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        if (i >= s.length()) throw err("неожиданный конец файла");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw err("ожидалось " + word);
        i += word.length();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++; // {
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw err("ожидался ключ");
            String k = string();
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw err("ожидалось ':'");
            i++;
            ws();
            m.put(k, value());
            ws();
            if (i >= s.length()) throw err("объект не закрыт");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw err("ожидалось ',' или '}'");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++; // [
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            ws();
            l.add(value());
            ws();
            if (i >= s.length()) throw err("массив не закрыт");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw err("ожидалось ',' или ']'");
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++; // "
        while (true) {
            if (i >= s.length()) throw err("строка не закрыта");
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c != '\\') { sb.append(c); continue; }
            if (i >= s.length()) throw err("обрыв экранирования");
            char e = s.charAt(i++);
            switch (e) {
                case '"': sb.append('"'); break;
                case '\\': sb.append('\\'); break;
                case '/': sb.append('/'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case 'n': sb.append('\n'); break;
                case 'r': sb.append('\r'); break;
                case 't': sb.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw err("неверный \\u");
                    try { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); }
                    catch (NumberFormatException ex) { throw err("неверный \\u"); }
                    i += 4;
                    break;
                default: throw err("неизвестное экранирование \\" + e);
            }
        }
    }

    private Object number() {
        int st = i;
        while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) i++;
        String t = s.substring(st, i);
        if (t.isEmpty()) throw err("ожидалось значение");
        try {
            if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.valueOf(t);
            return Long.valueOf(t);
        } catch (NumberFormatException ex) {
            throw err("неверное число: " + t);
        }
    }

    // ---- запись ----
    static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        w(sb, v, 0);
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int n) { for (int k = 0; k < n; k++) sb.append("  "); }

    private static boolean simple(Object o) {
        return o == null || o instanceof String || o instanceof Number || o instanceof Boolean;
    }

    private static void w(StringBuilder sb, Object v, int lvl) {
        if (v == null) { sb.append("null"); return; }
        if (v instanceof String str) { quote(sb, str); return; }
        if (v instanceof Boolean || v instanceof Number) { sb.append(v); return; }
        if (v instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append("{\n");
            int n = 0;
            for (Map.Entry<?, ?> en : m.entrySet()) {
                indent(sb, lvl + 1);
                quote(sb, String.valueOf(en.getKey()));
                sb.append(": ");
                w(sb, en.getValue(), lvl + 1);
                if (++n < m.size()) sb.append(',');
                sb.append('\n');
            }
            indent(sb, lvl);
            sb.append('}');
            return;
        }
        if (v instanceof List<?> l) {
            if (l.isEmpty()) { sb.append("[]"); return; }
            boolean inline = true;
            for (Object o : l) if (!simple(o)) { inline = false; break; }
            if (inline) {
                sb.append('[');
                for (int k = 0; k < l.size(); k++) { if (k > 0) sb.append(", "); w(sb, l.get(k), lvl); }
                sb.append(']');
                return;
            }
            sb.append("[\n");
            for (int k = 0; k < l.size(); k++) {
                indent(sb, lvl + 1);
                w(sb, l.get(k), lvl + 1);
                if (k < l.size() - 1) sb.append(',');
                sb.append('\n');
            }
            indent(sb, lvl);
            sb.append(']');
            return;
        }
        quote(sb, v.toString());
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ---- удобные getters ----
    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    static List<Object> asList(Object o) {
        if (o instanceof List<?> l) return new ArrayList<Object>(l);
        return new ArrayList<>();
    }

    static String str(Map<String, Object> m, String k, String def) {
        Object o = m.get(k);
        return o instanceof String x ? x : def;
    }

    static boolean bool(Map<String, Object> m, String k, boolean def) {
        Object o = m.get(k);
        return o instanceof Boolean b ? b : def;
    }

    static double dbl(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    static long lng(Map<String, Object> m, String k, long def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.longValue() : def;
    }
}

/** Общие системные помощники. */
final class Sys {
    static final Path HOME = Paths.get(System.getProperty("user.home"));

    private Sys() {}

    static String which(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) continue;
            Path p = Paths.get(dir, cmd);
            if (Files.isRegularFile(p) && Files.isExecutable(p)) return p.toString();
        }
        return null;
    }

    /** Открывает http(s)-ссылку в браузере по умолчанию. */
    static boolean openUrl(String url) {
        try {
            URI u = new URI(url);
            String sch = u.getScheme();
            if (sch == null || !(sch.equalsIgnoreCase("http") || sch.equalsIgnoreCase("https"))) return false;
            return detach("xdg-open", u.toString());
        } catch (Exception e) {
            return false;
        }
    }

    static boolean openPath(Path p) {
        return detach("xdg-open", p.toString());
    }

    private static boolean detach(String... cmd) {
        if (which(cmd[0]) == null) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Корень Steam (нужен Proton как STEAM_COMPAT_CLIENT_INSTALL_PATH). */
    static Path steamRoot() {
        Path[] cands = {
            HOME.resolve(".steam/steam"),
            HOME.resolve(".local/share/Steam"),
            HOME.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam")
        };
        for (Path c : cands) if (Files.isDirectory(c)) return c;
        Path stub = Store.dir().resolve("steam-stub");
        try { Files.createDirectories(stub); } catch (IOException ignored) {}
        return stub;
    }

    /** Разбор строки аргументов как в shell: кавычки, экранирование пробела. */
    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        StringBuilder cur = new StringBuilder();
        boolean inTok = false;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                else if (c == '\\' && quote == '"' && i + 1 < s.length()
                        && (s.charAt(i + 1) == '"' || s.charAt(i + 1) == '\\')) cur.append(s.charAt(++i));
                else cur.append(c);
            } else if (c == '"' || c == '\'') {
                quote = c;
                inTok = true;
            } else if (Character.isWhitespace(c)) {
                if (inTok) { out.add(cur.toString()); cur.setLength(0); inTok = false; }
            } else if (c == '\\' && i + 1 < s.length()
                    && (Character.isWhitespace(s.charAt(i + 1)) || s.charAt(i + 1) == '"'
                        || s.charAt(i + 1) == '\'' || s.charAt(i + 1) == '\\')) {
                cur.append(s.charAt(++i));
                inTok = true;
            } else {
                cur.append(c);
                inTok = true;
            }
        }
        if (inTok) out.add(cur.toString());
        return out;
    }

    static String joinForLog(List<String> cmd) {
        StringBuilder sb = new StringBuilder();
        for (String a : cmd) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(a.matches("[A-Za-z0-9_@%+=:,./-]+") ? a : "'" + a.replace("'", "'\\''") + "'");
        }
        return sb.toString();
    }

    /** start.sh ставит GDK_BACKEND=x11 только для самого лаунчера — играм и winetricks возвращаем исходное значение. */
    static void restoreDisplayEnv(Map<String, String> env) {
        env.remove("_JAVA_AWT_WM_NONREPARENTING");
        String orig = System.getenv("OFLL_GDK_BACKEND_ORIG");
        if (orig == null) return;
        if (orig.isEmpty()) env.remove("GDK_BACKEND");
        else env.put("GDK_BACKEND", orig);
    }

    /** «сегодня», «вчера», «5 дн. назад»… epochSec — время события, nowSec — текущее время. */
    static String formatAgo(long epochSec, long nowSec) {
        long d = (nowSec - epochSec) / 86400;
        if (d <= 0) return "сегодня";
        if (d == 1) return "вчера";
        if (d < 30) return d + " дн. назад";
        if (d < 365) return (d / 30) + " мес. назад";
        return (d / 365) + " г. назад";
    }

    static String formatPlaytime(long secs) {
        long h = secs / 3600, m = (secs % 3600) / 60;
        if (h == 0 && m == 0) return "меньше минуты";
        if (h == 0) return m + " мин";
        return h + " ч " + m + " мин";
    }
}

/** Настройки лаунчера. */
final class GlobalConfig {
    String protonPath = Sys.HOME.resolve("protons").toString();
    String prefixesPath = Sys.HOME.resolve(".local/share/OnlineFix Linux Launcher").toString();
    String defaultProton = Proton.SYSTEM_WINE;
    boolean defaultWined3d = false;
    boolean defaultWayland = false;

    String sgdbKey = "";               // API-ключ SteamGridDB (секрет!)
    boolean autoCovers = true;         // сама искать AppID и обложки для новых игр
    boolean showProtonRating = true;   // показывать рейтинг ProtonDB
    String cardSize = "medium";        // small / medium / large
    String sortMode = "name";          // name / recent / playtime
    boolean minimizeOnLaunch = false;  // сворачивать лаунчер при запуске игры
    String accent = "mauve";           // цвет акцента интерфейса
    boolean animations = true;         // анимации интерфейса
    String language = "en";            // язык интерфейса: en, ru, uk, pl, ja
    boolean coverBackground = true;    // размытая обложка игры на фоне, когда открыта панель игры
    boolean showSplash = true;         // заставка с аватаркой при запуске

    // геометрия окна — запоминается между запусками
    boolean winMaximized = true;
    double winW = 1280, winH = 800, winX = -1, winY = -1;

    static final List<String> CARD_SIZES = List.of("small", "medium", "large");
    static final List<String> SORT_MODES = List.of("name", "recent", "playtime");
    static final List<String> ACCENTS = List.of("mauve", "blue", "green", "peach", "pink", "teal");

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sgdbKey", sgdbKey);
        m.put("autoCovers", autoCovers);
        m.put("showProtonRating", showProtonRating);
        m.put("cardSize", cardSize);
        m.put("sortMode", sortMode);
        m.put("minimizeOnLaunch", minimizeOnLaunch);
        m.put("accent", accent);
        m.put("animations", animations);
        m.put("language", language);
        m.put("coverBackground", coverBackground);
        m.put("showSplash", showSplash);
        m.put("winMaximized", winMaximized);
        m.put("winW", winW);
        m.put("winH", winH);
        m.put("winX", winX);
        m.put("winY", winY);
        m.put("protonPath", protonPath);
        m.put("prefixesPath", prefixesPath);
        m.put("defaultProton", defaultProton);
        m.put("defaultWined3d", defaultWined3d);
        m.put("defaultWayland", defaultWayland);
        return m;
    }

    static GlobalConfig fromMap(Map<String, Object> m) {
        GlobalConfig c = new GlobalConfig();
        String p = Json.str(m, "protonPath", "");
        if (!p.isBlank()) c.protonPath = p;
        String x = Json.str(m, "prefixesPath", "");
        if (!x.isBlank()) c.prefixesPath = x;
        c.defaultProton = Proton.normalize(Json.str(m, "defaultProton", ""));
        c.defaultWined3d = Json.bool(m, "defaultWined3d", false);
        c.defaultWayland = Json.bool(m, "defaultWayland", false);
        c.sgdbKey = Json.str(m, "sgdbKey", "").trim();
        c.autoCovers = Json.bool(m, "autoCovers", true);
        c.showProtonRating = Json.bool(m, "showProtonRating", true);
        String cs = Json.str(m, "cardSize", "medium");
        c.cardSize = CARD_SIZES.contains(cs) ? cs : "medium";
        String sm = Json.str(m, "sortMode", "name");
        c.sortMode = SORT_MODES.contains(sm) ? sm : "name";
        c.minimizeOnLaunch = Json.bool(m, "minimizeOnLaunch", false);
        String ac = Json.str(m, "accent", "mauve");
        c.accent = ACCENTS.contains(ac) ? ac : "mauve";
        c.animations = Json.bool(m, "animations", true);
        c.language = I18n.normalize(Json.str(m, "language", "en"));
        c.coverBackground = Json.bool(m, "coverBackground", true);
        c.showSplash = Json.bool(m, "showSplash", true);
        c.winMaximized = Json.bool(m, "winMaximized", true);
        c.winW = Json.dbl(m, "winW", 1280);
        c.winH = Json.dbl(m, "winH", 800);
        c.winX = Json.dbl(m, "winX", -1);
        c.winY = Json.dbl(m, "winY", -1);
        return c;
    }
}

/** Игра в библиотеке. */
final class Game {
    String id = UUID.randomUUID().toString();
    String name = "";
    String executablePath = "";
    String rootPath = "";          // папка игры, выбранная при добавлении
    String coverPath = "";
    long playtimeSeconds = 0;
    long lastPlayed = 0;           // unix-время, сек

    String protonVersion = Proton.SYSTEM_WINE;
    String winePrefix = "";
    List<String> dllOverrides = new ArrayList<>();

    boolean sendSteamAppId = true;
    String steamAppId = "480";

    boolean eosEnabled = false;    // переключатель «Epic Games (EOS)»
    boolean eosChecked = false;    // авто-определение уже выполнялось
    boolean epicSkipPrompt = false;// не спрашивать перед открытием страницы входа

    boolean msEnabled = false;     // переключатель «Аккаунт Microsoft (Xbox)»
    boolean msChecked = false;     // авто-определение уже выполнялось
    boolean msSkipPrompt = false;  // не спрашивать перед открытием страницы входа Microsoft

    boolean gogEnabled = false;    // переключатель «GOG Galaxy»
    boolean gogChecked = false;    // авто-определение уже выполнялось
    boolean gogSkipPrompt = false; // не спрашивать перед открытием страницы GOG

    String tsCommunity = "";       // сообщество на thunderstore.io: "" — не проверялось, "-" — нет
    String modProfile = "default"; // активная коллекция модов BepInEx
    boolean dxvkSkipPrompt = false; // не предлагать установить DXVK в префикс

    String steamStoreId = "";      // Steam AppID для ссылок, обложек и рейтинга (не путать с SteamAppId для запуска)
    String sgdbId = "";            // ID игры на SteamGridDB
    boolean favorite = false;

    boolean useMangoHud = false;
    boolean useGameMode = false;
    boolean noEsync = false;
    boolean noFsync = false;
    boolean protonLog = false;

    boolean networkAccess = true;
    boolean useWine3D = false;
    boolean useWayland = false;
    String customArgs = "";
    String envVars = "";           // KEY=VALUE через пробел

    Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("executablePath", executablePath);
        m.put("rootPath", rootPath);
        m.put("coverPath", coverPath);
        m.put("playtimeSeconds", playtimeSeconds);
        m.put("lastPlayed", lastPlayed);
        m.put("protonVersion", protonVersion);
        m.put("winePrefix", winePrefix);
        m.put("dllOverrides", new ArrayList<Object>(dllOverrides));
        m.put("sendSteamAppId", sendSteamAppId);
        m.put("steamAppId", steamAppId);
        m.put("eosEnabled", eosEnabled);
        m.put("eosChecked", eosChecked);
        m.put("epicSkipPrompt", epicSkipPrompt);
        m.put("msEnabled", msEnabled);
        m.put("msChecked", msChecked);
        m.put("msSkipPrompt", msSkipPrompt);
        m.put("gogEnabled", gogEnabled);
        m.put("gogChecked", gogChecked);
        m.put("gogSkipPrompt", gogSkipPrompt);
        m.put("tsCommunity", tsCommunity);
        m.put("modProfile", modProfile);
        m.put("dxvkSkipPrompt", dxvkSkipPrompt);
        m.put("steamStoreId", steamStoreId);
        m.put("sgdbId", sgdbId);
        m.put("favorite", favorite);
        m.put("useMangoHud", useMangoHud);
        m.put("useGameMode", useGameMode);
        m.put("noEsync", noEsync);
        m.put("noFsync", noFsync);
        m.put("protonLog", protonLog);
        m.put("networkAccess", networkAccess);
        m.put("useWine3D", useWine3D);
        m.put("useWayland", useWayland);
        m.put("customArgs", customArgs);
        m.put("envVars", envVars);
        return m;
    }

    static Game fromMap(Map<String, Object> m) {
        Game g = new Game();
        String id = Json.str(m, "id", "");
        if (!id.isBlank()) g.id = id;
        g.name = Json.str(m, "name", "");
        g.executablePath = Json.str(m, "executablePath", "");
        g.rootPath = Json.str(m, "rootPath", "");
        g.coverPath = Json.str(m, "coverPath", "");
        g.playtimeSeconds = Json.lng(m, "playtimeSeconds", 0);
        g.lastPlayed = Json.lng(m, "lastPlayed", 0);
        g.protonVersion = Proton.normalize(Json.str(m, "protonVersion", ""));
        g.winePrefix = Json.str(m, "winePrefix", "");
        for (Object o : Json.asList(m.get("dllOverrides"))) {
            if (o instanceof String s && !s.isBlank()) g.dllOverrides.add(s.trim());
        }
        // совместимость со старым форматом: steamOverlay
        g.sendSteamAppId = Json.bool(m, "sendSteamAppId", Json.bool(m, "steamOverlay", true));
        g.steamAppId = Json.str(m, "steamAppId", "480");
        Object eos = m.get("eosEnabled"); // в старом формате мог быть null = «не решено»
        g.eosEnabled = eos instanceof Boolean b && b;
        g.eosChecked = Json.bool(m, "eosChecked", eos instanceof Boolean);
        g.epicSkipPrompt = Json.bool(m, "epicSkipPrompt", false);
        g.msEnabled = Json.bool(m, "msEnabled", false);
        g.msChecked = Json.bool(m, "msChecked", false);
        g.msSkipPrompt = Json.bool(m, "msSkipPrompt", false);
        g.gogEnabled = Json.bool(m, "gogEnabled", false);
        g.gogChecked = Json.bool(m, "gogChecked", false);
        g.gogSkipPrompt = Json.bool(m, "gogSkipPrompt", false);
        g.tsCommunity = Json.str(m, "tsCommunity", "");
        g.modProfile = Json.str(m, "modProfile", "default");
        g.dxvkSkipPrompt = Json.bool(m, "dxvkSkipPrompt", false);
        g.steamStoreId = Web.parseId(Json.str(m, "steamStoreId", "")) == 0 ? "" : Json.str(m, "steamStoreId", "").trim();
        g.sgdbId = Web.parseId(Json.str(m, "sgdbId", "")) == 0 ? "" : Json.str(m, "sgdbId", "").trim();
        g.favorite = Json.bool(m, "favorite", false);
        g.useMangoHud = Json.bool(m, "useMangoHud", false);
        g.useGameMode = Json.bool(m, "useGameMode", false);
        g.noEsync = Json.bool(m, "noEsync", false);
        g.noFsync = Json.bool(m, "noFsync", false);
        g.protonLog = Json.bool(m, "protonLog", false);
        g.networkAccess = Json.bool(m, "networkAccess", true);
        g.useWine3D = Json.bool(m, "useWine3D", false);
        g.useWayland = Json.bool(m, "useWayland", false);
        g.customArgs = Json.str(m, "customArgs", "");
        g.envVars = Json.str(m, "envVars", "");
        return g;
    }
}

/** Хранилище: ~/.config/onlinefix-linux-launcher (с миграцией из ./config). */
final class Store {
    static volatile String lastError = null;

    private Store() {}

    static Path dir() {
        String override = System.getProperty("ofll.config");
        Path d;
        if (override != null && !override.isBlank()) {
            d = Paths.get(override);
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            Path base = (xdg != null && !xdg.isBlank()) ? Paths.get(xdg) : Sys.HOME.resolve(".config");
            d = base.resolve("onlinefix-linux-launcher");
        }
        try {
            boolean fresh = !Files.exists(d.resolve("games.json"));
            Files.createDirectories(d);
            if (fresh) migrateLegacy(d);
        } catch (IOException ignored) {}
        return d;
    }

    private static void migrateLegacy(Path target) {
        Path legacy = Paths.get(System.getProperty("user.dir"), "config");
        if (legacy.equals(target)) return;
        for (String f : new String[] {"config.json", "games.json"}) {
            Path src = legacy.resolve(f);
            Path dst = target.resolve(f);
            try {
                if (Files.isRegularFile(src) && !Files.exists(dst)) Files.copy(src, dst);
            } catch (IOException ignored) {}
        }
    }

    static Path logFile(Game g) {
        Path d = dir().resolve("logs");
        try { Files.createDirectories(d); } catch (IOException ignored) {}
        return d.resolve(g.id + ".log");
    }

    static Path coversDir() {
        Path d = dir().resolve("covers");
        try { Files.createDirectories(d); } catch (IOException ignored) {}
        return d;
    }

    private static void atomicWrite(Path f, String text) throws IOException {
        atomicWrite(f, text, false);
    }

    /** privateFile — права rw------- (в config.json лежит API-ключ). */
    private static void atomicWrite(Path f, String text, boolean privateFile) throws IOException {
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        if (privateFile) {
            try {
                Files.setPosixFilePermissions(tmp, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {}
        }
        try {
            Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void backupCorrupt(Path f) {
        try {
            Files.move(f, f.resolveSibling(f.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }

    static synchronized void saveConfig(GlobalConfig c) {
        try { atomicWrite(dir().resolve("config.json"), Json.write(c.toMap()), true); }
        catch (IOException e) { lastError = "Не удалось сохранить настройки: " + e.getMessage(); }
    }

    static synchronized GlobalConfig loadConfig() {
        Path f = dir().resolve("config.json");
        if (!Files.isRegularFile(f)) return new GlobalConfig();
        try {
            return GlobalConfig.fromMap(Json.asMap(Json.parse(Files.readString(f, StandardCharsets.UTF_8))));
        } catch (Exception e) {
            backupCorrupt(f);
            lastError = "Файл настроек повреждён и сохранён как config.json.bak. Использую настройки по умолчанию.";
            return new GlobalConfig();
        }
    }

    static synchronized void saveGames(List<Game> games) {
        List<Object> arr = new ArrayList<>();
        for (Game g : games) arr.add(g.toMap());
        try { atomicWrite(dir().resolve("games.json"), Json.write(arr)); }
        catch (IOException e) { lastError = "Не удалось сохранить библиотеку: " + e.getMessage(); }
    }

    static synchronized List<Game> loadGames() {
        List<Game> out = new ArrayList<>();
        Path f = dir().resolve("games.json");
        if (!Files.isRegularFile(f)) return out;
        try {
            Object root = Json.parse(Files.readString(f, StandardCharsets.UTF_8));
            for (Object o : Json.asList(root)) {
                Game g = Game.fromMap(Json.asMap(o));
                if (!g.name.isEmpty() || !g.executablePath.isEmpty()) out.add(g);
            }
        } catch (Exception e) {
            backupCorrupt(f);
            out.clear();
            lastError = "Файл библиотеки повреждён и сохранён как games.json.bak. Библиотека начата заново.";
        }
        return out;
    }

    static String writeTheme(String css, String tag) {
        Path f = dir().resolve("theme-" + tag + ".css");
        try {
            Files.writeString(f, css, StandardCharsets.UTF_8);
            return f.toUri().toString();
        } catch (IOException e) {
            return "";
        }
    }
}

/** Всё, что связано с Epic Games / EOS. */
final class Epic {
    static final String LOGIN_URL = "https://www.epicgames.com/id/login";
    static final Pattern URL_RE = Pattern.compile("https?://[^\\s\"'<>\\\\)\\]}]+");

    private static final Set<String> EOS_DIRS = Set.of("eos", "eossdk", "epiconlineservices");

    private Epic() {}

    /** Файл или папка, по которой видно, что игра использует Epic Online Services. */
    static boolean isEosFileName(String lowerName) {
        return (lowerName.startsWith("eos") && lowerName.endsWith(".dll"))
            || (lowerName.startsWith("eosoverlayrenderer") && lowerName.endsWith(".exe"));
    }

    static List<String> findEosFiles(Path root) {
        List<String> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        int[] visited = {0};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 8, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (++visited[0] > 60000 || found.size() >= 20) return FileVisitResult.TERMINATE;
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (isEosFileName(n)) found.add(root.relativize(f).toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (!d.equals(root)) {
                        String n = d.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (EOS_DIRS.contains(n)) found.add(root.relativize(d) + "/");
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        return found;
    }

    static String cleanUrl(String u) {
        int end = u.length();
        while (end > 0 && ".,;:!?".indexOf(u.charAt(end - 1)) >= 0) end--;
        return u.substring(0, end);
    }

    /**
     * Ссылка для входа/активации на сайте Epic. API-адреса (api.epicgames.dev и т.п.)
     * и любые чужие домены не подходят — их в браузере открывать не нужно и небезопасно.
     */
    static boolean isLoginUrl(String url) {
        try {
            URI u = new URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme())) return false;
            String h = u.getHost();
            if (h == null) return false;
            h = h.toLowerCase(Locale.ROOT);
            boolean epic = h.equals("epicgames.com") || h.endsWith(".epicgames.com");
            if (!epic || h.startsWith("api.")) return false;
            String p = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
            return p.startsWith("/id") || p.startsWith("/activate") || p.startsWith("/login") || p.startsWith("/authorize");
        } catch (Exception e) {
            return false;
        }
    }

    /** Ищет в строке лога ссылки Epic, пригодные для открытия в браузере. */
    static List<String> extractLoginUrls(String line) {
        List<String> res = new ArrayList<>();
        Matcher m = URL_RE.matcher(line);
        while (m.find()) {
            String u = cleanUrl(m.group());
            if (isLoginUrl(u)) res.add(u);
        }
        return res;
    }
}

/** Всё, что связано со входом через аккаунт Microsoft / Xbox. */
final class Microsoft {
    static final String LOGIN_URL = "https://login.live.com/";

    private static final Set<String> LOGIN_HOSTS = Set.of("login.live.com", "login.microsoftonline.com", "account.live.com");
    private static final String[] LOGIN_WORDS = {"authorize", "login", "signin", "remoteconnect", "devicelogin", "deviceauth", "consent", "activate"};

    private Microsoft() {}

    /** Файл, по которому видно, что игра использует Xbox Live / Microsoft Store (GDK, XSAPI, UWP). Имя — в нижнем регистре. */
    static boolean isMsFileName(String n) {
        return n.equals("microsoftgame.config") || n.equals("appxmanifest.xml")
            || (n.endsWith(".dll") && (n.startsWith("xgameruntime") || n.startsWith("microsoft.xbox")
                || n.startsWith("xboxservices") || n.startsWith("xsapi")));
    }

    static List<String> findFiles(Path root) {
        List<String> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        int[] visited = {0};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 8, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (++visited[0] > 60000 || found.size() >= 20) return FileVisitResult.TERMINATE;
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (isMsFileName(n)) found.add(root.relativize(f).toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        return found;
    }

    /**
     * Ссылка для входа или ввода кода активации у Microsoft. Только https и только страницы входа:
     * адреса токенов и API (user.auth.xboxlive.com и т.п.), чужие и похожие домены не подходят.
     */
    static boolean isLoginUrl(String url) {
        try {
            URI u = new URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null) return false;
            String h = u.getHost();
            if (h == null) return false;
            h = h.toLowerCase(Locale.ROOT);
            String p = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
            if (p.contains("token")) return false;
            if (LOGIN_HOSTS.contains(h)) {
                if (p.isEmpty() || p.equals("/")) return true;
                for (String w : LOGIN_WORDS) if (p.contains(w)) return true;
                return false;
            }
            if (h.equals("microsoft.com") || h.equals("www.microsoft.com"))
                return p.equals("/link") || p.startsWith("/link/") || p.startsWith("/devicelogin");
            if (h.equals("aka.ms"))
                return p.equals("/remoteconnect") || p.equals("/devicelogin") || p.equals("/link");
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ищет в строке лога ссылки Microsoft, пригодные для открытия в браузере. */
    static List<String> extractLoginUrls(String line) {
        List<String> res = new ArrayList<>();
        Matcher m = Epic.URL_RE.matcher(line);
        while (m.find()) {
            String u = Epic.cleanUrl(m.group());
            if (isLoginUrl(u)) res.add(u);
        }
        return res;
    }
}

/** Всё, что связано с GOG и GOG Galaxy. */
final class Gog {
    static final String LOGIN_URL = "https://www.gog.com/";

    private static final Set<String> LOGIN_HOSTS = Set.of("login.gog.com", "auth.gog.com");

    private Gog() {}

    /**
     * Файл, по которому видно, что игра использует Galaxy SDK (достижения, мультиплеер, друзья).
     * Обычные файлы установки GOG (goggame-*.info и т.п.) не учитываются — им вход не нужен.
     * Имя — в нижнем регистре.
     */
    static boolean isGogFileName(String n) {
        return n.equals("galaxy.dll") || n.equals("galaxy64.dll")
            || n.equals("galaxypeer.dll") || n.equals("galaxypeer64.dll")
            || n.equals("goggalaxyhooks.dll") || n.equals("gog_api_link.dll")
            || n.equals("galaxycommunication.exe");
    }

    static List<String> findFiles(Path root) {
        List<String> found = new ArrayList<>();
        if (root == null || !Files.isDirectory(root)) return found;
        int[] visited = {0};
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 8, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    if (++visited[0] > 60000 || found.size() >= 20) return FileVisitResult.TERMINATE;
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (isGogFileName(n)) found.add(root.relativize(f).toString());
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        return found;
    }

    /**
     * Ссылка для входа в аккаунт GOG. Только https и только страницы входа: адреса токенов,
     * ссылки с кодом авторизации и чужие домены не подходят.
     */
    static boolean isLoginUrl(String url) {
        try {
            URI u = new URI(url);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null) return false;
            String h = u.getHost();
            if (h == null) return false;
            h = h.toLowerCase(Locale.ROOT);
            String p = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
            String q = u.getRawQuery() == null ? "" : u.getRawQuery().toLowerCase(Locale.ROOT);
            if (p.contains("token") || q.contains("code=") || q.contains("access_token")) return false;
            if (LOGIN_HOSTS.contains(h))
                return p.isEmpty() || p.equals("/") || p.startsWith("/auth") || p.contains("login");
            if (h.equals("gog.com") || h.equals("www.gog.com"))
                return p.startsWith("/login") || p.startsWith("/activate");
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** Ищет в строке лога ссылки GOG, пригодные для открытия в браузере. */
    static List<String> extractLoginUrls(String line) {
        List<String> res = new ArrayList<>();
        Matcher m = Epic.URL_RE.matcher(line);
        while (m.find()) {
            String u = Epic.cleanUrl(m.group());
            if (isLoginUrl(u)) res.add(u);
        }
        return res;
    }
}

/** Сеть: Steam (поиск AppID), ProtonDB (рейтинг), SteamGridDB (обложки). Ключ SteamGridDB нигде не пишется в логи. */
final class Web {
    // адреса вынесены в поля, чтобы их можно было подменить в тестах
    static volatile String sgdbBase = "https://www.steamgriddb.com/api/v2";
    static volatile String steamSearchUrl = "https://store.steampowered.com/api/storesearch/";
    static volatile String protonDbBase = "https://www.protondb.com/api/v1/reports/summaries/";
    static volatile boolean allowLocalhost = false;

    private static final String UA = "OnlineFix-Linux-Launcher/1.0";
    private static final int MAX_JSON = 4 << 20;
    private static final int MAX_IMAGE = 12 << 20;

    private static final HttpClient FOLLOW = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    /** Для запросов с ключом: редиректы запрещены, чтобы ключ не ушёл на другой адрес. */
    private static final HttpClient STRICT = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).build();

    private Web() {}

    static final class HttpStatusException extends IOException {
        final int status;
        HttpStatusException(int status) { super("HTTP " + status); this.status = status; }
    }

    static final class Found {
        final long id;
        final String name;
        Found(long id, String name) { this.id = id; this.name = name; }
    }

    static final class Rating {
        final String tier;
        final int total;
        final String confidence;
        Rating(String tier, int total, String confidence) { this.tier = tier; this.total = total; this.confidence = confidence; }
    }

    // ---------------------------------------------------------------- тексты
    static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    /** Из имени папки («Valheim (2026) [OnlineFix] v0.220») получает название для поиска («Valheim»). */
    static String cleanTitle(String raw) {
        String orig = raw == null ? "" : raw.trim();
        String s = orig.replaceAll("[\\[({][^\\])}]*[\\])}]", " ");
        s = s.replaceAll("(?i)\\b(?:v|ver|version|build)[ ._-]?\\d[\\w.\\-]*", " ");
        s = s.replaceAll("(?i)\\b(?:repack|portable|online[ -]?fix|fitgirl|dodi)\\b", " ");
        s = s.trim();
        if (!s.contains(" ")) s = s.replaceAll("[_.]+", " ");
        s = s.replaceAll("\\s+", " ").trim();
        return s.isEmpty() ? orig : s;
    }

    /** Названия «похожи»: одно содержит другое и они сравнимы по длине. */
    static boolean related(String a, String b) {
        String x = norm(a), y = norm(b);
        if (x.isEmpty() || y.isEmpty()) return false;
        if (x.equals(y)) return true;
        int min = Math.min(x.length(), y.length()), max = Math.max(x.length(), y.length());
        return min * 2 >= max && (x.startsWith(y) || y.startsWith(x) || (min >= 5 && (x.contains(y) || y.contains(x))));
    }

    static boolean validKey(String k) {
        return k != null && k.matches("[A-Za-z0-9_\\-]{8,128}");
    }

    static long parseId(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (!t.matches("\\d{1,10}")) return 0;
        return Long.parseLong(t);
    }

    static String tierRu(String tier) {
        switch (tier == null ? "" : tier.toLowerCase(Locale.ROOT)) {
            case "native": return "Родная";
            case "platinum": return "Платина";
            case "gold": return "Золото";
            case "silver": return "Серебро";
            case "bronze": return "Бронза";
            case "borked": return "Не работает";
            case "pending": return "Мало данных";
            default: return tier == null || tier.isEmpty() ? "Нет данных" : tier;
        }
    }

    // ---------------------------------------------------------------- HTTP
    private static String getText(String url, String bearer) throws IOException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(12))
            .header("User-Agent", UA).header("Accept", "application/json").GET();
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        try {
            HttpResponse<InputStream> r = (bearer != null ? STRICT : FOLLOW).send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body()) {
                if (r.statusCode() != 200) throw new HttpStatusException(r.statusCode());
                byte[] data = in.readNBytes(MAX_JSON + 1);
                if (data.length > MAX_JSON) throw new IOException("Ответ сервера слишком большой");
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запрос прерван");
        } catch (IOException e) {
            String host = URI.create(url).getHost();
            throw new IOException("Нет связи с сервером " + host + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        }
    }

    private static Map<String, Object> parseObject(String body) throws IOException {
        try {
            return Json.asMap(Json.parse(body));
        } catch (IllegalArgumentException e) {
            throw new IOException("Сервер вернул неожиданный ответ");
        }
    }

    // ---------------------------------------------------------------- Steam
    /** Ищет игру в магазине Steam. Возвращает совпадение только если название действительно похоже. */
    static Found steamSearch(String term) throws IOException {
        if (term == null || term.isBlank()) return null;
        String body = getText(steamSearchUrl + "?term=" + enc(term) + "&l=english&cc=US", null);
        Found firstRelated = null;
        String want = norm(term);
        for (Object o : Json.asList(parseObject(body).get("items"))) {
            Map<String, Object> m = Json.asMap(o);
            long id = Json.lng(m, "id", 0);
            String name = Json.str(m, "name", "");
            String type = Json.str(m, "type", "app");
            if (id <= 0 || name.isEmpty() || !type.equals("app")) continue;
            if (norm(name).equals(want)) return new Found(id, name);
            if (firstRelated == null && related(name, term)) firstRelated = new Found(id, name);
        }
        return firstRelated;
    }

    static List<String> steamCoverUrls(long appId) {
        String a = "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/" + appId + "/";
        String c = "https://cdn.cloudflare.steamstatic.com/steam/apps/" + appId + "/";
        return List.of(a + "header.jpg", c + "header.jpg", a + "library_600x900.jpg", c + "library_600x900.jpg");
    }

    // ---------------------------------------------------------------- ProtonDB
    /** Рейтинг совместимости. null — у игры на ProtonDB нет данных. */
    static Rating protonRating(long appId) throws IOException {
        String body;
        try {
            body = getText(protonDbBase + appId + ".json", null);
        } catch (HttpStatusException e) {
            if (e.status == 404) return null;
            throw e;
        }
        Map<String, Object> m = parseObject(body);
        String tier = Json.str(m, "tier", "").toLowerCase(Locale.ROOT);
        if (tier.isEmpty()) return null;
        return new Rating(tier, (int) Json.lng(m, "total", 0), Json.str(m, "confidence", ""));
    }

    // ---------------------------------------------------------------- SteamGridDB
    private static IOException sgdbError(HttpStatusException e) {
        if (e.status == 401 || e.status == 403) return new IOException("SteamGridDB не принял API-ключ (код " + e.status + "). Проверьте ключ в настройках лаунчера.");
        if (e.status == 429) return new IOException("SteamGridDB: слишком много запросов, попробуйте позже.");
        return new IOException("SteamGridDB ответил кодом " + e.status + ".");
    }

    /** ID игры на SteamGridDB по названию; 0 — не найдено. */
    static long sgdbSearchId(String key, String term) throws IOException {
        if (term == null || term.isBlank()) return 0;
        String body;
        try {
            body = getText(sgdbBase + "/search/autocomplete/" + enc(term), key);
        } catch (HttpStatusException e) {
            if (e.status == 404) return 0;
            throw sgdbError(e);
        }
        long firstRelated = 0;
        String want = norm(term);
        for (Object o : Json.asList(parseObject(body).get("data"))) {
            Map<String, Object> m = Json.asMap(o);
            long id = Json.lng(m, "id", 0);
            String name = Json.str(m, "name", "");
            if (id <= 0 || name.isEmpty()) continue;
            if (norm(name).equals(want)) return id;
            if (firstRelated == 0 && related(name, term)) firstRelated = id;
        }
        return firstRelated;
    }

    /** Ссылка на лучшую широкую обложку (PNG/JPEG). path: «/grids/steam/220» или «/grids/game/45». */
    static String sgdbBestGrid(String key, String path) throws IOException {
        String[] queries = {"?dimensions=460x215,920x430&types=static&nsfw=false&humor=false", "?types=static&nsfw=false&humor=false"};
        for (String q : queries) {
            String body;
            try {
                body = getText(sgdbBase + path + q, key);
            } catch (HttpStatusException e) {
                if (e.status == 404) return null;
                throw sgdbError(e);
            }
            String best = null;
            long bestScore = Long.MIN_VALUE;
            for (Object o : Json.asList(parseObject(body).get("data"))) {
                Map<String, Object> m = Json.asMap(o);
                String url = Json.str(m, "url", "");
                String low = url.toLowerCase(Locale.ROOT);
                if (!(low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg"))) continue;
                long sc = Json.lng(m, "score", 0);
                if (best == null || sc > bestScore) { best = url; bestScore = sc; }
            }
            if (best != null) return best;
        }
        return null;
    }

    // ---------------------------------------------------------------- картинки
    static boolean trustedImageUri(URI u) {
        if (u == null || u.getHost() == null) return false;
        String h = u.getHost().toLowerCase(Locale.ROOT);
        if (allowLocalhost && (h.equals("127.0.0.1") || h.equals("localhost"))) return true;
        if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null) return false;
        if (h.equals("steamgriddb.com") || h.endsWith(".steamgriddb.com")) return true;
        if (h.endsWith(".steamstatic.com") || h.equals("steamcdn-a.akamaihd.net")) return true;
        String p = u.getPath() == null ? "" : u.getPath();
        return h.equals("s3.amazonaws.com") && p.startsWith("/steamgriddb/");
    }

    /** Расширение по содержимому файла (не по ссылке); null — не поддерживаемая картинка. */
    static String imageExt(byte[] d) {
        if (d.length < 8) return null;
        if ((d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return ".png";
        if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return ".jpg";
        if (d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') return ".gif";
        if (d[0] == 'B' && d[1] == 'M') return ".bmp";
        return null;
    }

    /** Скачивает картинку в dir/baseName.ext (прежние файлы обложки этой игры заменяются). */
    static Path downloadImage(String url, Path dir, String baseName) throws IOException {
        URI u;
        try {
            u = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException("Некорректная ссылка на картинку");
        }
        if (!trustedImageUri(u)) throw new IOException("Адрес картинки не входит в список доверенных: " + u.getHost());
        HttpRequest req = HttpRequest.newBuilder(u).timeout(java.time.Duration.ofSeconds(20)).header("User-Agent", UA).GET().build();
        byte[] data;
        try {
            HttpResponse<InputStream> r = FOLLOW.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body()) {
                if (r.statusCode() != 200) throw new HttpStatusException(r.statusCode());
                if (!trustedImageUri(r.uri())) throw new IOException("Сервер перенаправил на недоверенный адрес");
                data = in.readNBytes(MAX_IMAGE + 1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Загрузка прервана");
        }
        if (data.length > MAX_IMAGE) throw new IOException("Картинка слишком большая");
        String ext = imageExt(data);
        if (ext == null) throw new IOException("Сервер вернул не картинку");
        Files.createDirectories(dir);
        try (var old = Files.list(dir)) {
            for (Path p : (Iterable<Path>) old::iterator) {
                String n = p.getFileName().toString();
                if (n.startsWith(baseName + ".")) Files.deleteIfExists(p);
            }
        }
        Path dst = dir.resolve(baseName + ext);
        Path tmp = dir.resolve(baseName + ".part");
        Files.write(tmp, data);
        Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
        return dst;
    }
}

/** Подбор обложки и AppID: SteamGridDB (если есть ключ), затем картинки Steam. */
final class Covers {
    static final class Result {
        long steamId;
        String sgdbId = "";
        Path cover;
        String source = "";
        String warning;
    }

    private Covers() {}

    /**
     * Ищет игру в Steam (если AppID не задан) и, если нужно, скачивает обложку. Ничего не меняет в Game —
     * результат применяет вызывающий (в потоке интерфейса).
     */
    static Result fetch(String name, String steamIdIn, String sgdbIdIn, String key, boolean needCover, String baseName, Path dir) {
        Result r = new Result();
        String title = Web.cleanTitle(name);
        long steam = Web.parseId(steamIdIn);
        if (steam == 0) {
            try {
                Web.Found f = Web.steamSearch(title);
                if (f != null) steam = f.id;
            } catch (IOException e) {
                r.warning = e.getMessage();
            }
        }
        r.steamId = steam;
        if (!needCover) return r;

        if (Web.validKey(key)) {
            try {
                String url = steam != 0 ? Web.sgdbBestGrid(key, "/grids/steam/" + steam) : null;
                if (url == null) {
                    long gid = Web.parseId(sgdbIdIn);
                    if (gid == 0) gid = Web.sgdbSearchId(key, title);
                    if (gid != 0) {
                        r.sgdbId = Long.toString(gid);
                        url = Web.sgdbBestGrid(key, "/grids/game/" + gid);
                    }
                }
                if (url != null) {
                    r.cover = Web.downloadImage(url, dir, baseName);
                    r.source = "SteamGridDB";
                    return r;
                }
            } catch (IOException e) {
                r.warning = e.getMessage();
            }
        }
        if (steam != 0) {
            for (String url : Web.steamCoverUrls(steam)) {
                try {
                    r.cover = Web.downloadImage(url, dir, baseName);
                    r.source = "Steam";
                    return r;
                } catch (IOException ignored) {
                    // пробуем следующий адрес
                }
            }
        }
        return r;
    }
}

/** Ссылки на страницы игры: прямые, если известен Steam AppID, иначе — поиск по названию. */
final class Links {
    enum Site { STEAM, STEAMDB, PROTONDB, SGDB, GOG }

    private Links() {}

    static String url(Site site, Game g) {
        boolean has = Web.parseId(g.steamStoreId) != 0;
        String id = has ? Long.toString(Web.parseId(g.steamStoreId)) : "";
        String q = Web.enc(Web.cleanTitle(g.name));
        switch (site) {
            case STEAM:
                return has ? "https://store.steampowered.com/app/" + id + "/" : "https://store.steampowered.com/search/?term=" + q;
            case STEAMDB:
                return has ? "https://steamdb.info/app/" + id + "/" : "https://steamdb.info/search/?a=app&q=" + q;
            case PROTONDB:
                return has ? "https://www.protondb.com/app/" + id : "https://www.protondb.com/search?q=" + q;
            case GOG:
                return "https://www.gog.com/en/games?query=" + q;
            default:
                return Web.parseId(g.sgdbId) != 0
                    ? "https://www.steamgriddb.com/game/" + Web.parseId(g.sgdbId)
                    : "https://www.steamgriddb.com/search/grids?term=" + q;
        }
    }
}

/** Поиск, установка и выбор Proton / Wine. */
final class Proton {
    static final String SYSTEM_WINE = "System Wine";
    private static final String LEGACY_SYSTEM = "GE-Proton Latest (Системный)";
    private static final String LEGACY_SYSTEM_RU = "Системный Wine";
    private static final String UA = "OnlineFix-Linux-Launcher";

    private Proton() {}

    static String normalize(String s) {
        if (s == null || s.isBlank() || s.equals(LEGACY_SYSTEM) || s.equals(LEGACY_SYSTEM_RU)) return SYSTEM_WINE;
        return s;
    }

    static boolean isSystem(String s) {
        return normalize(s).equals(SYSTEM_WINE);
    }

    private static List<Path> roots(GlobalConfig c) {
        List<Path> r = new ArrayList<>();
        r.add(Paths.get(c.protonPath));
        for (String steam : new String[] {".steam/steam", ".steam/root", ".local/share/Steam"}) {
            Path s = Sys.HOME.resolve(steam);
            r.add(s.resolve("compatibilitytools.d"));
            r.add(s.resolve("steamapps/common"));
        }
        return r;
    }

    static boolean isProtonDir(Path d) {
        return d != null && Files.isRegularFile(d.resolve("proton"));
    }

    /** Имя → папка с файлом `proton` или со сборкой Wine (bin/wine). Папка из настроек имеет приоритет. */
    static Map<String, Path> discover(GlobalConfig c) {
        Map<String, Path> res = new LinkedHashMap<>();
        Path main = Paths.get(c.protonPath);
        for (Path root : roots(c)) {
            File[] dirs = root.toFile().listFiles(File::isDirectory);
            if (dirs == null) continue;
            Arrays.sort(dirs, Comparator.comparing(File::getName, Comparator.reverseOrder()));
            for (File d : dirs) {
                boolean proton = new File(d, "proton").isFile();
                boolean wine = root.equals(main) && Files.isExecutable(d.toPath().resolve("bin/wine"));
                if (proton || wine) res.putIfAbsent(d.getName(), d.toPath());
            }
        }
        return res;
    }

    static List<String> available(GlobalConfig c) {
        List<String> l = new ArrayList<>();
        l.add(SYSTEM_WINE);
        l.addAll(discover(c).keySet());
        return l;
    }

    static Path resolve(GlobalConfig c, String name) {
        if (isSystem(name)) return null;
        return discover(c).get(name);
    }

    /** wine из состава Proton — нужен winetricks для префикса Proton. */
    static Path wineBinary(Path protonDir) {
        Path w = protonDir.resolve("bin/wine");
        if (Files.isExecutable(w) && !isProtonDir(protonDir)) return w;
        Path a = protonDir.resolve("files/bin/wine");
        if (Files.isExecutable(a)) return a;
        Path b = protonDir.resolve("dist/bin/wine");
        return Files.isExecutable(b) ? b : null;
    }

    // ---------- список версий и установка ----------

    enum Family { GE, WINE }

    static final class Asset {
        String name = "", url = "", digest = "";
        long size;

        @Override public String toString() { return name; }
    }

    static final class Release {
        String tag = "";
        final List<Asset> archives = new ArrayList<>();
        Asset sha;

        @Override public String toString() { return tag; }
    }

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*");
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(java.time.Duration.ofSeconds(15)).build();

    private static boolean trustedHost(String url) {
        try {
            URI u = new URI(url);
            String h = u.getHost();
            return h != null && "https".equals(u.getScheme())
                && (h.equals("github.com") || h.endsWith(".github.com")
                    || h.endsWith(".githubusercontent.com"));
        } catch (Exception e) {
            return false;
        }
    }

    static String repo(Family f) {
        return f == Family.GE ? "GloriousEggroll/proton-ge-custom" : "Kron4ek/Wine-Builds";
    }

    private static boolean wantedArchive(Family f, String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (f == Family.GE) return n.endsWith(".tar.gz");
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        return n.endsWith(".tar.xz") && n.startsWith("wine-")
            && (arm ? (n.contains("arm64") || n.contains("aarch64")) : n.contains("amd64"));
    }

    private static HttpRequest get(String url, String accept, int timeoutSec) {
        return HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", UA)
            .header("Accept", accept)
            .timeout(java.time.Duration.ofSeconds(timeoutSec))
            .GET().build();
    }

    static String httpText(String url, boolean api) throws IOException {
        try {
            HttpResponse<String> r = HTTP.send(get(url, api ? "application/vnd.github+json" : "*/*", 30),
                HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 403 || r.statusCode() == 429)
                throw new IOException(I18n.tr("GitHub временно ограничил число запросов. Подождите несколько минут и повторите."));
            if (r.statusCode() != 200) throw new IOException(I18n.fmt("GitHub ответил кодом {0}", r.statusCode()));
            return r.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(I18n.tr("Запрос прерван"));
        }
    }

    /** Список последних релизов (новые сверху). */
    static List<Release> listReleases(Family f) throws IOException {
        String body = httpText("https://api.github.com/repos/" + repo(f) + "/releases?per_page=40", true);
        List<Release> out = new ArrayList<>();
        try {
            for (Object o : Json.asList(Json.parse(body))) {
                Map<String, Object> rel = Json.asMap(o);
                if (Json.bool(rel, "draft", false)) continue;
                Release r = new Release();
                r.tag = Json.str(rel, "tag_name", "");
                for (Object ao : Json.asList(rel.get("assets"))) {
                    Map<String, Object> a = Json.asMap(ao);
                    Asset as = new Asset();
                    as.name = Json.str(a, "name", "");
                    as.url = Json.str(a, "browser_download_url", "");
                    as.size = Json.lng(a, "size", 0);
                    as.digest = Json.str(a, "digest", "");
                    if (!SAFE_NAME.matcher(as.name).matches() || !trustedHost(as.url)) continue;
                    if (wantedArchive(f, as.name)) r.archives.add(as);
                    else if (as.name.endsWith(".sha512sum")) r.sha = as;
                }
                r.archives.sort(Comparator.comparing((Asset x) -> x.name.contains("wow64")).thenComparing(x -> x.name.length()));
                if (!r.tag.isEmpty() && !r.archives.isEmpty()) out.add(r);
            }
        } catch (IllegalArgumentException e) {
            throw new IOException(I18n.tr("Сервер вернул неожиданный ответ"));
        }
        return out;
    }

    /** Скачивает файл, считая контрольные суммы на лету. progress = from + span * доля. */
    static void fetchFile(String url, Path dst, long expected, String label, Consumer<String> status, DoubleConsumer progress,
                          double from, double span, BooleanSupplier cancelled, MessageDigest... digests) throws Exception {
        if (!trustedHost(url)) throw new IOException("Untrusted download host");
        Files.deleteIfExists(dst);
        HttpResponse<InputStream> r = HTTP.send(get(url, "application/octet-stream", 120), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) {
            r.body().close();
            throw new IOException(I18n.fmt("Сервер ответил кодом {0}", r.statusCode()));
        }
        long total = r.headers().firstValueAsLong("Content-Length").orElse(expected);
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0, lastUi = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelled.getAsBoolean()) throw new CancellationException("cancelled");
                out.write(buf, 0, n);
                for (MessageDigest d : digests) d.update(buf, 0, n);
                done += n;
                long now = System.currentTimeMillis();
                if (now - lastUi > 150) {
                    lastUi = now;
                    if (total > 0) progress.accept(from + span * Math.min(1.0, (double) done / total));
                    status.accept(I18n.fmt("Скачиваю {0}: {1} МБ из {2} МБ", label, done / 1048576, total > 0 ? Long.toString(total / 1048576) : "?"));
                }
            }
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Достаёт из файла .sha512sum сумму для нужного архива (или первую, если имя не найдено). */
    private static String shaFromFile(Asset shaFile, String archiveName) {
        try {
            String body = httpText(shaFile.url, false);
            Pattern p = Pattern.compile("\\b[0-9a-fA-F]{128}\\b");
            String first = "";
            for (String line : body.split("\\R")) {
                Matcher m = p.matcher(line);
                if (!m.find()) continue;
                if (first.isEmpty()) first = m.group().toLowerCase(Locale.ROOT);
                if (line.contains(archiveName)) return m.group().toLowerCase(Locale.ROOT);
            }
            return first;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static boolean tarListOk(Path archive) {
        try {
            ProcessBuilder pb = new ProcessBuilder("tar", "-tf", archive.toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            return pb.start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /** Скачивает архив Proton/Wine, проверяет его и распаковывает в root. Возвращает имя папки. */
    static String install(Path root, Asset a, Asset shaFile, Consumer<String> status, DoubleConsumer progress,
                          BooleanSupplier cancelled) throws Exception {
        if (Sys.which("tar") == null) throw new IOException(I18n.tr("Не найдена утилита tar."));
        Files.createDirectories(root);
        Path tmp = root.resolve(".download-" + a.name + ".part");
        Path unpack = null;
        try {
            boolean mismatch = true;
            for (int attempt = 1; attempt <= 2 && mismatch; attempt++) {
                MessageDigest s256 = MessageDigest.getInstance("SHA-256"), s512 = MessageDigest.getInstance("SHA-512");
                status.accept(I18n.fmt("Скачиваю {0}…", a.name));
                fetchFile(a.url, tmp, a.size, a.name, status, progress, 0, 0.85, cancelled, s256, s512);
                if (a.size > 0 && Files.size(tmp) != a.size) {
                    if (attempt < 2) continue;
                    throw new IOException(I18n.tr("Архив скачан не полностью. Проверьте подключение и повторите."));
                }
                status.accept(I18n.tr("Проверяю контрольную сумму…"));
                String want256 = a.digest.startsWith("sha256:") ? a.digest.substring(7).toLowerCase(Locale.ROOT) : "";
                String want512 = want256.isEmpty() && shaFile != null ? shaFromFile(shaFile, a.name) : "";
                if (want256.isEmpty() && want512.isEmpty()) {
                    mismatch = false;   // сравнивать не с чем
                } else if (!want256.isEmpty()) {
                    mismatch = !want256.equals(hex(s256.digest()));
                } else {
                    mismatch = !want512.equals(hex(s512.digest()));
                }
                if (mismatch && attempt < 2) status.accept(I18n.tr("Контрольная сумма не совпала, скачиваю заново…"));
            }
            if (mismatch) {
                // сумма могла относиться к другому файлу — проверяем, что сам архив цел
                status.accept(I18n.tr("Проверяю целостность архива…"));
                if (!tarListOk(tmp))
                    throw new IOException(I18n.tr("Контрольная сумма архива не совпала — файл повреждён. Попробуйте ещё раз или выберите другую версию."));
                status.accept(I18n.tr("Контрольная сумма не подтвердилась, но архив цел — продолжаю установку."));
            }

            status.accept(I18n.tr("Распаковываю…"));
            progress.accept(0.9);
            unpack = Files.createTempDirectory(root, ".unpack-");
            ProcessBuilder pb = new ProcessBuilder("tar", "-xf", tmp.toString(), "-C", unpack.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) throw new IOException(I18n.tr("Не удалось распаковать архив: ") + out.trim());

            List<Path> kids = new ArrayList<>();
            try (var s = Files.list(unpack)) {
                for (Path k : (Iterable<Path>) s::iterator) kids.add(k);
            }
            Path src;
            String name;
            if (kids.size() == 1 && Files.isDirectory(kids.get(0))) {
                src = kids.get(0);
                name = src.getFileName().toString();
            } else {
                src = unpack;
                name = a.name.replaceAll("\\.tar\\.(gz|xz|zst|bz2)$", "");
            }
            if (!SAFE_NAME.matcher(name).matches()) throw new IOException(I18n.tr("Недопустимое имя папки в архиве."));
            if (!isProtonDir(src) && !Files.isExecutable(src.resolve("bin/wine")))
                throw new IOException(I18n.tr("В архиве не найден Proton или Wine."));
            Path dst = root.resolve(name);
            if (Files.exists(dst)) Fs.deleteTree(dst);
            Files.move(src, dst);
            progress.accept(1.0);
            return name;
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            if (unpack != null) Fs.deleteTree(unpack);
        }
    }

}

/** DXVK (Direct3D → Vulkan) для префиксов обычного Wine: у Proton он уже встроен. */
final class Dxvk {
    private static final String API = "https://api.github.com/repos/doitsujin/dxvk/releases/latest";

    private Dxvk() {}

    static Path marker(Path prefix) {
        return prefix.resolve(".ofll-dxvk");
    }

    static boolean isInstalled(Path prefix) {
        if (prefix == null) return false;
        if (Files.isRegularFile(marker(prefix))) return true;
        try {
            Path d = prefix.resolve("drive_c/windows/system32/d3d11.dll");
            return Files.isRegularFile(d) && Files.size(d) > 1_500_000;
        } catch (IOException e) {
            return false;
        }
    }

    static String version(Path prefix) {
        try {
            return Files.isRegularFile(marker(prefix)) ? Files.readString(marker(prefix)).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Есть ли в системе Vulkan-драйвер (файлы ICD). */
    static boolean vulkanDriverFound() {
        for (String d : new String[] {"/usr/share/vulkan/icd.d", "/etc/vulkan/icd.d", "/usr/local/share/vulkan/icd.d"}) {
            File[] f = new File(d).listFiles((dir, n) -> n.endsWith(".json"));
            if (f != null && f.length > 0) return true;
        }
        return false;
    }

    /** Скачивает последнюю DXVK и кладёт её библиотеки в префикс. Возвращает версию. */
    static String install(Path prefix, Path wine, Consumer<String> status, DoubleConsumer progress,
                          BooleanSupplier cancelled) throws Exception {
        if (Sys.which("tar") == null) throw new IOException(I18n.tr("Не найдена утилита tar."));
        Path sys32 = prefix.resolve("drive_c/windows/system32");
        if (!Files.isDirectory(sys32)) {
            if (wine == null) throw new IOException(I18n.tr("Префикс Wine ещё не создан, а Wine не найден. Запустите игру один раз и повторите."));
            status.accept(I18n.tr("Создаю префикс Wine…"));
            Files.createDirectories(prefix);
            ProcessBuilder pb = new ProcessBuilder(wine.toString(), "wineboot", "-u");
            pb.environment().put("WINEPREFIX", prefix.toString());
            pb.environment().put("WINEDLLOVERRIDES", "mscoree,mshtml=d;winemenubuilder.exe=d");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process pr = pb.start();
            long end = System.currentTimeMillis() + 180_000;
            while (pr.isAlive()) {
                if (cancelled.getAsBoolean()) {
                    pr.destroyForcibly();
                    throw new CancellationException("cancelled");
                }
                if (System.currentTimeMillis() > end) {
                    pr.destroyForcibly();
                    break;
                }
                Thread.sleep(300);
            }
            if (!Files.isDirectory(sys32)) throw new IOException(I18n.tr("Не удалось создать префикс Wine."));
        }

        status.accept(I18n.tr("Ищу последнюю версию DXVK…"));
        Map<String, Object> rel = Json.asMap(Json.parse(Proton.httpText(API, true)));
        String tag = Json.str(rel, "tag_name", "");
        Proton.Asset asset = null;
        for (Object o : Json.asList(rel.get("assets"))) {
            Map<String, Object> a = Json.asMap(o);
            String n = Json.str(a, "name", "");
            if (n.matches("dxvk-\\d[\\d.]*\\.tar\\.gz")) {
                asset = new Proton.Asset();
                asset.name = n;
                asset.url = Json.str(a, "browser_download_url", "");
                asset.size = Json.lng(a, "size", 0);
                asset.digest = Json.str(a, "digest", "");
                break;
            }
        }
        if (asset == null) throw new IOException(I18n.tr("В релизе DXVK не найден архив."));

        Path tmpDir = Files.createTempDirectory("ofll-dxvk");
        try {
            Path tgz = tmpDir.resolve(asset.name);
            MessageDigest s256 = MessageDigest.getInstance("SHA-256");
            Proton.fetchFile(asset.url, tgz, asset.size, asset.name, status, progress, 0, 0.8, cancelled, s256);
            if (asset.digest.startsWith("sha256:") && !asset.digest.substring(7).equalsIgnoreCase(Proton.hex(s256.digest())))
                throw new IOException(I18n.tr("Контрольная сумма архива не совпала — файл повреждён. Попробуйте ещё раз или выберите другую версию."));
            status.accept(I18n.tr("Распаковываю…"));
            progress.accept(0.85);
            Path out = tmpDir.resolve("x");
            Files.createDirectories(out);
            ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tgz.toString(), "-C", out.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) throw new IOException(I18n.tr("Не удалось распаковать архив: ") + log.trim());

            Path x64 = null, x32 = null;
            try (var w = Files.walk(out, 3)) {
                for (Path d : (Iterable<Path>) w::iterator) {
                    if (!Files.isDirectory(d)) continue;
                    String n = d.getFileName().toString();
                    if (n.equals("x64")) x64 = d;
                    else if (n.equals("x32")) x32 = d;
                }
            }
            if (x64 == null && x32 == null) throw new IOException(I18n.tr("В архиве DXVK не найдены библиотеки."));
            status.accept(I18n.tr("Копирую библиотеки DXVK в префикс…"));
            Path wow = prefix.resolve("drive_c/windows/syswow64");
            if (x64 != null) copyDlls(x64, sys32);
            if (x32 != null) copyDlls(x32, Files.isDirectory(wow) ? wow : sys32);
            Files.writeString(marker(prefix), tag.isEmpty() ? asset.name : tag);
            progress.accept(1.0);
            return tag.isEmpty() ? asset.name : tag;
        } finally {
            Fs.deleteTree(tmpDir);
        }
    }

    private static void copyDlls(Path from, Path to) throws IOException {
        try (var s = Files.list(from)) {
            for (Path f : (Iterable<Path>) s::iterator) {
                if (f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dll"))
                    Files.copy(f, to.resolve(f.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}

/** Формирование команды запуска и работа с процессом игры. */
final class Runner {
    private Runner() {}

    static final class LaunchException extends Exception {
        LaunchException(String m) { super(m); }
    }

    static final class LaunchPlan {
        final List<String> command = new ArrayList<>();
        final Map<String, String> env = new LinkedHashMap<>();
        final List<String> warnings = new ArrayList<>();
        Path workDir;
        Path prefixDir;
        Path protonDir;   // null — системный Wine или сборка Wine
        Path wineDir;     // папка сборки Wine (не Proton), иначе null
        boolean needsDxvk; // выбран Vulkan, но DXVK в префиксе не установлен
    }

    interface Listener {
        void log(String line);
        void epicUrl(String url);
        /** Ссылка входа Microsoft из лога игры (необязательный метод). */
        default void msUrl(String url) {}
        /** Ссылка входа GOG из лога игры (необязательный метод). */
        default void gogUrl(String url) {}
    }

    interface ExitHandler {
        void onExit(long seconds, int exitCode);
    }

    static Path prefixDir(Game g, GlobalConfig c) {
        return (g.winePrefix != null && !g.winePrefix.isBlank())
            ? Paths.get(g.winePrefix)
            : Paths.get(c.prefixesPath, g.id);
    }

    /** Реальная папка WINEPREFIX (у Proton — вложенный pfx). */
    static Path winePrefix(Game g, GlobalConfig c) {
        Path p = prefixDir(g, c);
        Path rd = Proton.isSystem(g.protonVersion) ? null : Proton.resolve(c, g.protonVersion);
        return Proton.isProtonDir(rd) ? p.resolve("pfx") : p;
    }

    /** Исполняемый файл wine для системного Wine или сборки Wine; для Proton — null. */
    static Path wineFor(GlobalConfig c, String runnerName) {
        if (Proton.isSystem(runnerName)) {
            String w = Sys.which("wine");
            return w == null ? null : Paths.get(w);
        }
        Path d = Proton.resolve(c, runnerName);
        return d == null || Proton.isProtonDir(d) ? null : Proton.wineBinary(d);
    }

    /** true — игра идёт через Wine (системный или сборка), а не через Proton. */
    static boolean isWineLike(GlobalConfig c, String runnerName) {
        return Proton.isSystem(runnerName) || !Proton.isProtonDir(Proton.resolve(c, runnerName));
    }

    /** Прокси-DLL рядом с exe, которые Wine нужно грузить «родными» (n,b). */
    static List<String> detectProxyDlls(Path exeDir) {
        List<String> res = new ArrayList<>();
        if (exeDir == null || !Files.isDirectory(exeDir)) return res;
        Set<String> present = new HashSet<>();
        File[] files = exeDir.toFile().listFiles();
        if (files != null) for (File f : files) present.add(f.getName().toLowerCase(Locale.ROOT));
        for (String dll : new String[] {"winhttp", "version", "winmm", "dinput8"}) {
            if (present.contains(dll + ".dll")) res.add(dll + "=n,b");
        }
        return res;
    }

    static LaunchPlan plan(Game g, GlobalConfig c) throws LaunchException {
        Path exe = Paths.get(g.executablePath);
        if (!Files.isRegularFile(exe)) throw new LaunchException("Файл запуска не найден:\n" + exe);
        LaunchPlan p = new LaunchPlan();
        p.workDir = exe.getParent();
        p.prefixDir = prefixDir(g, c);

        if (exe.toString().endsWith(".sh")) {
            p.command.add("bash");
            p.command.add(exe.toString());
        } else if (!Proton.isSystem(g.protonVersion) && Proton.resolve(c, g.protonVersion) == null) {
            throw new LaunchException("Proton «" + g.protonVersion
                + "» не найден. Установите его во вкладке «Proton / Wine» в настройках игры.");
        } else if (!Proton.isSystem(g.protonVersion) && Proton.isProtonDir(Proton.resolve(c, g.protonVersion))) {
            Path pd = Proton.resolve(c, g.protonVersion);
            p.protonDir = pd;
            p.command.add(pd.resolve("proton").toString());
            p.command.add("run");
            p.command.add(exe.toString());
            p.env.put("STEAM_COMPAT_DATA_PATH", p.prefixDir.toString());
            p.env.put("STEAM_COMPAT_CLIENT_INSTALL_PATH", Sys.steamRoot().toString());
        } else {
            String wine;
            if (Proton.isSystem(g.protonVersion)) {
                wine = Sys.which("wine");
                if (wine == null) throw new LaunchException("Wine не найден в системе.\n"
                    + "Установите wine или выберите GE-Proton во вкладке «Proton / Wine».");
            } else {
                Path wd = Proton.resolve(c, g.protonVersion);
                Path wb = Proton.wineBinary(wd);
                if (wb == null) throw new LaunchException("В «" + g.protonVersion + "» не найден bin/wine. Установите эту сборку заново.");
                wine = wb.toString();
                p.wineDir = wd;
                Path server = wb.getParent().resolve("wineserver");
                if (Files.isExecutable(server)) p.env.put("WINESERVER", server.toString());
                String oldPath = System.getenv("PATH");
                p.env.put("PATH", wb.getParent() + (oldPath == null || oldPath.isEmpty() ? "" : ":" + oldPath));
            }
            p.command.add(wine);
            p.command.add(exe.toString());
            p.env.put("WINEPREFIX", p.prefixDir.toString());
        }
        p.command.addAll(Sys.tokenize(g.customArgs));

        // обёртки производительности: [gamemoderun] [mangohud] <команда>
        List<String> wrap = new ArrayList<>();
        if (g.useGameMode) {
            String gm = Sys.which("gamemoderun");
            if (gm != null) wrap.add(gm);
            else p.warnings.add("GameMode включён, но gamemoderun не найден (установите пакет gamemode) — игра запущена без него.");
        }
        if (g.useMangoHud) {
            String mh = Sys.which("mangohud");
            if (mh != null) wrap.add(mh);
            else p.warnings.add("MangoHud включён, но mangohud не найден (установите пакет mangohud) — игра запущена без него.");
        }
        p.command.addAll(0, wrap);
        boolean viaProton = p.protonDir != null;
        if (g.noEsync) p.env.put(viaProton ? "PROTON_NO_ESYNC" : "WINEESYNC", viaProton ? "1" : "0");
        if (g.noFsync) p.env.put(viaProton ? "PROTON_NO_FSYNC" : "WINEFSYNC", viaProton ? "1" : "0");
        if (g.protonLog && viaProton) p.env.put("PROTON_LOG", "1");

        if (!g.networkAccess) {
            String fj = Sys.which("firejail");
            if (fj != null) {
                p.command.addAll(0, Arrays.asList(fj, "--noprofile", "--net=none"));
            } else {
                p.warnings.add("Блокировка сети недоступна (не установлен firejail) — игра запущена с доступом к сети.");
            }
        }

        // Графика. Proton сам использует DXVK, если не просить WineD3D (переменную «=0» лучше вообще не задавать).
        // Обычному Wine DXVK нужно положить в префикс — тогда его библиотеки включаются через WINEDLLOVERRIDES.
        List<String> gfx = new ArrayList<>();
        if (p.protonDir != null) {
            if (g.useWine3D) p.env.put("PROTON_USE_WINED3D", "1");
        } else if (!exe.toString().endsWith(".sh")) {
            boolean dx = Dxvk.isInstalled(winePrefix(g, c));
            if (g.useWine3D) {
                if (dx) gfx.add("d3d8,d3d9,d3d10core,d3d11,dxgi=b");   // DXVK лежит в префиксе, но выбран OpenGL
            } else if (dx) {
                gfx.add("d3d8,d3d9,d3d10core,d3d11,dxgi=n,b");
            } else {
                p.needsDxvk = true;
                p.warnings.add("Выбран Vulkan, но DXVK не установлен в префиксе Wine — игра работает через OpenGL (WineD3D). "
                    + "Откройте настройки игры → «Графика» и нажмите «Установить DXVK».");
            }
        }
        if (!g.useWine3D) p.env.put("DXVK_ASYNC", "1");

        List<String> ovr = new ArrayList<>();
        if (g.dllOverrides != null) ovr.addAll(g.dllOverrides);
        String ovrJoined = String.join(";", ovr);
        for (String o : gfx) {
            if (!ovrJoined.contains("d3d11") && !ovrJoined.contains("dxgi")) ovr.add(o);
        }
        if (!ovr.isEmpty()) p.env.put("WINEDLLOVERRIDES", String.join(";", ovr));
        if (g.useWayland) {
            p.env.put("SDL_VIDEODRIVER", "wayland");
            p.env.put("PROTON_ENABLE_WAYLAND", "1");
        }
        if (g.sendSteamAppId && g.steamAppId != null && g.steamAppId.matches("\\d{1,10}"))
            p.env.put("SteamAppId", g.steamAppId);

        for (String kv : Sys.tokenize(g.envVars)) {
            int eq = kv.indexOf('=');
            if (eq > 0) p.env.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        return p;
    }

    /**
     * Окружение для winetricks. Wine из состава Proton нельзя запускать «голым»: ему нужны его собственные
     * библиотеки и wineserver, иначе он падает с ошибками реестра вроде «RegOpenKeyExW failed».
     */
    static void winetricksEnv(Map<String, String> env, Path prefix, Path protonWine) {
        env.put("WINEPREFIX", prefix.toString());
        if (protonWine == null) return;
        Path bin = protonWine.getParent();          // .../files/bin
        Path files = bin.getParent();               // .../files
        env.put("WINE", protonWine.toString());
        env.put("WINELOADER", protonWine.toString());
        Path server = bin.resolve("wineserver");
        if (Files.isExecutable(server)) env.put("WINESERVER", server.toString());
        String path = env.getOrDefault("PATH", "");
        env.put("PATH", bin + (path.isEmpty() ? "" : ":" + path));
        env.put("WINEDLLPATH", files.resolve("lib64/wine") + ":" + files.resolve("lib/wine"));
        String ld = env.getOrDefault("LD_LIBRARY_PATH", "");
        env.put("LD_LIBRARY_PATH", files.resolve("lib64") + ":" + files.resolve("lib") + (ld.isEmpty() ? "" : ":" + ld));
        env.put("WINEDLLOVERRIDES", "winemenubuilder.exe=d");
    }

    static final class Session {
        final Game game;
        final Process process;
        final LaunchPlan plan;
        final long startMs = System.currentTimeMillis();
        volatile boolean stoppedByUser = false;

        Session(Game g, Process pr, LaunchPlan pl) { game = g; process = pr; plan = pl; }

        long elapsedSeconds() { return (System.currentTimeMillis() - startMs) / 1000; }

        void stop() {
            stoppedByUser = true;
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            Thread t = new Thread(() -> {
                try {
                    if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.descendants().forEach(ProcessHandle::destroyForcibly);
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ignored) {}
            });
            t.setDaemon(true);
            t.start();
        }
    }

    static Session start(Game g, GlobalConfig c, Listener l, ExitHandler onExit) throws LaunchException {
        LaunchPlan p = plan(g, c);
        try { Files.createDirectories(p.prefixDir); }
        catch (IOException e) { throw new LaunchException("Не удалось создать папку префикса:\n" + p.prefixDir); }

        ProcessBuilder pb = new ProcessBuilder(p.command);
        pb.directory(p.workDir.toFile());
        pb.redirectErrorStream(true);
        Sys.restoreDisplayEnv(pb.environment());
        pb.environment().putAll(p.env);

        Process proc;
        try {
            proc = pb.start();
        } catch (IOException e) {
            throw new LaunchException("Не удалось запустить процесс: " + e.getMessage());
        }
        Session s = new Session(g, proc, p);

        Path logPath = Store.logFile(g);
        l.log("Команда: " + Sys.joinForLog(p.command));
        for (String w : p.warnings) l.log("⚠ " + w);

        Thread reader = new Thread(() -> {
            Set<String> opened = ConcurrentHashMap.newKeySet();
            Set<String> openedMs = ConcurrentHashMap.newKeySet();
            Set<String> openedGog = ConcurrentHashMap.newKeySet();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8));
                 java.io.BufferedWriter lw = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8)) {
                lw.write("Команда: " + Sys.joinForLog(p.command) + "\n");
                String line;
                while ((line = br.readLine()) != null) {
                    lw.write(line);
                    lw.write('\n');
                    l.log(line);
                    if (g.eosEnabled && opened.size() < 5) {
                        for (String u : Epic.extractLoginUrls(line)) {
                            if (opened.add(u)) l.epicUrl(u);
                        }
                    }
                    if (g.msEnabled && openedMs.size() < 5) {
                        for (String u : Microsoft.extractLoginUrls(line)) {
                            if (openedMs.add(u)) l.msUrl(u);
                        }
                    }
                    if (g.gogEnabled && openedGog.size() < 5) {
                        for (String u : Gog.extractLoginUrls(line)) {
                            if (openedGog.add(u)) l.gogUrl(u);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }, "game-output");
        reader.setDaemon(true);
        reader.start();

        Thread waiter = new Thread(() -> {
            int code = -1;
            try { code = proc.waitFor(); } catch (InterruptedException ignored) {}
            try { reader.join(1500); } catch (InterruptedException ignored) {}
            onExit.onExit(s.elapsedSeconds(), code);
        }, "game-waiter");
        waiter.setDaemon(true);
        waiter.start();
        return s;
    }
}

/** Аватарка лаунчера: файл Image.png в папке проекта (рядом с start.sh). */
final class Brand {
    private static Image cached;
    private static boolean tried;
    private static Path found;

    private Brand() {}

    /** Папка проекта: start.sh передаёт её как -Dofll.home, иначе берётся текущая папка. */
    static Path home() {
        String h = System.getProperty("ofll.home");
        return Paths.get(h != null && !h.isBlank() ? h : System.getProperty("user.dir"));
    }

    /** Ищет Image.png без учёта регистра (Image.png, image.PNG…). */
    static Path findFile() {
        Path dir = home();
        Path exact = dir.resolve("Image.png");
        if (Files.isRegularFile(exact)) return exact;
        try (java.nio.file.DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (p.getFileName().toString().equalsIgnoreCase("image.png") && Files.isRegularFile(p)) return p;
            }
        } catch (IOException ignored) {}
        return null;
    }

    /** Картинка аватарки или null, если файла нет / он повреждён. Читается один раз. */
    static synchronized Image image() {
        if (tried) return cached;
        tried = true;
        found = findFile();
        if (found == null) return null;
        try {
            Image img = new Image(found.toUri().toString(), 512, 512, true, true, false);
            if (!img.isError() && img.getWidth() > 0 && img.getHeight() > 0) cached = img;
        } catch (Exception ignored) {}
        return cached;
    }

    /** Ставит аватарку значком окна (панель задач, переключатель окон). */
    static void apply(Stage st) {
        Image i = image();
        if (i != null && st.getIcons().isEmpty()) st.getIcons().add(i);
    }

    static boolean isSquare() {
        Image img = image();
        if (img == null) return true;
        double r = img.getWidth() / img.getHeight();
        return r > 0.75 && r < 1.34;
    }

    static String status() {
        Image img = image();
        if (img != null) return "Аватарка: найден файл " + found.getFileName() + " в папке " + home() + ".";
        if (found != null) return "Аватарка: файл " + found.getFileName() + " не удалось прочитать — нужен корректный PNG.";
        return "Аватарка: файл Image.png не найден. Положите его в папку " + home() + " (рядом с start.sh) и перезапустите лаунчер.";
    }

    /**
     * Аватарка высотой size. Почти квадратная картинка обрезается по центру в круг, вытянутая (логотип
     * с надписью) показывается целиком в скруглённой рамке. Нет файла — круг с буквами «OF».
     */
    static StackPane avatar(double size) {
        StackPane p = new StackPane();
        Image img = image();
        double w = size, h = size;
        if (img == null) {
            Circle bg = new Circle(size / 2);
            bg.getStyleClass().add("ofll-avatar-ph");
            Label l = new Label("OF");
            l.getStyleClass().add("ofll-avatar-letters");
            l.setStyle("-fx-font-size: " + Math.round(size * 0.38) + "px;");
            Circle ring = new Circle(size / 2 - 1);
            ring.getStyleClass().add("ofll-avatar-ring");
            ring.setMouseTransparent(true);
            p.getChildren().addAll(bg, l, ring);
        } else if (isSquare()) {
            double iw = img.getWidth(), ih = img.getHeight(), side = Math.min(iw, ih);
            ImageView iv = new ImageView(img);
            iv.setViewport(new Rectangle2D((iw - side) / 2, (ih - side) / 2, side, side));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setClip(new Circle(size / 2, size / 2, size / 2));
            Circle ring = new Circle(size / 2 - 1);
            ring.getStyleClass().add("ofll-avatar-ring");
            ring.setMouseTransparent(true);
            p.getChildren().addAll(iv, ring);
        } else {
            double ratio = img.getWidth() / img.getHeight();
            w = size * ratio;
            if (w > size * 3.2) { w = size * 3.2; h = w / ratio; }
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            Rectangle clip = new Rectangle(w, h);
            clip.setArcWidth(16);
            clip.setArcHeight(16);
            iv.setClip(clip);
            Rectangle frame = new Rectangle(w - 2, h - 2);
            frame.setArcWidth(16);
            frame.setArcHeight(16);
            frame.getStyleClass().add("ofll-avatar-ring");
            frame.setMouseTransparent(true);
            p.getChildren().addAll(iv, frame);
        }
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        return p;
    }
}

// =====================================================================
//  ИНТЕРФЕЙС (JavaFX)
// =====================================================================
public class LauncherApp extends Application {

    private static final String APP_TITLE = "OnlineFix Linux Launcher (OFLL)";

    private static final String THEME_CSS = """
        .root { -accent: %ACCENT%; -fx-background-color: #1e1e2e; -fx-font-family: 'Ubuntu', 'Cantarell', 'Noto Sans', 'DejaVu Sans', sans-serif; -fx-font-size: 13px; }
        .label { -fx-text-fill: #cdd6f4; }
        .ofll-brand { -fx-text-fill: -accent; -fx-font-size: 20px; -fx-font-weight: bold; }
        .ofll-title { -fx-text-fill: #ffffff; -fx-font-size: 18px; -fx-font-weight: bold; }
        .ofll-hero-title { -fx-text-fill: #ffffff; -fx-font-size: 21px; -fx-font-weight: bold; }
        .ofll-section { -fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold; }
        .ofll-side-section { -fx-text-fill: #7f849c; -fx-font-size: 11px; -fx-font-weight: bold; }
        .ofll-muted { -fx-text-fill: #a6adc8; }
        .ofll-hint { -fx-text-fill: #9399b2; -fx-font-size: 12px; }
        .ofll-notice { -fx-text-fill: #f9e2af; -fx-font-size: 12px; -fx-background-color: #2a2637; -fx-background-radius: 8; -fx-padding: 8 10; }
        .ofll-empty { -fx-text-fill: #6c7086; -fx-font-size: 15px; }
        .ofll-topbar { -fx-background-color: linear-gradient(to right, #11111b, #181825); -fx-border-color: transparent transparent #313244 transparent; -fx-border-width: 0 0 1 0; }
        .ofll-side { -fx-background-color: #181825; }
        .ofll-side-scroll { -fx-background: #181825; -fx-background-color: #181825; -fx-border-color: transparent transparent transparent #313244; -fx-border-width: 0 0 0 1; }
        .ofll-box { -fx-background-color: #262637; -fx-background-radius: 12; -fx-padding: 10; }

        .ofll-card { -fx-background-color: #313244; -fx-background-radius: 14; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 14, 0.1, 0, 4); }
        .ofll-card:hover { -fx-background-color: #585b70; }
        .ofll-card-selected, .ofll-card-selected:hover { -fx-background-color: -accent; }
        .ofll-card-shade { -fx-background-color: linear-gradient(to bottom, rgba(17,17,27,0), rgba(17,17,27,0.93)); -fx-padding: 30 10 10 10; }
        .ofll-card-name { -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 13px; }
        .ofll-card-meta { -fx-text-fill: #bac2de; -fx-font-size: 11px; }
        .ofll-cover-ph { -fx-background-color: linear-gradient(to bottom right, #45475a, #313244); }
        .ofll-cover-letter { -fx-text-fill: -accent; -fx-font-size: 52px; -fx-font-weight: bold; }
        .ofll-badge { -fx-background-color: rgba(17,17,27,0.78); -fx-background-radius: 10; -fx-padding: 2 8; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-font-weight: bold; }
        .ofll-badge-run { -fx-background-color: #a6e3a1; -fx-text-fill: #11111b; }
        .ofll-badge-fav { -fx-text-fill: #f9e2af; }

        .ofll-pill { -fx-background-color: #313244; -fx-background-radius: 10; -fx-padding: 3 10; -fx-text-fill: #cdd6f4; -fx-font-size: 11px; }
        .tier-native { -fx-background-color: #2f9e44; -fx-text-fill: #ffffff; }
        .tier-platinum { -fx-background-color: #b4c7dc; -fx-text-fill: #11111b; }
        .tier-gold { -fx-background-color: #cfb53b; -fx-text-fill: #11111b; }
        .tier-silver { -fx-background-color: #a6a6a6; -fx-text-fill: #11111b; }
        .tier-bronze { -fx-background-color: #cd7f32; -fx-text-fill: #ffffff; }
        .tier-borked { -fx-background-color: #f38ba8; -fx-text-fill: #11111b; }
        .tier-pending { -fx-background-color: #585b70; -fx-text-fill: #ffffff; }

        .ofll-tile { -fx-background-color: #313244; -fx-background-radius: 12; -fx-border-color: transparent; -fx-border-radius: 12; -fx-border-width: 2; -fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold; -fx-min-width: 46; -fx-pref-width: 46; -fx-max-width: 46; -fx-min-height: 46; -fx-pref-height: 46; -fx-max-height: 46; -fx-padding: 0; -fx-cursor: hand; }
        .ofll-tile:hover { -fx-border-color: -accent; }
        .ofll-tile:disabled { -fx-opacity: 0.4; }
        .ofll-tile-caption { -fx-text-fill: #9399b2; -fx-font-size: 10px; }
        .tile-steam { -fx-background-color: linear-gradient(to bottom right, #1b2838, #2a475e); -fx-text-fill: #66c0f4; }
        .tile-steamdb { -fx-background-color: linear-gradient(to bottom right, #0d3a5c, #1f6fa8); -fx-text-fill: #ffffff; }
        .tile-protondb { -fx-background-color: linear-gradient(to bottom right, #3b2a6e, #6a4fc2); -fx-text-fill: #ffffff; }
        .tile-sgdb { -fx-background-color: linear-gradient(to bottom right, #1d3b30, #2f7d5c); -fx-text-fill: #a6e3a1; }
        .tile-settings { -fx-background-color: linear-gradient(to bottom right, #45475a, #585b70); }
        .tile-tool { -fx-background-color: #313244; -fx-text-fill: #cdd6f4; }

        .ofll-btn { -fx-text-fill: #11111b; -fx-font-weight: bold; -fx-padding: 8 14; -fx-cursor: hand; -fx-background-radius: 8; }
        .ofll-btn:hover { -fx-opacity: 0.85; }
        .ofll-btn:disabled { -fx-opacity: 0.45; }
        .btn-play { -fx-font-size: 15px; -fx-padding: 11 14; }
        .btn-green { -fx-background-color: linear-gradient(to bottom, #b6ecb1, #a6e3a1); }
        .btn-blue { -fx-background-color: #89b4fa; }
        .btn-yellow { -fx-background-color: #f9e2af; }
        .btn-mauve { -fx-background-color: -accent; }
        .btn-red { -fx-background-color: #f38ba8; }
        .btn-surface { -fx-background-color: #45475a; -fx-text-fill: #cdd6f4; }
        .check-box { -fx-text-fill: #cdd6f4; }
        .check-box .box { -fx-background-color: #313244; -fx-border-color: #45475a; -fx-border-radius: 3; }
        .check-box:selected .mark { -fx-background-color: #a6e3a1; }
        .radio-button { -fx-text-fill: #cdd6f4; }
        .radio-button .radio { -fx-background-color: #313244; -fx-border-color: #45475a; }
        .radio-button:selected .dot { -fx-background-color: -accent; }
        .tab-pane .tab-header-area .tab-header-background { -fx-background-color: #11111b; }
        .tab { -fx-background-color: #313244; -fx-background-radius: 6 6 0 0; -fx-padding: 5 10; }
        .tab:selected { -fx-background-color: #45475a; }
        .tab .tab-label { -fx-text-fill: #cdd6f4; -fx-font-weight: bold; }
        .tab-pane > .tab-content-area { -fx-background-color: #1e1e2e; }
        .text-field, .password-field { -fx-background-color: #313244; -fx-text-fill: #ffffff; -fx-prompt-text-fill: #7f849c; -fx-border-color: #45475a; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 8; }
        .text-field:focused, .password-field:focused { -fx-border-color: -accent; }
        .combo-box { -fx-background-color: #313244; -fx-text-fill: #ffffff; -fx-border-color: #45475a; -fx-border-radius: 6; -fx-background-radius: 6; }
        .combo-box .list-cell { -fx-background-color: transparent; -fx-text-fill: #ffffff; }
        .combo-box-popup .list-view { -fx-background-color: #313244; -fx-border-color: #45475a; }
        .combo-box-popup .list-cell { -fx-background-color: #313244; -fx-text-fill: #ffffff; }
        .combo-box-popup .list-cell:hover { -fx-background-color: #45475a; }
        .list-view { -fx-background-color: #313244; -fx-control-inner-background: #313244; -fx-border-color: #45475a; -fx-border-radius: 4; }
        .list-cell { -fx-text-fill: #ffffff; -fx-background-color: #313244; }
        .list-cell:selected { -fx-background-color: #45475a; }
        .scroll-pane { -fx-background-color: #1e1e2e; -fx-background: #1e1e2e; }
        .scroll-bar { -fx-background-color: transparent; }
        .scroll-bar .thumb { -fx-background-color: #45475a; -fx-background-radius: 4; }
        .separator .line { -fx-border-color: #45475a; }
        .tooltip { -fx-background-color: #313244; -fx-text-fill: #cdd6f4; }
        .progress-bar > .track { -fx-background-color: #313244; -fx-background-radius: 4; }
        .progress-bar > .bar { -fx-background-color: -accent; -fx-background-insets: 0; -fx-background-radius: 4; }
        .dialog-pane { -fx-background-color: #1e1e2e; }
        .dialog-pane:header .header-panel { -fx-background-color: #181825; }
        .dialog-pane:header .header-panel .label { -fx-text-fill: #ffffff; -fx-font-size: 15px; -fx-font-weight: bold; }
        .dialog-pane .button { -fx-background-color: #45475a; -fx-text-fill: #cdd6f4; -fx-background-radius: 6; -fx-cursor: hand; }
        .dialog-pane .button:default { -fx-background-color: -accent; -fx-text-fill: #11111b; }

        .tile-gog { -fx-background-color: linear-gradient(to bottom right, #3a1d52, #8a3a9e); -fx-text-fill: #ffffff; -fx-font-size: 13px; }
        .ofll-avatar-ring { -fx-fill: transparent; -fx-stroke: -accent; -fx-stroke-width: 2; }
        .ofll-avatar-ph { -fx-fill: linear-gradient(to bottom right, #45475a, #313244); }
        .ofll-avatar-letters { -fx-text-fill: -accent; -fx-font-weight: bold; }
        .ofll-splash-card { -fx-background-color: linear-gradient(to bottom, #232336, #14141f); -fx-background-radius: 24; -fx-border-color: #313244; -fx-border-radius: 24; -fx-border-width: 1; }
        .ofll-splash-title { -fx-text-fill: #ffffff; -fx-font-size: 22px; -fx-font-weight: bold; }
        .ofll-splash-sub { -fx-text-fill: #9399b2; -fx-font-size: 12px; }
        .ofll-splash-status { -fx-text-fill: #bac2de; -fx-font-size: 12px; }
        .ofll-splash-percent { -fx-text-fill: -accent; -fx-font-size: 13px; -fx-font-weight: bold; }
        .ofll-splash-track { -fx-fill: #313244; }
        .ofll-splash-fill { -fx-fill: -accent; }

        .ofll-chip { -fx-background-color: #313244; -fx-text-fill: #cdd6f4; -fx-background-radius: 16; -fx-padding: 5 14 5 14; -fx-cursor: hand; }
        .ofll-chip:hover { -fx-background-color: #45475a; }
        .ofll-chip:selected { -fx-background-color: -accent; -fx-text-fill: #11111b; }
        .ofll-mod-row { -fx-background-color: #313244; -fx-background-radius: 12; -fx-padding: 10 14 10 12; }
        .ofll-mod-row:hover { -fx-background-color: #3a3c52; }
        .ofll-mod-name { -fx-text-fill: #cdd6f4; -fx-font-size: 14px; -fx-font-weight: bold; }
        .ofll-mod-author { -fx-text-fill: #7f849c; -fx-font-size: 12px; }
        .ofll-mod-stat { -fx-text-fill: #a6adc8; -fx-font-size: 12px; }
        .ofll-toast { -fx-background-color: rgba(24,24,37,0.95); -fx-background-radius: 14; -fx-border-color: -accent; -fx-border-radius: 14; -fx-padding: 10 18 10 18; -fx-text-fill: #cdd6f4; -fx-font-size: 13px; }
        .ofll-card-footer { -fx-background-color: #181825; }
        .ofll-lib-scroll, .ofll-lib-scroll > .viewport { -fx-background: transparent; -fx-background-color: transparent; }
        """;

    // ---- состояние ----
    private List<Game> games = new ArrayList<>();
    private GlobalConfig config = new GlobalConfig();
    private final Map<String, Runner.Session> running = new HashMap<>();
    private final Map<String, List<String>> eosCache = new HashMap<>();
    private final Map<String, List<String>> msCache = new HashMap<>();
    private final Map<String, List<String>> gogCache = new HashMap<>();
    private final Map<String, Web.Rating> ratings = new HashMap<>();
    private final Set<String> ratingRequested = new HashSet<>();
    // обложки подгружаются и из потока заставки, поэтому кэш потокобезопасный
    // LRU: старые записи вытесняются по одной, а не разом всем кэшем — меньше повторных перечиток с диска.
    private final Map<String, Image> imageCache = java.util.Collections.synchronizedMap(
        new LinkedHashMap<String, Image>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 240; }
        });
    private final Set<String> animatedCards = new HashSet<>();   // карточки, которым уже показали появление
    private boolean uiReady = false;                               // главное окно показано, можно анимировать
    private volatile Throwable buildError = null;
    private DropShadow brandGlow;
    private final Set<String> tsChecking = new HashSet<>();
    private final List<Animation> uiLoops = new ArrayList<>();
    private StackPane mainStack, libStack;
    private ImageView bgView;
    private Region bgShade;
    private Button btnMods;
    private Label statsLabel;
    private int filterMode = 0;
    private final Random random = new Random();
    private Timeline playGlow;
    private DropShadow playShadow;

    private static List<String> sortLabels() { return List.of("По названию", "Недавно запускались", "По времени игры"); }
    private static List<String> sizeLabels() { return List.of("Маленькие", "Средние", "Большие"); }
    private static List<String> accentLabels() { return List.of("Сиреневый", "Синий", "Зелёный", "Персиковый", "Розовый", "Бирюзовый"); }

    private record CoverJob(Game g, String name, String steam, String sgdb) {}
    private Stage primaryStage;
    private String themeUrl = "";
    private Game selected;

    // ---- элементы ----
    private FlowPane grid;
    private Label emptyLabel;
    private TextField searchField;
    private ScrollPane sideScroll;
    private StackPane coverHolder, iconHolder;
    private Label spTitle, spPlaytime, spLast, spRating, spNotice, spEpicStatus;
    private ComboBox<String> sortBox;
    private CheckBox cbEpic, cbMs, cbGog;
    private Label spMsStatus, spGogStatus;
    private Button btnMsLogin, btnGogLogin;
    private Button btnPlay, btnStop, btnConsole, btnEpicLogin;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        config = Store.loadConfig();
        themeUrl = Store.writeTheme(buildThemeCss(), config.accent);
        Image icon = Brand.image();
        if (icon != null) stage.getIcons().add(icon);
        stage.setTitle(APP_TITLE);
        stage.setMinWidth(860);
        stage.setMinHeight(560);
        applyWindowGeometry(stage, config);
        installMaximizeGuard(stage);
        stage.setOnCloseRequest(ev -> {
            saveRunningTime();
            saveWindowGeometry();
        });

        if (!config.showSplash) {
            games = Store.loadGames();
            buildMainWindow();
            showMainWindow();
            return;
        }
        startWithSplash();
    }

    /** Восстанавливает размер/положение/развёрнутость окна, сохранённые в прошлый раз. */
    private void applyWindowGeometry(Stage stage, GlobalConfig c) {
        stage.setWidth(Math.max(stage.getMinWidth(), c.winW));
        stage.setHeight(Math.max(stage.getMinHeight(), c.winH));
        if (c.winX >= 0 && c.winY >= 0) {
            boolean onScreen = false;
            for (Screen s : Screen.getScreens()) if (s.getVisualBounds().intersects(c.winX, c.winY, 50, 50)) { onScreen = true; break; }
            if (onScreen) {
                stage.setX(c.winX);
                stage.setY(c.winY);
            }
        }
        if (c.winMaximized) stage.setMaximized(true);
    }

    /** Запоминает текущий размер/положение/развёрнутость окна (вызывать перед закрытием). */
    private void saveWindowGeometry() {
        if (primaryStage == null) return;
        config.winMaximized = primaryStage.isMaximized();
        if (!config.winMaximized) {
            config.winW = primaryStage.getWidth();
            config.winH = primaryStage.getHeight();
            config.winX = primaryStage.getX();
            config.winY = primaryStage.getY();
        }
        Store.saveConfig(config);
    }

    /**
     * На некоторых оконных менеджерах Linux/X11 показ модального дочернего окна (настройки, установка
     * Proton, диалоги) «на лету» снимает с главного окна признак «развёрнуто» — оно становится обычного
     * размера и путается под другими окнами. Отслеживаем список всех окон приложения и, если главное окно
     * было развёрнуто перед открытием дочернего, разворачиваем его обратно, когда дочернее окно закрывается.
     */
    private void installMaximizeGuard(Stage stage) {
        final boolean[] wasMaximized = {false};
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Window w : c.getAddedSubList()) {
                        if (w != stage && stage.isMaximized()) wasMaximized[0] = true;
                    }
                }
                if (c.wasRemoved()) {
                    for (Window w : c.getRemoved()) {
                        if (w != stage && wasMaximized[0]) {
                            Platform.runLater(() -> {
                                if (stage.isShowing() && !stage.isMaximized()) stage.setMaximized(true);
                            });
                        }
                    }
                }
            }
        });
    }

    /** Строит главное окно (без показа). Вызывать в потоке интерфейса. */
    private void buildMainWindow() {
        for (Animation a : uiLoops) a.stop();
        uiLoops.clear();
        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildLibrary());
        sideScroll = buildSidePanel();
        sideScroll.setVisible(false);
        sideScroll.setManaged(false);
        root.setRight(sideScroll);
        refreshGrid();

        mainStack = new StackPane(root);
        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(mainStack);
            if (!themeUrl.isEmpty()) scene.getStylesheets().add(themeUrl);
            installSceneHandlers(scene);
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(mainStack);
        }
    }

    /** Горячие клавиши (Ctrl+F — поиск, Esc — закрыть панель игры) и перетаскивание папки с игрой в окно. */
    private void installSceneHandlers(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN), () -> {
            searchField.requestFocus();
            searchField.selectAll();
        });
        scene.setOnKeyPressed(ev -> {
            if (ev.getCode() != KeyCode.ESCAPE) return;
            if (searchField.isFocused() && !searchField.getText().isEmpty()) searchField.clear();
            else if (selected != null) selectGame(null);
            ev.consume();
        });
        scene.setOnDragOver(ev -> {
            if (ev.getDragboard().hasFiles()) ev.acceptTransferModes(TransferMode.COPY);
            ev.consume();
        });
        scene.setOnDragDropped(ev -> {
            Dragboard db = ev.getDragboard();
            boolean ok = false;
            if (db.hasFiles()) {
                for (File f : db.getFiles()) {
                    if (f.isDirectory()) {
                        addFromFolder(f.toPath());
                        ok = true;
                        break;
                    }
                }
            }
            ev.setDropCompleted(ok);
            ev.consume();
        });
    }

    /** Полностью пересобирает интерфейс (например, после смены языка), сохраняя выбранную игру. */
    private void rebuildUi() {
        Game keep = selected;
        buildMainWindow();
        selected = keep;
        refreshGrid();
        updatePanel();
    }

    /** Короткое всплывающее сообщение внизу окна. */
    private void toast(String msg) {
        if (mainStack == null) return;
        Label l = new Label(msg);
        l.getStyleClass().add("ofll-toast");
        l.setWrapText(true);
        l.setMaxWidth(520);
        l.setMaxHeight(Region.USE_PREF_SIZE);
        l.setMouseTransparent(true);
        l.setOpacity(0);
        StackPane.setAlignment(l, Pos.BOTTOM_CENTER);
        StackPane.setMargin(l, new Insets(0, 0, 28, 0));
        mainStack.getChildren().add(l);
        FadeTransition in = new FadeTransition(Duration.millis(220), l);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(420), l);
        out.setDelay(Duration.millis(3200));
        out.setToValue(0);
        out.setOnFinished(e -> mainStack.getChildren().remove(l));
        in.setOnFinished(e -> out.play());
        in.play();
    }

    /** Показывает главное окно с плавным появлением и запускает появление карточек. */
    private void showMainWindow() {
        boolean fade = config.animations;
        if (fade) primaryStage.setOpacity(0);
        primaryStage.show();
        if (fade) {
            new Timeline(new KeyFrame(Duration.millis(360),
                new KeyValue(primaryStage.opacityProperty(), 1.0, Interpolator.EASE_OUT))).play();
        }
        uiReady = true;
        animatedCards.clear();
        refreshGrid();
        if (Store.lastError != null) {
            // showAndWait нельзя вызывать прямо из анимации — откладываем
            Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Библиотека", Store.lastError));
        }
    }

    // ---------------------------------------------------------------
    //  Заставка и загрузка
    // ---------------------------------------------------------------

    /** Показывает заставку и в фоне готовит библиотеку: настройки, список игр, обложки, интерфейс. */
    private void startWithSplash() {
        Splash splash = new Splash(accentHex(config.accent), themeUrl, config.animations);
        splash.show();
        Thread t = new Thread(() -> {
            try {
                report(splash, 0.06, "Читаю настройки…", 240);
                List<Game> loaded = Store.loadGames();
                report(splash, 0.20, loaded.isEmpty() ? "Библиотека пока пуста" : "Игр в библиотеке: " + loaded.size(), 260);
                preloadCovers(loaded, splash);
                report(splash, 0.80, "Собираю интерфейс…", 200);
                Platform.runLater(() -> {
                    try {
                        games = loaded;
                        buildMainWindow();
                    } catch (Throwable e) {
                        buildError = e;
                    }
                });
                report(splash, 0.93, "Настраиваю анимации…", 300);
                report(splash, 1.0, "Готово!", 450);
                Platform.runLater(() -> splash.finish(this::showMainWindow));
            } catch (Throwable e) {
                buildError = e;
                Platform.runLater(() -> splash.finish(() -> {}));
            }
            Platform.runLater(() -> {
                if (buildError != null) {
                    showAlert(Alert.AlertType.ERROR, "Запуск", "Не удалось запустить интерфейс: " + buildError);
                    Platform.exit();
                }
            });
        }, "ofll-loader");
        t.setDaemon(true);
        t.start();
    }

    private static void report(Splash s, double p, String text, long pauseMs) throws InterruptedException {
        Platform.runLater(() -> s.progress(p, text));
        Thread.sleep(pauseMs);
    }

    /** Заранее декодирует обложки, чтобы библиотека открылась сразу целиком. Прогресс — от 20% до 76%. */
    private void preloadCovers(List<Game> list, Splash splash) throws InterruptedException {
        List<Game> with = new ArrayList<>();
        for (Game g : list) {
            try {
                if (g.coverPath != null && !g.coverPath.isBlank() && Files.isRegularFile(Paths.get(g.coverPath))) with.add(g);
            } catch (RuntimeException ignored) {}
        }
        if (with.isEmpty()) return;
        double cw = cardWidth() - 6;
        for (int i = 0; i < with.size(); i++) {
            try { loadCover(with.get(i), cw); } catch (RuntimeException ignored) {}
            final double p = 0.20 + 0.56 * (i + 1) / with.size();
            final String text = "Загружаю обложки… " + (i + 1) + " из " + with.size();
            Platform.runLater(() -> splash.progress(p, text));
            if (with.size() <= 6) Thread.sleep(120);
        }
    }

    /** Заставка при запуске: аватарка, вращающееся кольцо и бегущая полоса загрузки с процентами. */
    static final class Splash {
        private static final double BAR_W = 380, BAR_H = 8;

        private final Stage stage = new Stage();
        private final StackPane root = new StackPane();
        private final VBox card;
        private final Label status = new Label("Запуск…");
        private final Label percent = new Label("0%");
        private final DoubleProperty shown = new SimpleDoubleProperty(0);
        private final List<Animation> loops = new ArrayList<>();
        private final boolean animate;
        private Timeline mover;

        Splash(String accentHex, String themeUrl, boolean animate) {
            this.animate = animate;
            Color accent = Color.web(accentHex);
            boolean glass = Platform.isSupported(ConditionalFeature.TRANSPARENT_WINDOW)
                && System.getProperty("ofll.opaqueSplash") == null;
            stage.initStyle(glass ? StageStyle.TRANSPARENT : StageStyle.UNDECORATED);
            stage.setTitle(APP_TITLE);
            Image icon = Brand.image();
            if (icon != null) stage.getIcons().add(icon);

            // --- логотип: свечение, вращающееся кольцо, аватарка ---
            double av = 128, ring = av + 32;
            StackPane logo = new StackPane();
            logo.setMinSize(ring + 28, ring + 28);
            logo.setPrefSize(ring + 28, ring + 28);
            logo.setMaxSize(ring + 28, ring + 28);
            Circle glow = new Circle(ring / 2 + 12);
            glow.setFill(new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                new Stop(0.6, accent.deriveColor(0, 1, 1, 0.38)), new Stop(1, accent.deriveColor(0, 1, 1, 0))));
            StackPane avatar = Brand.avatar(av);
            avatar.setEffect(new DropShadow(22, accent.deriveColor(0, 1, 1, 0.55)));
            logo.getChildren().add(glow);
            if (Brand.isSquare()) {
                logo.getChildren().add(spinner(ring, 0, 110, accent, 1.0, 1.7));
                logo.getChildren().add(spinner(ring - 14, 180, 70, accent, 0.45, -2.8));
            }
            logo.getChildren().add(avatar);
            if (animate) {
                FadeTransition pulse = new FadeTransition(Duration.seconds(1.4), glow);
                pulse.setFromValue(0.5);
                pulse.setToValue(1.0);
                pulse.setAutoReverse(true);
                pulse.setCycleCount(Animation.INDEFINITE);
                loops.add(pulse);
                ScaleTransition breathe = new ScaleTransition(Duration.seconds(1.6), avatar);
                breathe.setFromX(1.0);
                breathe.setFromY(1.0);
                breathe.setToX(1.045);
                breathe.setToY(1.045);
                breathe.setInterpolator(Interpolator.EASE_BOTH);
                breathe.setAutoReverse(true);
                breathe.setCycleCount(Animation.INDEFINITE);
                loops.add(breathe);
            }

            // --- заголовок ---
            Label title = new Label("OnlineFix Linux Launcher");
            title.getStyleClass().add("ofll-splash-title");
            Label sub = new Label("OFLL · лаунчер игр для Linux");
            sub.getStyleClass().add("ofll-splash-sub");
            VBox titleBox = new VBox(3, title, sub);
            titleBox.setAlignment(Pos.CENTER);

            // --- полоса загрузки: подпись, проценты, бегущий блик ---
            status.getStyleClass().add("ofll-splash-status");
            percent.getStyleClass().add("ofll-splash-percent");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(status, spacer, percent);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPrefWidth(BAR_W);
            row.setMaxWidth(BAR_W);

            Rectangle track = new Rectangle(BAR_W, BAR_H);
            track.getStyleClass().add("ofll-splash-track");
            Rectangle fillRect = new Rectangle(BAR_W, BAR_H);
            fillRect.getStyleClass().add("ofll-splash-fill");
            Rectangle shine = new Rectangle(-90, 0, 90, BAR_H);
            shine.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255, 255, 255, 0)),
                new Stop(0.5, Color.rgb(255, 255, 255, 0.55)),
                new Stop(1, Color.rgb(255, 255, 255, 0))));
            Pane fillLayer = new Pane(fillRect, shine);
            fillLayer.setMinSize(BAR_W, BAR_H);
            fillLayer.setPrefSize(BAR_W, BAR_H);
            fillLayer.setMaxSize(BAR_W, BAR_H);
            Rectangle fillClip = new Rectangle(0, BAR_H);
            fillClip.widthProperty().bind(shown.multiply(BAR_W));
            fillLayer.setClip(fillClip);
            StackPane bar = new StackPane(track, fillLayer);
            bar.setMinSize(BAR_W, BAR_H);
            bar.setPrefSize(BAR_W, BAR_H);
            bar.setMaxSize(BAR_W, BAR_H);
            Rectangle barClip = new Rectangle(BAR_W, BAR_H);
            barClip.setArcWidth(BAR_H);
            barClip.setArcHeight(BAR_H);
            bar.setClip(barClip);
            if (animate) {
                TranslateTransition run = new TranslateTransition(Duration.millis(1300), shine);
                run.setFromX(0);
                run.setToX(BAR_W + 90);
                run.setInterpolator(Interpolator.LINEAR);
                run.setCycleCount(Animation.INDEFINITE);
                loops.add(run);
            }
            shown.addListener((o, a, b) -> percent.setText(Math.round(b.doubleValue() * 100) + "%"));

            VBox progressBox = new VBox(8, row, bar);
            progressBox.setAlignment(Pos.CENTER);
            progressBox.setMaxWidth(BAR_W);

            card = new VBox(18, logo, titleBox, progressBox);
            card.setAlignment(Pos.CENTER);
            card.setPadding(new Insets(28, 44, 32, 44));
            card.setPrefWidth(BAR_W + 88);
            card.setMaxWidth(BAR_W + 88);
            card.getStyleClass().add("ofll-splash-card");
            if (glass) card.setEffect(new DropShadow(30, Color.rgb(0, 0, 0, 0.6)));

            root.getChildren().add(card);
            root.setPadding(new Insets(glass ? 30 : 0));
            root.setStyle(glass ? "-fx-background-color: transparent;" : "-fx-background-color: #14141f;");
            Scene sc = new Scene(root);
            sc.setFill(glass ? Color.TRANSPARENT : Color.web("#14141f"));
            if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
            stage.setScene(sc);
        }

        /** Дуга, вращающаяся вокруг центра логотипа. seconds &lt; 0 — против часовой стрелки. */
        private Pane spinner(double d, double startAngle, double sweep, Color c, double opacity, double seconds) {
            double r = d / 2;
            Arc arc = new Arc(r, r, r - 2, r - 2, startAngle, sweep);
            arc.setType(ArcType.OPEN);
            arc.setFill(Color.TRANSPARENT);
            arc.setStroke(c);
            arc.setOpacity(opacity);
            arc.setStrokeWidth(3);
            arc.setStrokeLineCap(StrokeLineCap.ROUND);
            Pane holder = new Pane(arc);
            holder.setMinSize(d, d);
            holder.setPrefSize(d, d);
            holder.setMaxSize(d, d);
            holder.setMouseTransparent(true);
            if (animate) {
                RotateTransition rt = new RotateTransition(Duration.seconds(Math.abs(seconds)), holder);
                rt.setByAngle(seconds > 0 ? 360 : -360);
                rt.setInterpolator(Interpolator.LINEAR);
                rt.setCycleCount(Animation.INDEFINITE);
                loops.add(rt);
            }
            return holder;
        }

        void show() {
            if (animate) {
                root.setOpacity(0);
                card.setScaleX(0.94);
                card.setScaleY(0.94);
            }
            stage.show();
            if (animate) {
                FadeTransition in = new FadeTransition(Duration.millis(420), root);
                in.setToValue(1);
                ScaleTransition grow = new ScaleTransition(Duration.millis(520), card);
                grow.setToX(1);
                grow.setToY(1);
                grow.setInterpolator(Interpolator.EASE_OUT);
                new ParallelTransition(in, grow).play();
                for (Animation a : loops) a.play();
            }
        }

        /** Плавно доводит полосу до target (0..1) и меняет подпись. Вызывать в потоке интерфейса. */
        void progress(double target, String text) {
            if (text != null && !text.equals(status.getText())) {
                status.setText(text);
                if (animate) {
                    FadeTransition f = new FadeTransition(Duration.millis(220), status);
                    f.setFromValue(0.2);
                    f.setToValue(1);
                    f.play();
                }
            }
            double to = Math.max(shown.get(), Math.min(1.0, target));
            if (mover != null) mover.stop();
            if (!animate) {
                shown.set(to);
                return;
            }
            double dist = to - shown.get();
            mover = new Timeline(new KeyFrame(Duration.millis(220 + dist * 900),
                new KeyValue(shown, to, Interpolator.EASE_BOTH)));
            mover.play();
        }

        /** Показывает главное окно (reveal) и плавно убирает заставку. */
        void finish(Runnable reveal) {
            reveal.run();
            if (!animate) {
                close();
                return;
            }
            FadeTransition out = new FadeTransition(Duration.millis(480), root);
            out.setToValue(0);
            ScaleTransition zoom = new ScaleTransition(Duration.millis(480), card);
            zoom.setToX(1.05);
            zoom.setToY(1.05);
            ParallelTransition pt = new ParallelTransition(out, zoom);
            pt.setOnFinished(ev -> close());
            pt.play();
        }

        void close() {
            for (Animation a : loops) a.stop();
            if (mover != null) mover.stop();
            stage.close();
        }
    }

    // ---------------------------------------------------------------
    //  Анимации интерфейса
    // ---------------------------------------------------------------

    /** Появление карточки: плавный подъём с задержкой, зависящей от её места в сетке. */
    private static void playEntrance(Node n, int index) {
        n.setOpacity(0);
        n.setTranslateY(22);
        FadeTransition f = new FadeTransition(Duration.millis(340), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(340), n);
        t.setToY(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(f, t);
        pt.setDelay(Duration.millis(Math.min(index, 16) * 40L));
        pt.play();
    }

    /** Боковая панель выезжает справа, когда выбирают первую игру. */
    private static void slideIn(Node n) {
        n.setTranslateX(36);
        n.setOpacity(0);
        TranslateTransition t = new TranslateTransition(Duration.millis(300), n);
        t.setToX(0);
        t.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition f = new FadeTransition(Duration.millis(300), n);
        f.setToValue(1);
        new ParallelTransition(t, f).play();
    }

    /** Короткое «проявление» при переключении между играми. */
    private static void fadeSwap(Node n) {
        if (n == null) return;
        FadeTransition f = new FadeTransition(Duration.millis(220), n);
        f.setFromValue(0.25);
        f.setToValue(1);
        f.play();
    }

    /** Показывает окно с плавным появлением (если анимации включены). */
    private void showAnimated(Stage st) {
        if (!config.animations) {
            st.show();
            return;
        }
        st.setOpacity(0);
        st.show();
        new Timeline(new KeyFrame(Duration.millis(200), new KeyValue(st.opacityProperty(), 1.0, Interpolator.EASE_OUT))).play();
    }

    /** Аватарка в верхней панели: мягкое пульсирующее свечение, увеличение при наведении, вращение по клику. */
    private Node buildBrand() {
        StackPane av = Brand.avatar(44);
        Tooltip.install(av, new Tooltip(APP_TITLE));
        Color accent = Color.web(accentHex(config.accent));
        brandGlow = new DropShadow(10, accent.deriveColor(0, 1, 1, 0.6));
        av.setEffect(brandGlow);
        av.setCursor(Cursor.HAND);
        if (config.animations) {
            Timeline pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(brandGlow.radiusProperty(), 6)),
                new KeyFrame(Duration.seconds(1.8), new KeyValue(brandGlow.radiusProperty(), 20, Interpolator.EASE_BOTH)));
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            pulse.play();
            uiLoops.add(pulse);
        }
        av.setOnMouseEntered(ev -> scale(av, 1.12));
        av.setOnMouseExited(ev -> scale(av, 1.0));
        av.setOnMouseClicked(ev -> {
            if (!config.animations) return;
            if (Brand.isSquare()) {
                RotateTransition rt = new RotateTransition(Duration.millis(650), av);
                rt.setByAngle(360);
                rt.setInterpolator(Interpolator.EASE_BOTH);
                rt.play();
            } else {
                ScaleTransition st = new ScaleTransition(Duration.millis(220), av);
                st.setToX(1.25);
                st.setToY(1.25);
                st.setAutoReverse(true);
                st.setCycleCount(2);
                st.play();
            }
        });
        return av;
    }

    /** Если лаунчер закрывают во время игры — сохраняем уже наигранное время. */
    private void saveRunningTime() {
        for (Runner.Session s : running.values()) s.game.playtimeSeconds += s.elapsedSeconds();
        if (!running.isEmpty()) Store.saveGames(games);
    }

    // ---------------------------------------------------------------
    //  Построение интерфейса
    // ---------------------------------------------------------------

    private Node buildTopBar() {
        Node brand = buildBrand();
        Label heading = styled("Библиотека", "ofll-title");
        heading.setWrapText(false);
        searchField = new TextField();
        searchField.setPromptText("Поиск по названию…");
        searchField.setPrefWidth(240);
        PauseTransition searchDebounce = new PauseTransition(Duration.millis(140));
        searchDebounce.setOnFinished(e -> refreshGrid());
        searchField.textProperty().addListener((obs, oldV, newV) -> searchDebounce.playFromStart());
        sortBox = new ComboBox<>(FXCollections.observableArrayList(sortLabels()));
        sortBox.setValue(sortLabels().get(Math.max(0, GlobalConfig.SORT_MODES.indexOf(config.sortMode))));
        sortBox.setOnAction(ev -> {
            int i = sortLabels().indexOf(sortBox.getValue());
            if (i < 0 || config.sortMode.equals(GlobalConfig.SORT_MODES.get(i))) return;
            config.sortMode = GlobalConfig.SORT_MODES.get(i);
            Store.saveConfig(config);
            refreshGrid();
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = button("+ Добавить игру", "btn-mauve");
        add.setOnAction(ev -> handleAddGame());
        Button gear = iconButton("⚙", "tile-settings", "Настройки лаунчера", this::openLauncherSettings);
        HBox bar = new HBox(12, brand, heading, spacer, searchField, sortBox, add, gear);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 18, 14, 18));
        bar.getStyleClass().add("ofll-topbar");
        return bar;
    }

    private Node buildLibrary() {
        grid = new FlowPane(16, 16);
        grid.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("ofll-lib-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        emptyLabel = new Label();
        emptyLabel.getStyleClass().add("ofll-empty");
        emptyLabel.setTextAlignment(TextAlignment.CENTER);
        emptyLabel.setMouseTransparent(true);

        // размытая обложка выбранной игры на фоне библиотеки
        bgView = new ImageView();
        bgView.setPreserveRatio(false);
        bgView.setSmooth(true);
        bgView.setMouseTransparent(true);
        bgView.setEffect(new GaussianBlur(28));
        bgView.setScaleX(1.1);
        bgView.setScaleY(1.1);
        bgView.setCache(true);
        bgView.setCacheHint(javafx.scene.CacheHint.SPEED);
        bgView.setVisible(false);
        bgShade = new Region();
        bgShade.setMouseTransparent(true);
        bgShade.setStyle("-fx-background-color: rgba(30, 30, 46, 0.68);");
        bgShade.setVisible(false);

        libStack = new StackPane(bgView, bgShade, new VBox(0, buildFilterBar(), scroll), emptyLabel);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(libStack.widthProperty());
        clip.heightProperty().bind(libStack.heightProperty());
        libStack.setClip(clip);
        libStack.widthProperty().addListener((o, a, b) -> layoutBackground());
        libStack.heightProperty().addListener((o, a, b) -> layoutBackground());
        return libStack;
    }

    private Node buildFilterBar() {
        ToggleGroup tg = new ToggleGroup();
        ToggleButton[] chips = {
            chip("Все игры", tg), chip("★ Избранное", tg), chip("Недавние", tg), chip("● Запущены", tg)};
        chips[Math.max(0, Math.min(3, filterMode))].setSelected(true);
        tg.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                if (a != null) a.setSelected(true);
                return;
            }
            filterMode = Arrays.asList(chips).indexOf(b);
            refreshGrid();
        });
        statsLabel = styled("", "ofll-hint");
        statsLabel.setWrapText(false);
        Button btnRandom = button("🎲 Случайная игра", "btn-surface");
        btnRandom.setOnAction(ev -> pickRandom());
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        HBox bar = new HBox(8, chips[0], chips[1], chips[2], chips[3], sp, statsLabel, btnRandom);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 20, 0, 20));
        return bar;
    }

    private static ToggleButton chip(String text, ToggleGroup g) {
        ToggleButton t = new ToggleButton(text);
        t.setToggleGroup(g);
        t.getStyleClass().add("ofll-chip");
        return t;
    }

    private boolean passesFilter(Game g) {
        switch (filterMode) {
            case 1: return g.favorite;
            case 2: return g.lastPlayed > 0 && System.currentTimeMillis() / 1000 - g.lastPlayed < 14L * 24 * 3600;
            case 3: return running.containsKey(g.id);
            default: return true;
        }
    }

    private void pickRandom() {
        List<Game> pool = new ArrayList<>();
        for (Game g : games) if (passesFilter(g)) pool.add(g);
        if (pool.isEmpty()) {
            toast("В этом разделе пока нет игр.");
            return;
        }
        Game g = pool.get(random.nextInt(pool.size()));
        selectGame(g);
        toast(I18n.fmt("Сегодня играем в «{0}»!", g.name));
    }

    /** Подгоняет фоновую картинку под размер библиотеки (обрезка по центру, как «cover» в CSS). */
    private void layoutBackground() {
        if (bgView == null || libStack == null) return;
        Image img = bgView.getImage();
        double w = libStack.getWidth(), h = libStack.getHeight();
        if (img == null || w <= 0 || h <= 0 || img.getHeight() <= 0) return;
        double iw = img.getWidth(), ih = img.getHeight(), vw, vh, vx, vy;
        if (iw / ih > w / h) {
            vh = ih;
            vw = ih * w / h;
            vx = (iw - vw) / 2;
            vy = 0;
        } else {
            vw = iw;
            vh = iw * h / w;
            vx = 0;
            vy = (ih - vh) / 2;
        }
        bgView.setViewport(new Rectangle2D(vx, vy, vw, vh));
        bgView.setFitWidth(w);
        bgView.setFitHeight(h);
    }

    /** Фон библиотеки: пока открыта панель игры — размытая обложка этой игры, иначе обычный фон. */
    private void updateBackground(Game g) {
        if (bgView == null) return;
        Image img = (g == null || !config.coverBackground) ? null : loadCover(g, 900);
        if (img == null) {
            bgView.setVisible(false);
            bgShade.setVisible(false);
            bgView.setImage(null);
            return;
        }
        boolean changed = bgView.getImage() != img || !bgView.isVisible();
        bgView.setImage(img);
        layoutBackground();
        bgView.setVisible(true);
        bgShade.setVisible(true);
        if (changed && config.animations && uiReady) {
            FadeTransition f1 = new FadeTransition(Duration.millis(450), bgView);
            f1.setFromValue(0);
            f1.setToValue(1);
            FadeTransition f2 = new FadeTransition(Duration.millis(450), bgShade);
            f2.setFromValue(0);
            f2.setToValue(1);
            f1.play();
            f2.play();
        }
    }

    private ScrollPane buildSidePanel() {
        coverHolder = new StackPane();
        spTitle = styled("", "ofll-hero-title");
        spPlaytime = pill();
        spLast = pill();
        spRating = pill();
        spRating.setVisible(false);
        spRating.setManaged(false);
        FlowPane chips = new FlowPane(6, 6, spPlaytime, spLast, spRating);
        chips.setPrefWrapLength(226);
        spTitle.setMaxWidth(226);
        spRating.setMaxWidth(226);
        VBox titleBox = new VBox(8, spTitle, chips);
        titleBox.setAlignment(Pos.TOP_LEFT);
        titleBox.setPrefWidth(226);
        titleBox.setMaxWidth(226);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        iconHolder = new StackPane();
        HBox nameRow = new HBox(12, iconHolder, titleBox);
        nameRow.setAlignment(Pos.TOP_LEFT);
        VBox head = new VBox(12, coverHolder, nameRow);

        spNotice = styled("", "ofll-notice");
        spNotice.setVisible(false);
        spNotice.setManaged(false);

        btnPlay = button("▶  Играть", "btn-green");
        btnPlay.getStyleClass().add("btn-play");
        btnPlay.setOnAction(ev -> onPlay(false));
        btnStop = button("■  Остановить", "btn-red");
        btnStop.setOnAction(ev -> stopSelected());
        btnStop.setVisible(false);
        btnStop.setManaged(false);
        btnPlay.setMaxWidth(Double.MAX_VALUE);
        btnStop.setMaxWidth(Double.MAX_VALUE);
        btnMods = button("🧩 Моды (BepInEx)", "btn-mauve");
        btnMods.setMaxWidth(Double.MAX_VALUE);
        btnMods.setOnAction(ev -> openMods());
        btnMods.setVisible(false);
        btnMods.setManaged(false);
        if (config.animations) {
            playShadow = new DropShadow(10, Color.web("#a6e3a1", 0.55));
            btnPlay.setEffect(playShadow);
            playGlow = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(playShadow.radiusProperty(), 6)),
                new KeyFrame(Duration.seconds(1.4), new KeyValue(playShadow.radiusProperty(), 20, Interpolator.EASE_BOTH)));
            playGlow.setAutoReverse(true);
            playGlow.setCycleCount(Animation.INDEFINITE);
            playGlow.play();
            uiLoops.add(playGlow);
        }

        // --- Epic Games: быстрый переключатель ---
        cbEpic = new CheckBox("Epic Games (EOS)");
        cbEpic.setOnAction(ev -> {
            if (selected == null) return;
            selected.eosEnabled = cbEpic.isSelected();
            selected.eosChecked = true;
            Store.saveGames(games);
            refreshEpicStatus();
        });
        spEpicStatus = styled("", "ofll-hint");
        btnEpicLogin = button("Войти в Epic в браузере", "btn-blue");
        btnEpicLogin.setMaxWidth(Double.MAX_VALUE);
        btnEpicLogin.setOnAction(ev -> {
            openUrl(Epic.LOGIN_URL);
            setNotice("Страница входа Epic открыта в браузере. Войдите в аккаунт и запускайте игру.");
        });
        VBox epicBox = new VBox(6, cbEpic, spEpicStatus, btnEpicLogin);
        epicBox.getStyleClass().add("ofll-box");

        // --- Microsoft / Xbox: быстрый переключатель ---
        cbMs = new CheckBox("Аккаунт Microsoft (Xbox)");
        cbMs.setOnAction(ev -> {
            if (selected == null) return;
            selected.msEnabled = cbMs.isSelected();
            selected.msChecked = true;
            Store.saveGames(games);
            refreshMsStatus();
        });
        spMsStatus = styled("", "ofll-hint");
        btnMsLogin = button("Войти в Microsoft в браузере", "btn-blue");
        btnMsLogin.setMaxWidth(Double.MAX_VALUE);
        btnMsLogin.setOnAction(ev -> {
            openUrl(Microsoft.LOGIN_URL);
            setNotice("Страница входа Microsoft открыта в браузере. Войдите в аккаунт и запускайте игру.");
        });
        VBox msBox = new VBox(6, cbMs, spMsStatus, btnMsLogin);
        msBox.getStyleClass().add("ofll-box");

        // --- GOG Galaxy: быстрый переключатель ---
        cbGog = new CheckBox("GOG Galaxy");
        cbGog.setOnAction(ev -> {
            if (selected == null) return;
            selected.gogEnabled = cbGog.isSelected();
            selected.gogChecked = true;
            Store.saveGames(games);
            refreshGogStatus();
        });
        spGogStatus = styled("", "ofll-hint");
        btnGogLogin = button("Войти в GOG в браузере", "btn-blue");
        btnGogLogin.setMaxWidth(Double.MAX_VALUE);
        btnGogLogin.setOnAction(ev -> {
            openUrl(Gog.LOGIN_URL);
            setNotice("GOG открыт в браузере. Нажмите «Войти» в правом верхнем углу сайта и запускайте игру.");
        });
        VBox gogBox = new VBox(6, cbGog, spGogStatus, btnGogLogin);
        gogBox.getStyleClass().add("ofll-box");

        // --- сайты: миниатюры-кнопки ---
        FlowPane sites = new FlowPane(10, 10,
            captioned(iconButton("S", "tile-steam", "Страница игры в Steam", () -> openSite(Links.Site.STEAM)), "Steam"),
            captioned(iconButton("DB", "tile-steamdb", "Страница игры на SteamDB", () -> openSite(Links.Site.STEAMDB)), "SteamDB"),
            captioned(iconButton("P", "tile-protondb", "Страница игры на ProtonDB", () -> openSite(Links.Site.PROTONDB)), "ProtonDB"),
            captioned(iconButton("G", "tile-sgdb", "Обложки игры на SteamGridDB", () -> openSite(Links.Site.SGDB)), "SGDB"),
            captioned(iconButton("GOG", "tile-gog", "Игра в магазине GOG", () -> openSite(Links.Site.GOG)), "GOG"));

        // --- инструменты: миниатюры-кнопки ---
        btnConsole = iconButton(">_", "tile-tool", "Запуск с консолью (отладка)", () -> onPlay(true));
        FlowPane tools = new FlowPane(10, 10,
            captioned(iconButton("⚙", "tile-settings", "Настройки игры", this::openSettingsDialog), "Настройки"),
            captioned(iconButton("W", "tile-tool", "Префикс Wine (Winetricks)", this::launchWinetricks), "Winetricks"),
            captioned(btnConsole, "Консоль"),
            captioned(iconButton("↻", "tile-tool", "Найти Steam AppID, обложку и рейтинг ProtonDB", () -> {
                if (selected != null) enrichAsync(selected, true);
            }), "Обновить"));

        VBox panel = new VBox(14, head, spNotice, btnPlay, btnStop, btnMods, epicBox, msBox, gogBox,
            sideSection("САЙТЫ"), sites, sideSection("ИНСТРУМЕНТЫ"), tools);
        panel.setPadding(new Insets(20));
        panel.setPrefWidth(330);
        panel.getStyleClass().add("ofll-side");

        ScrollPane sp = new ScrollPane(panel);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefWidth(346);
        sp.getStyleClass().add("ofll-side-scroll");
        return sp;
    }

    private double cardWidth() {
        switch (config.cardSize) {
            case "small": return 200;
            case "large": return 320;
            default: return 250;
        }
    }

    private Comparator<Game> gameOrder() {
        Comparator<Game> byName = Comparator.comparing((Game x) -> x.name.toLowerCase(Locale.ROOT));
        Comparator<Game> main;
        switch (config.sortMode) {
            case "recent": main = Comparator.comparingLong((Game x) -> x.lastPlayed).reversed(); break;
            case "playtime": main = Comparator.comparingLong((Game x) -> x.playtimeSeconds).reversed(); break;
            default: main = byName;
        }
        return Comparator.comparing((Game x) -> !x.favorite).thenComparing(main).thenComparing(byName);
    }

    private void refreshGrid() {
        grid.getChildren().clear();
        grid.setHgap(18);
        grid.setVgap(18);
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Game> sorted = new ArrayList<>(games);
        sorted.sort(gameOrder());
        int shown = 0;
        for (Game g : sorted) {
            if (!q.isEmpty() && !g.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            if (!passesFilter(g)) continue;
            Node card = buildCard(g);
            grid.getChildren().add(card);
            if (uiReady && config.animations && animatedCards.add(g.id)) playEntrance(card, shown);
            shown++;
        }
        emptyLabel.setVisible(shown == 0);
        emptyLabel.setText(games.isEmpty()
            ? "Библиотека пуста.\nНажмите «+ Добавить игру» и выберите папку с игрой — или просто перетащите папку в это окно."
            : (q.isEmpty() ? "В этом разделе пока нет игр."
                : I18n.fmt("Ничего не найдено по запросу «{0}».", searchField.getText().trim())));
        if (statsLabel != null) {
            long totalSecs = 0;
            for (Game x : games) totalSecs += x.playtimeSeconds;
            statsLabel.setText(I18n.fmt("Игр: {0} · всего наиграно: {1}", games.size(), Sys.formatPlaytime(totalSecs)));
        }
    }

    private Node buildCard(Game g) {
        double w = cardWidth();
        double iw = w - 6;
        double bh = Math.round(iw * 215.0 / 460.0), fh = 52;
        double ih = bh + fh, h = ih + 6;
        StackPane card = new StackPane();
        card.setMinSize(w, h);
        card.setPrefSize(w, h);
        card.setMaxSize(w, h);
        card.setPadding(new Insets(3));
        card.getStyleClass().add("ofll-card");
        if (g == selected) card.getStyleClass().add("ofll-card-selected");

        // обложка (баннер) с бейджами
        StackPane banner = new StackPane();
        banner.setMinSize(iw, bh);
        banner.setPrefSize(iw, bh);
        banner.setMaxSize(iw, bh);
        HBox badges = new HBox(6);
        badges.setPadding(new Insets(8));
        badges.setMaxHeight(Region.USE_PREF_SIZE);
        badges.setPickOnBounds(false);
        StackPane.setAlignment(badges, Pos.TOP_LEFT);
        if (g.favorite) badges.getChildren().add(badge("★", "ofll-badge-fav"));
        if (running.containsKey(g.id)) badges.getChildren().add(badge("● Играет", "ofll-badge-run"));
        banner.getChildren().addAll(buildCover(g, iw, bh), badges);

        // подпись: значок игры, название, время в игре
        Label name = new Label(g.name);
        name.getStyleClass().add("ofll-card-name");
        name.setMaxWidth(iw - 34 - 30);
        Label meta = new Label(g.playtimeSeconds <= 0 ? "Не запускалась" : Sys.formatPlaytime(g.playtimeSeconds));
        meta.getStyleClass().add("ofll-card-meta");
        VBox texts = new VBox(2, name, meta);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox footer = new HBox(10, buildIcon(g, 34), texts);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(0, 10, 0, 10));
        footer.setMinSize(iw, fh);
        footer.setPrefSize(iw, fh);
        footer.setMaxSize(iw, fh);
        footer.getStyleClass().add("ofll-card-footer");

        VBox inner = new VBox(banner, footer);
        inner.setMinSize(iw, ih);
        inner.setPrefSize(iw, ih);
        inner.setMaxSize(iw, ih);
        Rectangle clip = new Rectangle(iw, ih);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        inner.setClip(clip);
        card.getChildren().add(inner);

        ContextMenu menu = new ContextMenu();
        MenuItem miPlay = new MenuItem("Играть");
        miPlay.setOnAction(ev -> { selectGame(g); onPlay(false); });
        MenuItem miFav = new MenuItem(g.favorite ? "Убрать из избранного" : "Добавить в избранное");
        miFav.setOnAction(ev -> {
            g.favorite = !g.favorite;
            Store.saveGames(games);
            refreshGrid();
        });
        MenuItem miData = new MenuItem("Найти обложку и данные в интернете");
        miData.setOnAction(ev -> enrichAsync(g, true));
        MenuItem miFolder = new MenuItem("Открыть папку игры");
        miFolder.setOnAction(ev -> {
            Path root = gameRoot(g);
            if (root == null || !Sys.openPath(root))
                showAlert(Alert.AlertType.WARNING, "Папка игры", "Не удалось открыть папку. Установлен ли xdg-utils?");
        });
        MenuItem miLog = new MenuItem("Открыть лог последнего запуска");
        miLog.setOnAction(ev -> {
            Path log = Store.logFile(g);
            if (!Files.isRegularFile(log)) showAlert(Alert.AlertType.INFORMATION, "Лог", "Для этой игры ещё нет лога — запустите её хотя бы раз.");
            else Sys.openPath(log);
        });
        MenuItem miCover = new MenuItem("Выбрать обложку из файла…");
        miCover.setOnAction(ev -> chooseCover(g));
        MenuItem miRemove = new MenuItem("Убрать из библиотеки");
        miRemove.setOnAction(ev -> removeGame(g));
        menu.getItems().addAll(miPlay, miFav, new SeparatorMenuItem(), miData, miCover, miFolder, miLog, new SeparatorMenuItem(), miRemove);

        card.setOnContextMenuRequested(ev -> menu.show(card, ev.getScreenX(), ev.getScreenY()));
        card.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) selectGame(g);
        });
        card.setOnMouseEntered(ev -> scale(card, 1.04));
        card.setOnMouseExited(ev -> scale(card, 1.0));
        return card;
    }

    private static void scale(Node n, double to) {
        ScaleTransition st = new ScaleTransition(Duration.millis(110), n);
        st.setToX(to);
        st.setToY(to);
        st.play();
    }

    /** Обложка заданного размера: картинка обрезается по центру до нужных пропорций. */
    private Node buildCover(Game g, double w, double h) {
        StackPane p = new StackPane();
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        Image img = loadCover(g, w);
        if (img != null) {
            ImageView iv = new ImageView(img);
            double iw = img.getWidth(), ih = img.getHeight(), vw, vh, vx, vy;
            if (iw / ih > w / h) {
                vh = ih;
                vw = ih * (w / h);
                vx = (iw - vw) / 2;
                vy = 0;
            } else {
                vw = iw;
                vh = iw * (h / w);
                vx = 0;
                vy = (ih - vh) * 0.3;   // у вертикальных постеров название обычно выше центра
            }
            iv.setViewport(new Rectangle2D(vx, vy, vw, vh));
            iv.setPreserveRatio(false);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setSmooth(true);
            p.getChildren().add(iv);
        } else {
            p.getStyleClass().add("ofll-cover-ph");
            String letter = g.name.isBlank() ? "?" : g.name.substring(0, 1).toUpperCase(Locale.ROOT);
            Label l = new Label(letter);
            l.getStyleClass().add("ofll-cover-letter");
            p.getChildren().add(l);
        }
        return p;
    }

    /** Маленький значок игры: квадрат из центра обложки со скруглёнными углами. */
    private Node buildIcon(Game g, double size) {
        StackPane p = new StackPane();
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        Image img = loadCover(g, cardWidth() - 6);
        if (img != null) {
            double iw = img.getWidth(), ih = img.getHeight(), side = Math.min(iw, ih);
            ImageView iv = new ImageView(img);
            iv.setViewport(new Rectangle2D((iw - side) / 2, (ih - side) / 2, side, side));
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            p.getChildren().add(iv);
        } else {
            p.getStyleClass().add("ofll-cover-ph");
            Label l = new Label(g.name.isBlank() ? "?" : g.name.substring(0, 1).toUpperCase(Locale.ROOT));
            l.getStyleClass().add("ofll-cover-letter");
            l.setStyle("-fx-font-size: " + Math.round(size * 0.45) + "px;");
            p.getChildren().add(l);
        }
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        p.setClip(clip);
        return p;
    }

    private Image loadCover(Game g, double w) {
        if (g.coverPath == null || g.coverPath.isBlank()) return null;
        Path cp = Paths.get(g.coverPath);
        if (!Files.isRegularFile(cp)) return null;
        long mt = 0;
        try { mt = Files.getLastModifiedTime(cp).toMillis(); } catch (IOException ignored) {}
        String key = cp + "@" + mt + "@w" + (int) w;
        Image img = imageCache.get(key);
        if (img == null) {
            img = new Image(cp.toUri().toString(), Math.max(1, w * 2), 0, true, true, false);
            if (img.isError() || img.getWidth() <= 0) return null;
            imageCache.put(key, img);
        }
        return img;
    }

    private static Label badge(String text, String extraClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("ofll-badge", extraClass);
        return l;
    }

    // ---------------------------------------------------------------
    //  Выбор игры и боковая панель
    // ---------------------------------------------------------------

    private void selectGame(Game g) {
        boolean wasHidden = !sideScroll.isVisible();
        Game prev = selected;
        selected = g;
        refreshGrid();
        updatePanel();
        if (config.animations && uiReady && g != null) {
            if (wasHidden) slideIn(sideScroll);
            else if (prev != g) fadeSwap(sideScroll.getContent());
        }
    }

    private void updatePanel() {
        Game g = selected;
        sideScroll.setVisible(g != null);
        sideScroll.setManaged(g != null);
        updateBackground(g);
        if (g == null) return;
        Node cov = buildCover(g, 290, 136);
        Rectangle covClip = new Rectangle(290, 136);
        covClip.setArcWidth(20);
        covClip.setArcHeight(20);
        cov.setClip(covClip);
        coverHolder.getChildren().setAll(cov);
        iconHolder.getChildren().setAll(buildIcon(g, 46));
        spTitle.setText(g.name);
        spPlaytime.setText(g.playtimeSeconds <= 0 ? "Ещё не запускалась" : "Наиграно: " + Sys.formatPlaytime(g.playtimeSeconds));
        boolean hasLast = g.lastPlayed > 0;
        spLast.setText(hasLast ? "Запуск: " + Sys.formatAgo(g.lastPlayed, System.currentTimeMillis() / 1000) : "");
        spLast.setVisible(hasLast);
        spLast.setManaged(hasLast);
        boolean run = running.containsKey(g.id);
        btnPlay.setDisable(run);
        btnPlay.setText(run ? "Игра запущена…" : "▶  Играть");
        if (playGlow != null) {
            if (run) {
                playGlow.pause();
                btnPlay.setEffect(null);
            } else {
                btnPlay.setEffect(playShadow);
                playGlow.play();
            }
        }
        btnConsole.setDisable(run);
        btnStop.setVisible(run);
        btnStop.setManaged(run);
        refreshEpicStatus();
        refreshMsStatus();
        refreshGogStatus();
        refreshModsButton();
        refreshRating();
    }

    // ---------------------------------------------------------------
    //  Моды (Thunderstore / BepInEx)
    // ---------------------------------------------------------------

    /** Кнопка «Моды» появляется, если для игры на thunderstore.io есть сообщество (определяется по названию). */
    private void refreshModsButton() {
        Game g = selected;
        if (g == null) return;
        boolean has = g.tsCommunity != null && !g.tsCommunity.isEmpty() && !g.tsCommunity.equals("-");
        btnMods.setVisible(has);
        btnMods.setManaged(has);
        if ((g.tsCommunity == null || g.tsCommunity.isEmpty()) && tsChecking.add(g.id)) {
            CompletableFuture.supplyAsync(() -> {
                try {
                    Thunder.Community c = Thunder.match(g.name);
                    return c == null ? "-" : c.id;
                } catch (IOException e) {
                    return "";
                }
            }).thenAccept(id -> Platform.runLater(() -> {
                if (id.isEmpty()) return;
                g.tsCommunity = id;
                Store.saveGames(games);
                if (selected == g) refreshModsButton();
            }));
        }
    }

    private void openMods() {
        Game g = selected;
        if (g == null) return;
        Path exeDir = Paths.get(g.executablePath).getParent();
        if (exeDir == null || g.tsCommunity == null || g.tsCommunity.isEmpty() || g.tsCommunity.equals("-")) return;
        Thunder.Community c = new Thunder.Community();
        c.id = g.tsCommunity;
        c.name = g.tsCommunity;
        new ModsWindow(primaryStage, g, c, exeDir, themeUrl, () -> Store.saveGames(games), this::openUrl).show();
    }

    /** Рейтинг ProtonDB: показывается, если известен Steam AppID; запрос идёт один раз за сеанс. */
    private void refreshRating() {
        Game g = selected;
        if (g == null) return;
        spRating.setVisible(false);
        spRating.setManaged(false);
        long id = Web.parseId(g.steamStoreId);
        if (!config.showProtonRating || id == 0) return;
        Web.Rating r = ratings.get(g.id);
        if (r != null) {
            spRating.getStyleClass().removeIf(c -> c.startsWith("tier-"));
            spRating.getStyleClass().add("tier-" + r.tier);
            spRating.setText("ProtonDB: " + Web.tierRu(r.tier) + (r.total > 0 ? " · " + r.total : ""));
            spRating.setVisible(true);
            spRating.setManaged(true);
        } else if (ratingRequested.add(g.id)) {
            CompletableFuture.supplyAsync(() -> {
                try { return Web.protonRating(id); } catch (IOException e) { return null; }
            }).thenAccept(rating -> Platform.runLater(() -> {
                if (rating == null) return;
                ratings.put(g.id, rating);
                if (selected == g) refreshRating();
            }));
        }
    }

    // ---------------------------------------------------------------
    //  Сайты, обложки и данные из интернета
    // ---------------------------------------------------------------

    /** Кнопка-миниатюра сайта: ведёт на страницу этой игры, а если AppID ещё неизвестен — сначала ищет его. */
    private void openSite(Links.Site kind) {
        Game g = selected;
        if (g == null) return;
        boolean needSteam = kind != Links.Site.SGDB && kind != Links.Site.GOG && Web.parseId(g.steamStoreId) == 0;
        boolean needSgdb = kind == Links.Site.SGDB && Web.parseId(g.sgdbId) == 0 && Web.validKey(config.sgdbKey);
        if (!needSteam && !needSgdb) {
            openUrl(Links.url(kind, g));
            return;
        }
        setNotice("Ищу игру на сайте…");
        String title = Web.cleanTitle(g.name), key = config.sgdbKey;
        CompletableFuture.supplyAsync(() -> {
            try {
                if (needSteam) {
                    Web.Found f = Web.steamSearch(title);
                    return f == null ? 0L : f.id;
                }
                return Web.sgdbSearchId(key, title);
            } catch (IOException e) {
                return 0L;
            }
        }).thenAccept(id -> Platform.runLater(() -> {
            if (id > 0) {
                if (needSteam) {
                    g.steamStoreId = Long.toString(id);
                    ratings.remove(g.id);
                    ratingRequested.remove(g.id);
                } else {
                    g.sgdbId = Long.toString(id);
                }
                Store.saveGames(games);
            }
            if (selected == g) {
                setNotice(null);
                refreshRating();
            }
            openUrl(Links.url(kind, g));
        }));
    }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x instanceof CompletionException && x.getCause() != null) x = x.getCause();
        return x.getMessage() == null ? x.toString() : x.getMessage();
    }

    /** Применяет найденное (AppID, обложка) к игре и сохраняет. Вызывать в потоке интерфейса. */
    private boolean applyCoverResult(Game g, Covers.Result res) {
        boolean changed = false;
        if (res.steamId != 0 && !Long.toString(res.steamId).equals(g.steamStoreId)) {
            g.steamStoreId = Long.toString(res.steamId);
            ratings.remove(g.id);
            ratingRequested.remove(g.id);
            changed = true;
        }
        if (!res.sgdbId.isEmpty() && !res.sgdbId.equals(g.sgdbId)) {
            g.sgdbId = res.sgdbId;
            changed = true;
        }
        if (res.cover != null) {
            g.coverPath = res.cover.toString();
            changed = true;
        }
        if (changed) {
            Store.saveGames(games);
            refreshGrid();
            if (selected == g) updatePanel();
        }
        return changed;
    }

    /** Ищет Steam AppID и обложку. manual — по нажатию кнопки (перезаписывает обложку и сообщает результат). */
    private void enrichAsync(Game g, boolean manual) {
        final String name = g.name, steamIn = g.steamStoreId, sgdbIn = g.sgdbId, key = config.sgdbKey, base = g.id;
        final boolean needCover = manual || g.coverPath == null || g.coverPath.isBlank();
        if (manual && selected == g) setNotice("Ищу игру, обложку и рейтинг…");
        CompletableFuture.supplyAsync(() -> Covers.fetch(name, steamIn, sgdbIn, key, needCover, base, Store.coversDir()))
            .whenComplete((res, err) -> Platform.runLater(() -> {
                if (err != null || res == null) {
                    if (manual) showAlert(Alert.AlertType.ERROR, "Данные игры", "Не удалось получить данные: " + (err == null ? "нет ответа" : rootMessage(err)));
                    return;
                }
                applyCoverResult(g, res);
                if (!manual) return;
                String msg;
                if (res.cover != null) msg = "Обложка найдена (" + res.source + ").";
                else if (res.warning != null) msg = "Обложка не найдена: " + res.warning;
                else if (res.steamId == 0) msg = "Игра «" + Web.cleanTitle(g.name) + "» не найдена в Steam. Укажите Steam AppID в настройках игры (вкладка «Данные и обложка»).";
                else msg = "Для этой игры нет вертикальной обложки. Добавьте ключ SteamGridDB в настройках лаунчера или выберите картинку вручную.";
                if (selected == g) setNotice(msg);
                else showAlert(Alert.AlertType.INFORMATION, "Данные игры", msg);
            }));
    }

    private static void deleteOwnCover(Game g) {
        if (g.coverPath == null || g.coverPath.isBlank()) return;
        try {
            Path p = Paths.get(g.coverPath);
            if (p.startsWith(Store.coversDir())) Files.deleteIfExists(p);
        } catch (Exception ignored) {}
    }

    private void clearCover(Game g) {
        deleteOwnCover(g);
        g.coverPath = "";
        Store.saveGames(games);
        refreshGrid();
        if (selected == g) updatePanel();
    }

    /** Пакетный поиск обложек для игр, у которых их ещё нет. */
    private void fetchMissingCovers(String key) {
        fetchCovers(key, false);
    }

    private void fetchCovers(String key, boolean all) {
        List<CoverJob> jobs = new ArrayList<>();
        for (Game g : games)
            if (all || g.coverPath == null || g.coverPath.isBlank()) jobs.add(new CoverJob(g, g.name, g.steamStoreId, g.sgdbId));
        if (jobs.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Обложки", "У всех игр в библиотеке уже есть обложки.");
            return;
        }
        Stage st = new Stage(StageStyle.UTILITY);
        st.initOwner(activeWindow());
        st.initModality(Modality.WINDOW_MODAL);
        st.setTitle("Поиск обложек");
        Brand.apply(st);
        Label status = styled("Подготовка…", "ofll-muted");
        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(400);
        Button cancel = button("Остановить", "btn-surface");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancel.setOnAction(ev -> {
            cancelled.set(true);
            cancel.setDisable(true);
            status.setText("Останавливаю…");
        });
        st.setOnCloseRequest(ev -> cancelled.set(true));
        VBox box = new VBox(14, styled("Поиск Steam AppID и обложек", "ofll-section"), status, bar, cancel);
        box.setPadding(new Insets(20));
        Scene sc = new Scene(box, 460, 190);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        st.setScene(sc);
        st.show();

        Path dir = Store.coversDir();
        Thread t = new Thread(() -> {
            int found = 0, i = 0;
            for (CoverJob j : jobs) {
                if (cancelled.get()) break;
                i++;
                final int idx = i;
                Platform.runLater(() -> {
                    status.setText("(" + idx + " из " + jobs.size() + ") " + j.name());
                    bar.setProgress((idx - 1.0) / jobs.size());
                });
                Covers.Result r = Covers.fetch(j.name(), j.steam(), j.sgdb(), key, true, j.g().id, dir);
                if (r.cover != null) found++;
                Platform.runLater(() -> applyCoverResult(j.g(), r));
                try { Thread.sleep(350); } catch (InterruptedException e) { break; }
            }
            final int total = found;
            Platform.runLater(() -> {
                st.close();
                showAlert(Alert.AlertType.INFORMATION, "Обложки", "Готово. Найдено обложек: " + total + " из " + jobs.size() + ".");
            });
        }, "covers-batch");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------
    //  Тема
    // ---------------------------------------------------------------

    private static String accentHex(String name) {
        switch (name) {
            case "blue": return "#89b4fa";
            case "green": return "#a6e3a1";
            case "peach": return "#fab387";
            case "pink": return "#f5c2e7";
            case "teal": return "#94e2d5";
            default: return "#cba6f7";
        }
    }

    private String buildThemeCss() {
        return THEME_CSS.replace("%ACCENT%", accentHex(config.accent));
    }

    /** Применяет цвет акцента ко всем открытым окнам. */
    private void reloadTheme() {
        String old = themeUrl;
        themeUrl = Store.writeTheme(buildThemeCss(), config.accent);
        if (brandGlow != null) brandGlow.setColor(Color.web(accentHex(config.accent)).deriveColor(0, 1, 1, 0.6));
        for (Window w : Window.getWindows()) {
            Scene sc = w.getScene();
            if (sc == null) continue;
            sc.getStylesheets().remove(old);
            if (!themeUrl.isEmpty() && !sc.getStylesheets().contains(themeUrl)) sc.getStylesheets().add(themeUrl);
        }
    }

    private void setNotice(String text) {
        boolean has = text != null && !text.isBlank();
        spNotice.setText(has ? text : "");
        spNotice.setVisible(has);
        spNotice.setManaged(has);
        if (has && uiReady && config.animations) {
            FadeTransition ft = new FadeTransition(Duration.millis(240), spNotice);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }

    private void refreshEpicStatus() {
        Game g = selected;
        if (g == null) return;
        cbEpic.setSelected(g.eosEnabled);
        btnEpicLogin.setVisible(g.eosEnabled);
        btnEpicLogin.setManaged(g.eosEnabled);
        if (!g.eosEnabled) {
            spEpicStatus.setText("Выключено: игра запускается без действий, связанных с Epic.");
            return;
        }
        List<String> cached = eosCache.get(g.id);
        if (cached != null) {
            spEpicStatus.setText(cached.isEmpty()
                ? "Включено, но EOS-файлы в папке игры не найдены — вход в Epic, скорее всего, не нужен."
                : "Включено. Найдено: " + shortName(cached.get(0)) + ". Ссылки входа Epic откроются в браузере.");
            return;
        }
        spEpicStatus.setText("Ищу EOS-файлы в папке игры…");
        Path root = gameRoot(g);
        CompletableFuture.supplyAsync(() -> Epic.findEosFiles(root)).thenAccept(files -> Platform.runLater(() -> {
            eosCache.put(g.id, files);
            if (selected == g) refreshEpicStatus();
        }));
    }

    private void refreshMsStatus() {
        Game g = selected;
        if (g == null) return;
        cbMs.setSelected(g.msEnabled);
        btnMsLogin.setVisible(g.msEnabled);
        btnMsLogin.setManaged(g.msEnabled);
        if (!g.msEnabled) {
            spMsStatus.setText("Выключено: игра запускается без действий, связанных с Microsoft.");
            return;
        }
        List<String> cached = msCache.get(g.id);
        if (cached != null) {
            spMsStatus.setText(cached.isEmpty()
                ? "Включено, но файлы Xbox / Microsoft Store в папке игры не найдены. Ссылки входа Microsoft всё равно откроются, если игра их выведет."
                : "Включено. Найдено: " + shortName(cached.get(0)) + ". Ссылки входа Microsoft откроются в браузере.");
            return;
        }
        spMsStatus.setText("Ищу файлы Xbox / Microsoft в папке игры…");
        Path root = gameRoot(g);
        CompletableFuture.supplyAsync(() -> Microsoft.findFiles(root)).thenAccept(files -> Platform.runLater(() -> {
            msCache.put(g.id, files);
            if (selected == g) refreshMsStatus();
        }));
    }

    private void refreshGogStatus() {
        Game g = selected;
        if (g == null) return;
        cbGog.setSelected(g.gogEnabled);
        btnGogLogin.setVisible(g.gogEnabled);
        btnGogLogin.setManaged(g.gogEnabled);
        if (!g.gogEnabled) {
            spGogStatus.setText("Выключено: игра запускается без действий, связанных с GOG.");
            return;
        }
        List<String> cached = gogCache.get(g.id);
        if (cached != null) {
            spGogStatus.setText(cached.isEmpty()
                ? "Включено, но файлы GOG Galaxy в папке игры не найдены. Ссылки входа GOG всё равно откроются, если игра их выведет."
                : "Включено. Найдено: " + shortName(cached.get(0)) + ". Ссылки входа GOG откроются в браузере.");
            return;
        }
        spGogStatus.setText("Ищу файлы GOG Galaxy в папке игры…");
        Path root = gameRoot(g);
        CompletableFuture.supplyAsync(() -> Gog.findFiles(root)).thenAccept(files -> Platform.runLater(() -> {
            gogCache.put(g.id, files);
            if (selected == g) refreshGogStatus();
        }));
    }

    private static String shortName(String relPath) {
        String s = relPath.endsWith("/") ? relPath.substring(0, relPath.length() - 1) : relPath;
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private static final Set<String> GENERIC_DIRS = Set.of("win64", "win32", "x64", "x86", "binaries", "bin", "shipping", "retail");

    /** Корневая папка игры (для поиска EOS): выбранная при добавлении или выведенная из пути к exe. */
    private Path gameRoot(Game g) {
        if (g.rootPath != null && !g.rootPath.isBlank() && Files.isDirectory(Paths.get(g.rootPath))) return Paths.get(g.rootPath);
        Path p = Paths.get(g.executablePath).getParent();
        int up = 0;
        while (p != null && p.getParent() != null && up < 4
                && GENERIC_DIRS.contains(p.getFileName().toString().toLowerCase(Locale.ROOT))) {
            p = p.getParent();
            up++;
        }
        return p;
    }

    // ---------------------------------------------------------------
    //  Добавление / удаление игр
    // ---------------------------------------------------------------

    private static final class ExeChoice {
        final Path path;
        final String label;
        final long size;
        ExeChoice(Path path, String label, long size) { this.path = path; this.label = label; this.size = size; }
        @Override public String toString() { return label; }
    }

    private static final Set<String> SKIP_DIRS = Set.of("_commonredist", "commonredist", "redist", "redistributables",
        "directx", "vcredist", "dotnet", "dotnetfx", "__installer", "installer");
    private static final String[] SKIP_NAMES = {"unins", "uninstall", "vcredist", "vc_redist", "dxsetup", "dotnet",
        "crashhandler", "crashreport", "crashpad", "ue4prereq", "oalinst", "physx", "notification_helper", "cefprocess", "installer"};

    private static List<ExeChoice> scanForExecutables(Path root) {
        List<ExeChoice> good = new ArrayList<>(), rest = new ArrayList<>();
        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 6, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (!d.equals(root) && SKIP_DIRS.contains(d.getFileName().toString().toLowerCase(Locale.ROOT)))
                        return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (!n.endsWith(".exe") && !n.endsWith(".sh")) return FileVisitResult.CONTINUE;
                    ExeChoice c = new ExeChoice(f, root.relativize(f).toString(), a.size());
                    boolean junk = false;
                    for (String bad : SKIP_NAMES) if (n.contains(bad)) { junk = true; break; }
                    (junk ? rest : good).add(c);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        Comparator<ExeChoice> order = Comparator
            .comparing((ExeChoice c) -> c.path.toString().endsWith(".sh"))
            .thenComparing(Comparator.comparingLong((ExeChoice c) -> c.size).reversed());
        good.sort(order);
        rest.sort(order);
        List<ExeChoice> all = new ArrayList<>(good);
        all.addAll(rest);
        return all;
    }

    private void handleAddGame() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Выберите папку с игрой");
        File dir = dc.showDialog(primaryStage);
        if (dir == null) return;
        addFromFolder(dir.toPath());
    }

    /** Ищет в папке файлы запуска и добавляет игру (выбор папки кнопкой или перетаскиванием в окно). */
    private void addFromFolder(Path root) {
        primaryStage.getScene().setCursor(Cursor.WAIT);
        CompletableFuture.supplyAsync(() -> scanForExecutables(root)).whenComplete((list, err) -> Platform.runLater(() -> {
            primaryStage.getScene().setCursor(Cursor.DEFAULT);
            if (err != null || list == null || list.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Файлы не найдены", "В выбранной папке не найдено ни одного .exe или .sh файла.");
                return;
            }
            ExeChoice picked = list.get(0);
            if (list.size() > 1) {
                ChoiceDialog<ExeChoice> d = new ChoiceDialog<>(list.get(0), list);
                d.setTitle("Файл запуска");
                d.setHeaderText("Выберите файл, которым запускается игра");
                d.setContentText("Файл:");
                d.getDialogPane().getStylesheets().add(themeUrl);
                d.initOwner(primaryStage);
                Optional<ExeChoice> res = d.showAndWait();
                if (!res.isPresent()) return;
                picked = res.get();
            }
            addGame(root, picked.path);
        }));
    }

    private void addGame(Path root, Path exe) {
        Game g = new Game();
        g.name = Web.cleanTitle(root.getFileName() == null ? exe.getFileName().toString() : root.getFileName().toString());
        g.rootPath = root.toString();
        g.executablePath = exe.toString();
        g.useWine3D = config.defaultWined3d;
        g.useWayland = config.defaultWayland;
        g.protonVersion = config.defaultProton;
        g.dllOverrides.addAll(Runner.detectProxyDlls(exe.getParent()));
        games.add(g);
        Store.saveGames(games);
        selectGame(g);

        if (config.autoCovers) enrichAsync(g, false);

        // авто-определение Epic Online Services и Xbox / Microsoft
        CompletableFuture.supplyAsync(() -> List.of(Epic.findEosFiles(root), Microsoft.findFiles(root), Gog.findFiles(root)))
            .thenAccept(res -> Platform.runLater(() -> {
                List<String> eos = res.get(0), ms = res.get(1), gog = res.get(2);
                eosCache.put(g.id, eos);
                msCache.put(g.id, ms);
                gogCache.put(g.id, gog);
                g.eosChecked = true;
                g.eosEnabled = !eos.isEmpty();
                g.msChecked = true;
                g.msEnabled = !ms.isEmpty();
                g.gogChecked = true;
                g.gogEnabled = !gog.isEmpty();
                Store.saveGames(games);
                if (selected == g) {
                    updatePanel();
                    List<String> names = new ArrayList<>();
                    if (!eos.isEmpty()) names.add("Epic Online Services");
                    if (!ms.isEmpty()) names.add("Xbox / Microsoft");
                    if (!gog.isEmpty()) names.add("GOG Galaxy");
                    if (!names.isEmpty())
                        setNotice("Найдено: " + String.join(", ", names) + " — поддержка включена. Отключить можно переключателями ниже.");
                }
            }));
    }

    private void removeGame(Game g) {
        Alert a = makeAlert(Alert.AlertType.CONFIRMATION, "Убрать из библиотеки",
            "Убрать «" + g.name + "» из библиотеки?", "Файлы игры и её префикс Wine не будут удалены.");
        a.showAndWait().ifPresent(r -> {
            if (r != ButtonType.OK) return;
            Runner.Session s = running.get(g.id);
            if (s != null) s.stop();
            deleteOwnCover(g);
            games.remove(g);
            Store.saveGames(games);
            if (selected == g) selected = null;
            refreshGrid();
            updatePanel();
        });
    }

    private void chooseCover(Game g) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Выберите обложку");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        File f = fc.showOpenDialog(primaryStage);
        if (f == null) return;
        try {
            String n = f.getName();
            String ext = n.contains(".") ? n.substring(n.lastIndexOf('.')).toLowerCase(Locale.ROOT) : ".png";
            Path dst = Store.coversDir().resolve(g.id + ext);
            if (!f.toPath().startsWith(Store.coversDir())) deleteOwnCover(g);
            Files.copy(f.toPath(), dst, StandardCopyOption.REPLACE_EXISTING);
            g.coverPath = dst.toString();
            Store.saveGames(games);
            refreshGrid();
            if (selected == g) updatePanel();
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Обложка", "Не удалось скопировать изображение: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Запуск игры
    // ---------------------------------------------------------------

    private void onPlay(boolean withConsole) {
        Game g = selected;
        if (g == null || running.containsKey(g.id)) return;
        if (!Files.isRegularFile(Paths.get(g.executablePath))) {
            showAlert(Alert.AlertType.ERROR, "Файл не найден",
                "Файл запуска не найден:\n" + g.executablePath + "\n\nУкажите правильный путь в настройках игры.");
            return;
        }
        if (!Proton.isSystem(g.protonVersion) && Proton.resolve(config, g.protonVersion) == null) {
            Alert a = makeAlert(Alert.AlertType.CONFIRMATION, "Нужен Proton",
                "Не найден «" + g.protonVersion + "»", "Скачать и установить последнюю версию GE-Proton сейчас?");
            ButtonType yes = new ButtonType("Скачать", ButtonBar.ButtonData.YES);
            a.getButtonTypes().setAll(yes, ButtonType.CANCEL);
            Optional<ButtonType> r = a.showAndWait();
            if (r.isPresent() && r.get() == yes) {
                showInstallDialog(name -> {
                    g.protonVersion = name;
                    Store.saveGames(games);
                    updatePanel();
                    prepareLaunch(g, withConsole);
                });
            }
            return;
        }
        prepareLaunch(g, withConsole);
    }

    /** Epic/EOS: если включено — ищем EOS-файлы и предлагаем открыть страницу входа. */
    private void prepareLaunch(Game g, boolean withConsole) {
        if (!g.msChecked) {
            List<String> foundMs = Microsoft.findFiles(gameRoot(g));
            msCache.put(g.id, foundMs);
            g.msEnabled = !foundMs.isEmpty();
            g.msChecked = true;
            Store.saveGames(games);
        }
        if (!g.gogChecked) {
            List<String> foundGog = Gog.findFiles(gameRoot(g));
            gogCache.put(g.id, foundGog);
            g.gogEnabled = !foundGog.isEmpty();
            g.gogChecked = true;
            Store.saveGames(games);
        }
        if (!g.eosChecked) {
            List<String> found = Epic.findEosFiles(gameRoot(g));
            eosCache.put(g.id, found);
            g.eosEnabled = !found.isEmpty();
            g.eosChecked = true;
            Store.saveGames(games);
        }
        if (g.eosEnabled) {
            List<String> files = eosCache.computeIfAbsent(g.id, k -> Epic.findEosFiles(gameRoot(g)));
            if (files.isEmpty()) {
                setNotice("Epic включён, но EOS-файлы не найдены — вход не требуется.");
            } else if (!g.epicSkipPrompt) {
                Alert a = makeAlert(Alert.AlertType.CONFIRMATION, "Epic Games",
                    "Игре нужен вход в Epic Games",
                    "В папке игры найден " + shortName(files.get(0)) + ".\n"
                    + "Открыть страницу входа Epic в браузере перед запуском?");
                ButtonType open = new ButtonType("Открыть и запустить", ButtonBar.ButtonData.YES);
                ButtonType noAsk = new ButtonType("Запустить, больше не спрашивать", ButtonBar.ButtonData.NO);
                a.getButtonTypes().setAll(open, noAsk, ButtonType.CANCEL);
                Optional<ButtonType> r = a.showAndWait();
                if (!r.isPresent() || r.get() == ButtonType.CANCEL) return;
                if (r.get() == open) {
                    openUrl(Epic.LOGIN_URL);
                    setNotice("Страница входа Epic открыта в браузере.");
                } else {
                    g.epicSkipPrompt = true;
                    Store.saveGames(games);
                }
            }
        }
        if (g.msEnabled) {
            List<String> msFiles = msCache.computeIfAbsent(g.id, k -> Microsoft.findFiles(gameRoot(g)));
            if (!msFiles.isEmpty() && !g.msSkipPrompt) {
                Alert a = makeAlert(Alert.AlertType.CONFIRMATION, "Microsoft / Xbox",
                    "Игре нужен вход в аккаунт Microsoft",
                    "В папке игры найден " + shortName(msFiles.get(0)) + ".\n"
                    + "Открыть страницу входа Microsoft в браузере перед запуском?");
                ButtonType open = new ButtonType("Открыть и запустить", ButtonBar.ButtonData.YES);
                ButtonType noAsk = new ButtonType("Запустить, больше не спрашивать", ButtonBar.ButtonData.NO);
                a.getButtonTypes().setAll(open, noAsk, ButtonType.CANCEL);
                Optional<ButtonType> r = a.showAndWait();
                if (!r.isPresent() || r.get() == ButtonType.CANCEL) return;
                if (r.get() == open) {
                    openUrl(Microsoft.LOGIN_URL);
                    setNotice("Страница входа Microsoft открыта в браузере.");
                } else {
                    g.msSkipPrompt = true;
                    Store.saveGames(games);
                }
            }
        }
        if (g.gogEnabled) {
            List<String> gogFiles = gogCache.computeIfAbsent(g.id, k -> Gog.findFiles(gameRoot(g)));
            if (!gogFiles.isEmpty() && !g.gogSkipPrompt) {
                Alert a = makeAlert(Alert.AlertType.CONFIRMATION, "GOG Galaxy",
                    "Игре нужен вход в аккаунт GOG",
                    "В папке игры найден " + shortName(gogFiles.get(0)) + ".\n"
                    + "Открыть сайт GOG в браузере перед запуском?");
                ButtonType open = new ButtonType("Открыть и запустить", ButtonBar.ButtonData.YES);
                ButtonType noAsk = new ButtonType("Запустить, больше не спрашивать", ButtonBar.ButtonData.NO);
                a.getButtonTypes().setAll(open, noAsk, ButtonType.CANCEL);
                Optional<ButtonType> r = a.showAndWait();
                if (!r.isPresent() || r.get() == ButtonType.CANCEL) return;
                if (r.get() == open) {
                    openUrl(Gog.LOGIN_URL);
                    setNotice("Сайт GOG открыт в браузере.");
                } else {
                    g.gogSkipPrompt = true;
                    Store.saveGames(games);
                }
            }
        }
        // моды BepInEx: подключаем активную коллекцию и прокси-DLL (winhttp=n,b) к запуску
        Path exeDirMods = Paths.get(g.executablePath).getParent();
        List<String> modWarn = ModStore.prepareLaunch(g, exeDirMods);
        Store.saveGames(games);
        if (!modWarn.isEmpty()) setNotice(String.join("\n", modWarn));
        // Vulkan на обычном Wine работает только с DXVK в префиксе
        if (Runner.isWineLike(config, g.protonVersion) && !g.useWine3D && !g.dxvkSkipPrompt
                && !g.executablePath.toLowerCase(Locale.ROOT).endsWith(".sh")
                && !Dxvk.isInstalled(Runner.winePrefix(g, config))) {
            Alert a = makeAlert(Alert.AlertType.CONFIRMATION, "DXVK (Vulkan)", "В префиксе Wine не установлен DXVK",
                "Выбран режим Vulkan, но без DXVK Wine рисует через OpenGL (WineD3D): игра может не запуститься или работать медленно.\n\n"
                + "Установить DXVK в префикс этой игры сейчас?");
            ButtonType inst = new ButtonType("Установить и играть", ButtonBar.ButtonData.YES);
            ButtonType skip = new ButtonType("Играть без DXVK", ButtonBar.ButtonData.NO);
            a.getButtonTypes().setAll(inst, skip, ButtonType.CANCEL);
            Optional<ButtonType> r = a.showAndWait();
            if (!r.isPresent() || r.get() == ButtonType.CANCEL) return;
            if (r.get() == inst) {
                installDxvk(g, () -> startGame(g, withConsole));
                return;
            }
            g.dxvkSkipPrompt = true;
            Store.saveGames(games);
        }
        startGame(g, withConsole);
    }

    private void installDxvk(Game g, Runnable then) {
        Path pfx = Runner.winePrefix(g, config);
        Path wine = Runner.wineFor(config, g.protonVersion);
        runWithProgress("Установка DXVK", (s, p, c) -> Dxvk.install(pfx, wine, s, p, c), v -> {
            toast(I18n.fmt("DXVK {0} установлен в префикс игры.", v));
            if (then != null) then.run();
        });
    }

    private void startGame(Game g, boolean withConsole) {
        DebugConsole console = withConsole ? new DebugConsole(g.name, themeUrl) : null;
        if (console != null) console.show();
        Runner.Listener listener = new Runner.Listener() {
            @Override public void log(String line) {
                if (console != null) console.append(line);
            }
            @Override public void epicUrl(String url) {
                Platform.runLater(() -> {
                    openUrl(url);
                    if (selected == g) setNotice("Игра запросила вход в Epic — страница открыта в браузере.");
                });
            }
            @Override public void msUrl(String url) {
                Platform.runLater(() -> {
                    openUrl(url);
                    if (selected == g) setNotice("Игра запросила вход в Microsoft — страница открыта в браузере.");
                });
            }
            @Override public void gogUrl(String url) {
                Platform.runLater(() -> {
                    openUrl(url);
                    if (selected == g) setNotice("Игра запросила вход в GOG — страница открыта в браузере.");
                });
            }
        };
        try {
            Runner.Session s = Runner.start(g, config, listener,
                (secs, code) -> Platform.runLater(() -> onGameExit(g, secs, code)));
            running.put(g.id, s);
            g.lastPlayed = System.currentTimeMillis() / 1000;
            if (selected == g) setNotice("Игра запускается…");
            refreshGrid();
            updatePanel();
            if (config.minimizeOnLaunch) primaryStage.setIconified(true);
        } catch (Runner.LaunchException ex) {
            showAlert(Alert.AlertType.ERROR, "Не удалось запустить игру", ex.getMessage());
        }
    }

    private void onGameExit(Game g, long secs, int code) {
        Runner.Session s = running.remove(g.id);
        if (config.minimizeOnLaunch && running.isEmpty()) primaryStage.setIconified(false);
        g.playtimeSeconds += secs;
        Store.saveGames(games);
        refreshGrid();
        if (selected == g) {
            setNotice(null);
            updatePanel();
        }
        boolean stopped = s != null && s.stoppedByUser;
        if (!stopped && code != 0 && secs < 20) {
            showAlert(Alert.AlertType.WARNING, "Игра сразу закрылась",
                "«" + g.name + "» завершилась с кодом " + code + " через " + secs + " с.\n\n"
                + "Запустите её через «Запуск с консолью (отладка)», чтобы увидеть причину.\n"
                + "Лог последнего запуска:\n" + Store.logFile(g));
        }
    }

    private void stopSelected() {
        if (selected == null) return;
        Runner.Session s = running.get(selected.id);
        if (s != null) s.stop();
    }

    private void launchWinetricks() {
        Game g = selected;
        if (g == null) return;
        if (Sys.which("winetricks") == null) {
            showAlert(Alert.AlertType.ERROR, "Winetricks", "Winetricks не найден. Установите пакет winetricks (и zenity для графического окна).");
            return;
        }
        boolean proton = !Proton.isSystem(g.protonVersion);
        Path prefix = Runner.winePrefix(g, config);
        Path protonWine = null;
        if (proton) {
            if (!Files.isRegularFile(prefix.resolve("system.reg"))) {
                showAlert(Alert.AlertType.INFORMATION, "Префикс ещё не создан",
                    "Запустите игру один раз — Proton создаст префикс, после этого Winetricks станет доступен.\n\n"
                    + "Если игра уже запускалась, а префикс всё равно пуст, он мог не создаться полностью: "
                    + "удалите папку\n" + prefix.getParent() + "\nи запустите игру заново.");
                return;
            }
            Path pd = Proton.resolve(config, g.protonVersion);
            protonWine = pd == null ? null : Proton.wineBinary(pd);
            if (protonWine == null) {
                showAlert(Alert.AlertType.ERROR, "Winetricks",
                    "В выбранной версии Proton не найден wine (files/bin/wine). Выберите другую версию или установите GE-Proton заново.");
                return;
            }
        }
        Path log = Store.logFile(g).resolveSibling(g.id + "-winetricks.log");
        ProcessBuilder pb = new ProcessBuilder("winetricks", "--gui");
        Sys.restoreDisplayEnv(pb.environment());
        Runner.winetricksEnv(pb.environment(), prefix, protonWine);
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        try {
            Process proc = pb.start();
            setNotice("Winetricks запущен для префикса этой игры.");
            proc.onExit().thenAccept(p -> {
                if (p.exitValue() != 0)
                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "Winetricks",
                        "Winetricks завершился с ошибкой (код " + p.exitValue() + ").\nПодробности в логе:\n" + log));
            });
        } catch (IOException ex) {
            showAlert(Alert.AlertType.ERROR, "Winetricks", "Не удалось запустить Winetricks: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Установка Proton / Wine и фоновые задачи
    // ---------------------------------------------------------------

    private interface ProgressTask {
        String run(Consumer<String> status, DoubleConsumer progress, BooleanSupplier cancelled) throws Exception;
    }

    /** Окно с полосой прогресса для долгой задачи (скачивание, установка). Результат задачи получает onDone. */
    private void runWithProgress(String title, ProgressTask task, Consumer<String> onDone) {
        Stage st = new Stage(StageStyle.UTILITY);
        st.initOwner(activeWindow());
        st.initModality(Modality.WINDOW_MODAL);
        st.setTitle(title);
        Brand.apply(st);
        Label head = styled(title, "ofll-section");
        Label status = styled("Подготовка…", "ofll-muted");
        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setPrefWidth(430);
        Button cancel = button("Отмена", "btn-surface");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancel.setOnAction(ev -> {
            cancelled.set(true);
            cancel.setDisable(true);
            status.setText("Отменяю…");
        });
        st.setOnCloseRequest(ev -> cancelled.set(true));
        VBox box = new VBox(14, head, status, bar, cancel);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER_LEFT);
        Scene sc = new Scene(box, 490, 200);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        st.setScene(sc);
        st.show();

        Thread t = new Thread(() -> {
            try {
                String res = task.run(m -> Platform.runLater(() -> status.setText(m)),
                    p -> Platform.runLater(() -> bar.setProgress(p)), cancelled::get);
                Platform.runLater(() -> {
                    st.close();
                    if (onDone != null) onDone.accept(res);
                });
            } catch (CancellationException ce) {
                Platform.runLater(st::close);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    st.close();
                    showAlert(Alert.AlertType.ERROR, "Ошибка", String.valueOf(ex.getMessage()));
                });
            }
        }, "ofll-task");
        t.setDaemon(true);
        t.start();
    }

    /** Выбор runner-а: GE-Proton или сборка Wine, конкретная версия и сборка — затем скачивание и установка. */
    private void showInstallDialog(Consumer<String> onInstalled) {
        Stage st = new Stage();
        st.initOwner(activeWindow());
        st.initModality(Modality.WINDOW_MODAL);
        st.setTitle("Установка Proton / Wine");
        Brand.apply(st);

        ComboBox<String> cbFamily = new ComboBox<>(FXCollections.observableArrayList(
            "GE-Proton (Proton)", "Wine (Kron4ek Wine-Builds)"));
        cbFamily.getSelectionModel().select(0);
        ComboBox<Proton.Release> cbVer = new ComboBox<>();
        ComboBox<Proton.Asset> cbBuild = new ComboBox<>();
        cbFamily.setPrefWidth(360);
        cbVer.setPrefWidth(360);
        cbBuild.setPrefWidth(360);
        Label lblBuild = new Label("Сборка");
        Label status = styled("", "ofll-muted");
        Label info = styled("GE-Proton подходит для большинства игр и уже содержит DXVK и VKD3D. "
            + "Wine — обычные сборки Wine (vanilla, staging, staging-tkg); DXVK для них ставится отдельно: "
            + "настройки игры → «Графика».", "ofll-hint");
        Button btnInstall = button("Установить", "btn-green");
        btnInstall.setDisable(true);
        Button btnCancel = button("Отмена", "btn-surface");
        btnCancel.setOnAction(ev -> st.close());

        int[] reqId = {0};
        Runnable load = () -> {
            boolean wine = cbFamily.getSelectionModel().getSelectedIndex() == 1;
            Proton.Family fam = wine ? Proton.Family.WINE : Proton.Family.GE;
            lblBuild.setVisible(wine);
            lblBuild.setManaged(wine);
            cbBuild.setVisible(wine);
            cbBuild.setManaged(wine);
            btnInstall.setDisable(true);
            cbVer.getItems().clear();
            cbBuild.getItems().clear();
            status.setText("Загружаю список версий…");
            final int my = ++reqId[0];
            CompletableFuture.supplyAsync(() -> {
                try {
                    return Proton.listReleases(fam);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            }).whenComplete((rels, err) -> Platform.runLater(() -> {
                if (my != reqId[0] || !st.isShowing()) return;
                if (err != null) {
                    status.setText("Не удалось получить список версий: " + rootMessage(err));
                    return;
                }
                cbVer.setItems(FXCollections.observableArrayList(rels));
                if (!rels.isEmpty()) cbVer.getSelectionModel().select(0);
                status.setText(I18n.fmt("Доступно версий: {0}", rels.size()));
            }));
        };
        cbFamily.setOnAction(ev -> load.run());
        cbVer.setOnAction(ev -> {
            Proton.Release r = cbVer.getValue();
            cbBuild.getItems().clear();
            if (r == null) {
                btnInstall.setDisable(true);
                return;
            }
            cbBuild.setItems(FXCollections.observableArrayList(r.archives));
            cbBuild.getSelectionModel().select(0);
            btnInstall.setDisable(false);
        });
        btnInstall.setOnAction(ev -> {
            Proton.Release r = cbVer.getValue();
            Proton.Asset a = cbBuild.getValue();
            if (r == null || a == null) return;
            Path root = Paths.get(config.protonPath);
            st.close();
            runWithProgress(I18n.fmt("Установка {0}", a.name),
                (s, p, c) -> Proton.install(root, a, r.sha, s, p, c), onInstalled);
        });

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("Тип"), 0, 0);
        form.add(cbFamily, 1, 0);
        form.add(new Label("Версия"), 0, 1);
        form.add(cbVer, 1, 1);
        form.add(lblBuild, 0, 2);
        form.add(cbBuild, 1, 2);
        HBox buttons = new HBox(10, btnCancel, btnInstall);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox box = new VBox(14, styled("Установка Proton / Wine", "ofll-section"), form, info, status, buttons);
        box.setPadding(new Insets(20));
        Scene sc = new Scene(box, 560, 360);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        st.setScene(sc);
        st.show();
        load.run();
    }

    // ---------------------------------------------------------------
    //  Окно настроек игры    // ---------------------------------------------------------------
    //  Окно настроек игры
    // ---------------------------------------------------------------

    private List<String> runnerChoices(String current) {
        List<String> l = Proton.available(config);
        String c = Proton.normalize(current);
        if (!l.contains(c)) l.add(c);
        return l;
    }

    private void openSettingsDialog() {
        final Game g = selected;
        if (g == null) return;
        Stage stage = new Stage();
        stage.initOwner(primaryStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Настройки: " + g.name);
        Brand.apply(stage);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ---- Основное ----
        TextField nameFld = new TextField(g.name);
        TextField exeFld = new TextField(g.executablePath);
        Button exeBrowse = button("Обзор…", "btn-surface");
        exeBrowse.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Файл запуска");
            File init = new File(exeFld.getText()).getParentFile();
            if (init != null && init.isDirectory()) fc.setInitialDirectory(init);
            File f = fc.showOpenDialog(stage);
            if (f != null) exeFld.setText(f.getAbsolutePath());
        });
        TextField argsFld = new TextField(g.customArgs);
        argsFld.setPromptText("например: -windowed -w 1280");
        TextField envFld = new TextField(g.envVars);
        envFld.setPromptText("например: PROTON_NO_ESYNC=1 DXVK_HUD=fps");
        GridPane basic = grid();
        basic.add(new Label("Название"), 0, 0);
        basic.add(nameFld, 1, 0, 2, 1);
        basic.add(new Label("Файл запуска"), 0, 1);
        basic.add(exeFld, 1, 1);
        basic.add(exeBrowse, 2, 1);
        basic.add(new Label("Аргументы запуска"), 0, 2);
        basic.add(argsFld, 1, 2, 2, 1);
        basic.add(new Label("Переменные окружения"), 0, 3);
        basic.add(envFld, 1, 3, 2, 1);
        basic.add(styled("Аргументы и переменные разделяются пробелами. Значения с пробелами берите в кавычки.", "ofll-hint"), 1, 4, 2, 1);
        CheckBox cbFav = new CheckBox("Избранное — показывать в начале библиотеки");
        cbFav.setSelected(g.favorite);
        basic.add(cbFav, 1, 5, 2, 1);
        Tab tabBasic = tab("Основное", basic);

        // ---- Графика ----
        ToggleGroup tg = new ToggleGroup();
        RadioButton rbVulkan = new RadioButton("Vulkan (DXVK / VKD3D) — максимальная производительность, рекомендуется");
        RadioButton rbGl = new RadioButton("OpenGL (WineD3D) — для старых видеокарт или если Vulkan не работает");
        rbVulkan.setToggleGroup(tg);
        rbGl.setToggleGroup(tg);
        rbGl.setSelected(g.useWine3D);
        rbVulkan.setSelected(!g.useWine3D);
        CheckBox cbWayland = new CheckBox("Нативный Wayland для этой игры (экспериментально)");
        cbWayland.setSelected(g.useWayland);
        CheckBox cbDefGl = new CheckBox("Использовать WineD3D (OpenGL)");
        cbDefGl.setSelected(config.defaultWined3d);
        CheckBox cbDefWayland = new CheckBox("Использовать нативный Wayland");
        cbDefWayland.setSelected(config.defaultWayland);
        VBox gfx = new VBox(12, styled("Графический API для этой игры", "ofll-section"), rbVulkan, rbGl, cbWayland,
            new Separator(), styled("По умолчанию для новых игр", "ofll-section"), cbDefGl, cbDefWayland);
        gfx.setPadding(new Insets(16));
        Tab tabGfx = tab("Графика", gfx);

        // ---- Proton / Wine ----
        ComboBox<String> cbRunner = new ComboBox<>(FXCollections.observableArrayList(runnerChoices(g.protonVersion)));
        cbRunner.setValue(Proton.normalize(g.protonVersion));
        ComboBox<String> cbDefRunner = new ComboBox<>(FXCollections.observableArrayList(runnerChoices(config.defaultProton)));
        cbDefRunner.setValue(Proton.normalize(config.defaultProton));
        Label runnerInfo = styled("", "ofll-hint");
        Runnable updateRunnerInfo = () -> runnerInfo.setText(
            "Найдено версий Proton: " + Proton.discover(config).size()
            + ". Системный Wine: " + (Sys.which("wine") != null ? "установлен" : "не найден") + ".");
        updateRunnerInfo.run();
        Button btnInstall = button("Установить Proton / Wine…", "btn-blue");
        btnInstall.setOnAction(ev -> showInstallDialog(name -> {
            cbRunner.setItems(FXCollections.observableArrayList(runnerChoices(name)));
            cbDefRunner.setItems(FXCollections.observableArrayList(runnerChoices(config.defaultProton)));
            cbRunner.setValue(name);
            cbDefRunner.setValue(Proton.normalize(config.defaultProton));
            updateRunnerInfo.run();
        }));
        GridPane pg = grid();
        pg.add(new Label("Для этой игры"), 0, 0);
        pg.add(cbRunner, 1, 0);
        pg.add(new Label("По умолчанию"), 0, 1);
        pg.add(cbDefRunner, 1, 1);
        pg.add(btnInstall, 1, 2);
        pg.add(runnerInfo, 1, 3);
        Tab tabProton = tab("Proton / Wine", pg);

        // ---- DXVK для обычного Wine (у Proton он встроен) ----
        Label dxvkStatus = styled("", "ofll-hint");
        Button btnDxvk = button("Установить DXVK в префикс", "btn-blue");
        VBox dxvkBox = new VBox(8, new Separator(), styled("DXVK (Vulkan) в префиксе Wine", "ofll-section"), dxvkStatus, btnDxvk);
        Runnable updDxvk = () -> {
            boolean wineLike = Runner.isWineLike(config, cbRunner.getValue());
            dxvkBox.setVisible(wineLike);
            dxvkBox.setManaged(wineLike);
            Path pfx = Runner.prefixDir(g, config);
            String vk = Dxvk.vulkanDriverFound() ? "Vulkan-драйвер найден."
                : "Vulkan-драйвер не найден — установите драйвер видеокарты и vulkan-icd-loader.";
            dxvkStatus.setText((Dxvk.isInstalled(pfx)
                ? I18n.fmt("DXVK установлен в префикс ({0}).", Dxvk.version(pfx))
                : "DXVK в префикс ещё не установлен: режим Vulkan для обычного Wine без него не работает, игра пойдёт через OpenGL.")
                + " " + vk);
        };
        cbRunner.setOnAction(ev -> updDxvk.run());
        updDxvk.run();
        btnDxvk.setOnAction(ev -> {
            Path wine = Runner.wineFor(config, cbRunner.getValue());
            Path pfx = Runner.prefixDir(g, config);
            runWithProgress("Установка DXVK", (s, p, c) -> Dxvk.install(pfx, wine, s, p, c), v -> {
                toast(I18n.fmt("DXVK {0} установлен в префикс игры.", v));
                updDxvk.run();
            });
        });
        gfx.getChildren().add(dxvkBox);

        // ---- Epic, Microsoft и Steam ----
        CheckBox cbEos = new CheckBox("Включить поддержку Epic Games (EOS)");
        cbEos.setSelected(g.eosEnabled);
        Label epicHint = styled("Если включено, лаунчер ищет в папке игры файлы Epic Online Services. Когда они найдены, "
            + "перед запуском он предлагает открыть страницу входа Epic в браузере, а ссылки входа и активации Epic, "
            + "которые игра выводит в лог, открываются в браузере автоматически. Если выключено — с Epic ничего не делается.", "ofll-hint");
        CheckBox cbEpicSkip = new CheckBox("Не спрашивать перед открытием страницы входа");
        cbEpicSkip.setSelected(g.epicSkipPrompt);
        Button btnCheckEos = button("Проверить EOS-файлы", "btn-surface");
        btnCheckEos.setOnAction(ev -> {
            List<String> found = Epic.findEosFiles(gameRoot(g));
            eosCache.put(g.id, found);
            if (found.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "EOS-файлы", "В папке игры файлы Epic Online Services не найдены.");
            } else {
                StringBuilder sb = new StringBuilder("Найдено:\n");
                for (int i = 0; i < Math.min(found.size(), 8); i++) sb.append(" • ").append(found.get(i)).append('\n');
                if (found.size() > 8) sb.append(" … и ещё ").append(found.size() - 8);
                showAlert(Alert.AlertType.INFORMATION, "EOS-файлы", sb.toString());
            }
        });
        Button btnEpicWeb = button("Открыть вход в Epic", "btn-blue");
        btnEpicWeb.setOnAction(ev -> openUrl(Epic.LOGIN_URL));
        HBox epicButtons = new HBox(10, btnCheckEos, btnEpicWeb);

        CheckBox cbMsOn = new CheckBox("Включить вход через аккаунт Microsoft (Xbox)");
        cbMsOn.setSelected(g.msEnabled);
        Label msHint = styled("Если включено, лаунчер ищет в папке игры файлы Xbox Live / Microsoft Store (GDK, XSAPI). "
            + "Когда они найдены, перед запуском он предлагает открыть страницу входа Microsoft в браузере, а ссылки входа "
            + "и кода активации Microsoft, которые игра выводит в лог, открываются в браузере автоматически. "
            + "Wine и Proton не содержат служб Xbox, поэтому игра может не войти в аккаунт, даже если страница открылась. "
            + "Если выключено — с Microsoft ничего не делается.", "ofll-hint");
        CheckBox cbMsSkip = new CheckBox("Не спрашивать перед открытием страницы входа");
        cbMsSkip.setSelected(g.msSkipPrompt);
        Button btnCheckMs = button("Проверить файлы Xbox", "btn-surface");
        btnCheckMs.setOnAction(ev -> {
            List<String> found = Microsoft.findFiles(gameRoot(g));
            msCache.put(g.id, found);
            if (found.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "Файлы Xbox", "В папке игры файлы Xbox Live / Microsoft Store не найдены.");
            } else {
                StringBuilder sb = new StringBuilder("Найдено:\n");
                for (int i = 0; i < Math.min(found.size(), 8); i++) sb.append(" • ").append(found.get(i)).append('\n');
                if (found.size() > 8) sb.append(" … и ещё ").append(found.size() - 8);
                showAlert(Alert.AlertType.INFORMATION, "Файлы Xbox", sb.toString());
            }
        });
        Button btnMsWeb = button("Открыть вход в Microsoft", "btn-blue");
        btnMsWeb.setOnAction(ev -> openUrl(Microsoft.LOGIN_URL));
        HBox msButtons = new HBox(10, btnCheckMs, btnMsWeb);

        CheckBox cbGogOn = new CheckBox("Включить поддержку GOG Galaxy");
        cbGogOn.setSelected(g.gogEnabled);
        Label gogHint = styled("Если включено, лаунчер ищет в папке игры файлы GOG Galaxy SDK (Galaxy64.dll и др.). "
            + "Когда они найдены, перед запуском он предлагает открыть сайт GOG в браузере, а ссылки входа GOG, "
            + "которые игра выводит в лог, открываются в браузере автоматически. "
            + "Официального клиента GOG Galaxy для Linux нет, поэтому онлайн-функции Galaxy могут не работать, "
            + "даже если страница открылась. Если выключено — с GOG ничего не делается.", "ofll-hint");
        CheckBox cbGogSkip = new CheckBox("Не спрашивать перед открытием сайта GOG");
        cbGogSkip.setSelected(g.gogSkipPrompt);
        Button btnCheckGog = button("Проверить файлы GOG", "btn-surface");
        btnCheckGog.setOnAction(ev -> {
            List<String> found = Gog.findFiles(gameRoot(g));
            gogCache.put(g.id, found);
            if (found.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "Файлы GOG", "В папке игры файлы GOG Galaxy SDK не найдены.");
            } else {
                StringBuilder sb = new StringBuilder("Найдено:\n");
                for (int i = 0; i < Math.min(found.size(), 8); i++) sb.append(" • ").append(found.get(i)).append('\n');
                if (found.size() > 8) sb.append(" … и ещё ").append(found.size() - 8);
                showAlert(Alert.AlertType.INFORMATION, "Файлы GOG", sb.toString());
            }
        });
        Button btnGogWeb = button("Открыть GOG", "btn-blue");
        btnGogWeb.setOnAction(ev -> openUrl(Gog.LOGIN_URL));
        HBox gogButtons = new HBox(10, btnCheckGog, btnGogWeb);

        CheckBox cbAppId = new CheckBox("Передавать SteamAppId");
        cbAppId.setSelected(g.sendSteamAppId);
        TextField appIdFld = new TextField(g.steamAppId);
        appIdFld.setPrefColumnCount(10);
        appIdFld.disableProperty().bind(cbAppId.selectedProperty().not());
        HBox appIdRow = new HBox(10, cbAppId, appIdFld);
        appIdRow.setAlignment(Pos.CENTER_LEFT);

        CheckBox cbNet = new CheckBox("Разрешить доступ к сети (онлайн-режим)");
        cbNet.setSelected(g.networkAccess);
        if (Sys.which("firejail") == null) {
            if (g.networkAccess) cbNet.setDisable(true);
            Tooltip.install(cbNet, new Tooltip("Отключение сети работает через firejail — он не установлен."));
        }

        ListView<String> dllList = new ListView<>(FXCollections.observableArrayList(g.dllOverrides));
        dllList.setPrefHeight(90);
        TextField newDll = new TextField();
        newDll.setPromptText("например: winhttp=n,b");
        HBox.setHgrow(newDll, Priority.ALWAYS);
        Button btnAddDll = button("Добавить", "btn-surface");
        btnAddDll.setOnAction(ev -> {
            String t = newDll.getText().trim();
            if (!t.isEmpty() && !dllList.getItems().contains(t)) dllList.getItems().add(t);
            newDll.clear();
        });
        Button btnRmDll = button("Удалить", "btn-surface");
        btnRmDll.setOnAction(ev -> {
            String sel = dllList.getSelectionModel().getSelectedItem();
            if (sel != null) dllList.getItems().remove(sel);
        });
        Button btnDetectDll = button("Определить", "btn-surface");
        Tooltip.install(btnDetectDll, new Tooltip("Найти прокси-библиотеки (winhttp, version, winmm, dinput8) рядом с exe"));
        btnDetectDll.setOnAction(ev -> {
            List<String> found = Runner.detectProxyDlls(Paths.get(exeFld.getText()).getParent());
            int added = 0;
            for (String d : found) if (!dllList.getItems().contains(d)) { dllList.getItems().add(d); added++; }
            showAlert(Alert.AlertType.INFORMATION, "Библиотеки",
                found.isEmpty() ? "Рядом с exe не найдено прокси-библиотек." : "Добавлено записей: " + added);
        });
        HBox dllControls = new HBox(8, newDll, btnAddDll, btnRmDll, btnDetectDll);

        VBox steamEpic = new VBox(10,
            styled("Epic Games (EOS)", "ofll-section"), cbEos, epicHint, cbEpicSkip, epicButtons,
            new Separator(),
            styled("Microsoft / Xbox", "ofll-section"), cbMsOn, msHint, cbMsSkip, msButtons,
            new Separator(),
            styled("GOG Galaxy", "ofll-section"), cbGogOn, gogHint, cbGogSkip, gogButtons,
            new Separator(),
            styled("Steam и сеть", "ofll-section"), appIdRow, cbNet,
            new Separator(),
            styled("Переопределения библиотек (WINEDLLOVERRIDES)", "ofll-section"), dllList, dllControls);
        steamEpic.setPadding(new Insets(16));
        ScrollPane steamScroll = new ScrollPane(steamEpic);
        steamScroll.setFitToWidth(true);
        Tab tabSteam = tab("Epic, Microsoft, GOG и Steam", steamScroll);

        // ---- Пути ----
        TextField prefixFld = new TextField(g.winePrefix);
        prefixFld.setPromptText("По умолчанию: " + Paths.get(config.prefixesPath, g.id));
        TextField protonPathFld = new TextField(config.protonPath);
        TextField prefixesPathFld = new TextField(config.prefixesPath);
        GridPane paths = grid();
        paths.add(new Label("Префикс этой игры"), 0, 0);
        paths.add(prefixFld, 1, 0);
        paths.add(browseButton(prefixFld, stage), 2, 0);
        paths.add(new Label("Папка Proton"), 0, 1);
        paths.add(protonPathFld, 1, 1);
        paths.add(browseButton(protonPathFld, stage), 2, 1);
        paths.add(new Label("Папка префиксов"), 0, 2);
        paths.add(prefixesPathFld, 1, 2);
        paths.add(browseButton(prefixesPathFld, stage), 2, 2);
        Tab tabPaths = tab("Пути", paths);

        // ---- Производительность ----
        CheckBox cbMango = new CheckBox("MangoHud — показывать FPS и нагрузку поверх игры");
        cbMango.setSelected(g.useMangoHud);
        Label mangoSt = styled(Sys.which("mangohud") != null ? "MangoHud установлен." : "MangoHud не найден — установите пакет mangohud, иначе игра запустится без него.", "ofll-hint");
        CheckBox cbGm = new CheckBox("GameMode — режим производительности на время игры");
        cbGm.setSelected(g.useGameMode);
        Label gmSt = styled(Sys.which("gamemoderun") != null ? "GameMode установлен." : "GameMode не найден — установите пакет gamemode, иначе игра запустится без него.", "ofll-hint");
        CheckBox cbNoEsync = new CheckBox("Отключить ESYNC");
        cbNoEsync.setSelected(g.noEsync);
        CheckBox cbNoFsync = new CheckBox("Отключить FSYNC");
        cbNoFsync.setSelected(g.noFsync);
        Label syncHint = styled("Esync и Fsync ускоряют синхронизацию потоков. Если игра вылетает, зависает или пишет об ошибках с лимитом открытых файлов, попробуйте их отключить.", "ofll-hint");
        CheckBox cbProtonLog = new CheckBox("Писать подробный лог Proton (только для Proton)");
        cbProtonLog.setSelected(g.protonLog);
        Label protonLogHint = styled("Файл steam-<номер>.log появится в домашней папке. Он нужен, если игра не запускается: его просят приложить к отчётам об ошибках.", "ofll-hint");
        VBox perf = new VBox(10, styled("Оверлей и режим производительности", "ofll-section"), cbMango, mangoSt, cbGm, gmSt,
            new Separator(), styled("Синхронизация", "ofll-section"), cbNoEsync, cbNoFsync, syncHint,
            new Separator(), styled("Диагностика", "ofll-section"), cbProtonLog, protonLogHint);
        perf.setPadding(new Insets(16));
        Tab tabPerf = tab("Производительность", perf);

        // ---- Данные и обложка ----
        TextField steamIdFld = new TextField(g.steamStoreId);
        steamIdFld.setPromptText("например: 892970");
        Label dataStatus = styled("", "ofll-hint");
        Button btnFindSteam = button("Найти в Steam по названию", "btn-blue");
        btnFindSteam.setOnAction(ev -> {
            String title = Web.cleanTitle(nameFld.getText());
            dataStatus.setText("Ищу «" + title + "» в Steam…");
            btnFindSteam.setDisable(true);
            CompletableFuture.supplyAsync(() -> {
                try { return Web.steamSearch(title); } catch (IOException e) { throw new CompletionException(e); }
            }).whenComplete((f, err) -> Platform.runLater(() -> {
                btnFindSteam.setDisable(false);
                if (err != null) dataStatus.setText(rootMessage(err));
                else if (f == null) dataStatus.setText("Совпадения не найдено. Введите AppID вручную: он есть в ссылке на страницу игры в Steam (…/app/НОМЕР/…).");
                else {
                    steamIdFld.setText(Long.toString(f.id));
                    dataStatus.setText("Найдено: " + f.name + " (AppID " + f.id + "). Нажмите «Сохранить», чтобы запомнить.");
                }
            }));
        });
        HBox steamRow = new HBox(10, steamIdFld, btnFindSteam);
        HBox.setHgrow(steamIdFld, Priority.ALWAYS);

        Button btnGetCover = button("Скачать обложку", "btn-blue");
        btnGetCover.setOnAction(ev -> {
            String steamText = steamIdFld.getText().trim();
            if (!steamText.isEmpty() && Web.parseId(steamText) == 0) {
                dataStatus.setText("Steam AppID должен состоять только из цифр.");
                return;
            }
            String title = nameFld.getText().trim(), key = config.sgdbKey, sgdbIn = g.sgdbId;
            dataStatus.setText("Ищу обложку…");
            btnGetCover.setDisable(true);
            CompletableFuture.supplyAsync(() -> Covers.fetch(title, steamText, sgdbIn, key, true, g.id, Store.coversDir()))
                .whenComplete((res, err) -> Platform.runLater(() -> {
                    btnGetCover.setDisable(false);
                    if (err != null) { dataStatus.setText(rootMessage(err)); return; }
                    applyCoverResult(g, res);
                    if (res.steamId != 0) steamIdFld.setText(Long.toString(res.steamId));
                    if (res.cover != null) dataStatus.setText("Обложка найдена (" + res.source + ").");
                    else if (res.warning != null) dataStatus.setText("Обложка не найдена: " + res.warning);
                    else dataStatus.setText("Обложка не найдена. Проверьте Steam AppID или добавьте ключ SteamGridDB в настройках лаунчера.");
                }));
        });
        Button btnPickCover = button("Выбрать файл…", "btn-surface");
        btnPickCover.setOnAction(ev -> chooseCover(g));
        Button btnClearCover = button("Убрать обложку", "btn-surface");
        btnClearCover.setOnAction(ev -> clearCover(g));

        Label protonSt = styled("", "ofll-hint");
        Button btnProtonCheck = button("Проверить рейтинг ProtonDB", "btn-surface");
        btnProtonCheck.setOnAction(ev -> {
            long id = Web.parseId(steamIdFld.getText());
            if (id == 0) {
                protonSt.setText("Сначала укажите Steam AppID.");
                return;
            }
            protonSt.setText("Запрашиваю…");
            btnProtonCheck.setDisable(true);
            CompletableFuture.supplyAsync(() -> {
                try {
                    Web.Rating r = Web.protonRating(id);
                    if (r == null) return "На ProtonDB пока нет данных об этой игре.";
                    return "Рейтинг: " + Web.tierRu(r.tier) + (r.total > 0 ? ", отчётов: " + r.total : "") + ".";
                } catch (IOException e) {
                    return e.getMessage();
                }
            }).thenAccept(msg -> Platform.runLater(() -> {
                protonSt.setText(msg);
                btnProtonCheck.setDisable(false);
            }));
        });
        String coverHint = Web.validKey(config.sgdbKey)
            ? "Обложки берутся с SteamGridDB, а если там нет — из Steam."
            : "Ключ SteamGridDB не задан (значок ⚙ в верхней панели), поэтому обложки берутся из Steam.";
        VBox data = new VBox(10,
            styled("Steam AppID", "ofll-section"), steamRow,
            styled("Он нужен для кнопок-миниатюр (Steam, SteamDB, ProtonDB, SGDB), обложки и рейтинга. На запуск игры он не влияет: для запуска есть отдельная настройка «Передавать SteamAppId» на вкладке «Epic, Microsoft, GOG и Steam».", "ofll-hint"),
            dataStatus, new Separator(),
            styled("Обложка", "ofll-section"), new HBox(10, btnGetCover, btnPickCover, btnClearCover), styled(coverHint, "ofll-hint"),
            new Separator(),
            styled("Рейтинг ProtonDB", "ofll-section"), btnProtonCheck, protonSt);
        data.setPadding(new Insets(16));
        Tab tabData = tab("Данные и обложка", data);

        tabs.getTabs().addAll(tabBasic, tabGfx, tabProton, tabPerf, tabData, tabSteam, tabPaths);

        Button btnSave = button("Сохранить", "btn-green");
        Button btnCancel = button("Отмена", "btn-surface");
        btnCancel.setOnAction(ev -> stage.close());
        btnSave.setOnAction(ev -> {
            String name = nameFld.getText().trim();
            if (name.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Настройки", "Название не может быть пустым.");
                return;
            }
            String appId = appIdFld.getText().trim();
            if (cbAppId.isSelected() && !appId.matches("\\d{1,10}")) {
                showAlert(Alert.AlertType.WARNING, "Настройки", "SteamAppId должен состоять только из цифр.");
                return;
            }
            String steamText = steamIdFld.getText().trim();
            if (!steamText.isEmpty() && Web.parseId(steamText) == 0) {
                showAlert(Alert.AlertType.WARNING, "Настройки", "Steam AppID (вкладка «Данные и обложка») должен состоять только из цифр.");
                return;
            }
            String newExe = exeFld.getText().trim();
            if (!newExe.equals(g.executablePath)) g.rootPath = "";
            if (!name.equals(g.name)) g.tsCommunity = "";
            g.name = name;
            g.executablePath = newExe;
            g.customArgs = argsFld.getText().trim();
            g.envVars = envFld.getText().trim();
            g.useWine3D = rbGl.isSelected();
            g.useWayland = cbWayland.isSelected();
            g.protonVersion = Proton.normalize(cbRunner.getValue());
            g.eosEnabled = cbEos.isSelected();
            g.eosChecked = true;
            g.epicSkipPrompt = cbEpicSkip.isSelected();
            g.msEnabled = cbMsOn.isSelected();
            g.msChecked = true;
            g.msSkipPrompt = cbMsSkip.isSelected();
            g.gogEnabled = cbGogOn.isSelected();
            g.gogChecked = true;
            g.gogSkipPrompt = cbGogSkip.isSelected();
            g.sendSteamAppId = cbAppId.isSelected();
            if (!appId.isEmpty()) g.steamAppId = appId;
            g.networkAccess = cbNet.isSelected();
            g.dllOverrides = new ArrayList<>(dllList.getItems());
            g.winePrefix = prefixFld.getText().trim();
            g.favorite = cbFav.isSelected();
            g.useMangoHud = cbMango.isSelected();
            g.useGameMode = cbGm.isSelected();
            g.noEsync = cbNoEsync.isSelected();
            g.noFsync = cbNoFsync.isSelected();
            g.protonLog = cbProtonLog.isSelected();
            if (!steamText.equals(g.steamStoreId)) {
                g.steamStoreId = steamText;
                ratings.remove(g.id);
                ratingRequested.remove(g.id);
            }

            config.defaultWined3d = cbDefGl.isSelected();
            config.defaultWayland = cbDefWayland.isSelected();
            config.defaultProton = Proton.normalize(cbDefRunner.getValue());
            if (!protonPathFld.getText().isBlank()) config.protonPath = protonPathFld.getText().trim();
            if (!prefixesPathFld.getText().isBlank()) config.prefixesPath = prefixesPathFld.getText().trim();

            Store.saveGames(games);
            Store.saveConfig(config);
            eosCache.remove(g.id);
            msCache.remove(g.id);
            gogCache.remove(g.id);
            refreshGrid();
            updatePanel();
            stage.close();
        });
        HBox buttons = new HBox(10, btnCancel, btnSave);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox layout = new VBox(10, tabs, buttons);
        layout.setPadding(new Insets(10));
        VBox.setVgrow(tabs, Priority.ALWAYS);
        Scene s = new Scene(layout, 760, 620);
        if (!themeUrl.isEmpty()) s.getStylesheets().add(themeUrl);
        stage.setScene(s);
        showAnimated(stage);
    }

    // ---------------------------------------------------------------
    //  Настройки лаунчера (общие для всех игр)
    // ---------------------------------------------------------------

    private void openLauncherSettings() {
        Stage stage = new Stage();
        stage.initOwner(primaryStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Настройки лаунчера");
        Brand.apply(stage);

        // --- обложки и данные ---
        PasswordField keyFld = new PasswordField();
        keyFld.setText(config.sgdbKey);
        keyFld.setPromptText("API-ключ SteamGridDB");
        Label keyStatus = styled("Ключ бесплатный: он создаётся в профиле на steamgriddb.com (раздел API). Без ключа обложки берутся из Steam.", "ofll-hint");
        Button btnGetKey = button("Получить ключ", "btn-surface");
        btnGetKey.setOnAction(ev -> openUrl("https://www.steamgriddb.com/profile/preferences/api"));
        Button btnCheckKey = button("Проверить ключ", "btn-surface");
        btnCheckKey.setOnAction(ev -> {
            String k = keyFld.getText().trim();
            if (!Web.validKey(k)) {
                keyStatus.setText("Ключ выглядит неверно: он состоит из латинских букв, цифр, «-» и «_».");
                return;
            }
            keyStatus.setText("Проверяю ключ…");
            btnCheckKey.setDisable(true);
            CompletableFuture.supplyAsync(() -> {
                try {
                    Web.sgdbSearchId(k, "Portal");
                    return "Ключ принят.";
                } catch (IOException e) {
                    return e.getMessage();
                }
            }).thenAccept(msg -> Platform.runLater(() -> {
                keyStatus.setText(msg);
                btnCheckKey.setDisable(false);
            }));
        });
        HBox keyRow = new HBox(10, keyFld, btnGetKey, btnCheckKey);
        HBox.setHgrow(keyFld, Priority.ALWAYS);
        CheckBox cbAuto = new CheckBox("Сама искать Steam AppID и обложку для новых игр");
        cbAuto.setSelected(config.autoCovers);
        CheckBox cbRating = new CheckBox("Показывать рейтинг ProtonDB");
        cbRating.setSelected(config.showProtonRating);
        Label privacy = styled("Для поиска на серверы Steam, ProtonDB и SteamGridDB отправляется только название игры — пути к файлам не передаются. Ключ хранится в файле настроек, доступном только вам.", "ofll-hint");
        Button btnAll = button("Найти обложки для всех игр без обложки", "btn-blue");
        btnAll.setOnAction(ev -> fetchMissingCovers(keyFld.getText().trim()));
        Button btnAllWide = button("Обновить обложки всех игр (широкий формат)", "btn-blue");
        btnAllWide.setOnAction(ev -> fetchCovers(keyFld.getText().trim(), true));

        // --- внешний вид ---
        ComboBox<String> cbSize = new ComboBox<>(FXCollections.observableArrayList(sizeLabels()));
        cbSize.setValue(sizeLabels().get(Math.max(0, GlobalConfig.CARD_SIZES.indexOf(config.cardSize))));
        ComboBox<String> cbAccent = new ComboBox<>(FXCollections.observableArrayList(accentLabels()));
        cbAccent.setValue(accentLabels().get(Math.max(0, GlobalConfig.ACCENTS.indexOf(config.accent))));
        ComboBox<String> cbSort = new ComboBox<>(FXCollections.observableArrayList(sortLabels()));
        cbSort.setValue(sortLabels().get(Math.max(0, GlobalConfig.SORT_MODES.indexOf(config.sortMode))));
        List<String> langNames = new ArrayList<>();
        for (String code : I18n.CODES) langNames.add(I18n.NAMES.get(code));
        ComboBox<String> cbLang = new ComboBox<>(FXCollections.observableArrayList(langNames));
        cbLang.setValue(I18n.NAMES.get(I18n.normalize(config.language)));
        GridPane look = new GridPane();
        look.setHgap(12);
        look.setVgap(10);
        look.add(new Label("Размер карточек"), 0, 0);
        look.add(cbSize, 1, 0);
        look.add(new Label("Цвет акцента"), 0, 1);
        look.add(cbAccent, 1, 1);
        look.add(new Label("Порядок игр"), 0, 2);
        look.add(cbSort, 1, 2);
        look.add(new Label("Язык / Language"), 0, 3);
        look.add(cbLang, 1, 3);

        // --- поведение ---
        CheckBox cbMin = new CheckBox("Сворачивать лаунчер, когда запускается игра");
        cbMin.setSelected(config.minimizeOnLaunch);
        CheckBox cbBg = new CheckBox("Размытая обложка игры на фоне, когда открыта панель игры");
        cbBg.setSelected(config.coverBackground);
        CheckBox cbAnim = new CheckBox("Анимации интерфейса (карточки, панели, окна, свечение)");
        cbAnim.setSelected(config.animations);
        CheckBox cbSplash = new CheckBox("Показывать заставку с аватаркой при запуске");
        cbSplash.setSelected(config.showSplash);
        Label animHint = styled("Анимации можно отключить на слабом компьютере. Часть из них (пульсация свечения) применится после перезапуска лаунчера.", "ofll-hint");
        Label avatarInfo = styled(Brand.status(), "ofll-hint");

        // --- служебное ---
        Button btnCfgDir = button("Открыть папку настроек", "btn-surface");
        btnCfgDir.setOnAction(ev -> {
            if (!Sys.openPath(Store.dir())) showAlert(Alert.AlertType.WARNING, "Папка настроек", "Не удалось открыть папку:\n" + Store.dir());
        });
        Button btnLogDir = button("Открыть папку с логами", "btn-surface");
        btnLogDir.setOnAction(ev -> {
            Path logs = Store.dir().resolve("logs");
            try { Files.createDirectories(logs); } catch (IOException ignored) {}
            if (!Sys.openPath(logs)) showAlert(Alert.AlertType.WARNING, "Логи", "Не удалось открыть папку:\n" + logs);
        });

        VBox content = new VBox(12,
            styled("Обложки и данные из интернета", "ofll-section"),
            new Label("Ключ SteamGridDB"), keyRow, keyStatus,
            cbAuto, cbRating, privacy, btnAll, btnAllWide,
            new Separator(),
            styled("Внешний вид", "ofll-section"), look,
            new Separator(),
            styled("Поведение", "ofll-section"), cbMin, cbBg, cbAnim, cbSplash, animHint, avatarInfo,
            new Separator(),
            styled("Служебное", "ofll-section"), new HBox(10, btnCfgDir, btnLogDir));
        content.setPadding(new Insets(18));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);

        Button btnSave = button("Сохранить", "btn-green");
        Button btnCancel = button("Отмена", "btn-surface");
        btnCancel.setOnAction(ev -> stage.close());
        btnSave.setOnAction(ev -> {
            String k = keyFld.getText().trim();
            if (!k.isEmpty() && !Web.validKey(k)) {
                showAlert(Alert.AlertType.WARNING, "Настройки лаунчера", "Ключ SteamGridDB выглядит неверно: он состоит из латинских букв, цифр, «-» и «_» (8–128 символов).");
                return;
            }
            String oldAccent = config.accent;
            config.sgdbKey = k;
            config.autoCovers = cbAuto.isSelected();
            config.showProtonRating = cbRating.isSelected();
            config.cardSize = GlobalConfig.CARD_SIZES.get(Math.max(0, sizeLabels().indexOf(cbSize.getValue())));
            config.accent = GlobalConfig.ACCENTS.get(Math.max(0, accentLabels().indexOf(cbAccent.getValue())));
            config.sortMode = GlobalConfig.SORT_MODES.get(Math.max(0, sortLabels().indexOf(cbSort.getValue())));
            config.minimizeOnLaunch = cbMin.isSelected();
            config.animations = cbAnim.isSelected();
            config.showSplash = cbSplash.isSelected();
            config.coverBackground = cbBg.isSelected();
            String newLang = I18n.CODES.get(Math.max(0, langNames.indexOf(cbLang.getValue())));
            boolean langChanged = !newLang.equals(config.language);
            config.language = newLang;
            I18n.set(newLang);
            Store.saveConfig(config);
            if (!config.accent.equals(oldAccent)) reloadTheme();
            if (langChanged) {
                stage.close();
                rebuildUi();
                return;
            }
            sortBox.setValue(sortLabels().get(GlobalConfig.SORT_MODES.indexOf(config.sortMode)));
            refreshGrid();
            updatePanel();
            stage.close();
        });
        HBox buttons = new HBox(10, btnCancel, btnSave);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox layout = new VBox(10, scroll, buttons);
        layout.setPadding(new Insets(10));
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Scene sc = new Scene(layout, 680, 700);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        stage.setScene(sc);
        showAnimated(stage);
    }

    // ---------------------------------------------------------------
    //  Мелкие помощники интерфейса
    // ---------------------------------------------------------------

    private static Button button(String text, String colorClass) {
        Button b = new Button(text);
        b.getStyleClass().addAll("ofll-btn", colorClass);
        return b;
    }

    private static Button iconButton(String glyph, String colorClass, String tip, Runnable action) {
        Button b = new Button(glyph);
        b.getStyleClass().addAll("ofll-tile", colorClass);
        Tooltip.install(b, new Tooltip(tip));
        b.setOnAction(ev -> action.run());
        return b;
    }

    private static VBox captioned(Node n, String caption) {
        Label l = new Label(caption);
        l.getStyleClass().add("ofll-tile-caption");
        VBox v = new VBox(4, n, l);
        v.setAlignment(Pos.TOP_CENTER);
        v.setMinWidth(62);
        v.setPrefWidth(62);
        return v;
    }

    private static Label pill() {
        Label l = new Label();
        l.getStyleClass().add("ofll-pill");
        return l;
    }

    private static Label sideSection(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("ofll-side-section");
        return l;
    }

    private static Label styled(String text, String cssClass) {
        Label l = new Label(text);
        l.getStyleClass().add(cssClass);
        l.setWrapText(true);
        return l;
    }

    private static Tab tab(String title, Node content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private static GridPane grid() {
        GridPane gp = new GridPane();
        gp.setHgap(10);
        gp.setVgap(12);
        gp.setPadding(new Insets(16));
        ColumnConstraints c0 = new ColumnConstraints();
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        gp.getColumnConstraints().addAll(c0, c1, c2);
        return gp;
    }

    private static Button browseButton(TextField target, Window owner) {
        Button b = button("Обзор…", "btn-surface");
        b.setOnAction(ev -> {
            DirectoryChooser dc = new DirectoryChooser();
            File cur = new File(target.getText());
            if (cur.isDirectory()) dc.setInitialDirectory(cur);
            File f = dc.showDialog(owner);
            if (f != null) target.setText(f.getAbsolutePath());
        });
        return b;
    }

    private void openUrl(String url) {
        if (!Sys.openUrl(url)) {
            try { getHostServices().showDocument(url); } catch (Exception ignored) {}
        }
    }

    private Window activeWindow() {
        for (Window w : Window.getWindows()) if (w.isShowing() && w.isFocused()) return w;
        return primaryStage;
    }

    private Alert makeAlert(Alert.AlertType type, String title, String header, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(header);
        a.setContentText(content);
        a.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if (!themeUrl.isEmpty()) a.getDialogPane().getStylesheets().add(themeUrl);
        a.initOwner(activeWindow());
        if (a.getDialogPane().getScene() != null && a.getDialogPane().getScene().getWindow() instanceof Stage as) Brand.apply(as);
        return a;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        makeAlert(type, title, null, content).showAndWait();
    }

    // ---------------------------------------------------------------
    //  Консоль отладки
    // ---------------------------------------------------------------

    static final class DebugConsole {
        private final Stage stage = new Stage();
        private final TextArea area = new TextArea();
        private final Queue<String> queue = new ConcurrentLinkedQueue<>();
        private final Timeline timer;

        DebugConsole(String gameName, String themeUrl) {
            stage.setTitle("Консоль: " + gameName);
        Brand.apply(stage);
            area.setEditable(false);
            area.setStyle("-fx-control-inner-background: #0b0b12; -fx-text-fill: #a6e3a1; -fx-font-family: 'Monospaced';");
            Button copy = button("Копировать всё", "btn-surface");
            copy.setOnAction(ev -> {
                ClipboardContent cc = new ClipboardContent();
                cc.putString(area.getText());
                Clipboard.getSystemClipboard().setContent(cc);
            });
            Button clear = button("Очистить", "btn-surface");
            clear.setOnAction(ev -> area.clear());
            HBox bar = new HBox(8, copy, clear);
            bar.setPadding(new Insets(8));
            BorderPane pane = new BorderPane(area);
            pane.setBottom(bar);
            Scene sc = new Scene(pane, 820, 540);
            if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
            stage.setScene(sc);
            timer = new Timeline(new KeyFrame(Duration.millis(150), ev -> flush()));
            timer.setCycleCount(Timeline.INDEFINITE);
            stage.setOnHidden(ev -> { flush(); timer.stop(); });
        }

        void show() {
            stage.show();
            timer.play();
        }

        /** Можно вызывать из любого потока. */
        void append(String line) {
            queue.add(line);
        }

        private void flush() {
            if (queue.isEmpty()) return;
            StringBuilder sb = new StringBuilder();
            String s;
            int n = 0;
            while (n < 2000 && (s = queue.poll()) != null) {
                sb.append(s).append('\n');
                n++;
            }
            area.appendText(sb.toString());
            if (area.getLength() > 400_000) area.deleteText(0, area.getLength() - 300_000);
        }
    }
}


// ================================================================
// Thunder.java
// ================================================================

/** Мелкие файловые помощники для менеджера модов. */
final class Fs {
    private Fs() {}

    static void deleteTree(Path p) {
        if (p == null || !Files.exists(p, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            if (Files.isSymbolicLink(p) || !Files.isDirectory(p)) {
                Files.deleteIfExists(p);
                return;
            }
            try (var s = Files.list(p)) {
                for (Path c : (Iterable<Path>) s::iterator) deleteTree(c);
            }
            Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }

    /** Копирует папку целиком. Если keepExisting — файлы, которые уже есть в назначении, не трогает. */
    static void copyTree(Path from, Path to, boolean keepExisting) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path d, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(d).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                Path dst = to.resolve(from.relativize(f).toString());
                if (keepExisting && Files.exists(dst)) return FileVisitResult.CONTINUE;
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static boolean isEmptyDir(Path d) {
        try (var s = Files.list(d)) {
            return s.findAny().isEmpty();
        } catch (IOException e) {
            return false;
        }
    }

    /** Распаковывает zip в dst. Пути с «..» и абсолютные отбрасываются (защита от zip-slip). */
    static void unzip(Path zip, Path dst) throws IOException {
        Files.createDirectories(dst);
        Path root = dst.toAbsolutePath().normalize();
        try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zip)), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName().replace('\\', '/');
                if (name.isEmpty()) continue;
                Path out = root.resolve(name).normalize();
                if (!out.startsWith(root)) continue;
                if (e.isDirectory() || name.endsWith("/")) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Сравнение версий «1.10.2» / «5.4.2200»: числа сравниваются по значению. */
    static int compareVersions(String a, String b) {
        String[] x = (a == null ? "" : a).split("[^0-9]+"), y = (b == null ? "" : b).split("[^0-9]+");
        int n = Math.max(x.length, y.length);
        for (int i = 0; i < n; i++) {
            long p = i < x.length ? num(x[i]) : 0, q = i < y.length ? num(y[i]) : 0;
            if (p != q) return Long.compare(p, q);
        }
        return 0;
    }

    private static long num(String s) {
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s.length() > 12 ? s.substring(0, 12) : s); } catch (NumberFormatException e) { return 0; }
    }
}

/** Клиент Thunderstore: сообщества, каталог модов, README и загрузка архивов. */
final class Thunder {
    static final String BASE = "https://thunderstore.io";
    private static final String UA = "OnlineFix-Linux-Launcher/1.0";
    private static final long MAX_ZIP = 600L << 20;
    private static final long INDEX_TTL_MS = 3L * 3600 * 1000;
    private static final long COMM_TTL_MS = 7L * 24 * 3600 * 1000;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL).build();

    private Thunder() {}

    static final class Community {
        String id = "", name = "";
        @Override public String toString() { return name; }
    }

    static final class Pkg {
        String owner = "", name = "", fullName = "", description = "", icon = "", version = "";
        String downloadUrl = "", pageUrl = "", updated = "";
        long downloads, size;
        int rating;
        boolean deprecated, nsfw;
        final List<String> categories = new ArrayList<>();
        final List<String> deps = new ArrayList<>();

        String key() { return owner + "-" + name; }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("owner", owner);
            m.put("name", name);
            m.put("desc", description);
            m.put("icon", icon);
            m.put("ver", version);
            m.put("dl", downloadUrl);
            m.put("page", pageUrl);
            m.put("upd", updated);
            m.put("downloads", downloads);
            m.put("size", size);
            m.put("rating", rating);
            m.put("dep", deprecated);
            m.put("nsfw", nsfw);
            m.put("cats", new ArrayList<Object>(categories));
            m.put("deps", new ArrayList<Object>(deps));
            return m;
        }

        static Pkg fromMap(Map<String, Object> m) {
            Pkg p = new Pkg();
            p.owner = Json.str(m, "owner", "");
            p.name = Json.str(m, "name", "");
            p.fullName = p.owner + "-" + p.name;
            p.description = Json.str(m, "desc", "");
            p.icon = Json.str(m, "icon", "");
            p.version = Json.str(m, "ver", "");
            p.downloadUrl = Json.str(m, "dl", "");
            p.pageUrl = Json.str(m, "page", "");
            p.updated = Json.str(m, "upd", "");
            p.downloads = Json.lng(m, "downloads", 0);
            p.size = Json.lng(m, "size", 0);
            p.rating = (int) Json.lng(m, "rating", 0);
            p.deprecated = Json.bool(m, "dep", false);
            p.nsfw = Json.bool(m, "nsfw", false);
            for (Object o : Json.asList(m.get("cats"))) if (o instanceof String s) p.categories.add(s);
            for (Object o : Json.asList(m.get("deps"))) if (o instanceof String s) p.deps.add(s);
            return p;
        }
    }

    private static Path cacheDir() {
        Path d = Store.dir().resolve("thunderstore");
        try { Files.createDirectories(d); } catch (IOException ignored) {}
        return d;
    }

    private static boolean fresh(Path f, long ttl) {
        try {
            return Files.isRegularFile(f) && System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() < ttl;
        } catch (IOException e) {
            return false;
        }
    }

    static boolean trustedUrl(String url) {
        try {
            URI u = new URI(url);
            String h = u.getHost();
            return "https".equalsIgnoreCase(u.getScheme()) && u.getUserInfo() == null && h != null
                && (h.equals("thunderstore.io") || h.endsWith(".thunderstore.io"));
        } catch (Exception e) {
            return false;
        }
    }

    private static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(java.time.Duration.ofSeconds(60)).header("User-Agent", UA);
    }

    private static String getText(String url) throws IOException {
        try {
            HttpResponse<InputStream> r = HTTP.send(req(url).header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = r.body()) {
                if (r.statusCode() != 200) throw new IOException("Thunderstore: HTTP " + r.statusCode());
                byte[] data = in.readNBytes(8 << 20);
                return new String(data, StandardCharsets.UTF_8);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    // ------------------------------------------------------------------ сообщества

    static List<Community> communities() throws IOException {
        Path cache = cacheDir().resolve("communities.json");
        if (fresh(cache, COMM_TTL_MS)) {
            try {
                return readCommunities(Files.readString(cache));
            } catch (RuntimeException | IOException ignored) {}
        }
        List<Community> all = new ArrayList<>();
        try {
            String url = BASE + "/api/experimental/community/";
            for (int page = 0; page < 30 && url != null; page++) {
                Map<String, Object> m = Json.asMap(Json.parse(getText(url)));
                for (Object o : Json.asList(m.get("results"))) {
                    Map<String, Object> c = Json.asMap(o);
                    Community cm = new Community();
                    cm.id = Json.str(c, "identifier", "");
                    cm.name = Json.str(c, "name", cm.id);
                    if (!cm.id.isEmpty()) all.add(cm);
                }
                String next = Json.str(Json.asMap(m.get("pagination")), "next_link", "");
                url = next.isEmpty() || !trustedUrl(next) ? null : next;
            }
        } catch (IOException | RuntimeException e) {
            if (Files.isRegularFile(cache)) {
                try { return readCommunities(Files.readString(cache)); } catch (RuntimeException | IOException ignored) {}
            }
            throw e instanceof IOException io ? io : new IOException(e.getMessage());
        }
        List<Object> ser = new ArrayList<>();
        for (Community c : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("name", c.name);
            ser.add(m);
        }
        try { Files.writeString(cache, Json.write(ser), StandardCharsets.UTF_8); } catch (IOException ignored) {}
        return all;
    }

    private static List<Community> readCommunities(String text) {
        List<Community> res = new ArrayList<>();
        for (Object o : Json.asList(Json.parse(text))) {
            Map<String, Object> m = Json.asMap(o);
            Community c = new Community();
            c.id = Json.str(m, "id", "");
            c.name = Json.str(m, "name", c.id);
            if (!c.id.isEmpty()) res.add(c);
        }
        return res;
    }

    /** Находит сообщество Thunderstore по названию игры; null — такой игры на Thunderstore нет. */
    static Community match(String gameName) throws IOException {
        String want = Web.norm(Web.cleanTitle(gameName));
        if (want.length() < 3) return null;
        Community best = null;
        int bestLen = 0;
        for (Community c : communities()) {
            String n = Web.norm(c.name), id = Web.norm(c.id);
            if (n.equals(want) || id.equals(want)) return c;
            for (String cand : new String[] {n, id}) {
                if (cand.length() >= 6 && want.contains(cand) && cand.length() > bestLen) {
                    best = c;
                    bestLen = cand.length();
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ каталог модов

    /**
     * Каталог модов сообщества. Список Thunderstore очень большой (десятки МБ), поэтому он читается потоком,
     * из каждого мода берутся только нужные поля, а результат кэшируется на диске.
     * progress: 0..1, либо -1 если размер неизвестен.
     */
    static List<Pkg> packages(String community, boolean force, DoubleConsumer progress, BooleanSupplier cancelled) throws IOException {
        if (!community.matches("[A-Za-z0-9_.\\-]{1,80}")) throw new IOException("Bad community id");
        Path cache = cacheDir().resolve(community + ".index.json");
        if (!force && fresh(cache, INDEX_TTL_MS)) {
            try {
                return readIndex(Files.readString(cache));
            } catch (RuntimeException | IOException ignored) {}
        }
        List<Pkg> res = new ArrayList<>();
        try {
            HttpResponse<InputStream> r = HTTP.send(
                req(BASE + "/c/" + community + "/api/v1/package/").timeout(java.time.Duration.ofMinutes(10))
                    .header("Accept", "application/json").header("Accept-Encoding", "gzip").GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IOException("Thunderstore: HTTP " + r.statusCode());
            long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
            boolean gz = r.headers().firstValue("Content-Encoding").orElse("").toLowerCase(Locale.ROOT).contains("gzip");
            long[] raw = {0};
            InputStream counting = new FilterInputStream(r.body()) {
                @Override public int read() throws IOException {
                    int b = super.read();
                    if (b >= 0) raw[0]++;
                    return b;
                }

                @Override public int read(byte[] buf, int off, int len) throws IOException {
                    int n = super.read(buf, off, len);
                    if (n > 0) raw[0] += n;
                    return n;
                }
            };
            try (InputStream in = gz ? new GZIPInputStream(counting, 1 << 16) : counting;
                 Reader rd = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                long[] last = {0};
                streamObjects(rd, obj -> {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("cancelled");
                    try {
                        Pkg p = parsePackage(Json.asMap(Json.parse(obj)));
                        if (p != null) res.add(p);
                    } catch (RuntimeException ignored) {}
                    long now = System.currentTimeMillis();
                    if (now - last[0] > 120) {
                        last[0] = now;
                        progress.accept(total > 0 && !gz ? Math.min(0.99, (double) raw[0] / total)
                            : total > 0 ? Math.min(0.99, (double) raw[0] / total) : -1);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
        List<Object> ser = new ArrayList<>();
        for (Pkg p : res) ser.add(p.toMap());
        try { Files.writeString(cache, Json.write(ser), StandardCharsets.UTF_8); } catch (IOException ignored) {}
        progress.accept(1.0);
        return res;
    }

    private static List<Pkg> readIndex(String text) {
        List<Pkg> res = new ArrayList<>();
        for (Object o : Json.asList(Json.parse(text))) res.add(Pkg.fromMap(Json.asMap(o)));
        return res;
    }

    /** Читает верхний JSON-массив и по одному отдаёт текст каждого объекта. */
    private static void streamObjects(Reader r, Consumer<String> onObject) throws IOException {
        char[] buf = new char[1 << 16];
        StringBuilder sb = new StringBuilder(8192);
        boolean started = false, inStr = false, esc = false;
        int depth = 0, n;
        while ((n = r.read(buf)) > 0) {
            for (int i = 0; i < n; i++) {
                char c = buf[i];
                if (!started) {
                    if (c == '[') started = true;
                    continue;
                }
                if (depth == 0) {
                    if (c == '{') {
                        depth = 1;
                        sb.setLength(0);
                        sb.append('{');
                    } else if (c == ']') {
                        return;
                    }
                    continue;
                }
                sb.append(c);
                if (inStr) {
                    if (esc) esc = false;
                    else if (c == '\\') esc = true;
                    else if (c == '"') inStr = false;
                } else if (c == '"') {
                    inStr = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}' && --depth == 0) {
                    onObject.accept(sb.toString());
                }
            }
        }
    }

    private static Pkg parsePackage(Map<String, Object> m) {
        List<Object> versions = Json.asList(m.get("versions"));
        if (versions.isEmpty()) return null;
        Pkg p = new Pkg();
        p.owner = Json.str(m, "owner", "");
        p.name = Json.str(m, "name", "");
        if (p.owner.isEmpty() || p.name.isEmpty()) return null;
        p.fullName = Json.str(m, "full_name", p.owner + "-" + p.name);
        p.pageUrl = Json.str(m, "package_url", "");
        p.updated = Json.str(m, "date_updated", "");
        p.rating = (int) Json.lng(m, "rating_score", 0);
        p.deprecated = Json.bool(m, "is_deprecated", false);
        p.nsfw = Json.bool(m, "has_nsfw_content", false);
        for (Object o : Json.asList(m.get("categories"))) if (o instanceof String s) p.categories.add(s);
        long dl = 0;
        for (Object o : versions) dl += Json.lng(Json.asMap(o), "downloads", 0);
        p.downloads = dl;
        Map<String, Object> v = Json.asMap(versions.get(0));
        p.description = Json.str(v, "description", "");
        p.icon = Json.str(v, "icon", "");
        p.version = Json.str(v, "version_number", "");
        p.downloadUrl = Json.str(v, "download_url", "");
        p.size = Json.lng(v, "file_size", 0);
        for (Object o : Json.asList(v.get("dependencies"))) if (o instanceof String s) p.deps.add(s);
        return p;
    }

    /** Пакет BepInEx для игры: самый скачиваемый пакет с именем вида «BepInExPack…». */
    static Pkg findBepInEx(Collection<Pkg> all) {
        Pkg best = null;
        for (Pkg p : all) {
            String n = p.name.toLowerCase(Locale.ROOT);
            if (!(n.startsWith("bepinexpack") || n.equals("bepinex_pack"))) continue;
            if (p.deprecated) continue;
            if (best == null || p.downloads > best.downloads) best = p;
        }
        return best;
    }

    /** README мода (Markdown). Если не удалось получить — возвращает краткое описание. */
    static String readme(Pkg p) {
        try {
            String url = BASE + "/api/experimental/package/" + Web.enc(p.owner) + "/" + Web.enc(p.name) + "/"
                + Web.enc(p.version) + "/readme/";
            Map<String, Object> m = Json.asMap(Json.parse(getText(url)));
            for (String k : new String[] {"markdown", "readme", "content"}) {
                String s = Json.str(m, k, "");
                if (!s.isBlank()) return s;
            }
        } catch (IOException | RuntimeException ignored) {}
        return p.description;
    }

    static String downloadUrl(String owner, String name, String version) {
        return BASE + "/package/download/" + Web.enc(owner) + "/" + Web.enc(name) + "/" + Web.enc(version) + "/";
    }

    /** Скачивает архив в dest (через .part). progress: 0..1 или -1. */
    static void download(String url, Path dest, DoubleConsumer progress, BooleanSupplier cancelled) throws IOException {
        if (!trustedUrl(url)) throw new IOException("Untrusted URL");
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        Files.createDirectories(dest.getParent());
        try {
            HttpResponse<InputStream> r = HTTP.send(req(url).header("Accept", "*/*").timeout(java.time.Duration.ofMinutes(10)).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
            long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
            try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException("cancelled");
                    done += n;
                    if (done > MAX_ZIP) throw new IOException("File too large");
                    out.write(buf, 0, n);
                    progress.accept(total > 0 ? Math.min(0.99, (double) done / total) : -1);
                }
            }
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }
}

/** Профили (коллекции) модов игры и установка модов/BepInEx. Всё лежит в папке настроек лаунчера. */
final class ModStore {
    static final String[] SUBDIRS = {"plugins", "config", "patchers", "monomod"};

    private ModStore() {}

    static final class Profile {
        String id = "", name = "";
        @Override public String toString() { return name; }
    }

    static final class Installed {
        String key = "", owner = "", name = "", version = "", icon = "";
        boolean auto;
        final List<String> deps = new ArrayList<>();
    }

    // ------------------------------------------------------------------ пути

    static Path base(Game g) {
        return Store.dir().resolve("mods").resolve(g.id);
    }

    static Path profileDir(Game g, String id) {
        return base(g).resolve(id);
    }

    static String activeId(Game g) {
        return g.modProfile == null || g.modProfile.isBlank() ? "default" : g.modProfile;
    }

    static boolean bepInExInstalled(Path exeDir) {
        return exeDir != null && Files.isDirectory(exeDir.resolve("BepInEx"))
            && (Files.isDirectory(exeDir.resolve("BepInEx/core")) || Files.isRegularFile(exeDir.resolve("winhttp.dll")));
    }

    static String bepInExVersion(Path exeDir) {
        try {
            Path m = exeDir.resolve(".ofll-bepinex");
            return Files.isRegularFile(m) ? Files.readString(m).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ профили

    private static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "profile" : (s.length() > 40 ? s.substring(0, 40) : s);
    }

    static List<Profile> profiles(Game g) {
        List<Profile> res = new ArrayList<>();
        Path b = base(g);
        try {
            Files.createDirectories(b);
            if (!Files.isRegularFile(b.resolve("default/profile.json"))) writeProfile(g, "default", I18n.tr("Основная"), new LinkedHashMap<>());
            try (var s = Files.list(b)) {
                for (Path d : (Iterable<Path>) s::iterator) {
                    Path pj = d.resolve("profile.json");
                    if (!Files.isRegularFile(pj)) continue;
                    Profile p = new Profile();
                    p.id = d.getFileName().toString();
                    try {
                        p.name = Json.str(Json.asMap(Json.parse(Files.readString(pj))), "name", p.id);
                    } catch (RuntimeException e) {
                        p.name = p.id;
                    }
                    res.add(p);
                }
            }
        } catch (IOException ignored) {}
        res.sort(Comparator.comparing((Profile p) -> !p.id.equals("default")).thenComparing(p -> p.name.toLowerCase(Locale.ROOT)));
        return res;
    }

    static Profile create(Game g, String name) throws IOException {
        String id = slug(name);
        Path b = base(g);
        int n = 2;
        String cand = id;
        while (Files.exists(b.resolve(cand))) cand = id + "-" + n++;
        writeProfile(g, cand, name, new LinkedHashMap<>());
        Profile p = new Profile();
        p.id = cand;
        p.name = name;
        return p;
    }

    static void rename(Game g, String id, String newName) throws IOException {
        Map<String, Installed> inst = installed(g, id);
        writeProfile(g, id, newName, inst);
    }

    static Profile duplicate(Game g, String id, String newName) throws IOException {
        Profile p = create(g, newName);
        Fs.copyTree(profileDir(g, id), profileDir(g, p.id), false);
        writeProfile(g, p.id, newName, installed(g, id));
        return p;
    }

    static void delete(Game g, String id) {
        Fs.deleteTree(profileDir(g, id));
    }

    static Map<String, Installed> installed(Game g, String id) {
        Map<String, Installed> res = new LinkedHashMap<>();
        Path pj = profileDir(g, id).resolve("profile.json");
        if (!Files.isRegularFile(pj)) return res;
        try {
            Map<String, Object> m = Json.asMap(Json.parse(Files.readString(pj)));
            for (Object o : Json.asList(m.get("mods"))) {
                Map<String, Object> e = Json.asMap(o);
                Installed i = new Installed();
                i.key = Json.str(e, "key", "");
                i.owner = Json.str(e, "owner", "");
                i.name = Json.str(e, "name", "");
                i.version = Json.str(e, "version", "");
                i.icon = Json.str(e, "icon", "");
                i.auto = Json.bool(e, "auto", false);
                for (Object d : Json.asList(e.get("deps"))) if (d instanceof String s) i.deps.add(s);
                if (!i.key.isEmpty()) res.put(i.key, i);
            }
        } catch (IOException | RuntimeException ignored) {}
        return res;
    }

    static void writeProfile(Game g, String id, String name, Map<String, Installed> mods) throws IOException {
        Path d = profileDir(g, id);
        Files.createDirectories(d);
        for (String sub : SUBDIRS) Files.createDirectories(d.resolve(sub));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        List<Object> list = new ArrayList<>();
        for (Installed i : mods.values()) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("key", i.key);
            e.put("owner", i.owner);
            e.put("name", i.name);
            e.put("version", i.version);
            e.put("icon", i.icon);
            e.put("auto", i.auto);
            e.put("deps", new ArrayList<Object>(i.deps));
            list.add(e);
        }
        m.put("mods", list);
        Files.writeString(d.resolve("profile.json"), Json.write(m), StandardCharsets.UTF_8);
    }

    private static String profileName(Game g, String id) {
        for (Profile p : profiles(g)) if (p.id.equals(id)) return p.name;
        return id;
    }

    // ------------------------------------------------------------------ активация профиля

    /**
     * Подключает профиль к игре: BepInEx/plugins, config, patchers и monomod становятся ссылками на папки профиля.
     * Так переключение между коллекциями мгновенное и моды профилей не смешиваются.
     */
    static void activate(Game g, Path exeDir, String profileId) throws IOException {
        Path bep = exeDir.resolve("BepInEx");
        Files.createDirectories(bep);
        Path pd = profileDir(g, profileId);
        Path migrated = base(g).resolve(".migrated");
        for (String sub : SUBDIRS) {
            Path dest = pd.resolve(sub);
            Files.createDirectories(dest);
            Path link = bep.resolve(sub);
            if (Files.isSymbolicLink(link)) {
                if (Files.readSymbolicLink(link).equals(dest)) continue;
                Files.delete(link);
            } else if (Files.isDirectory(link)) {
                if (!Files.exists(migrated) && Fs.isEmptyDir(dest)) {
                    // первый запуск: то, что уже лежало в игре (свои моды игрока), переезжает в этот профиль
                    try (var s = Files.list(link)) {
                        for (Path c : (Iterable<Path>) s::iterator)
                            Files.move(c, dest.resolve(c.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.delete(link);
                } else if (Fs.isEmptyDir(link)) {
                    Files.delete(link);
                } else {
                    Path old = bep.resolve(sub + ".ofll-old");
                    int n = 2;
                    while (Files.exists(old)) old = bep.resolve(sub + ".ofll-old" + n++);
                    Files.move(link, old);
                }
            } else if (Files.exists(link)) {
                Files.delete(link);
            }
            try {
                Files.createSymbolicLink(link, dest);
            } catch (UnsupportedOperationException | IOException e) {
                throw new IOException(I18n.tr("Не удалось создать ссылку на профиль модов: ") + link + " (" + e.getMessage() + ")");
            }
        }
        Files.writeString(migrated, "1");
    }

    /** Вызывается перед запуском игры: включает активный профиль и прокси-DLL BepInEx (WINEDLLOVERRIDES). */
    static List<String> prepareLaunch(Game g, Path exeDir) {
        List<String> warnings = new ArrayList<>();
        if (!bepInExInstalled(exeDir)) return warnings;
        try {
            activate(g, exeDir, activeId(g));
        } catch (IOException e) {
            warnings.add(e.getMessage());
        }
        ensureOverrides(g, exeDir);
        return warnings;
    }

    /** Добавляет winhttp=n,b (и другие найденные прокси-DLL) в переопределения, не трогая остальные записи. */
    static boolean ensureOverrides(Game g, Path exeDir) {
        boolean changed = false;
        for (String o : Runner.detectProxyDlls(exeDir)) {
            String dll = o.substring(0, o.indexOf('='));
            boolean has = false;
            for (String e : g.dllOverrides) {
                int eq = e.indexOf('=');
                if (eq > 0 && Arrays.asList(e.substring(0, eq).split(",")).contains(dll)) { has = true; break; }
            }
            if (!has) {
                g.dllOverrides.add(o);
                changed = true;
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ установка

    interface Step {
        void status(String text);
        void progress(double p);
        boolean cancelled();
    }

    /** Ставит BepInEx из пакета сообщества в папку рядом с exe игры. */
    static void installBepInEx(Game g, Thunder.Pkg pack, Path exeDir, Step st) throws IOException {
        Path tmp = Files.createTempDirectory("ofll-bepinex");
        try {
            Path zip = tmp.resolve("pack.zip");
            st.status(I18n.tr("Скачиваю BepInEx: ") + pack.name + " " + pack.version);
            Thunder.download(pack.downloadUrl.isEmpty() ? Thunder.downloadUrl(pack.owner, pack.name, pack.version) : pack.downloadUrl,
                zip, st::progress, st::cancelled);
            st.status(I18n.tr("Устанавливаю BepInEx…"));
            Path out = tmp.resolve("x");
            Fs.unzip(zip, out);
            Path root = findPackRoot(out);
            if (root == null) throw new IOException(I18n.tr("В архиве BepInEx не найдена папка BepInEx."));
            Files.createDirectories(exeDir);
            Path cfg = root.resolve("BepInEx/config");
            // конфиги игрока не перезаписываем
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                    Files.createDirectories(exeDir.resolve(root.relativize(d).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                    Path dst = exeDir.resolve(root.relativize(f).toString());
                    boolean isCfg = f.startsWith(cfg);
                    if (isCfg && Files.exists(dst)) return FileVisitResult.CONTINUE;
                    if (Files.isSymbolicLink(dst.getParent())) return FileVisitResult.CONTINUE;
                    Files.copy(f, dst, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });
            Files.writeString(exeDir.resolve(".ofll-bepinex"), pack.fullName + " " + pack.version, StandardCharsets.UTF_8);
        } finally {
            Fs.deleteTree(tmp);
        }
        activate(g, exeDir, activeId(g));
        ensureOverrides(g, exeDir);
    }

    private static Path findPackRoot(Path dir) throws IOException {
        Deque<Path> q = new ArrayDeque<>();
        q.add(dir);
        int guard = 0;
        while (!q.isEmpty() && guard++ < 200) {
            Path d = q.poll();
            try (var s = Files.list(d)) {
                List<Path> kids = new ArrayList<>();
                for (Path c : (Iterable<Path>) s::iterator) kids.add(c);
                for (Path c : kids)
                    if (Files.isDirectory(c) && c.getFileName().toString().equalsIgnoreCase("BepInEx")) return d;
                for (Path c : kids)
                    if (Files.isRegularFile(c) && c.getFileName().toString().equalsIgnoreCase("winhttp.dll")) return d;
                for (Path c : kids) if (Files.isDirectory(c)) q.add(c);
            }
        }
        return null;
    }

    /** Разбирает зависимость «Owner-Name-1.2.3» в {ключ «Owner-Name», версия «1.2.3»}. */
    static String[] parseDep(String dep) {
        int i = dep.lastIndexOf('-');
        if (i <= 0) return new String[] {dep, ""};
        return new String[] {dep.substring(0, i), dep.substring(i + 1)};
    }

    /**
     * Устанавливает мод вместе с зависимостями в профиль. Зависимость от самого BepInEx пропускается —
     * он ставится отдельно. Возвращает ключи всех установленных/обновлённых модов.
     */
    static List<String> installMod(Game g, String profileId, Thunder.Pkg root, Map<String, Thunder.Pkg> index, Step st) throws IOException {
        Map<String, Installed> inst = installed(g, profileId);
        List<Thunder.Pkg> order = new ArrayList<>();
        Map<String, String> want = new LinkedHashMap<>();   // key -> версия, которую нужно поставить
        collect(root, root.version, index, order, want, new HashSet<>(), true);
        List<String> done = new ArrayList<>();
        int total = order.size(), idx = 0;
        for (Thunder.Pkg p : order) {
            if (st.cancelled()) throw new java.util.concurrent.CancellationException("cancelled");
            String ver = want.get(p.key());
            boolean isRoot = p == root;
            Installed have = inst.get(p.key());
            if (!isRoot && have != null && Fs.compareVersions(have.version, ver) >= 0) { idx++; continue; }
            final int cur = idx;
            st.status(I18n.fmt("Скачиваю {0} ({1} из {2})…", p.name, cur + 1, total));
            Path tmp = Files.createTempDirectory("ofll-mod");
            try {
                Path zip = tmp.resolve("mod.zip");
                try {
                    Thunder.download(Thunder.downloadUrl(p.owner, p.name, ver), zip, x -> st.progress(x < 0 ? -1 : (cur + x) / total), st::cancelled);
                } catch (IOException first) {
                    if (ver.equals(p.version)) throw first;
                    ver = p.version;   // нужной версии уже нет — берём последнюю
                    Thunder.download(Thunder.downloadUrl(p.owner, p.name, ver), zip, x -> st.progress(x < 0 ? -1 : (cur + x) / total), st::cancelled);
                }
                st.status(I18n.fmt("Устанавливаю {0}…", p.name));
                Path out = tmp.resolve("x");
                Fs.unzip(zip, out);
                removeFiles(g, profileId, p.key());
                place(out, p.key(), profileDir(g, profileId));
            } finally {
                Fs.deleteTree(tmp);
            }
            Installed e = new Installed();
            e.key = p.key();
            e.owner = p.owner;
            e.name = p.name;
            e.version = ver;
            e.icon = p.icon;
            e.auto = !isRoot && (have == null || have.auto);
            e.deps.addAll(p.deps);
            inst.put(e.key, e);
            done.add(e.key);
            idx++;
            st.progress((double) idx / total);
        }
        writeProfile(g, profileId, profileName(g, profileId), inst);
        return done;
    }

    private static void collect(Thunder.Pkg p, String ver, Map<String, Thunder.Pkg> index, List<Thunder.Pkg> order,
                                Map<String, String> want, Set<String> visiting, boolean isRoot) {
        if (!visiting.add(p.key())) return;
        for (String dep : p.deps) {
            String[] kv = parseDep(dep);
            String low = kv[0].toLowerCase(Locale.ROOT);
            if (low.contains("bepinexpack") || low.endsWith("-bepinex")) continue;
            Thunder.Pkg d = index.get(kv[0]);
            if (d == null) continue;
            collect(d, kv[1].isEmpty() ? d.version : kv[1], index, order, want, visiting, false);
        }
        if (!want.containsKey(p.key())) {
            want.put(p.key(), ver);
            order.add(p);
        }
    }

    /** Раскладывает содержимое мода по папкам профиля так же, как это делают менеджеры модов. */
    private static void place(Path unpacked, String key, Path profile) throws IOException {
        Files.walkFileTree(unpacked, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, java.nio.file.attribute.BasicFileAttributes a) throws IOException {
                String rel = unpacked.relativize(f).toString().replace('\\', '/');
                String[] parts = rel.split("/");
                int skip = 0;
                if (parts.length > 1 && parts[0].equalsIgnoreCase("BepInEx")) skip = 1;
                String top = parts[skip].toLowerCase(Locale.ROOT);
                Path dst;
                if (parts.length - skip > 1 && (top.equals("plugins") || top.equals("patchers") || top.equals("monomod"))) {
                    dst = profile.resolve(top).resolve(key).resolve(join(parts, skip + 1));
                } else if (parts.length - skip > 1 && top.equals("config")) {
                    dst = profile.resolve("config").resolve(join(parts, skip + 1));
                } else {
                    dst = profile.resolve("plugins").resolve(key).resolve(join(parts, skip));
                }
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String join(String[] parts, int from) {
        return String.join("/", Arrays.copyOfRange(parts, from, parts.length));
    }

    private static void removeFiles(Game g, String profileId, String key) {
        Path pd = profileDir(g, profileId);
        for (String sub : new String[] {"plugins", "patchers", "monomod"}) Fs.deleteTree(pd.resolve(sub).resolve(key));
    }

    /** Удаляет мод и зависимости, которые ставились автоматически и больше никому не нужны. */
    static void uninstall(Game g, String profileId, String key) throws IOException {
        Map<String, Installed> inst = installed(g, profileId);
        if (inst.remove(key) == null) return;
        removeFiles(g, profileId, key);
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> needed = new HashSet<>();
            for (Installed i : inst.values()) for (String d : i.deps) needed.add(parseDep(d)[0]);
            for (Iterator<Installed> it = inst.values().iterator(); it.hasNext(); ) {
                Installed i = it.next();
                if (i.auto && !needed.contains(i.key)) {
                    removeFiles(g, profileId, i.key);
                    it.remove();
                    changed = true;
                }
            }
        }
        writeProfile(g, profileId, profileName(g, profileId), inst);
    }
}


// ================================================================
// ModsWindow.java
// ================================================================

/** Окно модов: каталог Thunderstore, установленные моды, обновления и коллекции (профили) модов. */
final class ModsWindow {
    private static final int MAX_ROWS = 400;
    private enum Mode { ONLINE, INSTALLED, UPDATES }

    private final Stage stage = new Stage();
    private final Game game;
    private final Thunder.Community community;
    private final Path exeDir;
    private final String themeUrl;
    private final Runnable saveGames;
    private final Consumer<String> openUrl;

    private final Map<String, Image> icons = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 300; }
    };
    private final Map<String, Thunder.Pkg> byKey = new HashMap<>();
    private List<Thunder.Pkg> all = new ArrayList<>();
    private Thunder.Pkg bepPack;
    private Map<String, ModStore.Installed> installed = new LinkedHashMap<>();
    private String profileId;
    private Mode mode = Mode.ONLINE;
    private boolean busy, loading, updatingProfiles;
    private volatile boolean cancel, closed;

    private final ObservableList<Thunder.Pkg> shown = FXCollections.observableArrayList();
    private ListView<Thunder.Pkg> list;
    private TextField search;
    private ComboBox<String> sortBox;
    private ComboBox<ModStore.Profile> profileBox;
    private ToggleButton tOnline, tInstalled, tUpdates;
    private Label status, bepStatus, countLabel;
    private ProgressBar bar;
    private Button bepBtn, refreshBtn, updateAllBtn, cancelBtn;

    ModsWindow(Stage owner, Game game, Thunder.Community community, Path exeDir, String themeUrl,
               Runnable saveGames, Consumer<String> openUrl) {
        this.game = game;
        this.community = community;
        this.exeDir = exeDir;
        this.themeUrl = themeUrl;
        this.saveGames = saveGames;
        this.openUrl = openUrl;
        this.profileId = ModStore.activeId(game);
        stage.initOwner(owner);
        stage.setTitle(I18n.tr("Моды") + " · " + game.name);
        Image icon = Brand.image();
        if (icon != null) stage.getIcons().add(icon);
        stage.setOnHidden(e -> {
            closed = true;
            cancel = true;
        });
        build();
    }

    // ------------------------------------------------------------------ построение окна

    private static Button btn(String text, String color) {
        Button b = new Button(text);
        b.getStyleClass().addAll("ofll-btn", color);
        return b;
    }

    private static Label lbl(String text, String css) {
        Label l = new Label(text);
        l.getStyleClass().add(css);
        return l;
    }

    private void build() {
        Label title = lbl(I18n.tr("Моды") + " · " + game.name, "ofll-title");
        title.setWrapText(false);
        bepStatus = lbl("", "ofll-muted");
        bepBtn = btn("", "btn-blue");
        bepBtn.setOnAction(ev -> installBepInEx(null));
        Button openDir = btn(I18n.tr("Папка модов"), "btn-surface");
        openDir.setOnAction(ev -> {
            Path d = ModStore.profileDir(game, profileId).resolve("plugins");
            if (!Sys.openPath(d)) alert(Alert.AlertType.WARNING, I18n.tr("Папка модов"), I18n.tr("Не удалось открыть папку:") + "\n" + d);
        });
        Region sp1 = new Region();
        HBox.setHgrow(sp1, Priority.ALWAYS);
        HBox head = new HBox(12, title, sp1, bepStatus, bepBtn, openDir);
        head.setAlignment(Pos.CENTER_LEFT);

        // --- коллекции (профили) ---
        profileBox = new ComboBox<>();
        profileBox.setPrefWidth(240);
        profileBox.setOnAction(ev -> {
            if (updatingProfiles || busy) return;
            ModStore.Profile p = profileBox.getValue();
            if (p != null && !p.id.equals(profileId)) switchProfile(p.id);
        });
        Button bNew = btn("＋", "btn-green");
        bNew.setTooltip(new Tooltip(I18n.tr("Новая коллекция модов")));
        bNew.setOnAction(ev -> newProfile());
        Button bRen = btn("✎", "btn-surface");
        bRen.setTooltip(new Tooltip(I18n.tr("Переименовать коллекцию")));
        bRen.setOnAction(ev -> renameProfile());
        Button bDup = btn("⧉", "btn-surface");
        bDup.setTooltip(new Tooltip(I18n.tr("Дублировать коллекцию вместе с модами")));
        bDup.setOnAction(ev -> duplicateProfile());
        Button bDel = btn("🗑", "btn-red");
        bDel.setTooltip(new Tooltip(I18n.tr("Удалить коллекцию")));
        bDel.setOnAction(ev -> deleteProfile());
        Label hint = lbl(I18n.tr("У каждой коллекции свои моды и настройки. Активная коллекция подключается к игре при запуске."), "ofll-hint");
        hint.setWrapText(false);
        HBox prof = new HBox(8, lbl(I18n.tr("Коллекция:"), "ofll-section"), profileBox, bNew, bRen, bDup, bDel, hint);
        prof.setAlignment(Pos.CENTER_LEFT);

        // --- поиск и фильтры ---
        search = new TextField();
        search.setPromptText(I18n.tr("Поиск мода…"));
        search.setPrefWidth(280);
        search.textProperty().addListener((o, a, b) -> refreshList());
        ToggleGroup tg = new ToggleGroup();
        tOnline = chip(I18n.tr("Каталог"), tg);
        tInstalled = chip(I18n.tr("Установлено"), tg);
        tUpdates = chip(I18n.tr("Обновления"), tg);
        tOnline.setSelected(true);
        tg.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
                return;
            }
            mode = b == tInstalled ? Mode.INSTALLED : b == tUpdates ? Mode.UPDATES : Mode.ONLINE;
            refreshList();
        });
        sortBox = new ComboBox<>(FXCollections.observableArrayList(
            I18n.tr("Популярные"), I18n.tr("Недавно обновлённые"), I18n.tr("По названию")));
        sortBox.setValue(sortBox.getItems().get(0));
        sortBox.setOnAction(ev -> refreshList());
        refreshBtn = btn("↻ " + I18n.tr("Обновить каталог"), "btn-surface");
        refreshBtn.setOnAction(ev -> load(true));
        Region sp2 = new Region();
        HBox.setHgrow(sp2, Priority.ALWAYS);
        HBox filters = new HBox(8, search, tOnline, tInstalled, tUpdates, sp2, sortBox, refreshBtn);
        filters.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(10, head, prof, filters);
        top.setPadding(new Insets(14, 16, 8, 16));

        // --- список ---
        list = new ListView<>(shown);
        list.setCellFactory(lv -> new ModCell());
        list.setPlaceholder(new Label(I18n.tr("Ничего не найдено")));
        list.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(list, Priority.ALWAYS);

        // --- низ ---
        status = lbl("", "ofll-muted");
        status.setMinWidth(0);
        countLabel = lbl("", "ofll-hint");
        bar = new ProgressBar(0);
        bar.setPrefWidth(200);
        bar.setVisible(false);
        cancelBtn = btn(I18n.tr("Отмена"), "btn-surface");
        cancelBtn.setVisible(false);
        cancelBtn.setOnAction(ev -> cancel = true);
        updateAllBtn = btn(I18n.tr("Обновить всё"), "btn-mauve");
        updateAllBtn.setOnAction(ev -> updateAll());
        Region sp3 = new Region();
        HBox.setHgrow(sp3, Priority.ALWAYS);
        HBox bottom = new HBox(10, status, sp3, countLabel, bar, cancelBtn, updateAllBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(8, 16, 12, 16));

        BorderPane root = new BorderPane();
        root.setTop(top);
        BorderPane.setMargin(list, new Insets(0, 12, 0, 12));
        root.setCenter(list);
        root.setBottom(bottom);
        Scene sc = new Scene(root, 960, 720);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        stage.setScene(sc);
    }

    private ToggleButton chip(String text, ToggleGroup g) {
        ToggleButton t = new ToggleButton(text);
        t.setToggleGroup(g);
        t.getStyleClass().add("ofll-chip");
        return t;
    }

    void show() {
        reloadProfiles();
        reloadInstalled();
        updateBepUi();
        stage.show();
        load(false);
    }

    // ------------------------------------------------------------------ загрузка каталога

    private void setBusyUi(boolean on, String text) {
        bar.setVisible(on);
        cancelBtn.setVisible(on && busy);
        if (on) bar.setProgress(-1);
        if (text != null) status.setText(text);
        refreshBtn.setDisable(on);
        updateAllBtn.setDisable(on);
        bepBtn.setDisable(on);
        profileBox.setDisable(on);
        if (list != null) list.refresh();
    }

    private void load(boolean force) {
        if (loading || busy) return;
        loading = true;
        setBusyUi(true, I18n.tr("Загружаю каталог Thunderstore… В первый раз это может занять минуту."));
        Thread t = new Thread(() -> {
            try {
                List<Thunder.Pkg> res = Thunder.packages(community.id, force,
                    p -> Platform.runLater(() -> bar.setProgress(p)), () -> closed);
                Platform.runLater(() -> onCatalog(res));
            } catch (Throwable e) {
                Platform.runLater(() -> {
                    loading = false;
                    setBusyUi(false, I18n.tr("Не удалось загрузить каталог: ") + String.valueOf(e.getMessage()));
                });
            }
        }, "ofll-thunder");
        t.setDaemon(true);
        t.start();
    }

    private void onCatalog(List<Thunder.Pkg> res) {
        loading = false;
        all = res;
        byKey.clear();
        for (Thunder.Pkg p : res) byKey.put(p.key(), p);
        bepPack = Thunder.findBepInEx(res);
        setBusyUi(false, I18n.fmt("Каталог загружен: модов — {0}.", res.size()));
        updateBepUi();
        refreshList();
    }

    // ------------------------------------------------------------------ данные

    private void reloadInstalled() {
        installed = ModStore.installed(game, profileId);
    }

    private void reloadProfiles() {
        updatingProfiles = true;
        List<ModStore.Profile> ps = ModStore.profiles(game);
        profileBox.setItems(FXCollections.observableArrayList(ps));
        for (ModStore.Profile p : ps) if (p.id.equals(profileId)) profileBox.setValue(p);
        if (profileBox.getValue() == null && !ps.isEmpty()) {
            profileBox.setValue(ps.get(0));
            profileId = ps.get(0).id;
        }
        updatingProfiles = false;
    }

    private void updateBepUi() {
        boolean has = ModStore.bepInExInstalled(exeDir);
        String ver = ModStore.bepInExVersion(exeDir);
        if (has) bepStatus.setText("BepInEx: " + I18n.tr("установлен") + (ver.isEmpty() ? "" : " (" + ver + ")"));
        else bepStatus.setText("BepInEx: " + I18n.tr("не установлен"));
        boolean outdated = has && bepPack != null && !ver.isEmpty() && !ver.endsWith(" " + bepPack.version);
        boolean show = bepPack != null && (!has || outdated);
        bepBtn.setVisible(show);
        bepBtn.setManaged(show);
        bepBtn.setText(has ? I18n.tr("Обновить BepInEx") : I18n.tr("Установить BepInEx"));
    }

    private List<Thunder.Pkg> updatable() {
        List<Thunder.Pkg> res = new ArrayList<>();
        for (ModStore.Installed i : installed.values()) {
            Thunder.Pkg p = byKey.get(i.key);
            if (p != null && Fs.compareVersions(i.version, p.version) < 0) res.add(p);
        }
        return res;
    }

    private Thunder.Pkg stub(ModStore.Installed i) {
        Thunder.Pkg p = new Thunder.Pkg();
        p.owner = i.owner;
        p.name = i.name;
        p.fullName = i.key;
        p.version = i.version;
        p.icon = i.icon;
        return p;
    }

    private void refreshList() {
        if (list == null) return;
        String q = search.getText().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        List<Thunder.Pkg> src = new ArrayList<>();
        if (mode == Mode.INSTALLED) {
            for (ModStore.Installed i : installed.values()) {
                Thunder.Pkg p = byKey.get(i.key);
                src.add(p != null ? p : stub(i));
            }
        } else if (mode == Mode.UPDATES) {
            src.addAll(updatable());
        } else {
            for (Thunder.Pkg p : all) {
                if (p.deprecated || p.nsfw) continue;
                if (p.name.toLowerCase(Locale.ROOT).startsWith("bepinexpack")) continue;
                src.add(p);
            }
        }
        List<Thunder.Pkg> out = new ArrayList<>();
        for (Thunder.Pkg p : src) {
            if (q.isEmpty()
                || (p.name + p.owner + p.description).toLowerCase(Locale.ROOT).replaceAll("\\s+", "").contains(q)) out.add(p);
        }
        int s = Math.max(0, sortBox.getItems().indexOf(sortBox.getValue()));
        if (s == 0) out.sort(Comparator.comparingLong((Thunder.Pkg p) -> p.downloads).reversed());
        else if (s == 1) out.sort(Comparator.comparing((Thunder.Pkg p) -> p.updated).reversed());
        else out.sort(Comparator.comparing((Thunder.Pkg p) -> p.name.toLowerCase(Locale.ROOT)));
        int total = out.size();
        if (out.size() > MAX_ROWS) out = new ArrayList<>(out.subList(0, MAX_ROWS));
        shown.setAll(out);
        int upd = updatable().size();
        tInstalled.setText(I18n.tr("Установлено") + " (" + installed.size() + ")");
        tUpdates.setText(I18n.tr("Обновления") + (upd > 0 ? " (" + upd + ")" : ""));
        updateAllBtn.setVisible(upd > 0);
        updateAllBtn.setManaged(upd > 0);
        updateAllBtn.setText(I18n.tr("Обновить всё") + " (" + upd + ")");
        countLabel.setText(total > MAX_ROWS ? I18n.fmt("Показано {0} из {1} — уточните поиск", MAX_ROWS, total) : "");
        list.refresh();
    }

    // ------------------------------------------------------------------ строка списка

    private Image icon(String url, double size) {
        if (url == null || !Thunder.trustedUrl(url)) return null;
        return icons.computeIfAbsent(url, u -> new Image(u, size * 2, size * 2, true, true, true));
    }

    private Node iconNode(String url, double size) {
        StackPane p = new StackPane();
        p.setMinSize(size, size);
        p.setPrefSize(size, size);
        p.setMaxSize(size, size);
        Image img = icon(url, size);
        if (img != null) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            p.getChildren().add(iv);
        } else {
            p.getStyleClass().add("ofll-cover-ph");
            p.getChildren().add(new Label("🧩"));
        }
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(14);
        clip.setArcHeight(14);
        p.setClip(clip);
        return p;
    }

    static String fmtCount(long n) {
        if (n >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        if (n >= 10_000) return (n / 1000) + "K";
        if (n >= 1000) return String.format(Locale.ROOT, "%.1fK", n / 1000.0);
        return Long.toString(n);
    }

    private final class ModCell extends ListCell<Thunder.Pkg> {
        @Override
        protected void updateItem(Thunder.Pkg p, boolean empty) {
            super.updateItem(p, empty);
            setText(null);
            setStyle("-fx-background-color: transparent; -fx-padding: 3 2 3 2;");
            if (empty || p == null) {
                setGraphic(null);
                return;
            }
            setGraphic(buildRow(p));
        }
    }

    private Node buildRow(Thunder.Pkg p) {
        Label name = lbl(p.name.replace('_', ' '), "ofll-mod-name");
        name.setMinWidth(0);
        Label author = lbl(p.owner, "ofll-mod-author");
        Label desc = lbl(p.description, "ofll-mod-author");
        desc.setMinWidth(0);
        VBox mid = new VBox(2, name, author, desc);
        mid.setAlignment(Pos.CENTER_LEFT);
        mid.setMinWidth(0);
        HBox.setHgrow(mid, Priority.ALWAYS);

        ModStore.Installed in = installed.get(p.key());
        boolean upd = in != null && Fs.compareVersions(in.version, p.version) < 0;
        Label dl = lbl("⬇ " + fmtCount(p.downloads), "ofll-mod-stat");
        Label ver = lbl(in == null ? "v" + p.version : upd ? "v" + in.version + " → v" + p.version : "v" + in.version, "ofll-mod-stat");
        VBox stats = new VBox(2, dl, ver);
        stats.setAlignment(Pos.CENTER_RIGHT);
        stats.setMinWidth(96);

        HBox act = new HBox(6);
        act.setAlignment(Pos.CENTER_RIGHT);
        act.setMinWidth(170);
        if (in == null) {
            Button b = btn(I18n.tr("Установить"), "btn-green");
            b.setOnAction(ev -> install(p));
            act.getChildren().add(b);
        } else {
            if (upd) {
                Button b = btn(I18n.tr("Обновить"), "btn-mauve");
                b.setOnAction(ev -> install(p));
                act.getChildren().add(b);
            } else {
                act.getChildren().add(lbl("✓ " + I18n.tr("Установлено"), "ofll-muted"));
            }
            Button rm = btn("✕", "btn-surface");
            rm.setTooltip(new Tooltip(I18n.tr("Удалить мод из этой коллекции")));
            rm.setOnAction(ev -> remove(p.key()));
            act.getChildren().add(rm);
        }
        for (Node n : act.getChildren()) {
            n.setDisable(busy || loading);
            n.setOnMouseClicked(ev -> ev.consume());
        }

        HBox row = new HBox(14, iconNode(p.icon, 56), mid, stats, act);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ofll-mod-row");
        row.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) showDetail(p);
        });
        return row;
    }

    // ------------------------------------------------------------------ действия

    private interface Body {
        void run(ModStore.Step st) throws Exception;
    }

    private void runTask(String title, Body body, Runnable after) {
        if (busy || loading) return;
        busy = true;
        cancel = false;
        setBusyUi(true, title);
        long[] last = {0};
        ModStore.Step st = new ModStore.Step() {
            @Override public void status(String t) { Platform.runLater(() -> status.setText(t)); }

            @Override public void progress(double p) {
                long now = System.currentTimeMillis();
                if (p >= 0 && now - last[0] < 80) return;
                last[0] = now;
                Platform.runLater(() -> bar.setProgress(p));
            }

            @Override public boolean cancelled() { return cancel; }
        };
        Thread t = new Thread(() -> {
            Throwable err = null;
            try {
                body.run(st);
            } catch (Throwable e) {
                err = e;
            }
            final Throwable fe = err;
            Platform.runLater(() -> {
                busy = false;
                setBusyUi(false, "");
                reloadInstalled();
                if (fe == null) {
                    saveGames.run();
                    if (after != null) after.run();
                } else if (fe instanceof CancellationException) {
                    status.setText(I18n.tr("Отменено."));
                } else {
                    status.setText("");
                    alert(Alert.AlertType.ERROR, I18n.tr("Ошибка"), String.valueOf(fe.getMessage()));
                }
                updateBepUi();
                refreshList();
            });
        }, "ofll-mods");
        t.setDaemon(true);
        t.start();
    }

    private void ensureBepInEx(Runnable then) {
        if (ModStore.bepInExInstalled(exeDir)) {
            then.run();
            return;
        }
        if (bepPack == null) {
            alert(Alert.AlertType.WARNING, "BepInEx",
                I18n.tr("Для этой игры на Thunderstore не найден пакет BepInEx. Установите BepInEx в папку игры вручную."));
            return;
        }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(stage);
        a.setTitle("BepInEx");
        a.setHeaderText(I18n.tr("Для модов нужен BepInEx"));
        a.setContentText(I18n.tr("Установить BepInEx рядом с игрой сейчас? Лаунчер сам добавит нужные параметры запуска, чтобы игра запускалась с OnlineFix и модами."));
        ButtonType yes = new ButtonType(I18n.tr("Установить"), ButtonBar.ButtonData.YES);
        a.getButtonTypes().setAll(yes, ButtonType.CANCEL);
        if (!themeUrl.isEmpty()) a.getDialogPane().getStylesheets().add(themeUrl);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isPresent() && r.get() == yes) installBepInEx(then);
    }

    private void installBepInEx(Runnable then) {
        if (bepPack == null) return;
        runTask(I18n.tr("Установка BepInEx…"), st -> ModStore.installBepInEx(game, bepPack, exeDir, st), () -> {
            String ov = String.join(";", game.dllOverrides);
            status.setText(I18n.tr("BepInEx установлен. Параметры запуска обновлены: WINEDLLOVERRIDES=") + ov);
            if (then != null) then.run();
        });
    }

    private void install(Thunder.Pkg p) {
        ensureBepInEx(() -> runTask(I18n.fmt("Устанавливаю {0}…", p.name),
            st -> ModStore.installMod(game, profileId, p, byKey, st),
            () -> status.setText(I18n.fmt("Готово: {0} установлен в коллекцию «{1}».", p.name, currentProfileName()))));
    }

    private void updateAll() {
        List<Thunder.Pkg> ups = updatable();
        if (ups.isEmpty()) return;
        ensureBepInEx(() -> runTask(I18n.tr("Обновляю моды…"), st -> {
            for (Thunder.Pkg p : ups) {
                if (st.cancelled()) throw new CancellationException("cancelled");
                ModStore.installMod(game, profileId, p, byKey, st);
            }
        }, () -> status.setText(I18n.fmt("Обновлено модов: {0}.", ups.size()))));
    }

    private void remove(String key) {
        try {
            ModStore.uninstall(game, profileId, key);
            reloadInstalled();
            refreshList();
            status.setText(I18n.tr("Мод удалён из коллекции."));
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, I18n.tr("Ошибка"), String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ коллекции

    private String currentProfileName() {
        ModStore.Profile p = profileBox.getValue();
        return p == null ? profileId : p.name;
    }

    private Optional<String> ask(String title, String header, String def) {
        TextInputDialog d = new TextInputDialog(def);
        d.initOwner(stage);
        d.setTitle(title);
        d.setHeaderText(header);
        if (!themeUrl.isEmpty()) d.getDialogPane().getStylesheets().add(themeUrl);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    private void switchProfile(String id) {
        profileId = id;
        game.modProfile = id;
        saveGames.run();
        try {
            if (ModStore.bepInExInstalled(exeDir)) ModStore.activate(game, exeDir, id);
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, I18n.tr("Ошибка"), String.valueOf(e.getMessage()));
        }
        reloadProfiles();
        reloadInstalled();
        refreshList();
        status.setText(I18n.fmt("Активная коллекция: «{0}».", currentProfileName()));
    }

    private void newProfile() {
        ask(I18n.tr("Новая коллекция"), I18n.tr("Название коллекции модов:"), "").ifPresent(name -> {
            try {
                ModStore.Profile p = ModStore.create(game, name);
                switchProfile(p.id);
            } catch (Exception e) {
                alert(Alert.AlertType.ERROR, I18n.tr("Ошибка"), String.valueOf(e.getMessage()));
            }
        });
    }

    private void renameProfile() {
        ask(I18n.tr("Переименовать коллекцию"), I18n.tr("Новое название:"), currentProfileName()).ifPresent(name -> {
            try {
                ModStore.rename(game, profileId, name);
                reloadProfiles();
            } catch (Exception e) {
                alert(Alert.AlertType.ERROR, I18n.tr("Ошибка"), String.valueOf(e.getMessage()));
            }
        });
    }

    private void duplicateProfile() {
        ask(I18n.tr("Дублировать коллекцию"), I18n.tr("Название копии:"), currentProfileName() + " 2").ifPresent(name -> {
            try {
                ModStore.Profile p = ModStore.duplicate(game, profileId, name);
                switchProfile(p.id);
            } catch (Exception e) {
                alert(Alert.AlertType.ERROR, I18n.tr("Ошибка"), String.valueOf(e.getMessage()));
            }
        });
    }

    private void deleteProfile() {
        List<ModStore.Profile> ps = ModStore.profiles(game);
        if (ps.size() <= 1) {
            alert(Alert.AlertType.INFORMATION, I18n.tr("Коллекции"), I18n.tr("Нельзя удалить единственную коллекцию."));
            return;
        }
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.initOwner(stage);
        a.setTitle(I18n.tr("Удалить коллекцию"));
        a.setHeaderText(I18n.fmt("Удалить коллекцию «{0}»?", currentProfileName()));
        a.setContentText(I18n.tr("Все моды и настройки этой коллекции будут удалены."));
        ButtonType yes = new ButtonType(I18n.tr("Удалить"), ButtonBar.ButtonData.YES);
        a.getButtonTypes().setAll(yes, ButtonType.CANCEL);
        if (!themeUrl.isEmpty()) a.getDialogPane().getStylesheets().add(themeUrl);
        Optional<ButtonType> r = a.showAndWait();
        if (r.isEmpty() || r.get() != yes) return;
        String old = profileId;
        String next = "default";
        for (ModStore.Profile p : ps) if (!p.id.equals(old)) { next = p.id; break; }
        switchProfile(next);
        ModStore.delete(game, old);
        reloadProfiles();
    }

    // ------------------------------------------------------------------ описание мода

    private void showDetail(Thunder.Pkg p) {
        Stage st = new Stage();
        st.initOwner(stage);
        st.initModality(Modality.WINDOW_MODAL);
        st.setTitle(p.name);
        Image ic = Brand.image();
        if (ic != null) st.getIcons().add(ic);

        ModStore.Installed in = installed.get(p.key());
        boolean upd = in != null && Fs.compareVersions(in.version, p.version) < 0;

        Label title = lbl(p.name.replace('_', ' '), "ofll-hero-title");
        title.setWrapText(true);
        Label author = lbl(I18n.tr("Автор: ") + p.owner, "ofll-muted");
        StringBuilder meta = new StringBuilder();
        meta.append("v").append(p.version).append("  ·  ⬇ ").append(fmtCount(p.downloads));
        if (p.rating > 0) meta.append("  ·  ★ ").append(p.rating);
        if (p.size > 0) meta.append("  ·  ").append(p.size / 1024 / 1024 > 0 ? (p.size / 1024 / 1024) + " MB" : (p.size / 1024) + " KB");
        if (p.updated.length() >= 10) meta.append("  ·  ").append(I18n.tr("обновлён ")).append(p.updated, 0, 10);
        Label metaL = lbl(meta.toString(), "ofll-mod-stat");
        Label cats = lbl(p.categories.isEmpty() ? "" : String.join(", ", p.categories), "ofll-hint");
        Label deps = lbl(p.deps.isEmpty() ? "" : I18n.tr("Зависимости: ") + String.join(", ", p.deps), "ofll-hint");
        deps.setWrapText(true);
        VBox info = new VBox(4, title, author, metaL, cats);
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox header = new HBox(16, iconNode(p.icon, 96), info);
        header.setAlignment(Pos.CENTER_LEFT);

        Button act;
        if (in == null) {
            act = btn(I18n.tr("Установить"), "btn-green");
            act.setOnAction(ev -> { st.close(); install(p); });
        } else if (upd) {
            act = btn(I18n.tr("Обновить"), "btn-mauve");
            act.setOnAction(ev -> { st.close(); install(p); });
        } else {
            act = btn("✓ " + I18n.tr("Установлено"), "btn-surface");
            act.setDisable(true);
        }
        act.setDisable(act.isDisabled() || busy || loading);
        Button page = btn(I18n.tr("Открыть на Thunderstore"), "btn-blue");
        page.setOnAction(ev -> openUrl.accept(p.pageUrl.isEmpty() ? Thunder.BASE : p.pageUrl));
        Button close = btn(I18n.tr("Закрыть"), "btn-surface");
        close.setOnAction(ev -> st.close());
        HBox buttons = new HBox(10, act, page, close);

        VBox body = new VBox(8, lbl(I18n.tr("Загружаю описание…"), "ofll-muted"));
        body.setPadding(new Insets(4, 8, 4, 4));
        ScrollPane sp = new ScrollPane(body);
        sp.setFitToWidth(true);
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox root = new VBox(12, header, buttons, deps, sp);
        root.setPadding(new Insets(18));
        Scene sc = new Scene(root, 780, 680);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        st.setScene(sc);
        st.show();

        Thread t = new Thread(() -> {
            String md = Thunder.readme(p);
            Platform.runLater(() -> {
                if (st.isShowing()) body.getChildren().setAll(Md.render(md, 700));
            });
        }, "ofll-readme");
        t.setDaemon(true);
        t.start();
    }

    private void alert(Alert.AlertType type, String title, String text) {
        Alert a = new Alert(type);
        a.initOwner(stage);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(text);
        if (!themeUrl.isEmpty()) a.getDialogPane().getStylesheets().add(themeUrl);
        a.show();
    }
}

/** Очень простой показ Markdown: заголовки, списки, цитаты, код и абзацы (без картинок и HTML). */
final class Md {
    private Md() {}

    static String clean(String s) {
        String t = s.replaceAll("<[^>]+>", "");
        t = t.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");
        t = t.replaceAll("\\[([^\\]]*)\\]\\(([^)]*)\\)", "$1");
        t = t.replace("**", "").replace("__", "").replace("`", "");
        t = t.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&quot;", "\"");
        return t.strip();
    }

    private static Label text(String s, String style, double width) {
        Label l = new Label(s);
        l.setWrapText(true);
        l.setMaxWidth(width);
        l.setMinHeight(Region.USE_PREF_SIZE);
        l.setStyle(style);
        return l;
    }

    static VBox render(String md, double width) {
        VBox box = new VBox(8);
        if (md == null) md = "";
        StringBuilder para = new StringBuilder(), code = new StringBuilder();
        boolean inCode = false;
        for (String raw : md.replace("\r", "").split("\n", -1)) {
            String line = raw.strip();
            if (line.startsWith("```")) {
                if (inCode) {
                    Label c = text(code.toString().stripTrailing(), "-fx-font-family: monospace; -fx-background-color: #181825; -fx-padding: 8; -fx-text-fill: #cdd6f4;", width);
                    box.getChildren().add(c);
                    code.setLength(0);
                } else {
                    flush(box, para, width);
                }
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                code.append(raw).append('\n');
                continue;
            }
            if (line.isEmpty()) {
                flush(box, para, width);
            } else if (line.matches("^#{1,6}\\s+.*")) {
                flush(box, para, width);
                int lvl = 0;
                while (lvl < line.length() && line.charAt(lvl) == '#') lvl++;
                String h = clean(line.substring(lvl));
                if (!h.isEmpty()) box.getChildren().add(text(h, "-fx-font-size: " + Math.max(14, 22 - lvl * 2) + "px; -fx-font-weight: bold; -fx-text-fill: #cdd6f4;", width));
            } else if (line.matches("^([-*+]|\\d+\\.)\\s+.*")) {
                flush(box, para, width);
                String item = clean(line.replaceFirst("^([-*+]|\\d+\\.)\\s+", ""));
                if (!item.isEmpty()) box.getChildren().add(text("•  " + item, "-fx-text-fill: #cdd6f4;", width));
            } else if (line.startsWith(">")) {
                flush(box, para, width);
                String q = clean(line.replaceFirst("^>+\\s*", ""));
                if (!q.isEmpty()) box.getChildren().add(text(q, "-fx-text-fill: #a6adc8; -fx-font-style: italic;", width));
            } else if (line.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
                flush(box, para, width);
                box.getChildren().add(new Separator());
            } else {
                String c = clean(line);
                if (!c.isEmpty()) para.append(c).append(' ');
            }
        }
        flush(box, para, width);
        if (box.getChildren().isEmpty()) box.getChildren().add(text("—", "-fx-text-fill: #a6adc8;", width));
        return box;
    }

    private static void flush(VBox box, StringBuilder para, double width) {
        String s = para.toString().strip();
        para.setLength(0);
        if (!s.isEmpty()) box.getChildren().add(text(s, "-fx-text-fill: #cdd6f4;", width));
    }
}


// ================================================================
// I18n.java
// ================================================================

/**
 * Локализация интерфейса. Ключи — русские строки (исходный текст лаунчера), значения — переводы.
 * Язык по умолчанию — английский. Если для текущего языка перевода нет, используется английский,
 * а если нет и его — сам ключ (русский текст), чтобы интерфейс не оставался пустым.
 */
final class I18n {
    static final List<String> CODES = List.of("en", "ru", "uk", "pl", "ja");
    static final Map<String, String> NAMES = Map.of(
        "en", "English",
        "ru", "Русский",
        "uk", "Українська",
        "pl", "Polski",
        "ja", "日本語");

    private static volatile String current = "en";

    private I18n() {}

    static String normalize(String code) {
        if (code == null) return "en";
        String c = code.trim().toLowerCase(Locale.ROOT);
        return CODES.contains(c) ? c : "en";
    }

    static void set(String code) {
        current = normalize(code);
    }

    static String current() {
        return current;
    }

    /** Перевод строки key на текущий язык (key — русский оригинал). */
    static String tr(String key) {
        if (key == null) return "";
        if ("ru".equals(current)) return key;
        Map<String, String> m = TABLE.get(current);
        String v = m == null ? null : m.get(key);
        if (v != null) return v;
        Map<String, String> en = TABLE.get("en");
        String ev = en == null ? null : en.get(key);
        return ev != null ? ev : key;
    }

    /** tr(pattern), затем подстановка {0}, {1}, … через MessageFormat. */
    static String fmt(String pattern, Object... args) {
        try {
            return MessageFormat.format(tr(pattern), args);
        } catch (IllegalArgumentException e) {
            return tr(pattern);
        }
    }

    private static Map<String, String> map(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    private static final Map<String, Map<String, String>> TABLE = new LinkedHashMap<>();
    static {
        TABLE.put("en", map(
            "BepInEx установлен. Параметры запуска обновлены: WINEDLLOVERRIDES=", "BepInEx installed. Launch parameters updated: WINEDLLOVERRIDES=",
            "GitHub временно ограничил число запросов. Подождите несколько минут и повторите.", "GitHub has temporarily rate-limited requests. Wait a few minutes and try again.",
            "Автор: ", "Author: ",
            "Архив скачан не полностью. Проверьте подключение и повторите.", "The archive was not fully downloaded. Check your connection and try again.",
            "В архиве BepInEx не найдена папка BepInEx.", "No BepInEx folder was found in the archive.",
            "В архиве DXVK не найдены библиотеки.", "No libraries were found in the DXVK archive.",
            "В архиве не найден Proton или Wine.", "No Proton or Wine build was found in the archive.",
            "В релизе DXVK не найден архив.", "No archive was found in the DXVK release.",
            "Все моды и настройки этой коллекции будут удалены.", "All mods and settings in this collection will be deleted.",
            "Для модов нужен BepInEx", "Mods require BepInEx",
            "Для этой игры на Thunderstore не найден пакет BepInEx. Установите BepInEx в папку игры вручную.",
                "No BepInEx package was found for this game on Thunderstore. Install BepInEx into the game folder manually.",
            "Дублировать коллекцию", "Duplicate collection",
            "Дублировать коллекцию вместе с модами", "Duplicate the collection together with its mods",
            "Зависимости: ", "Dependencies: ",
            "Загружаю каталог Thunderstore… В первый раз это может занять минуту.", "Loading the Thunderstore catalog… The first time this may take a minute.",
            "Загружаю описание…", "Loading description…",
            "Закрыть", "Close",
            "Запрос прерван", "Request interrupted",
            "Ищу последнюю версию DXVK…", "Looking up the latest DXVK version…",
            "Каталог", "Catalog",
            "Коллекции", "Collections",
            "Коллекция:", "Collection:",
            "Контрольная сумма архива не совпала — файл повреждён. Попробуйте ещё раз или выберите другую версию.",
                "The archive checksum did not match — the file is corrupted. Try again or choose a different version.",
            "Контрольная сумма не подтвердилась, но архив цел — продолжаю установку.", "The checksum could not be confirmed, but the archive is intact — continuing installation.",
            "Контрольная сумма не совпала, скачиваю заново…", "Checksum mismatch, downloading again…",
            "Копирую библиотеки DXVK в префикс…", "Copying DXVK libraries into the prefix…",
            "Мод удалён из коллекции.", "Mod removed from the collection.",
            "Моды", "Mods",
            "Название коллекции модов:", "Mod collection name:",
            "Название копии:", "Copy name:",
            "Не найдена утилита tar.", "The tar utility was not found.",
            "Не удалось загрузить каталог: ", "Failed to load the catalog: ",
            "Не удалось открыть папку:", "Could not open the folder:",
            "Не удалось распаковать архив: ", "Failed to extract the archive: ",
            "Не удалось создать префикс Wine.", "Failed to create the Wine prefix.",
            "Не удалось создать ссылку на профиль модов: ", "Failed to create a link to the mod profile: ",
            "Недавно обновлённые", "Recently updated",
            "Недопустимое имя папки в архиве.", "Invalid folder name in the archive.",
            "Нельзя удалить единственную коллекцию.", "You cannot delete the only collection.",
            "Ничего не найдено", "Nothing found",
            "Новая коллекция", "New collection",
            "Новая коллекция модов", "New mod collection",
            "Новое название:", "New name:",
            "Обновить", "Update",
            "Обновить BepInEx", "Update BepInEx",
            "Обновить всё", "Update all",
            "Обновить каталог", "Refresh catalog",
            "Обновления", "Updates",
            "Обновляю моды…", "Updating mods…",
            "Основная", "Default",
            "Открыть на Thunderstore", "Open on Thunderstore",
            "Отмена", "Cancel",
            "Отменено.", "Cancelled.",
            "Ошибка", "Error",
            "Папка модов", "Mods folder",
            "Переименовать коллекцию", "Rename collection",
            "По названию", "By name",
            "Поиск мода…", "Search mods…",
            "Популярные", "Popular",
            "Префикс Wine ещё не создан, а Wine не найден. Запустите игру один раз и повторите.",
                "The Wine prefix has not been created yet, and Wine was not found. Run the game once and try again.",
            "Проверяю контрольную сумму…", "Verifying checksum…",
            "Проверяю целостность архива…", "Verifying archive integrity…",
            "Распаковываю…", "Extracting…",
            "Сервер вернул неожиданный ответ", "The server returned an unexpected response",
            "Скачиваю BepInEx: ", "Downloading BepInEx: ",
            "Создаю префикс Wine…", "Creating the Wine prefix…",
            "У каждой коллекции свои моды и настройки. Активная коллекция подключается к игре при запуске.",
                "Each collection has its own mods and settings. The active collection is attached to the game on launch.",
            "Удалить", "Delete",
            "Удалить коллекцию", "Delete collection",
            "Удалить мод из этой коллекции", "Remove this mod from the collection",
            "Устанавливаю BepInEx…", "Installing BepInEx…",
            "Установить", "Install",
            "Установить BepInEx", "Install BepInEx",
            "Установить BepInEx рядом с игрой сейчас? Лаунчер сам добавит нужные параметры запуска, чтобы игра запускалась с OnlineFix и модами.",
                "Install BepInEx next to the game now? The launcher will add the required launch parameters itself, so the game runs with OnlineFix and mods.",
            "Установка BepInEx…", "Installing BepInEx…",
            "Установлено", "Installed",
            "не установлен", "not installed",
            "обновлён ", "updated ",
            "установлен", "installed",

            "DXVK {0} установлен в префикс игры.", "DXVK {0} installed into the game prefix.",
            "DXVK установлен в префикс ({0}).", "DXVK is installed in the prefix ({0}).",
            "GitHub ответил кодом {0}", "GitHub responded with code {0}",
            "Активная коллекция: «{0}».", "Active collection: \u201c{0}\u201d.",
            "Готово: {0} установлен в коллекцию «{1}».", "Done: {0} installed into the \u201c{1}\u201d collection.",
            "Доступно версий: {0}", "Versions available: {0}",
            "Игр: {0} · всего наиграно: {1}", "Games: {0} \u00b7 total playtime: {1}",
            "Каталог загружен: модов — {0}.", "Catalog loaded: {0} mods.",
            "Ничего не найдено по запросу «{0}».", "Nothing found for \u201c{0}\u201d.",
            "Обновлено модов: {0}.", "Mods updated: {0}.",
            "Показано {0} из {1} — уточните поиск", "Showing {0} of {1} \u2014 refine your search",
            "Сегодня играем в «{0}»!", "Today we're playing \u201c{0}\u201d!",
            "Сервер ответил кодом {0}", "The server responded with code {0}",
            "Скачиваю {0} ({1} из {2})…", "Downloading {0} ({1} of {2})\u2026",
            "Скачиваю {0}: {1} МБ из {2} МБ", "Downloading {0}: {1} MB of {2} MB",
            "Скачиваю {0}…", "Downloading {0}\u2026",
            "Удалить коллекцию «{0}»?", "Delete the \u201c{0}\u201d collection?",
            "Устанавливаю {0}…", "Installing {0}\u2026",
            "Установка {0}", "Installing {0}"
        ));

        TABLE.put("uk", map(
            "BepInEx установлен. Параметры запуска обновлены: WINEDLLOVERRIDES=", "BepInEx встановлено. Параметри запуску оновлено: WINEDLLOVERRIDES=",
            "GitHub временно ограничил число запросов. Подождите несколько минут и повторите.", "GitHub тимчасово обмежив кількість запитів. Зачекайте кілька хвилин і повторіть спробу.",
            "Автор: ", "Автор: ",
            "Архив скачан не полностью. Проверьте подключение и повторите.", "Архів завантажено не повністю. Перевірте з'єднання і повторіть спробу.",
            "В архиве BepInEx не найдена папка BepInEx.", "У архіві не знайдено папку BepInEx.",
            "В архиве DXVK не найдены библиотеки.", "У архіві DXVK не знайдено бібліотек.",
            "В архиве не найден Proton или Wine.", "У архіві не знайдено Proton або Wine.",
            "В релизе DXVK не найден архив.", "У релізі DXVK не знайдено архів.",
            "Все моды и настройки этой коллекции будут удалены.", "Усі моди та налаштування цієї колекції буде видалено.",
            "Для модов нужен BepInEx", "Для модів потрібен BepInEx",
            "Для этой игры на Thunderstore не найден пакет BepInEx. Установите BepInEx в папку игры вручную.",
                "Для цієї гри на Thunderstore не знайдено пакет BepInEx. Встановіть BepInEx у папку гри вручну.",
            "Дублировать коллекцию", "Дублювати колекцію",
            "Дублировать коллекцию вместе с модами", "Дублювати колекцію разом з модами",
            "Зависимости: ", "Залежності: ",
            "Загружаю каталог Thunderstore… В первый раз это может занять минуту.", "Завантажую каталог Thunderstore… Уперше це може зайняти хвилину.",
            "Загружаю описание…", "Завантажую опис…",
            "Закрыть", "Закрити",
            "Запрос прерван", "Запит перервано",
            "Ищу последнюю версию DXVK…", "Шукаю останню версію DXVK…",
            "Каталог", "Каталог",
            "Коллекции", "Колекції",
            "Коллекция:", "Колекція:",
            "Контрольная сумма архива не совпала — файл повреждён. Попробуйте ещё раз или выберите другую версию.",
                "Контрольна сума архіву не збіглася — файл пошкоджено. Спробуйте ще раз або виберіть іншу версію.",
            "Контрольная сумма не подтвердилась, но архив цел — продолжаю установку.", "Контрольну суму не підтверджено, але архів цілий — продовжую встановлення.",
            "Контрольная сумма не совпала, скачиваю заново…", "Контрольна сума не збіглася, завантажую знову…",
            "Копирую библиотеки DXVK в префикс…", "Копіюю бібліотеки DXVK у префікс…",
            "Мод удалён из коллекции.", "Мод видалено з колекції.",
            "Моды", "Моди",
            "Название коллекции модов:", "Назва колекції модів:",
            "Название копии:", "Назва копії:",
            "Не найдена утилита tar.", "Не знайдено утиліту tar.",
            "Не удалось загрузить каталог: ", "Не вдалося завантажити каталог: ",
            "Не удалось открыть папку:", "Не вдалося відкрити папку:",
            "Не удалось распаковать архив: ", "Не вдалося розпакувати архів: ",
            "Не удалось создать префикс Wine.", "Не вдалося створити префікс Wine.",
            "Не удалось создать ссылку на профиль модов: ", "Не вдалося створити посилання на профіль модів: ",
            "Недавно обновлённые", "Нещодавно оновлені",
            "Недопустимое имя папки в архиве.", "Недопустиме ім'я папки в архіві.",
            "Нельзя удалить единственную коллекцию.", "Не можна видалити єдину колекцію.",
            "Ничего не найдено", "Нічого не знайдено",
            "Новая коллекция", "Нова колекція",
            "Новая коллекция модов", "Нова колекція модів",
            "Новое название:", "Нова назва:",
            "Обновить", "Оновити",
            "Обновить BepInEx", "Оновити BepInEx",
            "Обновить всё", "Оновити все",
            "Обновить каталог", "Оновити каталог",
            "Обновления", "Оновлення",
            "Обновляю моды…", "Оновлюю моди…",
            "Основная", "Основна",
            "Открыть на Thunderstore", "Відкрити на Thunderstore",
            "Отмена", "Скасувати",
            "Отменено.", "Скасовано.",
            "Ошибка", "Помилка",
            "Папка модов", "Папка модів",
            "Переименовать коллекцию", "Перейменувати колекцію",
            "По названию", "За назвою",
            "Поиск мода…", "Пошук мода…",
            "Популярные", "Популярні",
            "Префикс Wine ещё не создан, а Wine не найден. Запустите игру один раз и повторите.",
                "Префікс Wine ще не створено, а Wine не знайдено. Запустіть гру один раз і повторіть спробу.",
            "Проверяю контрольную сумму…", "Перевіряю контрольну суму…",
            "Проверяю целостность архива…", "Перевіряю цілісність архіву…",
            "Распаковываю…", "Розпаковую…",
            "Сервер вернул неожиданный ответ", "Сервер повернув неочікувану відповідь",
            "Скачиваю BepInEx: ", "Завантажую BepInEx: ",
            "Создаю префикс Wine…", "Створюю префікс Wine…",
            "У каждой коллекции свои моды и настройки. Активная коллекция подключается к игре при запуске.",
                "У кожної колекції свої моди та налаштування. Активна колекція підключається до гри під час запуску.",
            "Удалить", "Видалити",
            "Удалить коллекцию", "Видалити колекцію",
            "Удалить мод из этой коллекции", "Видалити мод із цієї колекції",
            "Устанавливаю BepInEx…", "Встановлюю BepInEx…",
            "Установить", "Встановити",
            "Установить BepInEx", "Встановити BepInEx",
            "Установить BepInEx рядом с игрой сейчас? Лаунчер сам добавит нужные параметры запуска, чтобы игра запускалась с OnlineFix и модами.",
                "Встановити BepInEx поруч із грою зараз? Лаунчер сам додасть потрібні параметри запуску, щоб гра запускалася з OnlineFix і модами.",
            "Установка BepInEx…", "Встановлення BepInEx…",
            "Установлено", "Встановлено",
            "не установлен", "не встановлено",
            "обновлён ", "оновлено ",
            "установлен", "встановлено",

            "DXVK {0} установлен в префикс игры.", "DXVK {0} встановлено в префікс гри.",
            "DXVK установлен в префикс ({0}).", "DXVK встановлено в префікс ({0}).",
            "GitHub ответил кодом {0}", "GitHub відповів кодом {0}",
            "Активная коллекция: «{0}».", "Активна колекція: «{0}».",
            "Готово: {0} установлен в коллекцию «{1}».", "Готово: {0} встановлено в колекцію «{1}».",
            "Доступно версий: {0}", "Доступно версій: {0}",
            "Игр: {0} · всего наиграно: {1}", "Ігор: {0} \u00b7 всього зіграно: {1}",
            "Каталог загружен: модов — {0}.", "Каталог завантажено: модів — {0}.",
            "Ничего не найдено по запросу «{0}».", "Нічого не знайдено за запитом «{0}».",
            "Обновлено модов: {0}.", "Оновлено модів: {0}.",
            "Показано {0} из {1} — уточните поиск", "Показано {0} з {1} — уточніть пошук",
            "Сегодня играем в «{0}»!", "Сьогодні граємо в «{0}»!",
            "Сервер ответил кодом {0}", "Сервер відповів кодом {0}",
            "Скачиваю {0} ({1} из {2})…", "Завантажую {0} ({1} з {2})…",
            "Скачиваю {0}: {1} МБ из {2} МБ", "Завантажую {0}: {1} МБ з {2} МБ",
            "Скачиваю {0}…", "Завантажую {0}…",
            "Удалить коллекцию «{0}»?", "Видалити колекцію «{0}»?",
            "Устанавливаю {0}…", "Встановлюю {0}…",
            "Установка {0}", "Встановлення {0}"
        ));

        TABLE.put("pl", map(
            "BepInEx установлен. Параметры запуска обновлены: WINEDLLOVERRIDES=", "Zainstalowano BepInEx. Zaktualizowano parametry uruchamiania: WINEDLLOVERRIDES=",
            "GitHub временно ограничил число запросов. Подождите несколько минут и повторите.", "GitHub tymczasowo ograniczył liczbę żądań. Odczekaj kilka minut i spróbuj ponownie.",
            "Автор: ", "Autor: ",
            "Архив скачан не полностью. Проверьте подключение и повторите.", "Archiwum nie zostało pobrane w całości. Sprawdź połączenie i spróbuj ponownie.",
            "В архиве BepInEx не найдена папка BepInEx.", "W archiwum nie znaleziono folderu BepInEx.",
            "В архиве DXVK не найдены библиотеки.", "W archiwum DXVK nie znaleziono bibliotek.",
            "В архиве не найден Proton или Wine.", "W archiwum nie znaleziono Protona ani Wine.",
            "В релизе DXVK не найден архив.", "W wydaniu DXVK nie znaleziono archiwum.",
            "Все моды и настройки этой коллекции будут удалены.", "Wszystkie mody i ustawienia tej kolekcji zostaną usunięte.",
            "Для модов нужен BepInEx", "Mody wymagają BepInEx",
            "Для этой игры на Thunderstore не найден пакет BepInEx. Установите BepInEx в папку игры вручную.",
                "Nie znaleziono pakietu BepInEx dla tej gry na Thunderstore. Zainstaluj BepInEx w folderze gry ręcznie.",
            "Дублировать коллекцию", "Duplikuj kolekcję",
            "Дублировать коллекцию вместе с модами", "Duplikuj kolekcję razem z modami",
            "Зависимости: ", "Zależności: ",
            "Загружаю каталог Thunderstore… В первый раз это может занять минуту.", "Wczytuję katalog Thunderstore… Za pierwszym razem może to potrwać minutę.",
            "Загружаю описание…", "Wczytuję opis…",
            "Закрыть", "Zamknij",
            "Запрос прерван", "Żądanie przerwane",
            "Ищу последнюю версию DXVK…", "Szukam najnowszej wersji DXVK…",
            "Каталог", "Katalog",
            "Коллекции", "Kolekcje",
            "Коллекция:", "Kolekcja:",
            "Контрольная сумма архива не совпала — файл повреждён. Попробуйте ещё раз или выберите другую версию.",
                "Suma kontrolna archiwum się nie zgadza — plik jest uszkodzony. Spróbuj ponownie lub wybierz inną wersję.",
            "Контрольная сумма не подтвердилась, но архив цел — продолжаю установку.", "Nie udało się potwierdzić sumy kontrolnej, ale archiwum jest nienaruszone — kontynuuję instalację.",
            "Контрольная сумма не совпала, скачиваю заново…", "Suma kontrolna się nie zgadza, pobieram ponownie…",
            "Копирую библиотеки DXVK в префикс…", "Kopiuję biblioteki DXVK do prefiksu…",
            "Мод удалён из коллекции.", "Mod usunięty z kolekcji.",
            "Моды", "Mody",
            "Название коллекции модов:", "Nazwa kolekcji modów:",
            "Название копии:", "Nazwa kopii:",
            "Не найдена утилита tar.", "Nie znaleziono narzędzia tar.",
            "Не удалось загрузить каталог: ", "Nie udało się wczytać katalogu: ",
            "Не удалось открыть папку:", "Nie udało się otworzyć folderu:",
            "Не удалось распаковать архив: ", "Nie udało się rozpakować archiwum: ",
            "Не удалось создать префикс Wine.", "Nie udało się utworzyć prefiksu Wine.",
            "Не удалось создать ссылку на профиль модов: ", "Nie udało się utworzyć dowiązania do profilu modów: ",
            "Недавно обновлённые", "Ostatnio aktualizowane",
            "Недопустимое имя папки в архиве.", "Niedozwolona nazwa folderu w archiwum.",
            "Нельзя удалить единственную коллекцию.", "Nie można usunąć jedynej kolekcji.",
            "Ничего не найдено", "Nic nie znaleziono",
            "Новая коллекция", "Nowa kolekcja",
            "Новая коллекция модов", "Nowa kolekcja modów",
            "Новое название:", "Nowa nazwa:",
            "Обновить", "Aktualizuj",
            "Обновить BepInEx", "Zaktualizuj BepInEx",
            "Обновить всё", "Aktualizuj wszystko",
            "Обновить каталог", "Odśwież katalog",
            "Обновления", "Aktualizacje",
            "Обновляю моды…", "Aktualizuję mody…",
            "Основная", "Domyślna",
            "Открыть на Thunderstore", "Otwórz na Thunderstore",
            "Отмена", "Anuluj",
            "Отменено.", "Anulowano.",
            "Ошибка", "Błąd",
            "Папка модов", "Folder modów",
            "Переименовать коллекцию", "Zmień nazwę kolekcji",
            "По названию", "Według nazwy",
            "Поиск мода…", "Szukaj moda…",
            "Популярные", "Popularne",
            "Префикс Wine ещё не создан, а Wine не найден. Запустите игру один раз и повторите.",
                "Prefiks Wine nie został jeszcze utworzony, a Wine nie zostało znalezione. Uruchom grę raz i spróbuj ponownie.",
            "Проверяю контрольную сумму…", "Sprawdzam sumę kontrolną…",
            "Проверяю целостность архива…", "Sprawdzam integralność archiwum…",
            "Распаковываю…", "Rozpakowuję…",
            "Сервер вернул неожиданный ответ", "Serwer zwrócił nieoczekiwaną odpowiedź",
            "Скачиваю BepInEx: ", "Pobieram BepInEx: ",
            "Создаю префикс Wine…", "Tworzę prefiks Wine…",
            "У каждой коллекции свои моды и настройки. Активная коллекция подключается к игре при запуске.",
                "Każda kolekcja ma własne mody i ustawienia. Aktywna kolekcja jest podłączana do gry przy uruchomieniu.",
            "Удалить", "Usuń",
            "Удалить коллекцию", "Usuń kolekcję",
            "Удалить мод из этой коллекции", "Usuń mod z tej kolekcji",
            "Устанавливаю BepInEx…", "Instaluję BepInEx…",
            "Установить", "Zainstaluj",
            "Установить BepInEx", "Zainstaluj BepInEx",
            "Установить BepInEx рядом с игрой сейчас? Лаунчер сам добавит нужные параметры запуска, чтобы игра запускалась с OnlineFix и модами.",
                "Zainstalować BepInEx obok gry teraz? Launcher sam doda potrzebne parametry uruchamiania, aby gra działała z OnlineFix i modami.",
            "Установка BepInEx…", "Instalacja BepInEx…",
            "Установлено", "Zainstalowano",
            "не установлен", "nie zainstalowano",
            "обновлён ", "zaktualizowano ",
            "установлен", "zainstalowano",

            "DXVK {0} установлен в префикс игры.", "DXVK {0} zainstalowany w prefiksie gry.",
            "DXVK установлен в префикс ({0}).", "DXVK jest zainstalowany w prefiksie ({0}).",
            "GitHub ответил кодом {0}", "GitHub odpowiedział kodem {0}",
            "Активная коллекция: «{0}».", "Aktywna kolekcja: \u201e{0}\u201d.",
            "Готово: {0} установлен в коллекцию «{1}».", "Gotowe: {0} zainstalowano w kolekcji \u201e{1}\u201d.",
            "Доступно версий: {0}", "Dostępnych wersji: {0}",
            "Игр: {0} · всего наиграно: {1}", "Gier: {0} \u00b7 łączny czas gry: {1}",
            "Каталог загружен: модов — {0}.", "Katalog wczytany: modów — {0}.",
            "Ничего не найдено по запросу «{0}».", "Nie znaleziono niczego dla \u201e{0}\u201d.",
            "Обновлено модов: {0}.", "Zaktualizowano modów: {0}.",
            "Показано {0} из {1} — уточните поиск", "Wyświetlono {0} z {1} \u2014 doprecyzuj wyszukiwanie",
            "Сегодня играем в «{0}»!", "Dziś gramy w \u201e{0}\u201d!",
            "Сервер ответил кодом {0}", "Serwer odpowiedział kodem {0}",
            "Скачиваю {0} ({1} из {2})…", "Pobieram {0} ({1} z {2})\u2026",
            "Скачиваю {0}: {1} МБ из {2} МБ", "Pobieram {0}: {1} MB z {2} MB",
            "Скачиваю {0}…", "Pobieram {0}\u2026",
            "Удалить коллекцию «{0}»?", "Usunąć kolekcję \u201e{0}\u201d?",
            "Устанавливаю {0}…", "Instaluję {0}\u2026",
            "Установка {0}", "Instalacja {0}"
        ));

        TABLE.put("ja", map(
            "BepInEx установлен. Параметры запуска обновлены: WINEDLLOVERRIDES=", "BepInEx をインストールしました。起動パラメータを更新しました: WINEDLLOVERRIDES=",
            "GitHub временно ограничил число запросов. Подождите несколько минут и повторите.", "GitHub のリクエスト数が一時的に制限されています。数分待ってから再試行してください。",
            "Автор: ", "作者: ",
            "Архив скачан не полностью. Проверьте подключение и повторите.", "アーカイブが完全にダウンロードされませんでした。接続を確認して再試行してください。",
            "В архиве BepInEx не найдена папка BepInEx.", "アーカイブ内に BepInEx フォルダが見つかりません。",
            "В архиве DXVK не найдены библиотеки.", "DXVK アーカイブ内にライブラリが見つかりません。",
            "В архиве не найден Proton или Wine.", "アーカイブ内に Proton または Wine が見つかりません。",
            "В релизе DXVK не найден архив.", "DXVK のリリースにアーカイブが見つかりません。",
            "Все моды и настройки этой коллекции будут удалены.", "このコレクションのすべての MOD と設定が削除されます。",
            "Для модов нужен BepInEx", "MOD には BepInEx が必要です",
            "Для этой игры на Thunderstore не найден пакет BepInEx. Установите BepInEx в папку игры вручную.",
                "このゲームの BepInEx パッケージが Thunderstore に見つかりません。ゲームフォルダに手動で BepInEx をインストールしてください。",
            "Дублировать коллекцию", "コレクションを複製",
            "Дублировать коллекцию вместе с модами", "MOD ごとコレクションを複製",
            "Зависимости: ", "依存関係: ",
            "Загружаю каталог Thunderstore… В первый раз это может занять минуту.", "Thunderstore のカタログを読み込んでいます…初回は1分ほどかかる場合があります。",
            "Загружаю описание…", "説明を読み込んでいます…",
            "Закрыть", "閉じる",
            "Запрос прерван", "リクエストが中断されました",
            "Ищу последнюю версию DXVK…", "DXVK の最新バージョンを検索しています…",
            "Каталог", "カタログ",
            "Коллекции", "コレクション",
            "Коллекция:", "コレクション:",
            "Контрольная сумма архива не совпала — файл повреждён. Попробуйте ещё раз или выберите другую версию.",
                "アーカイブのチェックサムが一致しません — ファイルが破損しています。もう一度試すか、別のバージョンを選んでください。",
            "Контрольная сумма не подтвердилась, но архив цел — продолжаю установку.", "チェックサムは確認できませんでしたが、アーカイブは無事です — インストールを続行します。",
            "Контрольная сумма не совпала, скачиваю заново…", "チェックサムが一致しません。再ダウンロードしています…",
            "Копирую библиотеки DXVK в префикс…", "DXVK ライブラリをプレフィックスにコピーしています…",
            "Мод удалён из коллекции.", "MOD をコレクションから削除しました。",
            "Моды", "MOD",
            "Название коллекции модов:", "MOD コレクション名:",
            "Название копии:", "コピーの名前:",
            "Не найдена утилита tar.", "tar コマンドが見つかりません。",
            "Не удалось загрузить каталог: ", "カタログの読み込みに失敗しました: ",
            "Не удалось открыть папку:", "フォルダを開けませんでした:",
            "Не удалось распаковать архив: ", "アーカイブの展開に失敗しました: ",
            "Не удалось создать префикс Wine.", "Wine プレフィックスの作成に失敗しました。",
            "Не удалось создать ссылку на профиль модов: ", "MOD プロファイルへのリンクの作成に失敗しました: ",
            "Недавно обновлённые", "最近更新された",
            "Недопустимое имя папки в архиве.", "アーカイブ内のフォルダ名が不正です。",
            "Нельзя удалить единственную коллекцию.", "唯一のコレクションは削除できません。",
            "Ничего не найдено", "見つかりませんでした",
            "Новая коллекция", "新しいコレクション",
            "Новая коллекция модов", "新しい MOD コレクション",
            "Новое название:", "新しい名前:",
            "Обновить", "更新",
            "Обновить BepInEx", "BepInEx を更新",
            "Обновить всё", "すべて更新",
            "Обновить каталог", "カタログを更新",
            "Обновления", "アップデート",
            "Обновляю моды…", "MOD を更新しています…",
            "Основная", "デフォルト",
            "Открыть на Thunderstore", "Thunderstore で開く",
            "Отмена", "キャンセル",
            "Отменено.", "キャンセルしました。",
            "Ошибка", "エラー",
            "Папка модов", "MOD フォルダ",
            "Переименовать коллекцию", "コレクション名を変更",
            "По названию", "名前順",
            "Поиск мода…", "MOD を検索…",
            "Популярные", "人気",
            "Префикс Wine ещё не создан, а Wine не найден. Запустите игру один раз и повторите.",
                "Wine プレフィックスがまだ作成されておらず、Wine も見つかりません。一度ゲームを起動してから再試行してください。",
            "Проверяю контрольную сумму…", "チェックサムを確認しています…",
            "Проверяю целостность архива…", "アーカイブの整合性を確認しています…",
            "Распаковываю…", "展開しています…",
            "Сервер вернул неожиданный ответ", "サーバーから予期しない応答がありました",
            "Скачиваю BepInEx: ", "BepInEx をダウンロードしています: ",
            "Создаю префикс Wine…", "Wine プレフィックスを作成しています…",
            "У каждой коллекции свои моды и настройки. Активная коллекция подключается к игре при запуске.",
                "コレクションごとに独自の MOD と設定があります。起動時にはアクティブなコレクションがゲームに適用されます。",
            "Удалить", "削除",
            "Удалить коллекцию", "コレクションを削除",
            "Удалить мод из этой коллекции", "このコレクションから MOD を削除",
            "Устанавливаю BepInEx…", "BepInEx をインストールしています…",
            "Установить", "インストール",
            "Установить BepInEx", "BepInEx をインストール",
            "Установить BepInEx рядом с игрой сейчас? Лаунчер сам добавит нужные параметры запуска, чтобы игра запускалась с OnlineFix и модами.",
                "今すぐゲームの隣に BepInEx をインストールしますか?ランチャーが必要な起動パラメータを自動的に追加し、OnlineFix と MOD でゲームが起動するようにします。",
            "Установка BepInEx…", "BepInEx をインストール中…",
            "Установлено", "インストール済み",
            "не установлен", "未インストール",
            "обновлён ", "更新日 ",
            "установлен", "インストール済み",

            "DXVK {0} установлен в префикс игры.", "DXVK {0} をゲームのプレフィックスにインストールしました。",
            "DXVK установлен в префикс ({0}).", "DXVK はプレフィックスにインストールされています ({0})。",
            "GitHub ответил кодом {0}", "GitHub がコード {0} を返しました",
            "Активная коллекция: «{0}».", "アクティブなコレクション:「{0}」。",
            "Готово: {0} установлен в коллекцию «{1}».", "完了:{0} を「{1}」コレクションにインストールしました。",
            "Доступно версий: {0}", "利用可能なバージョン: {0}",
            "Игр: {0} · всего наиграно: {1}", "ゲーム数: {0}・合計プレイ時間: {1}",
            "Каталог загружен: модов — {0}.", "カタログを読み込みました:MOD 数 {0}。",
            "Ничего не найдено по запросу «{0}».", "「{0}」に一致するものが見つかりません。",
            "Обновлено модов: {0}.", "更新した MOD 数: {0}。",
            "Показано {0} из {1} — уточните поиск", "{1} 件中 {0} 件を表示中 — 検索条件を絞ってください",
            "Сегодня играем в «{0}»!", "今日は「{0}」をプレイしよう!",
            "Сервер ответил кодом {0}", "サーバーがコード {0} を返しました",
            "Скачиваю {0} ({1} из {2})…", "{0} をダウンロード中 ({1}/{2})…",
            "Скачиваю {0}: {1} МБ из {2} МБ", "{0} をダウンロード中: {1} MB / {2} MB",
            "Скачиваю {0}…", "{0} をダウンロード中…",
            "Удалить коллекцию «{0}»?", "「{0}」コレクションを削除しますか?",
            "Устанавливаю {0}…", "{0} をインストール中…",
            "Установка {0}", "{0} のインストール"
        ));
    }
}


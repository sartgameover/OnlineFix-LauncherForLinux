import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
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
        return List.of(a + "library_600x900.jpg", c + "library_600x900.jpg", a + "header.jpg", c + "header.jpg");
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

    /** Ссылка на лучшую вертикальную обложку (PNG/JPEG). path: «/grids/steam/220» или «/grids/game/45». */
    static String sgdbBestGrid(String key, String path) throws IOException {
        String[] queries = {"?dimensions=600x900,342x482,660x930&types=static&nsfw=false&humor=false", "?types=static&nsfw=false&humor=false"};
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
    enum Site { STEAM, STEAMDB, PROTONDB, SGDB }

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
            default:
                return Web.parseId(g.sgdbId) != 0
                    ? "https://www.steamgriddb.com/game/" + Web.parseId(g.sgdbId)
                    : "https://www.steamgriddb.com/search/grids?term=" + q;
        }
    }
}

/** Поиск, установка и выбор Proton / Wine. */
final class Proton {
    static final String SYSTEM_WINE = "Системный Wine";
    private static final String LEGACY_SYSTEM = "GE-Proton Latest (Системный)";
    private static final String API = "https://api.github.com/repos/GloriousEggroll/proton-ge-custom/releases/latest";
    private static final String UA = "OnlineFix-Linux-Launcher";

    private Proton() {}

    static String normalize(String s) {
        if (s == null || s.isBlank() || s.equals(LEGACY_SYSTEM)) return SYSTEM_WINE;
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

    /** Имя → папка с файлом `proton`. Папка из настроек имеет приоритет. */
    static Map<String, Path> discover(GlobalConfig c) {
        Map<String, Path> res = new LinkedHashMap<>();
        for (Path root : roots(c)) {
            File[] dirs = root.toFile().listFiles(File::isDirectory);
            if (dirs == null) continue;
            Arrays.sort(dirs, Comparator.comparing(File::getName, Comparator.reverseOrder()));
            for (File d : dirs) {
                if (new File(d, "proton").isFile()) res.putIfAbsent(d.getName(), d.toPath());
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
        Path a = protonDir.resolve("files/bin/wine");
        if (Files.isExecutable(a)) return a;
        Path b = protonDir.resolve("dist/bin/wine");
        return Files.isExecutable(b) ? b : null;
    }

    // ---------- установка последней GE-Proton ----------

    static final class Release {
        String tag = "", tarName, tarUrl, shaUrl;
        long tarSize;
    }

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private static boolean trustedHost(String url) {
        try {
            String h = new URI(url).getHost();
            return h != null && "https".equals(new URI(url).getScheme())
                && (h.equals("github.com") || h.endsWith(".github.com")
                    || h.endsWith(".githubusercontent.com"));
        } catch (Exception e) {
            return false;
        }
    }

    static Release parseRelease(String json) throws IOException {
        Map<String, Object> rel = Json.asMap(Json.parse(json));
        Release r = new Release();
        r.tag = Json.str(rel, "tag_name", "");
        for (Object o : Json.asList(rel.get("assets"))) {
            Map<String, Object> a = Json.asMap(o);
            String name = Json.str(a, "name", "");
            String url = Json.str(a, "browser_download_url", "");
            if (!SAFE_NAME.matcher(name).matches() || !trustedHost(url)) continue;
            if (name.endsWith(".tar.gz") && r.tarUrl == null) {
                r.tarName = name;
                r.tarUrl = url;
                r.tarSize = Json.lng(a, "size", 0);
            } else if (name.endsWith(".sha512sum")) {
                r.shaUrl = url;
            }
        }
        if (r.tarUrl == null) throw new IOException("В последнем релизе не найден архив .tar.gz");
        return r;
    }

    private static HttpRequest get(String url) {
        return HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", UA)
            .header("Accept", "application/vnd.github+json, application/octet-stream, */*")
            .timeout(java.time.Duration.ofSeconds(60))
            .GET().build();
    }

    /** Скачивает и распаковывает последнюю GE-Proton. Возвращает имя установленной папки. */
    static String installLatestGe(Path root, Consumer<String> status, DoubleConsumer progress,
                                  BooleanSupplier cancelled) throws Exception {
        if (Sys.which("tar") == null) throw new IOException("Не найдена утилита tar.");
        HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(15)).build();

        status.accept("Запрашиваю информацию о последней версии GE-Proton…");
        HttpResponse<String> resp = http.send(get(API), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new IOException("GitHub ответил кодом " + resp.statusCode());
        Release rel = parseRelease(resp.body());
        String folder = rel.tarName.substring(0, rel.tarName.length() - ".tar.gz".length());

        Files.createDirectories(root);
        if (Files.isRegularFile(root.resolve(folder).resolve("proton"))) {
            status.accept(folder + " уже установлен.");
            progress.accept(1.0);
            return folder;
        }

        Path tmp = root.resolve(rel.tarName + ".part");
        boolean ok = false;
        try {
            status.accept("Скачиваю " + rel.tarName + "…");
            HttpResponse<InputStream> r = http.send(get(rel.tarUrl), HttpResponse.BodyHandlers.ofInputStream());
            if (r.statusCode() != 200) throw new IOException("Сервер ответил кодом " + r.statusCode());
            long total = r.headers().firstValueAsLong("Content-Length").orElse(rel.tarSize);
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0, lastUi = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (cancelled.getAsBoolean()) throw new CancellationException("Отменено");
                    out.write(buf, 0, n);
                    md.update(buf, 0, n);
                    done += n;
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 150) {
                        lastUi = now;
                        if (total > 0) progress.accept(Math.min(0.98, (double) done / total));
                        status.accept(String.format(Locale.ROOT, "Скачиваю %s: %d МБ%s",
                            rel.tarName, done / 1048576, total > 0 ? " из " + total / 1048576 + " МБ" : ""));
                    }
                }
            }

            if (rel.shaUrl != null) {
                status.accept("Проверяю контрольную сумму…");
                HttpResponse<String> sr = http.send(get(rel.shaUrl), HttpResponse.BodyHandlers.ofString());
                if (sr.statusCode() == 200) {
                    String expected = sr.body().trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
                    StringBuilder hex = new StringBuilder();
                    for (byte b : md.digest()) hex.append(String.format("%02x", b));
                    if (!hex.toString().equals(expected)) throw new IOException("Контрольная сумма архива не совпала — файл повреждён.");
                }
            }

            status.accept("Распаковываю…");
            progress.accept(0.99);
            ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", tmp.toString(), "-C", root.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) throw new IOException("Не удалось распаковать архив: " + out.trim());
            if (!Files.isRegularFile(root.resolve(folder).resolve("proton")))
                throw new IOException("После распаковки не найдена папка " + folder);
            ok = true;
            progress.accept(1.0);
            return folder;
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
            if (!ok) status.accept("Установка не завершена.");
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
        Path protonDir;   // null — системный Wine
    }

    interface Listener {
        void log(String line);
        void epicUrl(String url);
        /** Ссылка входа Microsoft из лога игры (необязательный метод). */
        default void msUrl(String url) {}
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
        return Proton.isSystem(g.protonVersion) ? p : p.resolve("pfx");
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
        } else if (!Proton.isSystem(g.protonVersion)) {
            Path pd = Proton.resolve(c, g.protonVersion);
            if (pd == null) throw new LaunchException("Proton «" + g.protonVersion
                + "» не найден. Установите его во вкладке «Proton / Wine» в настройках игры.");
            p.protonDir = pd;
            p.command.add(pd.resolve("proton").toString());
            p.command.add("run");
            p.command.add(exe.toString());
            p.env.put("STEAM_COMPAT_DATA_PATH", p.prefixDir.toString());
            p.env.put("STEAM_COMPAT_CLIENT_INSTALL_PATH", Sys.steamRoot().toString());
        } else {
            String wine = Sys.which("wine");
            if (wine == null) throw new LaunchException("Wine не найден в системе.\n"
                + "Установите wine или выберите GE-Proton во вкладке «Proton / Wine».");
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

        if (g.dllOverrides != null && !g.dllOverrides.isEmpty())
            p.env.put("WINEDLLOVERRIDES", String.join(";", g.dllOverrides));

        if (g.useWine3D) {
            p.env.put("PROTON_USE_WINED3D", "1");
        } else {
            p.env.put("PROTON_USE_WINED3D", "0");
            p.env.put("DXVK_ASYNC", "1");
        }
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
        """;

    // ---- состояние ----
    private List<Game> games = new ArrayList<>();
    private GlobalConfig config = new GlobalConfig();
    private final Map<String, Runner.Session> running = new HashMap<>();
    private final Map<String, List<String>> eosCache = new HashMap<>();
    private final Map<String, List<String>> msCache = new HashMap<>();
    private final Map<String, Web.Rating> ratings = new HashMap<>();
    private final Set<String> ratingRequested = new HashSet<>();
    private final Map<String, Image> imageCache = new HashMap<>();

    private static final List<String> SORT_LABELS = List.of("По названию", "Недавно запускались", "По времени игры");
    private static final List<String> SIZE_LABELS = List.of("Маленькие", "Средние", "Большие");
    private static final List<String> ACCENT_LABELS = List.of("Сиреневый", "Синий", "Зелёный", "Персиковый", "Розовый", "Бирюзовый");

    private record CoverJob(Game g, String name, String steam, String sgdb) {}
    private Stage primaryStage;
    private String themeUrl = "";
    private Game selected;

    // ---- элементы ----
    private FlowPane grid;
    private Label emptyLabel;
    private TextField searchField;
    private ScrollPane sideScroll;
    private StackPane coverHolder;
    private Label spTitle, spPlaytime, spLast, spRating, spNotice, spEpicStatus;
    private ComboBox<String> sortBox;
    private CheckBox cbEpic, cbMs;
    private Label spMsStatus;
    private Button btnMsLogin;
    private Button btnPlay, btnStop, btnConsole, btnEpicLogin;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        config = Store.loadConfig();
        games = Store.loadGames();
        themeUrl = Store.writeTheme(buildThemeCss(), config.accent);

        BorderPane root = new BorderPane();
        root.setTop(buildTopBar());
        root.setCenter(buildLibrary());
        sideScroll = buildSidePanel();
        sideScroll.setVisible(false);
        sideScroll.setManaged(false);
        root.setRight(sideScroll);
        refreshGrid();

        Scene scene = new Scene(root, 1180, 760);
        if (!themeUrl.isEmpty()) scene.getStylesheets().add(themeUrl);
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.setOnCloseRequest(ev -> saveRunningTime());
        stage.show();

        if (Store.lastError != null) showAlert(Alert.AlertType.WARNING, "Библиотека", Store.lastError);
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
        Label brand = styled("OFLL", "ofll-brand");
        brand.setWrapText(false);
        Label heading = styled("Библиотека", "ofll-title");
        heading.setWrapText(false);
        searchField = new TextField();
        searchField.setPromptText("Поиск по названию…");
        searchField.setPrefWidth(240);
        searchField.textProperty().addListener((obs, oldV, newV) -> refreshGrid());
        sortBox = new ComboBox<>(FXCollections.observableArrayList(SORT_LABELS));
        sortBox.setValue(SORT_LABELS.get(Math.max(0, GlobalConfig.SORT_MODES.indexOf(config.sortMode))));
        sortBox.setOnAction(ev -> {
            int i = SORT_LABELS.indexOf(sortBox.getValue());
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
        emptyLabel = new Label();
        emptyLabel.getStyleClass().add("ofll-empty");
        emptyLabel.setTextAlignment(TextAlignment.CENTER);
        emptyLabel.setMouseTransparent(true);
        return new StackPane(scroll, emptyLabel);
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
        chips.setPrefWrapLength(150);
        spTitle.setMaxWidth(166);
        spRating.setMaxWidth(166);
        VBox titleBox = new VBox(10, spTitle, chips);
        titleBox.setAlignment(Pos.TOP_LEFT);
        titleBox.setPrefWidth(166);
        titleBox.setMaxWidth(166);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        HBox head = new HBox(14, coverHolder, titleBox);
        head.setAlignment(Pos.TOP_LEFT);

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

        // --- сайты: миниатюры-кнопки ---
        FlowPane sites = new FlowPane(10, 10,
            captioned(iconButton("S", "tile-steam", "Страница игры в Steam", () -> openSite(Links.Site.STEAM)), "Steam"),
            captioned(iconButton("DB", "tile-steamdb", "Страница игры на SteamDB", () -> openSite(Links.Site.STEAMDB)), "SteamDB"),
            captioned(iconButton("P", "tile-protondb", "Страница игры на ProtonDB", () -> openSite(Links.Site.PROTONDB)), "ProtonDB"),
            captioned(iconButton("G", "tile-sgdb", "Обложки игры на SteamGridDB", () -> openSite(Links.Site.SGDB)), "SGDB"));

        // --- инструменты: миниатюры-кнопки ---
        btnConsole = iconButton(">_", "tile-tool", "Запуск с консолью (отладка)", () -> onPlay(true));
        FlowPane tools = new FlowPane(10, 10,
            captioned(iconButton("⚙", "tile-settings", "Настройки игры", this::openSettingsDialog), "Настройки"),
            captioned(iconButton("W", "tile-tool", "Префикс Wine (Winetricks)", this::launchWinetricks), "Winetricks"),
            captioned(btnConsole, "Консоль"),
            captioned(iconButton("↻", "tile-tool", "Найти Steam AppID, обложку и рейтинг ProtonDB", () -> {
                if (selected != null) enrichAsync(selected, true);
            }), "Обновить"));

        VBox panel = new VBox(14, head, spNotice, btnPlay, btnStop, epicBox, msBox,
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
            case "small": return 120;
            case "large": return 190;
            default: return 150;
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
            grid.getChildren().add(buildCard(g));
            shown++;
        }
        emptyLabel.setVisible(shown == 0);
        emptyLabel.setText(games.isEmpty()
            ? "Библиотека пуста.\nНажмите «+ Добавить игру» и выберите папку с игрой."
            : "Ничего не найдено по запросу «" + searchField.getText().trim() + "».");
    }

    private Node buildCard(Game g) {
        double w = cardWidth(), h = Math.round(w * 1.5);
        double iw = w - 6, ih = h - 6;
        StackPane card = new StackPane();
        card.setMinSize(w, h);
        card.setPrefSize(w, h);
        card.setMaxSize(w, h);
        card.setPadding(new Insets(3));
        card.getStyleClass().add("ofll-card");
        if (g == selected) card.getStyleClass().add("ofll-card-selected");

        StackPane inner = new StackPane();
        inner.setMinSize(iw, ih);
        inner.setPrefSize(iw, ih);
        inner.setMaxSize(iw, ih);
        Rectangle clip = new Rectangle(iw, ih);
        clip.setArcWidth(22);
        clip.setArcHeight(22);
        inner.setClip(clip);

        Label name = new Label(g.name);
        name.setWrapText(true);
        name.setMaxWidth(iw - 20);
        name.getStyleClass().add("ofll-card-name");
        Label meta = new Label(g.playtimeSeconds <= 0 ? "Не запускалась" : Sys.formatPlaytime(g.playtimeSeconds));
        meta.getStyleClass().add("ofll-card-meta");
        VBox shade = new VBox(2, name, meta);
        shade.getStyleClass().add("ofll-card-shade");
        shade.setMaxHeight(Region.USE_PREF_SIZE);
        shade.setAlignment(Pos.BOTTOM_LEFT);
        StackPane.setAlignment(shade, Pos.BOTTOM_CENTER);

        HBox badges = new HBox(6);
        badges.setPadding(new Insets(8));
        badges.setMaxHeight(Region.USE_PREF_SIZE);
        badges.setPickOnBounds(false);
        StackPane.setAlignment(badges, Pos.TOP_LEFT);
        if (g.favorite) badges.getChildren().add(badge("★", "ofll-badge-fav"));
        if (running.containsKey(g.id)) badges.getChildren().add(badge("● Играет", "ofll-badge-run"));

        inner.getChildren().addAll(buildCover(g, iw, ih), shade, badges);
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

    /** Обложка заданного размера: картинка обрезается по центру до пропорций 2:3. */
    private Node buildCover(Game g, double w, double h) {
        StackPane p = new StackPane();
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        Image img = loadCover(g, h);
        if (img != null) {
            ImageView iv = new ImageView(img);
            double iw = img.getWidth(), ih = img.getHeight();
            double vw = Math.min(iw, ih * (w / h));
            iv.setViewport(new Rectangle2D((iw - vw) / 2, 0, vw, ih));
            iv.setPreserveRatio(true);
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

    private Image loadCover(Game g, double h) {
        if (g.coverPath == null || g.coverPath.isBlank()) return null;
        Path cp = Paths.get(g.coverPath);
        if (!Files.isRegularFile(cp)) return null;
        long mt = 0;
        try { mt = Files.getLastModifiedTime(cp).toMillis(); } catch (IOException ignored) {}
        String key = cp + "@" + mt + "@" + (int) h;
        Image img = imageCache.get(key);
        if (img == null) {
            img = new Image(cp.toUri().toString(), 0, Math.max(1, h * 2), true, true, false);
            if (img.isError() || img.getWidth() <= 0) return null;
            if (imageCache.size() > 200) imageCache.clear();
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
        selected = g;
        refreshGrid();
        updatePanel();
    }

    private void updatePanel() {
        Game g = selected;
        sideScroll.setVisible(g != null);
        sideScroll.setManaged(g != null);
        if (g == null) return;
        coverHolder.getChildren().setAll(buildCover(g, 110, 165));
        spTitle.setText(g.name);
        spPlaytime.setText(g.playtimeSeconds <= 0 ? "Ещё не запускалась" : "Наиграно: " + Sys.formatPlaytime(g.playtimeSeconds));
        boolean hasLast = g.lastPlayed > 0;
        spLast.setText(hasLast ? "Запуск: " + Sys.formatAgo(g.lastPlayed, System.currentTimeMillis() / 1000) : "");
        spLast.setVisible(hasLast);
        spLast.setManaged(hasLast);
        boolean run = running.containsKey(g.id);
        btnPlay.setDisable(run);
        btnPlay.setText(run ? "Игра запущена…" : "▶  Играть");
        btnConsole.setDisable(run);
        btnStop.setVisible(run);
        btnStop.setManaged(run);
        refreshEpicStatus();
        refreshMsStatus();
        refreshRating();
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
        boolean needSteam = kind != Links.Site.SGDB && Web.parseId(g.steamStoreId) == 0;
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
        List<CoverJob> jobs = new ArrayList<>();
        for (Game g : games)
            if (g.coverPath == null || g.coverPath.isBlank()) jobs.add(new CoverJob(g, g.name, g.steamStoreId, g.sgdbId));
        if (jobs.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "Обложки", "У всех игр в библиотеке уже есть обложки.");
            return;
        }
        Stage st = new Stage(StageStyle.UTILITY);
        st.initOwner(activeWindow());
        st.initModality(Modality.WINDOW_MODAL);
        st.setTitle("Поиск обложек");
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
        Path root = dir.toPath();
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
        CompletableFuture.supplyAsync(() -> List.of(Epic.findEosFiles(root), Microsoft.findFiles(root)))
            .thenAccept(res -> Platform.runLater(() -> {
                List<String> eos = res.get(0), ms = res.get(1);
                eosCache.put(g.id, eos);
                msCache.put(g.id, ms);
                g.eosChecked = true;
                g.eosEnabled = !eos.isEmpty();
                g.msChecked = true;
                g.msEnabled = !ms.isEmpty();
                Store.saveGames(games);
                if (selected == g) {
                    updatePanel();
                    if (!eos.isEmpty() && !ms.isEmpty())
                        setNotice("Найдены Epic Online Services и Xbox / Microsoft — поддержка обоих включена. Отключить можно переключателями ниже.");
                    else if (!eos.isEmpty())
                        setNotice("Найден Epic Online Services — поддержка Epic включена. Отключить можно переключателем ниже.");
                    else if (!ms.isEmpty())
                        setNotice("Найдены файлы Xbox / Microsoft — вход через аккаунт Microsoft включён. Отключить можно переключателем ниже.");
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
        startGame(g, withConsole);
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
    //  Установка GE-Proton
    // ---------------------------------------------------------------

    private void showInstallDialog(Consumer<String> onInstalled) {
        Stage st = new Stage(StageStyle.UTILITY);
        st.initOwner(activeWindow());
        st.initModality(Modality.WINDOW_MODAL);
        st.setTitle("Установка GE-Proton");

        Label title = styled("Установка последней версии GE-Proton", "ofll-section");
        Label status = styled("Подготовка…", "ofll-muted");
        ProgressBar bar = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setPrefWidth(400);
        Button cancel = button("Отмена", "btn-surface");
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancel.setOnAction(ev -> {
            cancelled.set(true);
            cancel.setDisable(true);
            status.setText("Отменяю…");
        });
        st.setOnCloseRequest(ev -> cancelled.set(true));

        VBox box = new VBox(14, title, status, bar, cancel);
        box.setPadding(new Insets(20));
        box.setAlignment(Pos.CENTER_LEFT);
        Scene sc = new Scene(box, 460, 190);
        if (!themeUrl.isEmpty()) sc.getStylesheets().add(themeUrl);
        st.setScene(sc);
        st.show();

        Path root = Paths.get(config.protonPath);
        Thread t = new Thread(() -> {
            try {
                String name = Proton.installLatestGe(root,
                    m -> Platform.runLater(() -> status.setText(m)),
                    p -> Platform.runLater(() -> bar.setProgress(p)),
                    cancelled::get);
                Platform.runLater(() -> {
                    st.close();
                    onInstalled.accept(name);
                });
            } catch (CancellationException ce) {
                Platform.runLater(st::close);
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    st.close();
                    showAlert(Alert.AlertType.ERROR, "Не удалось установить Proton", String.valueOf(ex.getMessage()));
                });
            }
        }, "proton-install");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------
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
        Button btnInstall = button("Скачать последнюю GE-Proton", "btn-blue");
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
            styled("Steam и сеть", "ofll-section"), appIdRow, cbNet,
            new Separator(),
            styled("Переопределения библиотек (WINEDLLOVERRIDES)", "ofll-section"), dllList, dllControls);
        steamEpic.setPadding(new Insets(16));
        ScrollPane steamScroll = new ScrollPane(steamEpic);
        steamScroll.setFitToWidth(true);
        Tab tabSteam = tab("Epic, Microsoft и Steam", steamScroll);

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
            styled("Он нужен для кнопок-миниатюр (Steam, SteamDB, ProtonDB, SGDB), обложки и рейтинга. На запуск игры он не влияет: для запуска есть отдельная настройка «Передавать SteamAppId» на вкладке «Epic, Microsoft и Steam».", "ofll-hint"),
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
        stage.show();
    }

    // ---------------------------------------------------------------
    //  Настройки лаунчера (общие для всех игр)
    // ---------------------------------------------------------------

    private void openLauncherSettings() {
        Stage stage = new Stage();
        stage.initOwner(primaryStage);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Настройки лаунчера");

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

        // --- внешний вид ---
        ComboBox<String> cbSize = new ComboBox<>(FXCollections.observableArrayList(SIZE_LABELS));
        cbSize.setValue(SIZE_LABELS.get(Math.max(0, GlobalConfig.CARD_SIZES.indexOf(config.cardSize))));
        ComboBox<String> cbAccent = new ComboBox<>(FXCollections.observableArrayList(ACCENT_LABELS));
        cbAccent.setValue(ACCENT_LABELS.get(Math.max(0, GlobalConfig.ACCENTS.indexOf(config.accent))));
        ComboBox<String> cbSort = new ComboBox<>(FXCollections.observableArrayList(SORT_LABELS));
        cbSort.setValue(SORT_LABELS.get(Math.max(0, GlobalConfig.SORT_MODES.indexOf(config.sortMode))));
        GridPane look = new GridPane();
        look.setHgap(12);
        look.setVgap(10);
        look.add(new Label("Размер карточек"), 0, 0);
        look.add(cbSize, 1, 0);
        look.add(new Label("Цвет акцента"), 0, 1);
        look.add(cbAccent, 1, 1);
        look.add(new Label("Порядок игр"), 0, 2);
        look.add(cbSort, 1, 2);

        // --- поведение ---
        CheckBox cbMin = new CheckBox("Сворачивать лаунчер, когда запускается игра");
        cbMin.setSelected(config.minimizeOnLaunch);

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
            cbAuto, cbRating, privacy, btnAll,
            new Separator(),
            styled("Внешний вид", "ofll-section"), look,
            new Separator(),
            styled("Поведение", "ofll-section"), cbMin,
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
            config.cardSize = GlobalConfig.CARD_SIZES.get(Math.max(0, SIZE_LABELS.indexOf(cbSize.getValue())));
            config.accent = GlobalConfig.ACCENTS.get(Math.max(0, ACCENT_LABELS.indexOf(cbAccent.getValue())));
            config.sortMode = GlobalConfig.SORT_MODES.get(Math.max(0, SORT_LABELS.indexOf(cbSort.getValue())));
            config.minimizeOnLaunch = cbMin.isSelected();
            Store.saveConfig(config);
            if (!config.accent.equals(oldAccent)) reloadTheme();
            sortBox.setValue(SORT_LABELS.get(GlobalConfig.SORT_MODES.indexOf(config.sortMode)));
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
        stage.show();
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
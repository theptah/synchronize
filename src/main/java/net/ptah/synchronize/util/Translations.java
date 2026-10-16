package net.ptah.synchronize.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.ptah.synchronize.Synchronize;
import net.ptah.synchronize.config.ModConfig;

public final class Translations {
    public static final String DEFAULT_LANGUAGE = "en_us";
    private static final String LANG_PATH = "/assets/" + Synchronize.MOD_ID + "/lang/";
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private Translations() {
    }

    public static String serverLanguage() {
        ModConfig config = Synchronize.getConfig();
        if (config == null || config.language == null || config.language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        return config.language.trim().toLowerCase(Locale.ROOT);
    }

    public static String pattern(String language, String key) {
        String value = load(language).get(key);
        if (value == null && !DEFAULT_LANGUAGE.equals(language)) {
            value = load(DEFAULT_LANGUAGE).get(key);
        }
        return value != null ? value : key;
    }

    public static String translate(String language, String key, Object... args) {
        String value = pattern(language, key);
        if (args == null || args.length == 0) {
            return value.replace("%%", "%");
        }
        Object[] plainArgs = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            plainArgs[i] = args[i] instanceof Text text ? text.getString() : args[i];
        }
        try {
            return String.format(Locale.ROOT, value, plainArgs);
        } catch (IllegalFormatException e) {
            return value;
        }
    }

    public static String server(String key, Object... args) {
        return translate(serverLanguage(), key, args);
    }

    public static MutableText literal(String key, Object... args) {
        return Text.literal(server(key, args));
    }

    public static MutableText player(String key, Object... args) {
         
        //? if >=1.19.4 {
        return Text.translatableWithFallback(key, pattern(serverLanguage(), key), args);
        //?} else
        /*return Text.literal(translate(serverLanguage(), key, args));*/
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Map<String, String> load(String language) {
        return CACHE.computeIfAbsent(language, code -> {
            try (InputStream in = Translations.class.getResourceAsStream(LANG_PATH + code + ".json")) {
                if (in == null) {
                    return Collections.emptyMap();
                }
                try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    Map<String, String> values = GSON.fromJson(reader, MAP_TYPE);
                    return values != null ? values : Collections.emptyMap();
                }
            } catch (Exception e) {
                Synchronize.LOGGER.warn("[Synchronize] Failed to load language file {}", code, e);
                return Collections.emptyMap();
            }
        });
    }
}

import org.jruby.embed.ScriptingContainer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

public class GrubbyjarMain {
    static final String GRUBBYJAR_MAIN_RB = "grubbyjar_main.rb";
    public static final String GRUBBYJAR_JAR_PRELOAD_LIST
            = ".grubbyjar_jar_preload_list";
    private static final String URI_CLASSLOADER = "uri:classloader://";

    public static void main(String[] args)
    throws Exception
    {
        ScriptingContainer s = new ScriptingContainer();

        disabledSharedGems(s);

        populateLoadedJarList(s);

        s.setArgv(args);
        InputStream main = getResource(GRUBBYJAR_MAIN_RB);
        if (main == null) {
            throw new RuntimeException(GRUBBYJAR_MAIN_RB + " not found in jar");
        }
        Object returnValue = s.runScriptlet(main,
                URI_CLASSLOADER + GRUBBYJAR_MAIN_RB);
        if (returnValue == null)
            returnValue = Long.valueOf(0);
        System.exit((int)(long)returnValue);
    }

    static InputStream getResource(String name) {
        return GrubbyjarMain.class.getResourceAsStream(name);
    }

    /**
     * {@code require "ext/foo.jar"}, {@code require "ext/foo"}, and
     * {@code require_relative "ext/foo"} can all refer to jars. ShadowJar
     * automatically unpacks them when building the jars, so the contents are on
     * the classpath but the files don’t exist anymore. To prevent such requires
     * from crashing a program, the shadow jar contains a list of jars repacked
     * into the shadow jar. These jars are added to {@coe $LOADED_FEATURES} on
     * startup.
     */
    static void populateLoadedJarList(ScriptingContainer s) {
        List<String> jars = loadPreloadFile();
        if (jars == null)
            return;

        for (int i = 0; i < jars.size(); i++) {
            jars.set(i, URI_CLASSLOADER + jars.get(i));
        }

        s.runScriptlet("def _grubbyjar_preload(list)\n"
                + "  list.each do |item|\n"
                + "    $LOADED_FEATURES << item\n"
                + "  end\n"
                + "end\n");
        s.callMethod(null, "_grubbyjar_preload", jars);
    }

    private static List<String> loadPreloadFile() {
        InputStream preloadStream = getResource(GRUBBYJAR_JAR_PRELOAD_LIST);
        if (preloadStream == null)
            return null;

        BufferedReader b = new BufferedReader(
                new InputStreamReader(preloadStream, UTF_8));
        String line = null;
        List<String> jars = new ArrayList<>();
        try {
            while ((line = b.readLine()) != null) {
                jars.add(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return jars;
    }

    @SuppressWarnings("unchecked")
    private static void disabledSharedGems(ScriptingContainer s) {
        s.getEnvironment().put("GEM_PATH", "");
        s.getEnvironment().put("RUBYLIB", "");
    }
}

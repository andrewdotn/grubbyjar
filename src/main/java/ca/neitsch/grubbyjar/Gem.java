package ca.neitsch.grubbyjar;

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTreeElement;
import org.jruby.RubyArray;
import org.jruby.embed.ScriptingContainer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

import static ca.neitsch.grubbyjar.Exceptionable.rethrowing;

public class Gem {
    private static final String DETERMINE_GEM_FILES_RB = "determine_gem_files.rb";
    public static final String EXECUTABLE = "executable";
    public static final String FILES = "files";
    public static final String FULL_NAME = "full_name";
    public static final String GEMSPEC = "gemspec";
    public static final String INSTALL_PATH = "install_path";
    public static final String NAME = "name";
    public static final String SPEC_TEXT = "spec_text";
    public static final String VERSION = "version";

    private Map<String, Object> _hash;
    private Set<String> _files;
    private Set<String> _directories;

    Gem(Map<String, Object> h) {
        _hash = h;
    }

    public String getInstallPath() {
        return (String)_hash.get(INSTALL_PATH);
    }

    public String getExecutable() {
        Object executable = _hash.get("executable");
        if (executable == null)
            return null;
        return (String)executable;
    }

    public String getFullName() {
        return (String)_hash.get(FULL_NAME);
    }

    public String getGemspec() {
        return (String)_hash.get(GEMSPEC);
    }

    public String getTargetDir() {
        return "gems/" + getFullName();
    }

    // If the gem has a list of files to include, we have to include only those
    // files, but also return true on including any parent directories,
    // otherwise pruning will prevent the target files from ever being
    // considered.
    boolean include(FileTreeElement e) {
        if (e.getRelativePath().getLastName().equals("MANIFEST.MF"))
            return false;
        if (getFiles() == null)
            return true;

        // The current version of shadow has a bug where getPath() returns the
        // name and getName() returns the path. We can’t rely on that behaviour
        // in case it gets fixed. Fortunately both are also accessible through
        // the RelativePath object.
        String path = e.getRelativePath().getPathString();
        if (getDirectories().contains(path))
            return true;
        return getFiles().contains(path);
    }

    public Set<String> getFiles() {
        if (_files != null)
            return _files;
        Object filesObj = _hash.get(FILES);
        if (filesObj == null)
            return null;
        String[] files = fromRubyArrayMaybe(filesObj);

        Set<String> directories = Sets.newHashSet();
        for (String f: files) {
            while (f.contains("/")) {
                f = f.substring(0, f.lastIndexOf("/"));
                directories.add(f);
            }
        }

        _files = Sets.newHashSet(files);
        _directories = directories;
        return _files;
    }

    private static String[] fromRubyArrayMaybe(Object stringArray) {
        if (stringArray instanceof String[]) {
            return (String[])stringArray;
        }

        RubyArray a = (RubyArray)stringArray;
        int l = a.getLength();
        String ret[] = new String[l];
        for (int i = 0; i < l; i++) {
            ret[i] = (String)a.get(i);
        }
        return ret;
    }

    public Set<String> getDirectories() {
        getFiles();
        return _directories;
    }

    static List<Gem> loadGemDeps(Project project) {
        File gemfile = project.file("Gemfile");
        if (!gemfile.exists())
            return Collections.emptyList();

        File gemfileLock = project.file("Gemfile.lock");
        if (!gemfileLock.exists()) {
            throw new GradleException("Gemfile exists but Gemfile.lock does not; please run `bundle install`.");
        }

        ScriptingContainer s = new ScriptingContainer();

        s.runScriptlet(
                Gem.class.getResourceAsStream(DETERMINE_GEM_FILES_RB),
                DETERMINE_GEM_FILES_RB);

        RubyArray gems = (RubyArray)s.callMethod(null, "determine_gem_files",
                gemfile.toString(), gemfileLock.toString());

        List<Gem> gemList = Lists.newArrayList();
        for (Object o: gems) {
            addNewGemToList(gemList, o);
        }
        return gemList;
    }

    // Separate method purely to isolate unchecked suppression
    @SuppressWarnings("unchecked")
    private static void addNewGemToList(List<Gem> list, Object o) {
        list.add(new Gem((Map<String, Object>)o));
    }

    void configure(ShadowJar jar, File workDir)
    {
        String gemspec;
        if (_hash.containsKey(SPEC_TEXT)) {
            String specText = (String)_hash.get(SPEC_TEXT);
            File specDir = new File(workDir, "specifications");
            specDir.mkdir();
            File output = new File(specDir, getFullName() + ".gemspec");
            rethrowing(() ->
                    Files.write(output.toPath(), specText.getBytes(StandardCharsets.UTF_8)));
        } else {
            gemspec = getGemspec();

            jar.from(gemspec,
                    copyspec -> copyspec.into("specifications"));
        }

        jar.from(getInstallPath(),
                (copyspec) -> {
                    copyspec.into(getTargetDir());
                    copyspec.include(this::include);
                });
    }

    List<String> getJarDeps(ShadowJar jar)
    {
        ConfigurableFileTree tree = jar.getProject().fileTree(getInstallPath(),
                s -> s.include(this::include));

        List<String> jars = Lists.newArrayList();
        tree.visit(f -> {
            if (f.getName().endsWith(".jar")) {
                LoadServiceCheck c = new LoadServiceCheck(f.getFile());

                for (String path: getJarLoadPathPossibilities(f)) {
                    if (c.containsLoadServiceFor(path)) {
                        // Don’t add to preload list, because extra magic has to
                        // happen at runtime.
                        continue;
                    }

                    jars.add(path);
                }
            }
        });
        return jars;
    }

    List<String> getJarLoadPathPossibilities(FileTreeElement e) {
        Set<String> possibilities = new HashSet<>();

        for (String prefix: new String[] {
                "uri:classloader:/" + getTargetDir() + "/",
                "uri:classloader://" + getTargetDir() + "/",
                ""
        }) {
            String path = e.getRelativePath().getParent().toString();
            for (String middle: new String[] {
                    path,
                    withoutLibPrefix(path)
            }) {
                if (!middle.equals(path) && !prefix.equals("")) {
                    // Dropping something in the middle of an absolute path is
                    // not valid
                    continue;
                }
                String lastName = e.getRelativePath().getLastName();
                for (String end: new String[] {
                        lastName,
                        withoutJarSuffix(lastName)
                }) {
                    String fullPath = prefix + middle
                            + (middle.isEmpty()? "" : "/") + end;
                    possibilities.add(fullPath);
                }
            }
        }
        List<String> ret = Lists.newArrayList(possibilities);
        ret.sort(String::compareTo);
        return ret;
    }

    private String withoutLibPrefix(String path) {
        if (path.equals("lib"))
            return "";
        return path.replaceAll("^lib/", "");
    }

    private String withoutJarSuffix(String path) {
        return path.replaceAll("\\.jar$", "");
    }
}

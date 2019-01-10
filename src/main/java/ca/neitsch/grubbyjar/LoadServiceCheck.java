package ca.neitsch.grubbyjar;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.jruby.runtime.load.LoadService;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 *  {@link LoadService} is a jruby mechanism to allow a jruby {@code require} to
 *  call a java class that uses jruby APIs to define modules, classes, and so
 *  on, instead of the usual effect of adding a jar to the classpath. Normally
 *  when a jar is packed into the grubbyjar, all of the names under which a jar
 *  might be required are added to {@code $LOADED_FEATURES} so that the
 *  now-unnecessary {@code require} doesn’t raise an exception at run time when
 *  the jar file no longer exists. But this breaks {@code LoadService} jars,
 *  where the code actually needs to run.
 *
 *  As an example, {@code puma} contains {@code lib/puma/puma_http11.jar}.
 *  {@code require "puma/puma_http1"} would normally add that jar to the
 *  classpath. But it <i>actually</i> does that <i>and</i> runs the {@code
 *  basicLoad} method of {@code puma/PumaHttp11Service.class}. This means that
 *  {@code "puma/puma_http1"} must be kept off the preload list because the
 *  require must happen at run time.
 *
 * This class enables checking short require names against a jar to see if there
 * is a {@code LoadService} class exists that precludes putting the short name
 * on the preload list.
 */
public class LoadServiceCheck {
    private File _jarFile;
    private ClassLoader _classLoader;

    public LoadServiceCheck(File jarFile) {
        _jarFile = jarFile;
    }

    public boolean containsLoadServiceFor(String path) {
        if (path.endsWith(".jar"))
            path = path.replaceFirst("\\.jar$", "");

        for (String name: javaClassNames(path)) {
            // Note that ClassLoader.getResource() requires an absolute path not
            // starting with ‘/’, while Class.getResource() prepends the package
            // name unless the path start with a ‘/’, in which case
            // Class.getResource() strips the leading ‘/’ before calling
            // ClassLoader.getResource().
            if (getClassLoader().getResource(name + ".class") != null) {
                return true;
            }
        }

        return false;
    }

    // It’s ok for this method to produce bogus names, because the classloader
    // will simply reject them.
    List<String> javaClassNames(String path) {
        List<String> ret = Lists.newArrayList();

        List<String> pieces = Lists.newArrayList(path.split("/"));

        pieces.set(pieces.size() - 1, camelize(pieces.get(pieces.size() - 1)));

        for (int i = 0; i < pieces.size(); i++) {
            ret.add(Joiner.on("/").join(pieces.subList(i, pieces.size())) + "Service");
        }

        return ret;
    }

    private String camelize(String snakeCaseString) {
        String[] pieces = snakeCaseString.split("_");
        for (int i = 0; i < pieces.length; i++) {
            pieces[i] = Character.toUpperCase(pieces[i].charAt(0))
                    + pieces[i].substring(1);
        }
        return Joiner.on("").join(pieces);
    }

    private ClassLoader getClassLoader() {
        if (_classLoader != null)
            return _classLoader;

        try {
            _classLoader = new URLClassLoader(new URL[] {
                    _jarFile.toURI().toURL()
            }, null);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        return _classLoader;
    }
}

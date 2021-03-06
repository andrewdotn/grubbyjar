package ca.neitsch.grubbyjar;

import com.google.common.collect.ImmutableMap;
import org.jruby.runtime.load.LoadService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

public class JarRequireIntegTest
        extends GrubbyjarAbstractIntegTest
{
    @Rule
    public TemporaryFolder _folder2 = new TemporaryFolder();

    @Test
    public void testUsingGemThatIncludesJarRelativelyWithExtension()
    throws Exception
    {
        testWithJarRequireStatement(
                "require_relative 'ext/commons_codec.jar'");
    }

    @Test
    public void testUsingGemThatIncludesJarRelativelyWithoutExtension()
    throws Exception
    {
        testWithJarRequireStatement(
                "require_relative 'ext/commons_codec'");
    }

    @Test
    public void testUsingGemThatIncludesJarGenerallyWithExtension()
    throws Exception
    {
        testWithJarRequireStatement(
                "require 'ext/commons_codec.jar'");
    }

    @Test
    public void testUsingGemThatIncludesJarGenerallyWithoutExtension()
    throws Exception
    {
        testWithJarRequireStatement(
                "require 'ext/commons_codec'");
    }

    /**
     * This load mechanism, documented in {@link LoadService} is used by puma.
     * {@code require 'puma/puma_http11'} actually means to run {@code
     * puma/PumaHttp11Service.class}’s {@code basicLoad(Runtime)} method.
     */
    @Test
    public void testRequireServiceLoader() {
        String requireStatement = "require \"ext/commons_codec\"";
        testWithJarRequireStatement(requireStatement,
                () -> {
                    copyResourcesToDirectory("b64wrapper_loadservice", _folder2.getRoot(),
                            "b64wrapper.gemspec",
                            "build.gradle",
                            "lib/b64wrapper.rb",
                            "Gemfile",
                            "Gemfile.lock",
                            "src/main/java/ext/CommonsCodecService.java"
                    );
                    runGradle(_folder2,"dep");
                },
                "CommonsCodec");
    }

    private void testWithJarRequireStatement(String requireStatement) {
        testWithJarRequireStatement(requireStatement, null,
                "hello base64 gem");
    }

    /**
     * Set up a ruby script that depends on a gem that includes a jar, and try
     * to run it with both the jruby launcher and grubbyjar, to ensure that the
     * jar require statement actually works in both cases.
     */
    private void testWithJarRequireStatement(
            String requireStatement, Runnable postSetupAction,
            String expected)
    {
        copyResourcesToDirectory("b64wrapper", _folder2.getRoot(),
                ImmutableMap.of("REQUIRE_COMMONS_CODEC",
                        requireStatement),
                "b64wrapper.gemspec",
                "build.gradle",
                "lib/b64wrapper.rb",
                "Gemfile",
                "Gemfile.lock"
        );

        runGradle(_folder2, "dep");

        Path b64wrapperPath = Paths.get(_folder2.getRoot().toString());
        Path jardep2Path = Paths.get(_folder.getRoot().toString());
        String relativePath = jardep2Path.relativize(b64wrapperPath).toString();

        copyResourcesToDirectory("jardep2", _folder.getRoot(),
                ImmutableMap.of("REPLACE_ME", relativePath),
                ".ruby-version",
                "Gemfile",
                "Gemfile.lock",
                "build.gradle",
                "hello.rb");

        if (postSetupAction != null)
            postSetupAction.run();

        String rubyOutput = new BuildDirCommand("jruby", "-G", "hello.rb").run();
        assertThat("jruby -G output", rubyOutput, containsString(expected));

        runGradle();

        String jarOutput = runJar();
        assertThat("java -jar output", jarOutput, containsString(expected));
    }
}

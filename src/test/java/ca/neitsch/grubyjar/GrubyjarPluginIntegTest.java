package ca.neitsch.grubyjar;

import com.google.common.base.Joiner;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.jruby.embed.EvalFailedException;
import org.jruby.embed.ScriptingContainer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;

public class GrubyjarPluginIntegTest {
    @Rule
    public TemporaryFolder _folder = new TemporaryFolder();

    @Rule
    public ExpectedException _thrown = ExpectedException.none();

    private File _gradleBuildFile;

    private static final String HELLO_CONCURRENT_RB = textFromLines(
            "require 'concurrent'",
            "x = Concurrent::Event.new",
            "x.set",
            "puts x",
            "puts x.set");

    @Before
    public void createBuildFileObject()
    throws IOException
    {
        _gradleBuildFile = _folder.newFile("build.gradle");
    }

    @Test
    public void testHelloWorld()
    throws Exception
    {
        TestUtil.writeTextToFile(_gradleBuildFile,
                TestUtil.readResource("hello-world-script.gradle"));
        TestUtil.writeTextToFile(_folder.newFile("hello.rb"),
                "puts 'hello world'.upcase");

        runGradle();

        assertThat(runJar(), containsString("HELLO WORLD"));
    }

    @Test
    public void testAccessingUnbundledSystemGemShouldFail()
    throws Exception
    {
        new Gem("concurrent-ruby", "concurrent").ensureInstalled();

        TestUtil.writeTextToFile(_gradleBuildFile,
                TestUtil.readResource("hello-world-script.gradle"));

        textFile("hello.rb", HELLO_CONCURRENT_RB);

        runGradle();

        _thrown.expect(InvalidExitValueException.class);

        runJar();
    }

    @Test
    public void testAccessBundledGemShouldSucceed() throws Exception {
        TestUtil.writeTextToFile(_gradleBuildFile,
                TestUtil.readResource("hello-world-script.gradle"));

        TestUtil.writeTextToFile(_folder.newFile("Gemfile"),
                TestUtil.readResource("concurrent-ruby.Gemfile"));

        TestUtil.writeTextToFile(_folder.newFile("Gemfile.lock"),
                TestUtil.readResource("concurrent-ruby.Gemfile.lock"));

        textFile("hello.rb", HELLO_CONCURRENT_RB);

        runGradle();

        String output = runJar();
        assertThat(output, containsString("#<Concurrent::Event"));
        assertThat(output, endsWith("\ntrue\n"));
    }

    BuildResult runGradle() {
        return GradleRunner.create()
                .withProjectDir(_folder.getRoot())
                .withPluginClasspath()
                .withArguments("shadowJar")
                .forwardOutput()
                .build();
    }

    private void textFile(String name, String... lines)
    throws IOException
    {
        TestUtil.writeTextToFile(_folder.newFile(name),
                textFromLines(lines));
    }

    private static String textFromLines(String... lines) {
        return Joiner.on("\n").join(lines) + "\n";
    }

    String runJar()
    throws IOException, InterruptedException, TimeoutException
    {
        return new ProcessExecutor()
                .command("java", "-jar",
                        "build/libs/" + _folder.getRoot().getName() + ".jar")
                .directory(_folder.getRoot())
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8();
    }
}

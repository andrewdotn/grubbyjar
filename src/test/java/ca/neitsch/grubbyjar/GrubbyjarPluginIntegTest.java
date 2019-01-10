package ca.neitsch.grubbyjar;

import org.junit.Test;
import org.zeroturnaround.exec.InvalidExitValueException;

import java.io.File;
import java.time.ZonedDateTime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class GrubbyjarPluginIntegTest
        extends GrubbyjarAbstractIntegTest
{
    private static final String HELLO_CONCURRENT_RB = textFromLines(
            "require 'concurrent'",
            "x = Concurrent::Event.new",
            "x.set",
            "puts x",
            "puts x.set",
            "raise 'Version Mismatch' unless Concurrent::VERSION == '1.0.5'");

    private static final String HELLO_CONCURRENT_RB_1_1_1 = textFromLines(
            "require 'concurrent'",
            "x = Concurrent::Event.new",
            "x.set",
            "puts x",
            "puts x.set",
            "raise 'Version Mismatch' unless Concurrent::VERSION == '1.1.1'");

    @Test
    public void testHelloWorld()
    throws Exception
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);
        Util.writeTextToFile("puts 'hello world'.upcase",
                _folder.newFile("hello.rb"));

        runGradle();

        assertThat(runJar(), containsString("HELLO WORLD"));
    }

    @Test
    public void testGoodbyeWorld() throws Exception
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"), _gradleBuildFile);
        Util.writeTextToFile("at_exit{puts 'Goodbye World'.upcase}", _folder.newFile("hello.rb"));

        runGradle();
        assertThat(runJar(), containsString("GOODBYE WORLD"));
    }

    @Test
    public void testIncludesJavaBits()
    throws Exception
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);
        Util.writeTextToFile(textFromLines(
                "public class Foo {",
                "  public void sayHello() {",
                "    System.out.println(\"hello from java\".toUpperCase());",
                "  }",
                "}"),
                new File(_folder.getRoot(), "src/main/java/Foo.java"));
        Util.writeTextToFile(textFromLines(
                "require_relative 'lib/ext/" + _folder.getRoot().getName() + ".jar'",
                "Java::Foo.new.say_hello"),
                _folder.newFile("hello.rb"));

        runGradle("grubbyjar");

        String jrubyOutput = new BuildDirCommand("jruby", "hello.rb").run();
        assertThat(jrubyOutput, containsString("HELLO FROM JAVA"));

        assertThat(runJar(), containsString("HELLO FROM JAVA"));
    }

    @Test
    public void testSystemExit()
    throws Exception
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);

        textFile("hello.rb", "p ARGV.map(&:upcase)", "exit 2");

        runGradle();

        String date = ZonedDateTime.now().toString();
        String output = new BuildDirCommand()
                .addJarArguments(date)
                .exitValues(2)
                .run();
        assertThat(output, containsString(date.toUpperCase()));
    }

    @Test
    public void testRaiseWithJavaThread()
    throws Exception
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);

        textFile("hello.rb",
                "t = Java::javaLang::Thread.new do",
                "  i = 0",
                "  loop { puts i; i += 1; sleep 0.5 }",
                "end",
                "t.start",
                "raise 'blah'");

        runGradle();

        // This will time out if the exception on main doesn’t exit the program.
        new BuildDirCommand()
                .exitValues(1)
                .run();
    }

    @Test
    public void testSystemExitWithGemDeps()
    throws Exception
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);

        Util.writeTextToFile(TestUtil.readResource("concurrent-ruby.Gemfile"), _folder.newFile("Gemfile")
        );

        Util.writeTextToFile(TestUtil.readResource("concurrent-ruby.Gemfile.lock"), _folder.newFile("Gemfile.lock")
        );

        textFile("hello.rb", "p ARGV.map(&:upcase)", "exit 2");

        runGradle();

        String date = ZonedDateTime.now().toString();
        String output = new BuildDirCommand()
                .addJarArguments(date)
                .exitValues(2)
                .run();
        assertThat(output, containsString(date.toUpperCase()));
    }


    @Test
    public void testDoesntPreferLocalFiles()
    {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);

        textFile("hello.rb", "puts 'foo'");

        runGradle();

        textFile("hello.rb", "puts 'bar'");

        String output = runJar();
        assertThat(output, containsString("foo"));
        assertThat(output, not(containsString("bar")));
    }

    @Test
    public void testAccessingUnbundledSystemGemShouldFail()
    throws Exception
    {
        new SystemGem("concurrent-ruby", "concurrent").ensureInstalled();

        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"),
                _gradleBuildFile);

        textFile("hello.rb", HELLO_CONCURRENT_RB);

        runGradle();

        _thrown.expect(InvalidExitValueException.class);

        runJar();
    }

    @Test
    public void testAccessBundledGemShouldSucceed() throws Exception {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"), _gradleBuildFile
        );

        Util.writeTextToFile(TestUtil.readResource("concurrent-ruby.Gemfile"), _folder.newFile("Gemfile")
        );

        Util.writeTextToFile(TestUtil.readResource("concurrent-ruby.Gemfile.lock"), _folder.newFile("Gemfile.lock")
        );

        textFile("hello.rb", HELLO_CONCURRENT_RB);

        runGradle();

        String output = runJar();
        assertThat(output, containsString("#<Concurrent::Event"));
        assertThat(output, endsWith("\ntrue\n"));
    }

    @Test
    public void testNewServiceForConcurrentRuby111() throws Exception {
        Util.writeTextToFile(TestUtil.readResource("hello-world-script.gradle"), _gradleBuildFile
        );

        Util.writeTextToFile(TestUtil.readResource("concurrent-ruby.Gemfile"), _folder.newFile("Gemfile")
        );

        Util.writeTextToFile(TestUtil.readResource("concurrent-ruby.Gemfile.111.lock"), _folder.newFile("Gemfile.lock")
        );

        textFile("hello.rb", HELLO_CONCURRENT_RB_1_1_1);

        runGradle();

        String output = runJar();
        assertThat(output, containsString("#<Concurrent::Event"));
        assertThat(output, endsWith("\ntrue\n"));
    }

    @Test
    public void testGemspecHelloWorld() throws Exception {
        copyResourcesToDirectory("gemspec1", _folder.getRoot(),
                "Gemfile",
                "Gemfile.lock",
                "bin/gemspec1",
                "build.gradle",
                "gemspec1.gemspec",
                "lib/gemspec1.rb");

        runGradle();

        String output = runJar();
        assertThat(output, containsString("#<Concurrent::Event"));
    }

    @Test
    public void testExtraNestedDirectories() throws Exception {
        copyResourcesToDirectory("gemspec1", _folder.getRoot(),
                "Gemfile",
                "Gemfile.lock",
                "foo/bar/blah.json",
                "build.gradle",
                "gemspec1.gemspec");

        textFile("bin/gemspec1", textFromLines(
                "file = File.expand_path('../../foo/bar/blah.json', __FILE__)",
                "puts file",
                "puts IO.read(file)"));

        runGradle();

        String output = runJar();
        assertThat(output, containsString("{\"hello\": \"world\"}"));
    }

    @Test
    public void testJardepHelloWorld() throws Exception {
        copyResourcesToDirectory("jardep1", _folder.getRoot(),
                ".ruby-version",
                "Gemfile",
                "Gemfile.lock",
                "bin/jardep1",
                "build.gradle",
                "jardep1.gemspec",
                "lib/jardep1.rb",
                "settings.gradle");

        runGradle("grubbyjarRequire");

        String output = new BuildDirCommand("ruby", "-G", "bin/jardep1").run();
        assertThat(output, containsString("#<Concurrent::Event"));
        assertThat(output, containsString("hellohellohello"));
    }
}

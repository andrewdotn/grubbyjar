# Grubbyjar

Grubbyjar is a gradle plugin for building self-contained jruby jars.

Given a jruby script `foo.rb` and this `build.gradle`:

    plugins {
        id 'ca.neitsch.grubbyjar' version '0.0.1'
    }

    repositories {
        mavenCentral()
    }

    dependencies {
        runtimeOnly "org.jruby:jruby-complete:9.1.12.0"
    }

`gradle grubbyjar` will build a self-contained jar that runs `foo.rb` when
you run `java -jar build/libs/<project name>-grubbyjar.jar`. This
self-contained jar will contain not only the JRuby runtime and all java
library dependencies that you declare, but also all of the gems from the
Gemfile. And it will ignore any gems installed locally on the target
machine, so that you can reliably deploy JRuby apps without installing
any other jars, JRuby, or any gems on the target.

## Including java code

Because you’re already using Gradle which is a great java build system,
it’s super-easy to pull java code into jruby: just add java source in
`src/main/java` and it automatically gets built into your jar.

For example, the class defined in `src/main/java/org/example/Foo.java` is
[accessible from jruby][] as `Java::OrgExample::Foo`.

[accessible from jruby]: https://github.com/jruby/jruby/wiki/CallingJavaFromJRuby#referencing-java-classes-using-full-qualified-class-name

## Tasks

  - **`grubbyjar`**
    Builds the Grubbyjar

  - **`grubbyjarRequire`**
    Copies jar dependencies locally and generates a
    `lib/<gem_name>_jars.rb` file you can `require` to use java libraries
    when not running from a jar

  - **`shadowJar`**
    Grubbyjar configures the shadow plugin, so some things can be tweaked by
    adjusting the standard shadow configuration.

  - **`grubbyjarPrep`**
      is something that grubbyjar makes `shadowJar` depend on so that some
      needed files are in place when building the shadow jar

## Configuration

You can configure the name of the script to be run with

    grubbyjar {
        script 'foo.rb'
    }

By default, the gem’s default executable is used, or if not building a gem and
there is a single `*.rb` file in the project directory, it will be used.
Otherwise configuration will be needed.

## Troubleshooting

If required libraries aren’t being found, `-Djruby.debug.loadService`
enables debugging from the jruby side.

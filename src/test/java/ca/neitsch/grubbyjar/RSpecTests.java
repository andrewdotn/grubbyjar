package ca.neitsch.grubbyjar;

import org.jruby.embed.ScriptingContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RSpecTests {

    private ScriptingContainer scriptingContainer;

    @Before
    public void setup() {
        scriptingContainer = new ScriptingContainer();
    }

    @After
    public void tearDown() {
        scriptingContainer.getProvider().getRuntime().tearDown();
    }

    @Test
    public void determineGemFileSpec() {


        Object o = scriptingContainer.runScriptlet("require 'rspec'\n"
                + "RSpec::Core::Runner.run(['spec/determine_gem_file_spec.rb'])\n");
        long returnValue = (Long)o;
        assertEquals("rspec return value", 0, returnValue);
    }
}

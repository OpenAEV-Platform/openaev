package io.openaev.ocsf.parser;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.io.File;

public class GenerateParserTest {
    @Test
    void test() throws MojoExecutionException {
        new GenerateParser(new File(".."), "test").execute();
    }
}

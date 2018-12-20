/*
      Licensed under the Apache License, Version 2.0 (the "License");
      you may not use this file except in compliance with the License.
      You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

      Unless required by applicable law or agreed to in writing, software
      distributed under the License is distributed on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
      See the License for the specific language governing permissions and
      limitations under the License.
*/

package io.github.cucumber.plugin;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

/*
 * Goal which generates the runner classes for the failed scenarios.
 *
 * @author Anoop Sivarajan on 19/12/2018
 */
@Mojo(name = "rerun")
public class RunnerMojo extends AbstractMojo {
   @Parameter(property = "jsonResultFolder", defaultValue = "/cucumber-parallel/", required = true)
   private String jsonPath;

   @Parameter(property = "packageName", required = true)
   private String packageName;

   @Parameter(property = "steps", required = true)
   private String glue;


   public void execute() throws MojoFailureException {
      try {
         final String userDir = System.getProperty("user.dir");
         final Path dir = Paths.get(jsonPath);

         final File directory = new File(userDir + "/src/test/java/" + packageName.replaceAll("\\.", "/"));
         if (!directory.exists()) {
            directory.mkdirs();
         }

         if (directory.listFiles().length != 0) {
            Arrays.stream(directory.listFiles()).forEach(x -> x.delete());
         }

         final DirectoryStream<Path> files = Files.newDirectoryStream(dir, path -> path.toString().endsWith(".txt"));
         int[] index = {0};
         files.forEach(x -> {
            try {
               final String text = new String(Files.readAllBytes(x), StandardCharsets.UTF_8);
               final String className = String.format("FailedRunner%d.java", index[0]);
               final String plugin = String.format("json:%sFailed%d.json",
                     Paths.get(jsonPath).toUri().toString().replaceAll("file:///", "")
                     , index[0]);
               if (!text.trim().isEmpty()) {
                  getLog().info(text);
                  Writer writer = Files.newBufferedWriter(Paths.get(directory.getPath() + "/" + className));
                  writeTemplate(text, writer, className, plugin);
                  writer.close();
               }
            } catch (IOException e) {
               getLog().error(e.getMessage());
            }
            index[0]++;
         });
      } catch (IOException e) {
         getLog().error(e.getCause());
         throw new MojoFailureException(e.getMessage());
      }
   }

   /**
    * Writes the templates to the disk.
    *
    * @param feature
    * @param writer
    * @param className
    * @param plugin
    */
   public void writeTemplate(final String feature, Writer writer, final String className, final String plugin) {
      //TODO templates for junit and testng
      String name = "cucumber-serenity-runner.vm";
      VelocityEngine velocityEngine = getVelocityEngine();
      Template template = velocityEngine.getTemplate(name);
      template.merge(buildContext(feature, className, plugin), writer);
   }

   /**
    * Instantiates velocity engine
    *
    * @return {@link VelocityEngine}
    */
   private VelocityEngine getVelocityEngine() {
      final Properties props = new Properties();
      props.put("resource.loader", "class");
      props.put("class.resource.loader.class",
            "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
      VelocityEngine velocityEngine = new VelocityEngine(props);
      velocityEngine.init();
      return velocityEngine;
   }

   /**
    * Builds and updates the velocity context
    *
    * @param feature
    * @param className
    * @param plugin
    * @return {@link VelocityContext}
    */
   private VelocityContext buildContext(String feature, String className, String plugin) {
      VelocityContext context = new VelocityContext();
      context.put("featureFile", feature.trim());
      context.put("plugins", plugin);
      context.put("packageName", packageName);
      context.put("strict", true);
      context.put("monochrome", true);
      context.put("glue", glue);
      context.put("className", className.replaceAll(".java", ""));
      return context;
   }
}

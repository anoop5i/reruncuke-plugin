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

package com.github.reruncuke;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
@Mojo(name = "generate")
public class RunnerMojo extends AbstractMojo {

   /**
    * This property defines the json path and is required
    *
    * e.g., <jsonPath>${project.build.dir}/cucumber-parallel</jsonPath>
    */
   @Parameter(property = "jsonResultFolder", required = true)
   private String jsonPath;

   /**
    * This property value will be used as the package name for the failed test runners
    *
    * e.g.,<package>com.test.failed</package>
    */
   @Parameter(property = "packageName", required = true)
   private String packageName;

   /**
    * Property to add the glue package e.g., <glue>com.test.abc</glue>
    */
   @Parameter(property = "steps", required = true)
   private String glue;

   /**
    * type selection
    * <p>
    * <ul>
    * <li>SERENITY - for runners with cucumber serenity runner</li>
    * <li>JUNIT- for runners with Cucumber runner</li>
    * <li>TESTNG - for runners with TESTNG annotations ****Pending implementation****</li>
    * </ul>
    * </p>
    */
   @Parameter(property = "type", required = true)
   private String type;


   /**
    * This method locates all the failed rerun txt files from the jsonPath and creates separate runners for each txt file.
    * <p>
    * The generated runner classes will be saved under src/test/java and package name set in the config.
    * </p>
    *
    * @throws MojoFailureException Throws plugin failure exception
    */
   public void execute() throws MojoFailureException {
      if (type == null) {
         throw new MojoFailureException("Failed to find the type: expected JUNIT or SERENITY as type");
      }
      try {
         final String userDir = System.getProperty("user.dir");
         final Path dir = Paths.get(jsonPath);

         final File directory = new File(userDir + "/src/test/java/" + packageName.replaceAll("\\.", "/"));
         //create package if not found
         if (!directory.exists()) {
            directory.mkdirs();
         }

         if (Objects.requireNonNull(directory.listFiles()).length != 0) {
            Arrays.stream(Objects.requireNonNull(directory.listFiles())).forEach(File::delete);
         }
         final DirectoryStream<Path> files = Files.newDirectoryStream(dir, path -> path.toString().endsWith(".txt"));
         int[] index = {0};
         files.forEach(x -> {
            try {
               final String text = new String(Files.readAllBytes(x), StandardCharsets.UTF_8);
               String[] features = text.split("\\n");
               getLog().info(String.valueOf(features.length));
               //There will be more than one lines if they are executed in single thread.
               Arrays.stream(features).forEach(feature -> {
                  final String className = String.format("FailedRunner%d.java", index[0]);
                  final String plugin = String.format("json:target/cucumber-parallel/Failed%d.json", index[0]);
                  final String rerun = String.format("rerun:target/cucumber-parallel/Failed%d.txt", index[0]);
                  List<String> plugins = new ArrayList<>();
                  plugins.add(plugin);
                  plugins.add(rerun);

                  if (!feature.trim().isEmpty()) {
                     getLog().info(feature);
                     try {
                        Writer writer = Files.newBufferedWriter(Paths.get(directory.getPath() + "/" + className));
                        writeTemplate(feature, writer, className, plugins);
                        writer.close();
                        index[0]++;
                     } catch (IOException e) {
                        getLog().error(e);
                     }
                  }
               });
            } catch (IOException e) {
               getLog().error(e);
            }
         });
      } catch (IOException e) {
         getLog().error("Failed to parse the cucumber json files");
         getLog().error(e);
      }
   }

   /**
    * Writes the templates to the disk.
    *
    * @param feature the feature file name
    * @param writer file writer
    * @param className String class name
    * @param plugin List of plugins
    */
   private void writeTemplate(final String feature, Writer writer, final String className, final List<String> plugin) {
      String name;
      switch (RunnerType.valueOf(type)) {
         default:
         case JUNIT:
            name = "cucumber-junit-runner.vm";
            break;
         case SERENITY:
            name = "cucumber-serenity-runner.vm";
            break;
      }
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
      props.put("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
      VelocityEngine velocityEngine = new VelocityEngine(props);
      velocityEngine.init();
      return velocityEngine;
   }

   /**
    * Builds and updates the velocity context
    *
    * @param feature feature file name
    * @param className java class name
    * @param plugins json and rerun plugins
    * @return {@link VelocityContext}
    */
   private VelocityContext buildContext(final String feature, final String className, final List<String> plugins) {
      final VelocityContext context = new VelocityContext();
      context.put("featureFile", feature.trim());
      context.put("plugins", plugins);
      context.put("packageName", packageName);
      context.put("strict", true);
      context.put("monochrome", true);
      context.put("glue", glue);
      context.put("className", className.replaceAll(".java", ""));
      return context;
   }
}

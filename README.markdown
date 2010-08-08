Welcome to the youtrack-maven-plugin for Apache Maven 2.

## Available Goals

  * youtrack:create-version

## Configuring the plugin

In order to use this plugin, declare it in your pom as follows:

    <plugins>
      ...
      <plugin>
        <groupId>com.theoryinpractise</groupId>
        <artifactId>youtrack-maven-plugin</artifactId>
        <version>1.3.1</version>
        <configuration>
          <url>http://your/youtrack/server/here</url>
          <username>XXX</username>
          <password>XXX</password>
          <project>project-key</project>
          <descrpition>${project.description}</description>
          <iterationLength>14</iterationLength>
        </configuration>
      </plugin>
      ...
    </plugins>

## Creating a new Youtrack Version on release

The initial purpose of this plugin was to automatically create a new "version" record in a Youtrack
issue tracker when releasing an artifact.

This can be configured by adding the youtrack-maven-plugin to your maven-release-plugin's configuration:

    <plugins>
      ...
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-release-plugin</artifactId>
        <version>2.0</version>
        <configuration>
          <preparationGoals>clean verify</preparationGoals>
          <goals>deploy youtrack:create-version</goals>
        </configuration>
      </plugin>
      ...
    </plugins>


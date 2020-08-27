/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.maven

import org.junit.jupiter.api.Test
import org.openrewrite.Parser
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.maven.tree.Maven

class UpgradeVersionTest : RefactorVisitorTestForParser<Maven.Pom> {
    override val visitors: Iterable<RefactorVisitor<*>> = emptyList()
    override val parser: Parser<Maven.Pom> = MavenParser.builder().build()

    @Test
    fun upgradeVersion() = assertRefactored(
            visitors = listOf(UpgradeVersion().apply {
                setGroupId("org.springframework.boot")
                setToVersion("~1.5")
            }),
            before = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  
                  <dependencies>
                    <dependency>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot</artifactId>
                      <version>1.5.1.RELEASE</version>
                      <scope>test</scope>
                    </dependency>
                  </dependencies>
                </project>
            """,
            after = """
                    <project>
                      <modelVersion>4.0.0</modelVersion>
                      
                      <groupId>com.mycompany.app</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1</version>
                      
                      <dependencies>
                        <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot</artifactId>
                          <version>1.5.22.RELEASE</version>
                          <scope>test</scope>
                        </dependency>
                      </dependencies>
                    </project>
                """
    )

    @Test
    fun upgradeGuava() = assertRefactored(
            visitors = listOf(UpgradeVersion().apply {
                setGroupId("com.google.guava")
                setToVersion("25-28")
                setMetadataPattern("-jre")
            }),
            before = """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  
                  <groupId>com.mycompany.app</groupId>
                  <artifactId>my-app</artifactId>
                  <version>1</version>
                  
                  <dependencies>
                    <dependency>
                      <groupId>com.google.guava</groupId>
                      <artifactId>guava</artifactId>
                      <version>25.0-android</version>
                    </dependency>
                  </dependencies>
                </project>
            """,
            after = """
                    <project>
                      <modelVersion>4.0.0</modelVersion>
                      
                      <groupId>com.mycompany.app</groupId>
                      <artifactId>my-app</artifactId>
                      <version>1</version>
                      
                      <dependencies>
                        <dependency>
                          <groupId>com.google.guava</groupId>
                          <artifactId>guava</artifactId>
                          <version>28.0-jre</version>
                        </dependency>
                      </dependencies>
                    </project>
                """
    )
}

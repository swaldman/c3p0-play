val projectName = "c3p0-play"

version := "0.1.4"

scalaVersion := "2.10.7"

organization := "com.mchange"

name := projectName

scalacOptions := Seq("-feature", "-deprecation")

libraryDependencies ++= Seq(
  "com.mchange" % "c3p0" % "0.12.0",
  "com.mchange" %% "mlog-scala" % "0.4.1",
  "com.typesafe.play" %% "play-jdbc" % "2.2.0" % "compile,optional",
  "org.specs2" %% "specs2" % "2.3.4+" % "test"
)

resolvers += "Typesafe Releases" at "https://repo.typesafe.com/typesafe/releases/"

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (version.value.endsWith("-SNAPSHOT")) Some("central-snapshots" at centralSnapshots) else localStaging.value
}

pomExtra := (
      <url>https://github.com/swaldman/{projectName}</url>
      <licenses>
        <license>
          <name>GNU Lesser General Public License, Version 2.1</name>
          <url>http://www.gnu.org/licenses/lgpl-2.1.html</url> 
          <distribution>repo</distribution>
        </license>
        <license>
          <name>Eclipse Public License, Version 1.0</name>
          <url>http://www.eclipse.org/org/documents/epl-v10.html</url> 
          <distribution>repo</distribution>
        </license>
     </licenses>
     <scm>
       <url>git@github.com:swaldman/{projectName}.git</url>
       <connection>scm:git:git@github.com:swaldman/{projectName}.git</connection>
     </scm>
     <developers>
       <developer>
         <id>swaldman</id>
         <name>Steve Waldman</name>
         <email>swaldman@mchange.com</email>
       </developer>
     </developers>
  )








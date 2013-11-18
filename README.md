c3p0-play
=========

[Play Framework](http://www.playframework.com/) integrates the excellent [Bone CP](http://jolbox.com/) as its
default JDBC Connection pool. However, some users or applications may prefer to use other Connection pools for
a variety of reasons. This plug-in enables the use of [c3p0](http://www.mchange.com/projects/c3p0/) as an
effortless first-class alternative to Play's built-in pool.

### Quickstart ###

+ Configure your DataSources in the [ordinary Play way](http://www.playframework.com/documentation/2.2.x/ScalaDatabase).
+ Add the c3p0-play plug-in to your Play project's build.sbt as a libraryDependency:

```
libraryDependencies ++= Seq(
  "com.mchange" %%  "c3p0-play"  % "0.1.0-SNAPSHOT",
  "postgresql"  %   "postgresql" % "9.1-901-1.jdbc4"
)
```

+ Disable the default pool and enable c3p0 in your Play application's `conf/application.conf` file.

```
dbplugin=disabled
c3p0.play.enabled=true
```

+ Congratulations! You're app will now use c3p0. Please see below for configuration detals.

### Basic Configuration ###

The c3p0-play plugin is designed to make it extremely easy to "drop-in" c3p0, to drop-back to the
default BoneCP pool, or to mix the two pools. To enable that...

1. c3p0-play reads Play's default DataSource configuration and translates it to c3p0 configuration. Some (not so common) BoneCP configuration properties do not easily map to c3p0. Those will be ignored with a warning in the logs upon initiatlization. (They are also documented below.)
2. [_All_ c3p0 configuration](http://www.mchange.com/projects/c3p0/#configuration_properties) can be embedded directly in your `conf/application.conf` file. c3p0-native configuration will take precedence over play-style configuration for c3p0 pools.

For example:

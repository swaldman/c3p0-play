package com.mchange.sc.v2.c3p0.play;

import com.typesafe.config._;
import com.mchange.sc.v2.c3p0.play._;
import org.specs2._;
import scala.collection.JavaConverters._;

import scala.util.Try;
import play.api.Configuration;
import play.api.libs.JNDI;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.cfg.C3P0Config;

object C3P0ConfigurationSpec {
  def withCpds[T]( configName : String )( op : (ComboPooledDataSource) => T ) : T  = {
    val cpds = new ComboPooledDataSource( configName );
    try {
      op( cpds )
    } finally {
      cpds.close()
    }
  }

  def withConfiguration[T]( c3p0Configuration : Configuration, dumpConfig : Boolean = false )( op : => T ) = {
    classOf[C3P0Config].synchronized {
      try {
        updateC3P0Configuration( c3p0Configuration );
        if ( dumpConfig )
          C3P0Config.allCurrentProperties.entrySet.asScala.foreach( entry => println( s"${entry.getKey}=${entry.getValue}" ) );
        op
      } finally {
        revertC3P0Configuration()
      }
    }
  }
}


class C3P0ConfigurationSpec extends Specification {
  import C3P0ConfigurationSpec._;

  def is = s2"""
      Testing c3p0-play configuration...
          Parameters defined in c3p0-native format should shadow those defined in Play framework format  $e1
          c3p0 params defined in Play format are effective                                               $e2
          Distinct named configurations in Play format are honored                                       $e3
          c3p0-style named configurations override c3p0-style defaults                                   $e4
          c3p0-style named configurations override c3p0-style defaults (nested format)                   $e5
          Time parameters with units are properly interpreted                                            $e6
          ConnectionCustomizer params [initSQL, isolation, autocommit, defaultCatalog] become extensions $e7
          ConnectionCustomizer params provoke connectionCustomizerClassName                              $e8
          ConnectionCustomizer conflict provokes Exception                                               $e9
          ConnectionCustomizer customizes autocommit and isolation                                       $e10
          Import BoneCP-style size information                                                           $e11
          Local shadow of BoneCP-style size information                                                  $e12
          Global shadow of BoneCP-style size information                                                 $e13
          dataSourceName properly set for non-default name                                               $e14
          dataSourceName set to "default" for name "default"                                             $e15
          jndiName becomes an extension                                                                  $e17
             """;

  def e1 = {
    withConfiguration( testBasicConfiguration ){
      withCpds( "default" ){ ds =>
        ds.getAcquireIncrement() == 5
      }
    }
  }

  def e2 = {
    withConfiguration( testBasicConfiguration ){
      withCpds( "default" ){ ds =>
        ds.isTestConnectionOnCheckout() == true
      }
    }
  }

  def e3 = {
    withConfiguration( playStyleNamedConfigurations ){
      withCpds( "default" ){ ds1 =>
        withCpds( "awesome" ){ ds2 =>
          ds1.isTestConnectionOnCheckout() == false && ds2.isTestConnectionOnCheckout() == true
        }
      }
    }
  }

  def e4 = {
    withConfiguration( testC3P0NamedConfiguration1 ){
      withCpds( "default" ){ ds1 =>
        withCpds( "awesome" ){ ds2 =>
          ds1.getMinPoolSize()  == 2 && ds2.getMinPoolSize() == 10
        }
      }
    }
  }

  def e5 = {
    withConfiguration( testC3P0NamedConfiguration2 ){
      withCpds( "default" ){ ds1 =>
        withCpds( "awesome" ){ ds2 =>
          ds1.getMinPoolSize()  == 2 && ds2.getMinPoolSize() == 10
        }
      }
    }
  }

  def e6 = {
    withConfiguration( testTimeParameterSettings ){
      withCpds( "default" ){ ds1 =>
        List(
          ds1.getIdleConnectionTestPeriod == 1,
          ds1.getMaxAdministrativeTaskTime == 1,
          ds1.getMaxConnectionAge == 1,
          ds1.getMaxIdleTime == 1,
          ds1.getMaxIdleTimeExcessConnections == 1,
          ds1.getPropertyCycle == 1,
          ds1.getUnreturnedConnectionTimeout == 1,
          ds1.getAcquireRetryDelay == 1000,
          ds1.getCheckoutTimeout == 1000
        ).forall( bool => bool )
      }
    }
  }

  def e7 = {
    withConfiguration( testConnectionCustomizerCreation ){
      withCpds( "default" ){ ds1 =>
        val extensions = ds1.getExtensions.asInstanceOf[java.util.Map[String,String]]
        List(
          extensions.get( "play.initSQL" ).toString == "SET SCHEMA foo",
          extensions.get( "play.isolation" ).toString == "TRANSACTION_SERIALIZABLE",
          extensions.get( "play.autocommit" ).toString == "false",
          extensions.get( "play.defaultCatalog" ).toString == "Sears"
        ).forall( identity )
      }
    }
  }

  def e8 = {
    withConfiguration( testConnectionCustomizerCreation ){
      withCpds( "default" ){ ds1 =>
        ds1.getConnectionCustomizerClassName != null
      }
    }
  }

  def e9 = Try{ testConnectionCustomizerConflict }.isFailure

  def e10 = {
    withConfiguration( testConnectionCustomization ){
      withCpds( "default" ){ ds1 =>
        val conn = ds1.getConnection;
        val autoCommit = conn.getAutoCommit;
        val isolation = conn.getTransactionIsolation;
        conn.close;
        autoCommit == false && isolation == java.sql.Connection.TRANSACTION_SERIALIZABLE
      }
    }
  }

  def e11 = {
    withConfiguration( testBoneCpPoolSizeImports ){
      withCpds( "default" ){ ds =>

        ds.getMinPoolSize() == 16 && ds.getMaxPoolSize() == 24
      }
    }
  }

  def e12 = {
    withConfiguration( testBoneCpPoolSizeLocalShadow ){
      withCpds( "default" ){ ds =>
        ds.getMinPoolSize() == 5 && ds.getMaxPoolSize() == 15 //c3p0-default maxPoolSize
      }
    }
  }

  def e13 = {
    withConfiguration( testBoneCpPoolSizeTopLevelShadow ){
      withCpds( "default" ){ ds =>
        ds.getMinPoolSize() == 3 && ds.getMaxPoolSize() == 25 //c3p0-default minPoolSize
      }
    }
  }

  def e14 = {
    withConfiguration( testC3P0NamedConfiguration1 ){
      withCpds( "awesome" ){ ds =>
        ds.getDataSourceName() == "awesome"
      }
    }
  }

  def e15 = {
    withConfiguration( testC3P0NamedConfiguration1 ){
      withCpds( "default" ){ ds =>
        ds.getDataSourceName() == "default"
      }
    }
  }

  /*
   No... we'd need to start the plugin for this to work.
   The mere configuration doesn't bind us to the InitialContext.

  jndiName properly set                $e16

  def e16 = {
    withConfiguration( testJndiName ){
      withCpds( "default" ){ ds =>
        JNDI.initialContext.lookup( "jdbc/myDataSource" ) eq ds
      }
    }
  }
  */ 

  def e17 = {
    withConfiguration( testJndiName ){
      withCpds( "default" ){ ds =>
        ds.getExtensions().get("jndiName") == "jdbc/myDataSource" 
      }
    }
  }

  def makeConfiguration( s : String ) = C3P0PlayConfig( Configuration ( ConfigFactory.parseString ( s ) ) ).configuration;

  val testBasicConfiguration = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.default.acquireIncrement=1
db.default.testConnectionOnCheckout=true

c3p0.acquireIncrement=5
c3p0.minPoolSize=10
c3p0.maxPoolSize=30
    """
  );

  val playStyleNamedConfigurations = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.awesome.driver=org.postgresql.Driver
db.awesome.url="jdbc:postgresql://localhost/awesomedb"
db.awesome.user=superlative
db.awesome.password=hushhush

c3p0.minPoolSize=2
c3p0.maxPoolSize=10

db.default.testConnectionOnCheckout=false
db.awesome.testConnectionOnCheckout=true
    """
  );

  val testC3P0NamedConfiguration1 = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.awesome.driver=org.postgresql.Driver
db.awesome.url="jdbc:postgresql://localhost/awesomedb"
db.awesome.user=superlative
db.awesome.password=hushhush

c3p0.minPoolSize=2
c3p0.maxPoolSize=10
c3p0.testConnectionOnCheckout=false

c3p0.named-configs.awesome {
  minPoolSize=10
  maxPoolSize=30
  testConnectionsOnCheckout=true
  preferredTestQuery="SELECT 1"
}
    """
  );

  val testC3P0NamedConfiguration2 = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.awesome.driver=org.postgresql.Driver
db.awesome.url="jdbc:postgresql://localhost/awesomedb"
db.awesome.user=superlative
db.awesome.password=hushhush

c3p0 {
  minPoolSize=2
  maxPoolSize=10
  testConnectionOnCheckout=false

  named-configs {
    awesome {
      minPoolSize=10
      maxPoolSize=30
      testConnectionsOnCheckout=true
      preferredTestQuery="SELECT 1"
    }
  }
}
    """
  );

  val testTimeParameterSettings = makeConfiguration (
    """
c3p0 {
   # Seconds params
   idleConnectionTestPeriod=1000 ms
   maxAdministrativeTaskTime=1000 ms
   maxConnectionAge=1000 ms
   maxIdleTime=1000 ms
   maxIdleTimeExcessConnections=1000 ms
   propertyCycle=1000 ms
   unreturnedConnectionTimeout=1000 ms

   # Milliseconds params
   acquireRetryDelay=1 second
   checkoutTimeout=1 second
}     
    """
  );

  val testConnectionCustomizerCreation = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret
db.default.initSQL=SET SCHEMA foo
db.default.isolation=TRANSACTION_SERIALIZABLE
db.default.autocommit=false
db.default.defaultCatalog=Sears
   """
  );

  def testConnectionCustomizerConflict = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret
db.default.initSQL=SET SCHEMA foo
db.default.isolation=TRANSACTION_SERIALIZABLE
db.default.autocommit=false
db.default.defaultCatalog=Sears
db.default.connectionCustomizerClassName=foo
   """
  );

  val testConnectionCustomization = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret
db.default.autocommit=false
db.default.isolation=TRANSACTION_SERIALIZABLE
   """
  );

  val testBoneCpPoolSizeImports = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.default.partitionCount=4
db.default.minConnectionsPerPartition=4
db.default.maxConnectionsPerPartition=6
   """
  );

  val testBoneCpPoolSizeLocalShadow = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.default.partitionCount=4
db.default.minConnectionsPerPartition=4
db.default.maxConnectionsPerPartition=6
db.default.minPoolSize=5
   """
  );

  val testBoneCpPoolSizeTopLevelShadow = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret

db.default.partitionCount=4
db.default.minConnectionsPerPartition=4
db.default.maxConnectionsPerPartition=6
db.default.maxPoolSize=25
   """
  );

  val testJndiName = makeConfiguration(
    """
dbplugin=disabled
c3p0.play.enabled=true

db.default.driver=org.h2.Driver
db.default.url="jdbc:h2:mem:play"
db.default.user=sa
db.default.password=secret
db.default.jndiName="jdbc/myDataSource"

db.default.acquireIncrement=1
db.default.testConnectionOnCheckout=true

c3p0.acquireIncrement=5
c3p0.minPoolSize=10
c3p0.maxPoolSize=30
    """
  );

}



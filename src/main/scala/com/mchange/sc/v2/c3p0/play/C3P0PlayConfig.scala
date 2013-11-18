/*
 * Distributed as part of c3p0-play 0.1.0
 *
 * Copyright (C) 2013 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.sc.v2.c3p0.play;

import scala.util.Try;
import scala.util.Failure;

import play.api.Application;
import play.api.Configuration;

import com.mchange.v2.c3p0.AbstractConnectionCustomizer;
import scala.collection.JavaConverters._;

import com.typesafe.config._;

import com.mchange.sc.v1.log._;
import MLevel._;

object C3P0PlayConfig {
  implicit val logger = MLogger( this );

  val DefaultDataSourceName = "default"; //token expected by play.api.db.DB

  val DataSourceNamesKey       = "c3p0.play.dataSourceNames";
  val ImportPlayStyleConfigKey = "c3p0.play.importPlayStyleConfig";

  val EnabledConfigKey     = "c3p0.play.enabled";

  val NamedConfigPrefix = "c3p0.named-configs.";
  val ImportedConfigOriginDescription = "c3p0-play-style-db-configuration"; 

  object PlayBoneCpConfig {
    val autocommit     = "autocommit";
    val isolation      = "isolation";
    val defaultCatalog = "defaultCatalog";
    val initSQL        = "initSQL";

    private[this] val ConnectionCustomizerParams = Set(
      autocommit,
      isolation,
      defaultCatalog,
      initSQL
    );

    val ConnectionCustomizerParamsStringfied = ConnectionCustomizerParams.mkString("[\"","\", \"","\"]");

    private[this] val UnsupportedParams = Set(
      "logStatements",
      "maxConnectionsPerPartition",
      "minConnectionsPerPartition",
      "partitionCount",
      "statisticsEnabled"
    );

    // names that have to be transformed only
    private[this] val ConfigNameMappings = Map (
      "driver" -> "driverClass",
      "url" -> "jdbcUrl",
      "pass" -> "password",
      "connectionTestStatement" -> "preferredTestQuery",
      "connectionTimeout" -> "clientTimeout",
      "idleMaxAge" -> "maxIdleTime",
      "jndiName" -> "extensions.jndiName"
    );

    private[this] val IntRegex = """^[\+\-]?\s*\d+$""".r;

    private[this] val StringIntSecsToMillis : Function[String,String] = // if a straight int, secs to millisecs, otherwise leave alone
      ( str : String ) => IntRegex.findPrefixMatchOf(str).fold( str )( m => (m.matched.toInt * 1000).toString ) 

    private[this] val StringIntMillisToSecs : Function[String,String] = // if a straight int, secs to millisecs, otherwise leave alone
      ( str : String ) => IntRegex.findPrefixMatchOf(str).fold( str )( m => (m.matched.toInt / 1000).toString ) 

    private[this] val ConfigValueMappings : Map[String,Function1[String,String]] = Map(
      "connectionTimeout" -> StringIntSecsToMillis,
      "idleMaxAge" -> StringIntMillisToSecs
    );

    private[this] val identityFunction : Function1[String,String] = (str : String) => str

    private[this] def transformPlayBoneCpTuple( tup : Pair[String,String] ) : Pair[String,String] = {
      val xformedKey = ConfigNameMappings.getOrElse( tup._1, tup._1 );
      val xformedVal = ConfigValueMappings.getOrElse( tup._1, identityFunction )(tup._2);
      Pair( xformedKey, xformedVal )
    }

    private[this] def extractPlayBoneCpConfig( config : Config ) : PlayBoneCpConfig = {
      import com.typesafe.config._;

      val configKeys = config.entrySet().asScala.map( _.getKey() );

      val importedNames = {
        val AnyNameRegex = """db\.([^\.]+)\.[^\.]+""".r;
        configKeys
          .map( AnyNameRegex.findPrefixMatchOf( _ ) )
          .filter( _ != None )
          .map( _.get.group(1) )
      }

      val namedConfigMaps : Map[String,NamedConfig] = {
        def namedConfigForName( name : String ) : NamedConfig = {
          val SpecificNameRegex = ("""db\.""" + name + """\.(.*)""").r;
          val perNameKeysAndStripped = configKeys
            .map( SpecificNameRegex.findPrefixMatchOf( _ ) )
            .filter( _ != None )
            .map( _.get )
            .map( mtch => Pair( mtch.group(0), mtch.group(1) ) );
          val perNameTuples = perNameKeysAndStripped
            .map( tup => Pair( tup._2, config.getString( tup._1 ) ) );
          val xformedPerNameTuples = perNameTuples
            .map( transformPlayBoneCpTuple )
          val ( unsupportedTuples, supportedTuples ) = xformedPerNameTuples.partition( tup => UnsupportedParams( tup._1 ) );
          unsupportedTuples.foreach { tup =>
            WARNING.log( s"Configuration key '${tup._1}' not supported by c3p0, will be ignored for DataSource '${name}'." );
          }
          val (ccTuples, ordTuples) = supportedTuples.partition( tup => ConnectionCustomizerParams( tup._1 ) );
          NamedConfig( ordTuples.toMap, ccTuples.toMap )
        }
        importedNames.map { name => 
          Pair( name, namedConfigForName ( name ) ) 
        }.toMap;
      }

      PlayBoneCpConfig( namedConfigMaps );
    }


    def apply( config : Config ) = extractPlayBoneCpConfig( config );
  }

  case class NamedConfig( val ordinaryConfig : Map[String,String], val connectionCustomizerConfig : Map[String,String] );
  case class PlayBoneCpConfig( val data : Map[String, NamedConfig] );

  val SecondsParams = Set(
    "idleConnectionTestPeriod",
    "maxAdministrativeTaskTime",
    "maxConnectionAge",
    "maxIdleTime",
    "maxIdleTimeExcessConnections",
    "propertyCycle",
    "unreturnedConnectionTimeout"
  );

  val MillisecondsParams = Set(
    "acquireRetryDelay",
    "checkoutTimeout"
  );

  def isolationCode( str : String ) : Int = str.toUpperCase match {
    case "TRANSACTION_NONE" => java.sql.Connection.TRANSACTION_NONE;
    case "TRANSACTION_READ_COMMITTED" => java.sql.Connection.TRANSACTION_READ_COMMITTED;
    case "TRANSACTION_READ_UNCOMMITTED" => java.sql.Connection.TRANSACTION_READ_UNCOMMITTED;
    case "TRANSACTION_REPEATABLE_READ" => java.sql.Connection.TRANSACTION_REPEATABLE_READ;
    case "TRANSACTION_SERIALIZABLE" => java.sql.Connection.TRANSACTION_SERIALIZABLE;
    case _ => throw new IllegalArgumentException(s"Unknown transaction isolation level: '${str}'");
  }

  object Customizer {
    object Key {
      private[this] def p( str : String ) = "play." + str;

      def fromPlayCpKey( pcpk : String ) = p( pcpk );

      val autocommit     = p( PlayBoneCpConfig.autocommit );
      val isolation      = p( PlayBoneCpConfig.isolation );
      val defaultCatalog = p( PlayBoneCpConfig.defaultCatalog );
      val initSQL        = p( PlayBoneCpConfig.initSQL );
    }
  }
  class Customizer extends AbstractConnectionCustomizer {
    override def onCheckOut( c : java.sql.Connection, parentDataSourceIdentityToken : String ) {
      val extensions : java.util.Map[String,String] = extensionsForToken( parentDataSourceIdentityToken ).asInstanceOf[java.util.Map[String,String]];

      import Customizer.Key;
      val au = extensions.get( Key.autocommit );
      val is = extensions.get( Key.isolation );
      val de = extensions.get( Key.defaultCatalog );
      val in = extensions.get( Key.initSQL );

      if ( au != null ) c.setAutoCommit( au.toBoolean );
      if ( is != null ) c.setTransactionIsolation( isolationCode( is ) );
      if ( de != null ) c.setCatalog( de );

      if (in != null) {
        var stmt : java.sql.Statement = null;
        try {
          stmt = c.createStatement();
          stmt.executeUpdate( in );
        } finally {
          if ( stmt != null ) stmt.close();
        }
      }
    }
  }
  def apply( application : Application ) = new C3P0PlayConfig( application )
}

class C3P0PlayConfig( application : Application ){
  import C3P0PlayConfig._;

  private[this] val shouldImportPlayStyleConfig : Boolean = application.configuration.getBoolean( ImportPlayStyleConfigKey ).getOrElse( true );

  val config : Config = {
    val dsnamed = listifyDataSourceNames( application.configuration.underlying );
    val withImports = if ( shouldImportPlayStyleConfig ) {
      val imported = PlayBoneCpConfig( dsnamed );
      mergeConfiguration( dsnamed, imported );
    } else {
      dsnamed;
    }
    val unpackedUrls = unpackPlaySpecialUrlFormats( withImports );
    normalizeTimeValues( unpackedUrls )
  }

  val configuration : Configuration = Configuration( config )

  private[this] def reportError(path: String, message: String, e : Option[Throwable] = None) : Nothing = throw application.configuration.reportError( path, message, e );

  private[this] def listifyDataSourceNames( beforeFix : Config ) : Config = {
    import java.util.Arrays;
    def fromStringArray( arr : Array[String] ) = beforeFix.withValue( DataSourceNamesKey, ConfigValueFactory.fromAnyRef( Arrays.asList( arr : _* ) ) );
    try {
      val rawValue = beforeFix.getAnyRef( DataSourceNamesKey );
      rawValue match {
        case _ : java.util.List[_] => beforeFix;
        case str : String => fromStringArray( str.split("""\s*,\s*""") )
        case _ => throw reportError( DataSourceNamesKey, s"Can't parse, neither a List nor comma-separated String: '${rawValue}'" )
      }
    } catch {
      case e : ConfigException.Missing => fromStringArray( Array[String]() );
    }
  }

  private[this] def mergeConfiguration( premerge : Config, imported : PlayBoneCpConfig ) : Config = {
    def mergeDataSourceNames( into : Config ) : Config = {
      val startList = into.getStringList( DataSourceNamesKey );
      val javaSet = new java.util.HashSet( startList );
      javaSet.addAll( imported.data.keySet.asJava );
      into.withValue( DataSourceNamesKey, ConfigValueFactory.fromAnyRef( new java.util.ArrayList( javaSet ) ) )
    }
    def mergeOrdinaryConfig( into : Config, name : String, namedConfig : NamedConfig ) : Config = {
      val importedBindings = ConfigValueFactory.fromAnyRef( namedConfig.ordinaryConfig.asJava, ImportedConfigOriginDescription );
      into.withFallback( ConfigFactory.empty( ImportedConfigOriginDescription ).withValue( NamedConfigPrefix + name, importedBindings ) );
    }
    def connectionCustomizerClassNameKey( name : String ) : String = {
      val middle = if ( name == null ) "" else (".named-configs." + name);
      s"c3p0{$middle}.connectionCustomizerClassName"
    }
    def setupConnectionCustomizer( into : Config, name : String, namedConfig : NamedConfig ) : Config = {
      if (! namedConfig.connectionCustomizerConfig.isEmpty ) {
        val ncKey = connectionCustomizerClassNameKey(name);
        val genKey = connectionCustomizerClassNameKey(null);
        ( Try{ into.getString( ncKey ) }, Try{  into.getString( genKey ) } ) match {
          case ( Failure( exc1 : ConfigException.Missing ), Failure( exc2 : ConfigException.Missing ) ) => {
            // Yay, no custom ConnectionCustomizers set!
            namedConfig.connectionCustomizerConfig.foldLeft( into ) { ( config, tup ) =>
              config.withValue( NamedConfigPrefix + Customizer.Key.fromPlayCpKey( tup._1 ), ConfigValueFactory.fromAnyRef( tup._2 ) )
            }
          }
          case tup => {
            val customizers = {
              var customizersSet = collection.mutable.Set.empty[String]
              tup._1.foreach{ customizersSet += _ }
              tup._2.foreach{ customizersSet += _ }
              customizersSet.mkString("'","', '","'");
            }
            val errKey = if ( tup._1.isSuccess ) ncKey else genKey;
            val errMessage =
              """|A DataSource with name '%s' is configured with user-specified ConnectionCustomizers ('%s'),
                 |but has also specified parameters for the default ConnectionCustomizer %s. Please resolve
                 |the conflict.""".stripMargin.format( name, customizers, PlayBoneCpConfig.ConnectionCustomizerParamsStringfied );
            reportError( errKey, errMessage );
          }
        }
      } else {
        into // the original config unchanged
      }
    }

    val mergedNames = mergeDataSourceNames( premerge );
    val newConfig : Config = imported.data.foldLeft( mergedNames ){ (config, tup) => 
      val step1 = mergeOrdinaryConfig( config, tup._1, tup._2 );
      val step2 = setupConnectionCustomizer( step1, tup._1, tup._2 );
      step2
    }
    newConfig
  }
  private[this] def normalizeTimeValues( before : Config ) : Config = {
    def isC3P0InSetPath( set : Set[String], path : String ) = path.startsWith("c3p0.") && set.exists( path.endsWith( _ ) );
    def isC3P0SecondsPath( path : String ) = isC3P0InSetPath( SecondsParams, path );
    def isC3P0MillisecondsPath( path : String ) = isC3P0InSetPath( MillisecondsParams, path );
    def keyToSeconds( config : Config, path : String ) : Long = keyToTime( 1000, config, path );
    def keyToMilliseconds( config : Config, path : String ) : Long = keyToTime( 1, config, path );
    def keyToTime( millisDivisor : Long, config : Config, path : String ) : Long = {
      val stringValue = config.getString( path ).trim;
      try {
        try { stringValue.toLong }
        catch {
          case nfe : NumberFormatException => ( config.getMilliseconds( path ) / millisDivisor )
        }
      } catch {
        case e : Exception => reportError( path, "Could not convert '${stringValue}' to a number of time value.", Some(e) )
      }
    }
    val paths = before.entrySet.asScala.map( _.getKey() );
    val secondsPaths = paths.filter( isC3P0SecondsPath );
    val secondsConverted = secondsPaths.foldLeft( before ){ ( config, path ) => 
      config.withValue( path, ConfigValueFactory.fromAnyRef( keyToSeconds( config, path ) ) )
    }
    val millisecondsPaths = paths.filter( isC3P0MillisecondsPath );
    val secondsAndMillisecondsConverted = millisecondsPaths.foldLeft( secondsConverted ){ ( config, path ) => 
      config.withValue( path, ConfigValueFactory.fromAnyRef( keyToMilliseconds( config, path ) ) )
    }
    secondsAndMillisecondsConverted
  }

  // taking some undocumented conventions from
  // https://github.com/playframework/playframework/blob/2.2.1-RC1/framework/src/play-jdbc/src/main/scala/play/api/db/DB.scala
  def unpackPlaySpecialUrlFormats( into : Config ) : Config = {
    def isC3P0JdbcUrlPath( path : String ) : Boolean = path.startsWith("c3p0.") && path.endsWith( "jdbcUrl" );
    case class ParsedUrl( jdbcDriver: String, jdbcUrl : String, user : String, password : String );
    def parseSpecialUrl( url : String ) : Option[ParsedUrl] = {
      case class DbmsInfo( jdbcDriver : String, jdbcUrlTag : String, defaultQueryString : Option[String] );
      val infoMap = Map (
        "postgres" -> DbmsInfo( "org.postgresql.Driver", "postgresql", None ),
        "mysql" -> DbmsInfo( "com.mysql.jdbc.Driver", "mysql", Some("""?useUnicode=yes&characterEncoding=UTF-8&connectionCollation=utf8_general_ci""") )
      )
      def nullToEmpty( s : String ) = if ( s == null ) "" else s;
      def buildQuery( found : String, dbmsInfo : DbmsInfo ) = {
        if ( found == null ) {
          dbmsInfo.defaultQueryString.getOrElse("")
        } else {
          found
        }
      }
      def buildJdbcUrl( host : String, portStr : String, dbname : String, queryStr : String, dbmsInfo  : DbmsInfo ) = {
        s"jdbc:${dbmsInfo.jdbcUrlTag}://${host}${nullToEmpty(portStr)}/${dbname}${buildQuery(queryStr, dbmsInfo)}"
      }
      try {
        val SpecialUrl = """^(postgres|mysql)://([^:]+):([^@]+)@([^:/]+)(:\d+)?/([^?]+)(\?.+)?$""".r;
        val SpecialUrl(dbmskey, user, password, host, port, dbname, query) = url;
        val info = infoMap.get( dbmskey );
        info.fold( None.asInstanceOf[Option[ParsedUrl]] /* why is the cast necessary here? */ ){ dbmsInfo =>
          val jdbcDriver = dbmsInfo.jdbcDriver;
          val jdbcUrl = buildJdbcUrl( host, port, dbname, query, dbmsInfo );
          Some( ParsedUrl( jdbcDriver, jdbcUrl, user, password ) )
        }
      } catch {
        case me : MatchError => None
      }
    }
    def getStringOrElse( config : Config, path : String, dflt : String ) : String = {
      try { config.getString( path ) }
      catch {
        case cem : ConfigException.Missing => dflt
      }
    }
    def withOverrides( config : Config, map : Map[String,String] ) = {
      map.foldLeft( config ){ (config, tup) =>
        config.withValue( tup._1, ConfigValueFactory.fromAnyRef( tup._2 ) )
      }
    }
    into.entrySet.asScala.map( _.getKey ).filter( isC3P0JdbcUrlPath ).foldLeft( into ) { ( config, path ) =>
      val url = config.getString( path );
      val pathPrefix = path.substring(0, path.lastIndexOf('.'));
      parseSpecialUrl( url ).fold( config ) { parsedUrl =>
        val k_jdbcUrl = pathPrefix + ".jdbcUrl";
        val k_user = pathPrefix + ".user";
        val k_password = pathPrefix + ".password";
        val k_jdbcDriver = pathPrefix + ".jdbcDriver";
        val overridesMap = Map(
          k_jdbcUrl -> parsedUrl.jdbcUrl, //we always override jdbcUrl
          k_user -> getStringOrElse( config, k_user, parsedUrl.user ),
          k_password -> getStringOrElse( config, k_password, parsedUrl.password ),
          k_jdbcDriver -> getStringOrElse( config, k_jdbcDriver, parsedUrl.jdbcDriver )
        )
        withOverrides( config, overridesMap )
      }
    }
  }
}

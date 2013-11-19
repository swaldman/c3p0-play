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

import play.api.Application;
import play.api.Configuration;
import play.api.db.DBApi;
import play.api.db.DBPlugin;
import play.api.Play;
import play.api.libs.JNDI;
import scala.util.Try;
import javax.sql.DataSource;
import com.mchange.v2.c3p0.ComboPooledDataSource;

import com.mchange.sc.v1.log._;
import MLevel._;

object C3P0PlayDBPlugin{
  implicit val logger = MLogger( this );
  val EagerKey = "c3p0.play.eager";
}

class C3P0PlayDBPlugin( application : Application ) extends DBPlugin {
  import C3P0PlayDBPlugin._;

  override def onStart() : Unit = {
    updateC3P0Configuration( c3p0Configuration, application );
    val eager = c3p0Configuration.getBoolean( EagerKey ).getOrElse( true );
    initialize( eager );
  }

  override def api: DBApi = c3p0DBApi;

  override def onStop() : Unit = {
    deinitialize;
    revertC3P0Configuration();
  }

  override lazy val enabled = {
    val _enabled = c3p0Configuration.getBoolean( C3P0PlayConfig.EnabledConfigKey ).getOrElse( true );
    if ( _enabled && c3p0Configuration.getString("dbplugin").getOrElse("enabled") != "disabled") {
      // SEVERE.log("dbplugin --> " + c3p0Configuration.getString("dbplugin") );
      val importConfig = c3p0Configuration.getBoolean( C3P0PlayConfig.ImportPlayStyleConfigKey ).getOrElse( true );
      if ( importConfig ) {
        WARNING.log(
          """|
             |TL; DR: set 'dbplugin=disabled' if you mean to use c3p0.
             |
             |Both c3p0 and Play's default BoneCP pooling are enabled, and both are configured
             |to import the default configuration. This will lead to duplicated DataSources, and
             |is probably not what you intend. You may wish to disable one of the two pooling
             |libraries. To disable c3p0, set 'c3p0.play.enabled=false' in application.conf.
             |To disable the default BoneCP pool, set 'dbplugin=disabled'. 
             |
             |If you wish to have a mixture of BoneCP and c3p0 DataSources, set 
             |'c3p0.play.importPlayStyleConfig=false' and define the c3p0 DataSources
             |as a comma-separated list under the key 'c3p0.play.dataSourceNames'
             |then configure c3p0 DataSources in c3p0-native HOCON config using default and named configurations. Please
             |see http://www.mchange.com/projects/c3p0/""".stripMargin
        )
      } else {
        INFO.log(
          """|
             |Play is configured to generate both c3p0 and BoneCP DataSources. DataSources
             |configured in play's default DataSource configuration format will be BoneCP
             |DataSources. Declare c3p0 DataSources as a comma-separated list under the 
             |key 'c3p0.play.dataSourceNames' then configure each DataSource as HOCON named 
             |configurations. Please see http://www.mchange.com/projects/c3p0/#named_configurations""".stripMargin
        )
      }
    }
    _enabled
  }

  private[this] lazy val c3p0Configuration : Configuration = C3P0PlayConfig( application ).configuration;

  private[this] lazy val c3p0DBApi : C3P0PlayDBApi = new C3P0PlayDBApi( c3p0Configuration, application );


  // TODO: ensure jndiName maps to an extension
  private[this] def initialize( eager : Boolean ) : Unit = {
    def tryInitialize( ds : DataSource, dsn : String ) : Unit = {
      val cpds = ds.asInstanceOf[ComboPooledDataSource]
      val jndiName = cpds.getExtensions.get( "jndiName" ).asInstanceOf[String];

      var dsInit   : Option[Int] = None;
      var jndiInit : Option[Int] = None;
      try {
        if ( eager )
          ds.getConnection.close;
        dsInit = Some(1);
        if ( jndiName != null ) {
          JNDI.initialContext.rebind( dsn, ds );
          jndiInit = Some(1);
        }

        val createdOrInitialized = if ( eager ) "created (but not initialized)" else "initialized";
        INFO.log(s"c3p0 datasource '${cpds.getDataSourceName }' ${ createdOrInitialized }" + jndiInit.fold(".")( i => s" and bound under JNDI name '${jndiName}'." ) )

      } catch {
        case e : Exception => {
          SEVERE.log( s"Failed to initialize c3p0 DataSource ${cpds.getDataSourceName}.", e );
          dsInit.map( i => 
            Try{ cpds.close }.recover {
              case t : Throwable => WARNING.log( s"Exception on close of DataSource '${cpds.getDataSourceName}'.", t )
            } 
          );
          jndiInit.map( i => 
            Try{ JNDI.initialContext.unbind( jndiName ) }.recover { 
              case t : Throwable => WARNING.log( s"Exception on unbinding jndiName '${jndiName}' for DataSource '${cpds.getDataSourceName}'.", t )
            }
          );
        }
      }
    }
    api.datasources.map{ tup =>
      tryInitialize( tup._1, tup._2 )
    }
  }

  private[this] def deinitialize = {
    def tryInitialize( ds : DataSource, dsn : String ) : Unit = {
      val cpds = ds.asInstanceOf[ComboPooledDataSource]
      val jndiName = cpds.getExtensions.get( "jndiName" ).asInstanceOf[String];

      Try{ cpds.close }.recover {
        case t : Throwable => WARNING.log( s"Exception on close of DataSource '${cpds.getDataSourceName}'.", t )
      }
      if ( jndiName != null ) {
        Try{ JNDI.initialContext.unbind( jndiName ) }.recover {
          case t : Throwable => WARNING.log( s"Exception on unbinding jndiName '${jndiName}' for DataSource '${cpds.getDataSourceName}'.", t )
        }
      }
      INFO.log( s"Closed and unbound c3p0 DataSource '${ cpds.getDataSourceName }'." );
    }
  }
}

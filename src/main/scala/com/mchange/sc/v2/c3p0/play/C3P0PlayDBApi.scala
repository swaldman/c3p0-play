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


import javax.sql.DataSource;
import play.api.Application;
import play.api.Configuration;
import play.api.db.DBApi;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import com.mchange.v2.c3p0.DataSources;

import scala.collection.JavaConverters._;

class C3P0PlayDBApi( c3p0Configuration : Configuration, application : Application ) extends DBApi {

  override lazy val datasources: List[(ComboPooledDataSource, String)] = {
    val namesJavaList : java.util.List[String] = c3p0Configuration.getStringList( C3P0PlayConfig.DataSourceNamesKey ).getOrElse( java.util.Collections.emptyList() );
    namesJavaList.asScala.map( name => ( new ComboPooledDataSource( name ), name ) ).toList;
  }

  override def shutdownPool(ds : DataSource) : Unit = DataSources.destroy( ds );

  override def getDataSource(name : String) : ComboPooledDataSource = namesToDataSources( name );

  override def getDataSourceURL(name : String): String = getDataSource( name ).getJdbcUrl()

  private[this] lazy val namesToDataSources = {
    val tupList = datasources.map( tup => ( tup._2, tup._1 ) );
    Map( tupList : _* );
  }
}

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

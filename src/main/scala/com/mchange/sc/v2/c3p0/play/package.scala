package com.mchange.sc.v2.c3p0;

import _root_.play.api.Application;
import _root_.play.api.Configuration;
import com.mchange.v2.c3p0.cfg.C3P0Config;
import com.mchange.v2.cfg.MultiPropertiesConfig;
import com.mchange.v3.hocon.HoconMultiPropertiesConfig;

package object play {
  // def updateC3P0Configuration( application : Application ) : Unit = updateC3P0Configuration( application.configuration, application );

  def updateC3P0Configuration( configuration : Configuration, application : Application ) : Unit = {
    val appId = "play-application:" + application.path.getPath;
    val mpc = new HoconMultiPropertiesConfig( appId, configuration.underlying );
    //com.mchange.v2.cfg.ConfigUtils.dumpByPrefix( mpc, "" );
    C3P0Config.refreshMainConfig( Array[MultiPropertiesConfig]( mpc ), appId );
  }
  def revertC3P0Configuration() : Unit = C3P0Config.refreshMainConfig();
}

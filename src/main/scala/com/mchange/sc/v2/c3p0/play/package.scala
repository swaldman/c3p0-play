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

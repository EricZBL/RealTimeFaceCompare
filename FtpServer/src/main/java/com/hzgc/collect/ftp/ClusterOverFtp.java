package com.hzgc.collect.ftp;

import com.hzgc.collect.expand.util.ClusterOverFtpProperHelper;
import com.hzgc.collect.expand.util.HelperFactory;
import com.hzgc.collect.ftp.util.FtpUtils;
import com.hzgc.util.common.FileUtil;
import org.apache.log4j.Logger;

import java.io.FileInputStream;
import java.io.Serializable;
import java.util.Properties;

public abstract class ClusterOverFtp implements Serializable {
    protected static int listenerPort = 0;
    protected static String passivePorts = null;
    protected static DataConnectionConfigurationFactory dataConnConf;

    public void loadConfig() throws Exception {

        dataConnConf = new DataConnectionConfigurationFactory();
        listenerPort = ClusterOverFtpProperHelper.getPort();
        passivePorts = ClusterOverFtpProperHelper.getDataPorts();
        if (passivePorts != null){
            dataConnConf.setPassivePorts(passivePorts);
        }
    }

public abstract void startFtpServer();
        }

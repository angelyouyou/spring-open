#!/usr/bin/env python

"""
onos.py: A basic (?) ONOS Controller() subclass for Mininet

We implement the following classes:

ONOSController: a custom Controller() subclass to start ONOS
OVSSwitchONOS: a custom OVSSwitch() switch that connects to multiple controllers.

We use single Zookeeper and Ramcloud instances for now.

As a custom file, exports:

--controller onos
--switch ovso

Usage:

$ sudo -E ./onos.py

This will start up a simple 2-host, 2 ONOS network

$ sudo -E mn --custom onos.py --controller onos,2 --switch ovso
"""

from mininet.node import Controller, OVSSwitch, CPULimitedHost, RemoteController
from mininet.net import Mininet
from mininet.cli import CLI
from mininet.topo import LinearTopo
from mininet.log import setLogLevel, info, warn
from mininet.util import quietRun, numCores

# This should be cleaned up to avoid interfering with mn
from shutil import copyfile
from os import environ, path
from functools import partial
import time
from sys import argv
from time import sleep

class ONOS( Controller ):
    "Custom controller class for ONOS"

    # Directories and configuration templates
    home = environ[ 'HOME' ]
    onosDir = home + "/ONOS"
    zookeeperDir = home + "/zookeeper-3.4.6"
    dirBase = '/tmp'
    logDir = dirBase + '/onos-logs'
    logbackFile = dirBase + '/onos-%s.logback.xml'

    # Additions for reactive forwarding
    reactiveModules = (
            'net.onrc.onos.apps.proxyarp.ProxyArpManager',
            'net.onrc.onos.core.main.config.DefaultConfiguration',
            'net.onrc.onos.apps.forwarding.Forwarding'
    )

    # Module parameters
    ofbase = 6633
    restbase = 8080
    jmxbase = 7189
    hcbase = 5701

    fc = 'net.floodlightcontroller.'

    # Things that vary per ONOS id
    perNodeConfigBase = {
        fc + 'core.FloodlightProvider.openflowport': ofbase,
        fc + 'restserver.RestApiServer.port': restbase,
        fc + 'core.FloodlightProvider.controllerid': 0
    }

    proctag = 'mn-onos-id'

    # List of scripts that we need/use
    scripts = ( 'onos.sh', 'start-rest.sh' )

    def __init__( self, name, n=1, reactive=True, runAsRoot=False, **params):
        """n: number of ONOS instances to run (1)
           reactive: run in reactive mode (True)
           runAsRoot: run ONOS as root (False)"""
        self.check()
        self.count = n
        self.reactive = reactive
        self.runAsRoot = runAsRoot
        self.ids = range( 0, self.count )
        Controller.__init__( self, name, **params )
        self.proxies = []
        # We don't need to run as root, and it can interfere
        # with starting Zookeeper manually
        self.user = None
        if not self.runAsRoot:
            try:
                self.user = quietRun( 'who am i' ).split()[ 0 ]
                self.sendCmd( 'su', self.user )
                self.waiting = False
            except:
                warn( '__init__: failed to drop privileges\n' )
        self.cmd( 'mkdir -p', self.logDir )
        # Need to run commands from ONOS dir
        self.cmd( 'cd', self.onosDir )
        self.cmd( 'export PATH=$PATH:%s' % self.onosDir )

    def check( self ):
        "Set onosDir and check for ONOS prerequisites"
        if not quietRun( 'which java' ):
                raise Exception( 'java not found -'
                                 ' make sure it is installed and in $PATH' )
        if 'ONOS_HOME' in environ:
            self.onosDir = environ[ 'ONOS_HOME' ]
        else:
            warn( '* $ONOS_HOME is not set - assuming %s\n' % self.onosDir )
        for script in self.scripts:
            script = path.join( self.onosDir, script )
            if not path.exists( script ):
                msg = '%s not found' % script
                if 'ONOS_HOME' not in environ:
                    msg += ' (try setting $ONOS_HOME and/or sudo -E)'
                raise Exception( msg )

    def waitNetstat( self, pid ):
        """Wait for pid to show up in netstat
           We assume that once a process is listening on some
           port, it is ready to go!"""
        while True:
            output = self.cmd( 'sudo netstat -natup | grep %s/' % pid )
            if output:
                break
            info( '.' )
            time.sleep( 1 )
        info( '\n* Process %d is listening\n' % pid  )

    def waitStart( self, procname, pattern, maxWait=10 ):
        "Wait for proces to start up and be visible to pgrep"
        # Check script exit code
        exitCode = int( self.cmd( 'echo $?' ) )
        if exitCode != 0:
            raise Exception( '%s startup failed with code %d' %
                             ( procname, exitCode ) )
        info( '* Waiting for %s startup' % procname )
        while True:
            result = self.cmd( 'pgrep -f %s' % pattern )
            if result:
                break
            info( '.' )
            sleep( 1 )
        pid = int( result.split()[ 0 ] )
        return pid

    def startRamcloud( self, cpu=.6 ):
        """Start Ramcloud
           cpu: CPU usage limit (in seconds/s)"""
        # Create a cgroup so Ramcloud doesn't eat all of our CPU
        ramcloud = CPULimitedHost( 'ramcloud', inNamespace=False, period_us=5000 )
        ramcloud.setCPUFrac( cpu / numCores() )
        info( '\n' )
        ramcloud.cmd( 'export PATH=%s:$PATH' % self.onosDir )
        ramcloud.cmd( 'export ONOS_LOGDIR=%s' % self.logDir )
        for daemon in 'coord', 'server':
            ramcloud.cmd( 'onos.sh rc-%s start' % daemon )
            pid = self.waitStart( 'Ramcloud %s' % daemon, 'obj.master/' + daemon )
            self.waitNetstat( pid )
            status = self.cmd( 'onos.sh rc-%s.sh status' % daemon )
            if 'running' not in status:
                raise Exception( 'Ramcloud %s startup failed: ' % daemon + status )
        self.ramcloud = ramcloud

    def stopRamcloud( self ):
        "Stop Ramcloud"
        for daemon in 'coord', 'server':
            self.ramcloud.cmd( 'onos.sh rc-%s stop' % daemon )
        self.ramcloud.terminate()

    def startZookeeper( self, initcfg=True ):
        "Start Zookeeper"
        self.cmd( 'onos.sh zk start' )
        pid = self.waitStart( 'Zookeeper', 'zookeeper' )
        self.waitNetstat( pid )
        status = self.cmd( 'onos.sh zk status' )
        if 'Error' in status:
            raise Exception( 'Zookeeper startup failed: ' + status )

    def stopZookeeper( self ):
        "Stop Zookeeper"
        self.cmd( 'onos.sh zk stop' )

    def getPropsFilename( self, id, path ):
        return path + '/onos-%s.properties' % id
        
    def genProperties( self, id, path='/tmp' ):
        "Generate ONOS properties file and return its full pathname"
        defaultProps = self.onosDir + '/conf/onos.properties'
        propsFile = self.getPropsFilename( id, path )
        with open( propsFile, 'w' ) as f:
            with open( defaultProps ) as d:
                for line in d.readlines():
                    prop = line.split( ' ' )[ 0 ]
                    val = self.perNodeConfigBase.get( prop, None )
                    if val:
                        # Write updated property
                        f.write( '%s = %s\n' % ( prop, val + id) )
                    else:
                        # Write original property
                        f.write( line )
                    if prop == 'floodlight.modules' and ',\\' in line:
                        if self.reactive:
                            # Insert reactive modules into list
                            for module in self.reactiveModules:
                                f.write( '%s,\\\n' % module )
        return propsFile

    def getConfsFilename( self, id, path ):
        return path + '/onos-%s.conf' % id
        
    def genConfig( self, id, path='/tmp' ):
        "Generate ONOS node config file and return its full pathname"
        confsFile = self.getConfsFilename( id, path )
        with open( confsFile, 'w' ) as f:
            f.write( 'host.ip = 127.0.0.1\n' )
            f.write( 'host.backend = ramcloud\n' )
            f.write( 'hazelcast.host.port = %s\n' % ( self.hcbase + 10 * id ) )
        return confsFile

    def setVarsGlobal( self, path='/tmp'):
        logdir = self.logDir
        self.cmd( 'export ONOS_LOGDIR=%s' % logdir )
        self.cmd( 'export ZK_LOG_DIR=%s' % logdir )

    def setVarsLocal( self, id, path='/tmp' ):
        """Set and return environment vars
           id: ONOS instance number
           propsFile: properties file name"""
        logback = self.logbackFile % id
        jmxport = self.jmxbase + id
        logdir = self.logDir
        self.cmd( 'export ONOS_LOGBASE=onos-%d.`hostname`' % id)
        self.cmd( 'export ONOS_LOGBACK="%s"' % logback )
        self.cmd( 'export JMX_PORT=%s' % jmxport )
        self.cmd( 'export JVM_OPTS="-D%s=%s"' % (
            self.proctag, id ) )
        propsFile = self.getPropsFilename( id, path )
        self.cmd( 'export ONOS_PROPS="%s"' % propsFile )
        confsFile = self.getConfsFilename( id, path )
        self.cmd( 'export ONOS_CONF="%s"' % confsFile )
        self.cmd( 'export HC_CONF="%s/hazelcast.%s.conf"' % ( path, id ) )

    def setupONOS (self, id):
        propsFile = self.genProperties( id )
        confFile = self.genConfig( id )
        self.setVarsLocal( id )
        self.cmd( 'onos.sh setup -f' )
        
    def startONOS( self, id ):
        """Start ONOS
           id: new instance number"""
        start = time.time()
        self.setVarsLocal( id )
        self.stopONOS( id )
        self.cmd( 'onos.sh core unchecked-start' )
        # onos.sh waits for ONOS startup
        elapsed = time.time() - start
        info( '* ONOS %s started in %.2f seconds\n' % ( id, elapsed ) )

    def stopONOS( self, id ):
        """Shut down ONOS
           id: identifier for instance"""
        pid = self.cmd( "jps -v | grep %s=%s | awk '{print $1}'" %
            ( self.proctag, id ) ).strip()
        if pid:
            self.cmd( 'kill', pid )

    def start( self, *args ):
        "Start ONOS instances"
        self.setVarsGlobal()
        # TODO: use onos-cluster.sh to setup/start/stop ONOS cluster
        for id in self.ids:
            info( '* Setting up ONOS %s\n' % id )
            self.setupONOS( id )
        info( '* Starting Zookeeper\n' )
        self.startZookeeper()
        info( '* Starting Ramcloud\n' )
        self.startRamcloud()
        for id in self.ids:
            info( '* Starting ONOS %s\n' % id )
            self.startONOS( id )
        self.cmd( 'start-rest.sh start' )
        # Initialize proxies for clist()
        self.proxies = [ RemoteController( 'onos-%d' % id, port=(self.ofbase + id ) )
            for id in range( 0, self.count ) ]

    def stop( self, *args ):
        "Stop ONOS instances"
        self.cmd( 'start-rest.sh stop' )
        for id in self.ids:
            info( '* Stopping ONOS %s\n' % id )
            self.stopONOS( id )
        info( '* Stopping Zookeeper\n' )
        self.stopZookeeper()
        info( '* Stopping Ramcloud\n' )
        self.stopRamcloud()
        for p in self.proxies:
            p.stop()
        p.proxies = []

    def clist( self ):
        "Return list of Controller proxies for this ONOS cluster"
        return self.proxies


class OVSSwitchONOS( OVSSwitch ):
    "OVS switch which connects to multiple controllers"
    def start( self, controllers ):
        assert len( controllers ) == 1
        c0 = controllers[ 0 ]
        assert type( c0 ) == ONOS
        controllers = c0.clist()
        OVSSwitch.start( self, controllers )


def waitConnected( switches ):
    "Wait until all switches connect to controllers"
    start = time.time()
    info( '* Waiting for switches to connect...\n' )
    for s in switches:
        info( s )
        while not s.connected():
            info( '.' )
            time.sleep( 1 )
        info( ' ' )
    elapsed = time.time() - start
    info( '\n* Connected in %.2f seconds\n' % elapsed )


controllers = { 'onos': ONOS }
switches = { 'ovso': OVSSwitchONOS }


if __name__ == '__main__':
    # Simple test for ONOS() controller class
    setLogLevel( 'info' )
    size = 2 if len( argv ) != 2 else int( argv[ 1 ] )
    net = Mininet( topo=LinearTopo( size ),
                   controller=partial( ONOS, n=2 ),
                   switch=OVSSwitchONOS )
    net.start()
    waitConnected( net.switches )
    CLI( net )
    net.stop()
